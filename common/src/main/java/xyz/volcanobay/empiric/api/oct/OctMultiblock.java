package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.system.OctMultiblockSystem;

public interface OctMultiblock {

    VoxelShape getMultiblockShape(BlockState state, BlockGetter level, BlockPos pos);

    /**
     * The outline drawn when any part of the multiblock is looked at, relative to the center block. Defaults to the
     * center's own shape joined with {@link #getMultiblockShape}.
     */
    default VoxelShape getMultiblockOutline(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.or(state.getShape(level, pos), getMultiblockShape(state, level, pos));
    }

    default boolean canPlaceMultiblock(Level level, BlockPos pos, BlockState state) {
        return OctMultiblockSystem.canPlace(level, pos, state);
    }
}
