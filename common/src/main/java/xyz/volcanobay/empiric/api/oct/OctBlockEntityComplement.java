package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

public class OctBlockEntityComplement extends OctBlockEntity {
    private OctPos sourcePos;
    private boolean fullBlockSource;
    public OctBlockEntityComplement(BlockPos worldPosition, int octantPosition, BlockState blockState) {
        super(worldPosition, octantPosition, blockState);
    }

    public OctPos getSourcePos() {
        return sourcePos;
    }

    @ApiStatus.Internal
    public void setSourcePos(OctPos pos) {
        sourcePos = pos;
    }

    public boolean isFullBlockSource() {
        return fullBlockSource;
    }

    @ApiStatus.Internal
    public void setFullBlockSource(BlockPos pos) {
        sourcePos = new OctPos(pos, 0);
        fullBlockSource = true;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("x",sourcePos.pos().getX());
        tag.putInt("y",sourcePos.pos().getY());
        tag.putInt("z",sourcePos.pos().getZ());
        tag.putInt("octant", sourcePos.octant());
        if (fullBlockSource) {
            tag.putBoolean("full", true);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sourcePos = new OctPos(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag.getInt("octant"));
        fullBlockSource = tag.getBoolean("full");
    }
}
