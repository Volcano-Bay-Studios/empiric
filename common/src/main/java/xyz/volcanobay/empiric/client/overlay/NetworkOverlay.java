package xyz.volcanobay.empiric.client.overlay;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;
import xyz.volcanobay.empiric.networking.packet.NetworkOverlayS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Network members sent by the server, one colored volume per network, drawn over blocks.
 */
public final class NetworkOverlay implements OverlayLayer {

    public static final NetworkOverlay INSTANCE = new NetworkOverlay();

    private static final double CELL_SIZE = 0.5;
    private static final float GOLDEN_RATIO = 0.618034f;

    private final Map<String, List<OverlayMesh>> meshes = new HashMap<>();

    private NetworkOverlay() {
    }

    public static int color(int network) {
        float hue = (network * GOLDEN_RATIO) % 1f;
        return Mth.hsvToRgb(hue < 0 ? hue + 1 : hue, 0.7f, 1f);
    }

    public void apply(NetworkOverlayS2CPacket packet) {
        remove(packet.networkType());
        if (!packet.visible()) {
            return;
        }
        List<OverlayMesh> built = new ArrayList<>();
        for (NetworkOverlayS2CPacket.Network network : packet.networks()) {
            Set<SpatialPos> members = new HashSet<>(network.members());
            int color = color(network.id());
            OverlayMesh mesh = OverlayMesh.build(CELL_SIZE, members, (x, y, z) -> members.contains(new SpatialPos(x, y, z)), (x, y, z) -> color);
            if (mesh != null) {
                built.add(mesh);
            }
        }
        meshes.put(packet.networkType(), built);
    }

    private void remove(String type) {
        List<OverlayMesh> old = meshes.remove(type);
        if (old != null) {
            old.forEach(OverlayMesh::close);
        }
    }

    @Override
    public boolean isVisible() {
        return !meshes.isEmpty();
    }

    @Override
    public boolean throughBlocks() {
        return true;
    }

    @Override
    public float opacity() {
        return 0.55f;
    }

    @Override
    public void tick() {
    }

    @Override
    public void render(Matrix4fc view, Matrix4fc projection, Vec3 camera) {
        for (List<OverlayMesh> type : meshes.values()) {
            for (OverlayMesh mesh : type) {
                mesh.draw(view, projection, camera);
            }
        }
    }

    @Override
    public void clear() {
        meshes.values().forEach(type -> type.forEach(OverlayMesh::close));
        meshes.clear();
    }
}
