package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import org.jetbrains.annotations.ApiStatus;

public class OctBlockEntity {
    
    protected final BlockPos worldPosition;
    protected final int octantPosition;
    
    private FullOctBlockEntity owner;
    protected OctLevel octLevel;
    protected Level level;

    protected BlockState blockState;
    
    public OctBlockEntity(BlockPos worldPosition, int octantPosition, BlockState blockState) {
        this.worldPosition = worldPosition;
        this.octantPosition = octantPosition;
        this.blockState = blockState;
    }
    
    /**
     * Should the entity be ticked. This method should return the same value throughout the lifetime of the block.
     *
     * @return True if this block entity should be ticked
     */
    public boolean isTicked() {
        return false;
    }
    
    /**
     * Tick method, called if {@link OctBlockEntity#isTicked()} returns true
     */
    public void tick() {
    
    }
    
    /**
     * Called before the block entity is removed (or replaced).
     */
    public void unload() {
    
    }
    
    /**
     * Write persistent data to NBT
     *
     * @param tag        Stored tag
     * @param registries Registry lookup
     */
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    
    }
    
    /**
     * Read persistent data from NBT
     *
     * @param tag        Stored tag
     * @param registries Registry lookup
     */
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    
    }
    
    /**
     * Mark this block as needing to be saved.
     */
    public void setChanged() {
        if (owner != null) {
            owner.setUnsaved();
        }
    }
    
    /**
     * Mark this block for network synchronization.
     */
    public void sendData() {
        if (owner != null) {
            owner.sendData();
        }
    }

    /**
     * Called when the owner block entity has been loaded
     */
    public void onLoad() {

    }

    /**
     * Called when the oct state in the level is changed
     * @param blockState Oct block state
     */
    public void updateBlockState(BlockState blockState) {
        this.blockState = blockState;
    }
    
    /**
     * Set owner of this oct entity.
     *
     * @param owner Owner block entity
     * This method may only be called when the owner itself has received a level.
     */
    @ApiStatus.Internal
    public void setOwner(FullOctBlockEntity owner) {
        this.octLevel = LevelOctExtension.get(owner.getLevel());
        this.level = owner.getLevel();
        this.owner = owner;
    }

    public OctPos getOctPos() {
        return new OctPos(worldPosition,octantPosition);
    }


}
