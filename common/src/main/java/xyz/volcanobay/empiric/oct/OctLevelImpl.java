package xyz.volcanobay.empiric.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;

public final class OctLevelImpl extends OctGetterImpl implements OctLevel {
    private final Level level;
    
    public OctLevelImpl(Level level) {
        super(level);
        this.level = level;
    }
    
    public Level level() {
        return level;
    }
    
    @Override
    public boolean setOctState(BlockPos pos, int octant, BlockState state) {
        if (!OctBlock.isIn(state)) {
            throw new IllegalArgumentException("Block state is not an oct block");
        }
        BlockState currentState = this.level.getBlockState(pos);
        if (!currentState.is(EmpiricBlocks.OCT_BLOCK.get())) {
            // Initialize a new oct block
            if (!this.level.setBlock(pos,
                                     EmpiricBlocks.OCT_BLOCK.get().defaultBlockState(),
                                     FullOctBlock.UPDATE_ALL_IMMEDIATE)) {
                return false;
            }
        }
        BlockEntity be = this.level.getBlockEntity(pos);
        if (!(be instanceof FullOctBlockEntity octEntity)) {
            return false;
        }
        return octEntity.setOctState(octant, state);
    }
}
