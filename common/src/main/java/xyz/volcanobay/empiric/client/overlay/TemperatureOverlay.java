package xyz.volcanobay.empiric.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Heated octs around the player from the client's synced temperature, colored by a heat palette and
 * hidden by blocks in front of them. Sections are remeshed as their temperature packets arrive.
 */
public final class TemperatureOverlay implements OverlayLayer {

    public static final TemperatureOverlay INSTANCE = new TemperatureOverlay();

    private static final double CELL_SIZE = 0.5;
    private static final int REBUILDS_PER_TICK = 8;
    // Turbo palette, cold to hot.
    private static final int[] PALETTE = {0x30123b, 0x4662d7, 0x36aaf9, 0x1ae4b6, 0x72fe5e, 0xc8ef34, 0xfaba39, 0xf66b19, 0xca2a04};

    private final Map<Section, OverlayMesh> meshes = new HashMap<>();
    private final Set<Section> dirty = new HashSet<>();
    private int radius;

    private TemperatureOverlay() {
    }

    public static int color(int temperature) {
        float t = Mth.clamp(temperature / (float) TemperatureModule.LIMIT, 0, 1) * (PALETTE.length - 1);
        int low = Math.min((int) t, PALETTE.length - 2);
        return lerp(t - low, PALETTE[low], PALETTE[low + 1]);
    }

    private static int lerp(float t, int from, int to) {
        int r = Mth.lerpInt(t, (from >> 16) & 255, (to >> 16) & 255);
        int g = Mth.lerpInt(t, (from >> 8) & 255, (to >> 8) & 255);
        int b = Mth.lerpInt(t, from & 255, to & 255);
        return (r << 16) | (g << 8) | b;
    }

    public void show(int radius) {
        this.radius = radius;
        if (radius <= 0) {
            clear();
        }
    }

    public void changed(ChunkPos chunk, int section) {
        if (radius > 0) {
            dirty.add(new Section(chunk.toLong(), section));
        }
    }

    @Override
    public boolean isVisible() {
        return radius > 0;
    }

    @Override
    public boolean throughBlocks() {
        return false;
    }

    @Override
    public float opacity() {
        return 0.6f;
    }

    @Override
    public void tick() {
        Level level = Minecraft.getInstance().level;
        Player player = Minecraft.getInstance().player;
        if (radius <= 0 || level == null || player == null) {
            return;
        }
        Set<Section> wanted = new HashSet<>();
        int budget = REBUILDS_PER_TICK;
        int minChunkX = Mth.floor((player.getX() - radius) / 16);
        int maxChunkX = Mth.floor((player.getX() + radius) / 16);
        int minChunkZ = Mth.floor((player.getZ() - radius) / 16);
        int maxChunkZ = Mth.floor((player.getZ() + radius) / 16);
        int minSection = level.getSectionIndex(Mth.floor(player.getY() - radius));
        int maxSection = level.getSectionIndex(Mth.floor(player.getY() + radius));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                TemperatureModule module = module(level, chunkX, chunkZ);
                if (module == null) {
                    continue;
                }
                for (int section : module.sections.keySet()) {
                    if (section < minSection || section > maxSection) {
                        continue;
                    }
                    Section key = new Section(ChunkPos.asLong(chunkX, chunkZ), section);
                    wanted.add(key);
                    if ((!meshes.containsKey(key) || dirty.contains(key)) && budget > 0) {
                        budget--;
                        dirty.remove(key);
                        replace(key, build(level, module, chunkX, chunkZ, section));
                    }
                }
            }
        }
        Iterator<Map.Entry<Section, OverlayMesh>> entries = meshes.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Section, OverlayMesh> entry = entries.next();
            if (!wanted.contains(entry.getKey())) {
                if (entry.getValue() != null) {
                    entry.getValue().close();
                }
                entries.remove();
            }
        }
        dirty.retainAll(wanted);
    }

    private void replace(Section key, OverlayMesh mesh) {
        OverlayMesh old = meshes.put(key, mesh);
        if (old != null) {
            old.close();
        }
    }

    private static TemperatureModule module(Level level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk == null ? null : ChunkAttachment.of(chunk).modules().get(TemperatureSystem.TEMPERATURE);
    }

    private static OverlayMesh build(Level level, TemperatureModule module, int chunkX, int chunkZ, int section) {
        short[] values = module.sections.get(section);
        if (values == null) {
            return null;
        }
        long baseX = chunkX * (long) TemperatureModule.SIZE;
        long baseY = (long) (level.getMinSection() + section) * TemperatureModule.SIZE;
        long baseZ = chunkZ * (long) TemperatureModule.SIZE;
        List<SpatialPos> cells = new ArrayList<>();
        for (int i = 0; i < TemperatureModule.CELLS; i++) {
            if (values[i] > 0) {
                cells.add(new SpatialPos(baseX + ((i >> 10) & 31), baseY + ((i >> 5) & 31), baseZ + (i & 31)));
            }
        }
        Lookup lookup = new Lookup(level);
        return OverlayMesh.build(CELL_SIZE, cells, (x, y, z) -> lookup.get(x, y, z) > 0, (x, y, z) -> color(lookup.get(x, y, z)));
    }

    @Override
    public void render(Matrix4fc view, Matrix4fc projection, Vec3 camera) {
        for (OverlayMesh mesh : meshes.values()) {
            if (mesh != null) {
                mesh.draw(view, projection, camera);
            }
        }
    }

    @Override
    public void clear() {
        for (OverlayMesh mesh : meshes.values()) {
            if (mesh != null) {
                mesh.close();
            }
        }
        meshes.clear();
        dirty.clear();
    }

    private record Section(long chunk, int section) {
    }

    /**
     * Reads temperatures across chunk and section borders, caching the last chunk looked up.
     */
    private static final class Lookup {

        private final Level level;
        private long cachedChunk = Long.MIN_VALUE;
        private TemperatureModule cached;

        Lookup(Level level) {
            this.level = level;
        }

        short get(long x, long y, long z) {
            int chunkX = (int) Math.floorDiv(x, TemperatureModule.SIZE);
            int chunkZ = (int) Math.floorDiv(z, TemperatureModule.SIZE);
            long key = ChunkPos.asLong(chunkX, chunkZ);
            if (key != cachedChunk) {
                cachedChunk = key;
                cached = module(level, chunkX, chunkZ);
            }
            if (cached == null) {
                return 0;
            }
            int section = (int) Math.floorDiv(y, TemperatureModule.SIZE) - level.getMinSection();
            if (section < 0 || section >= level.getSectionsCount()) {
                return 0;
            }
            int index = TemperatureModule.index((int) Math.floorMod(x, TemperatureModule.SIZE), (int) Math.floorMod(y, TemperatureModule.SIZE), (int) Math.floorMod(z, TemperatureModule.SIZE));
            return cached.get(section, index);
        }
    }
}
