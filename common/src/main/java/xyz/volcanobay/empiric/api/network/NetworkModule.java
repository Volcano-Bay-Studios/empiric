package xyz.volcanobay.empiric.api.network;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork;

import java.util.Set;

public abstract class NetworkModule extends Module<NetworkAttachment> {

    public final int id() {
        return attachment().id();
    }

    public final SpatialNetworkMap<?> map() {
        return attachment().map();
    }

    public final Level level() {
        return attachment().level();
    }

    public final Set<ManagedSpatialNetwork.SpatialPos> members() {
        return map().getNetworkMembers(id());
    }

    public void update() {
    }

    public void onMerged(NetworkModule absorbed) {
    }

    public void onSplit(NetworkModule split) {
    }
}
