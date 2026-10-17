package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface OctLevel extends OctGetter {
    Level level();
    
    boolean setOctState(BlockPos pos, int octant, BlockState state);
}
