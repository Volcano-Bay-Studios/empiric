package xyz.volcanobay.empiric.api.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class SpatialNetworkType<N extends NetworkModule> {

    private final ResourceLocation id;
    private final ModuleType<N, NetworkAttachment> moduleType;
    private final List<NetworkListener<N>> listeners = new CopyOnWriteArrayList<>();
    private int index = -1;

    public SpatialNetworkType(ResourceLocation id, Class<N> moduleClass, Supplier<N> factory) {
        this.id = id;
        this.moduleType = ModuleType.builder(id::toString, moduleClass, EmpiricAttachments.NETWORK, factory).build();
    }

    public ResourceLocation id() {
        return id;
    }

    public ModuleType<N, NetworkAttachment> moduleType() {
        return moduleType;
    }

    public void listen(NetworkListener<N> listener) {
        listeners.add(listener);
    }

    public List<NetworkListener<N>> listeners() {
        return listeners;
    }

    public int index() {
        return index;
    }

    public void assignIndex(int index) {
        this.index = index;
    }

    public boolean isEnabled() {
        return moduleType.isEnabled();
    }

    public SpatialNetworkMap<N> get(Level level) {
        return SpatialNetworkSystem.map(level, this);
    }

    /**
     * If true, the default network will be used whenever a flood fill runs out of room.
     */
    public boolean allowDefaultNetwork() {
        return false;
    }

    @Nullable
    public SpatialNetworkable getBlockOverride(BlockState blockState, Level level, OctLevel octLevel, OctPos pos) {
        return blockState.getBlock() instanceof SpatialNetworkable networkable ? networkable : null;
    }

    public boolean shouldConnect(BlockState firstState, BlockState secondState, Level level, OctLevel octLevel, OctPos first, OctPos second) {
        if (getBlockOverride(firstState, level, octLevel, first) instanceof SpatialNetworkable firstNetworkable && getBlockOverride(secondState, level, octLevel, second) instanceof SpatialNetworkable secondNetworkable) {
            if (firstNetworkable.shouldJoinNetwork(firstState, secondState, level, octLevel, first, second) && secondNetworkable.shouldJoinNetwork(secondState, firstState, level, octLevel, second, first)) {
                for (SpatialNetworkType<?> spatialNetwork : firstNetworkable.spatialNetworks()) {
                    if (secondNetworkable.spatialNetworks().contains(spatialNetwork)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isMember(long x, long y, long z, OctLevel octLevel, Level level) {
        OctPos pos = new OctPos(x, y, z);
        BlockState blockState = octLevel.getBlockState(pos);
        if (blockState.getBlock() instanceof SpatialNetworkable networkable) {
            return networkable.spatialNetworks().contains(this);
        }
        return false;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
