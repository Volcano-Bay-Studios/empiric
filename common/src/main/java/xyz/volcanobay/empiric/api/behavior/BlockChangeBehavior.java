package xyz.volcanobay.empiric.api.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.core.module.Behavior;

public interface BlockChangeBehavior extends Behavior {

    void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState);
}
