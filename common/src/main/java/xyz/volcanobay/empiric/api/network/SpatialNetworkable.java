package xyz.volcanobay.empiric.api.network;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;

import java.util.List;

public interface SpatialNetworkable {
    List<SpatialNetworkType<?>> spatialNetworks();

    /**
     * Tells a spatial network if the our (oct)block should join a network with the other (oct)block.
     */
    default boolean shouldJoinNetwork(BlockState ourState, BlockState otherState, Level level, OctLevel octLevel, OctPos our, OctPos other) {
        return true;
    }

    /**
     * Returns if a port can be formed between the two (oct)blocks
     *
     */
    default boolean allowPort(BlockState ourState, BlockState otherState, Level level, OctLevel octLevel, OctPos ourPos, OctPos otherPos) {
        return true;
    }


    default void place(OctPos pos, Level level) {
        for (SpatialNetworkType<?> spatialNetwork : spatialNetworks()) {
            spatialNetwork.get(level).addPoint(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        }
    }

    default void destroy(OctPos pos, Level level) {
        for (SpatialNetworkType<?> spatialNetwork : spatialNetworks()) {
            spatialNetwork.get(level).removePoint(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        }
    }

    default void update(OctPos pos, Level level) {
        for (SpatialNetworkType<?> spatialNetwork : spatialNetworks()) {
            spatialNetwork.get(level).updatePoint(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        }
    }
}
