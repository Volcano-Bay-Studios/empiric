package xyz.volcanobay.empiric.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.oct.OctGetter;
import org.jetbrains.annotations.Nullable;

public class OctGetterImpl implements OctGetter {
    private final BlockGetter level;
    
    public OctGetterImpl(BlockGetter level) {
        this.level = level;
    }
    
    public BlockGetter blockGetter() {
        return this.level;
    }
    
    @Override
    public BlockState getOctState(BlockPos pos, int octant) {
        BlockEntity be = this.level.getBlockEntity(pos);
        if (!(be instanceof FullOctBlockEntity octEntity)) {
            return Blocks.AIR.defaultBlockState();
        }
        return octEntity.getOctState(octant);
    }
    
    @Override
    public BlockState getBlockState(BlockPos pos, int octant) {
        BlockEntity be = this.level.getBlockEntity(pos);
        if (!(be instanceof FullOctBlockEntity octEntity)) {
            return this.level.getBlockState(pos);
        }
        return octEntity.getOctState(octant);
    }
    
    @Nullable
    @Override
    public OctBlockEntity getOctEntity(BlockPos pos, int octant) {
        BlockEntity be = this.level.getBlockEntity(pos);
        if (!(be instanceof FullOctBlockEntity octEntity)) {
            return null;
        }
        return octEntity.getOctEntity(octant);
    }
}
