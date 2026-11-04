package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

public interface OctLevel extends OctGetter {

    static OctLevel get(Level level) {
        return ((LevelOctExtension) level).empiric$getOctLevel();
    }
    
    Level level();
    
    boolean setOctState(BlockPos pos, int octant, BlockState state);
}
