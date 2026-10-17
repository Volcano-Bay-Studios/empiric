package xyz.volcanobay.empiric.oct;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.oct.OctBlockRendererRegistry;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.mixin.oct.LevelChunkInvoker;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.system.OctModuleSystem;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;
import xyz.volcanobay.empiric.system.TemperatureSystem;
import xyz.volcanobay.empiric.oct.TickableBlockEntity;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.registry.EmpiricBlockEntities;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

public class FullOctBlockEntity extends BlockEntity implements TickableBlockEntity {

    protected static final int OCT_FEATURE_TICKED = 1;
    protected static final int OCT_FEATURE_RENDERED = 2;

    protected final BlockState[] octBlocks = new BlockState[8];
    protected final OctBlockEntity[] octBlockEntities = new OctBlockEntity[8];
    private final Boolean[] presentOcts = new Boolean[8];
    private final boolean transforming;

    private int features;
    private boolean loaded = false;

    public FullOctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        Arrays.fill(this.octBlocks, Blocks.AIR.defaultBlockState());
        Arrays.fill(this.presentOcts, Boolean.FALSE);
        this.transforming = false;
    }

    public void onLoad() {
        if (this.loaded) {
            return;
        }
        for (int i = 0; i < 8; ++i) {
            if (this.octBlockEntities[i] != null) {
                this.octBlockEntities[i].onLoad();
            }
        }
        this.loaded = true;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (this.transforming) {
            return;
        }
        int requiredFeatures = 0;
        for (int i = 0; i < 8; ++i) {
            if (this.octBlockEntities[i] != null) {
                this.octBlockEntities[i].setOwner(this);
                requiredFeatures |= this.featuresFor(this.octBlockEntities[i]);
            }
        }
        this.setFeatures(requiredFeatures);
    }

    public static Optional<? extends FullOctBlockEntity> get(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FullOctBlockEntity fullOctBlockEntity) {
            return Optional.of(fullOctBlockEntity);
        }
        return Optional.empty();
    }

    protected void modelChanged() {
        ClientLookup.blockChanged(this.worldPosition);
    }

    protected void loadOctants(CompoundTag tag, HolderLookup.Provider registries) {
        int requiredFeatures = 0;
        for (int i = 0; i < 8; ++i) {
            BlockState oldState = this.octBlocks[i];
            Tag data = tag.get(Integer.toString(i));
            if (data == null) {
                this.octBlocks[i] = Blocks.AIR.defaultBlockState();
                continue;
            } else {
                DataResult<Pair<BlockState, Tag>> result = BlockState.CODEC.decode(NbtOps.INSTANCE, data);
                if (result.isSuccess()) {
                    Pair<BlockState, Tag> resultPair = result.getOrThrow();
                    this.octBlocks[i] = resultPair.getFirst();
                    data = resultPair.getSecond();
                } else {
                    this.octBlocks[i] = Blocks.AIR.defaultBlockState();
                }
            }
            OctBlockEntity obe;
            if (OctBlock.getEntityType(oldState) != OctBlock.getEntityType(this.octBlocks[i])) {
                // Update the block entity
                if (this.octBlockEntities[i] != null) {
                    this.octBlockEntities[i].unload();
                }
                if (this.octBlocks[i].getBlock() instanceof OctBlock octBlock) {
                    obe = this.octBlockEntities[i] = octBlock.createOctEntity(this.worldPosition, i, this.octBlocks[i]);
                    if (this.level != null && obe != null) {
                        requiredFeatures |= this.featuresFor(obe);
                        obe.setOwner(this);
                        if (this.loaded) {
                            obe.onLoad();
                        }
                    }
                } else {
                    obe = null;
                }
            } else {
                obe = this.octBlockEntities[i];
                if (obe != null) {
                    obe.updateBlockState(this.octBlocks[i]);
                }
            }
            if (obe != null && data instanceof CompoundTag remainingData) {
                obe.loadAdditional(remainingData, registries);
            }
        }
        if (this.level != null) {
            if (this.level.isClientSide) {
                this.modelChanged();
            }
            this.setFeatures(requiredFeatures);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.loadOctants(tag, registries);
    }

    protected void saveOctants(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < 8; ++i) {
            CompoundTag entry = new CompoundTag();
            if (this.octBlockEntities[i] != null) {
                this.octBlockEntities[i].saveAdditional(entry, registries);
            }
            DataResult<Tag> result = BlockState.CODEC.encode(this.octBlocks[i], NbtOps.INSTANCE, entry);
            if (result.isSuccess()) {
                tag.put(Integer.toString(i), result.getOrThrow());
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.saveOctants(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveOctants(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void tick() {
        for (int i = 0; i < 8; i++) {
            OctBlockEntity be = this.octBlockEntities[i];
            if (be != null && be.isTicked()) {
                be.tick();
            }
        }
    }

    public boolean isEmpty() {
        for (int i = 0; i < 8; ++i) {
            if (!this.octBlocks[i].isAir()) {
                return false;
            }
        }
        return true;
    }

    protected int features() {
        return this.features;
    }

    public boolean setOctState(int octant, BlockState state) {
        if (!OctBlock.isIn(state)) {
            throw new IllegalArgumentException("Block state is not an oct block");
        }
        BlockState oldState = this.octBlocks[octant];
        if (oldState == state) {
            return true;
        }
        this.octBlocks[octant] = state;
        if (this.level != null) {
            OctModuleSystem.octChanged(this.level, new OctPos(this.worldPosition, octant), oldState, state);
            if (!this.level.isClientSide) {
                TemperatureSystem.blockChanged(this.level, this.worldPosition);
                SpatialNetworkSystem.octChanged(this.level, new OctPos(this.worldPosition, octant));
            }
        }
        if (OctBlock.getEntityType(oldState) != OctBlock.getEntityType(state)) {
            if (state.getBlock() instanceof OctBlock octBlock) {
                // Update the block entity
                this.setOctEntity(octant, octBlock.createOctEntity(this.worldPosition, octant, state));
            } else {
                this.setOctEntity(octant, null);
            }
        } else {
            // Update block entity block state
            if (this.octBlockEntities[octant] != null) {
                this.octBlockEntities[octant].updateBlockState(state);
            }
        }
        // Perform an emptiness check if we just set an octant to be empty.
        if (state.isAir() && this.isEmpty()) {
            this.level.setBlock(this.worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        } else {
            if (this.level.isClientSide) {
                this.modelChanged();
            }
            this.sendData();
            this.setChanged();
        }
        return true;
    }

    public BlockState getOctState(int octant) {
        return this.octBlocks[octant];
    }

    public Optional<BlockState> getOctOnlyState(int octant) {
        if (this.octBlocks[octant].getBlock() instanceof OctBlock) {
            return Optional.of(this.octBlocks[octant]);
        }
        return Optional.empty();
    }

    public VoxelShape getShape(BlockPos pos, CollisionContext context) {
        if (this.level == null) {
            return Shapes.empty();
        }
        for (int i = 0; i < this.presentOcts.length; i++) {
            this.presentOcts[i] = !this.octBlocks[i].is(Blocks.AIR);
        }

        VoxelShape shape = Shapes.empty();
        for (int octantIndex = 0; octantIndex < 8; octantIndex++) {
            if (this.presentOcts[octantIndex] && this.octBlocks[octantIndex].getBlock() instanceof OctBlock octant) {
                double oX = ((octantIndex & 1) != 0) ? 0.5 : 0;
                double oY = ((octantIndex & 2) != 0) ? 0.5 : 0;
                double oZ = ((octantIndex & 4) != 0) ? 0.5 : 0;
                shape = Shapes.joinUnoptimized(shape, octant.getShape(
                        this.octBlocks[octantIndex],
                        LevelOctExtension.get(this.level),
                        pos,
                        octantIndex,
                        context
                ).move(oX, oY, oZ), BooleanOp.OR);
            }
        }
        return shape.optimize();
    }

    public BlockState destroyBlock(int octant) {
        BlockState state = this.octBlocks[octant];
        BlockState empty = Blocks.AIR.defaultBlockState();
        if (state.getBlock() instanceof OctBlock octBlock) {
            octBlock.onRemove(state, LevelOctExtension.get(this.level), this.level, this.worldPosition, octant, empty);
        }
        this.setOctState(octant, empty);
        if (state.getBlock() instanceof OctBlock octBlock) {
            octBlock.afterRemove(state, LevelOctExtension.get(this.level), this.level, this.worldPosition, octant, empty);
        }
        return state;
    }

    @Nullable
    public OctBlockEntity getOctEntity(int octant) {
        return this.octBlockEntities[octant];
    }

    public void setOctEntity(int octant, @Nullable OctBlockEntity entity) {
        if (this.octBlockEntities[octant] != null) {
            this.octBlockEntities[octant].unload();
        }
        this.octBlockEntities[octant] = entity;
        if (entity != null) {
            entity.setOwner(this);
            int requiredFeatures = this.featuresFor(entity);
            if ((requiredFeatures & this.features()) != requiredFeatures) {
                // Update this block entity to have the required features,
                // but keep current features for other entities.
                this.setFeatures(requiredFeatures | this.features());
            }
            if (this.loaded) {
                entity.onLoad();
            }
        } else {
            // Block entity removed, try to reduce feature set
            int requiredFeatures = 0;
            for (int i = 0; i < 8; ++i) {
                if (this.octBlockEntities[i] != null) {
                    requiredFeatures |= this.featuresFor(this.octBlockEntities[i]);
                }
            }
            this.setFeatures(requiredFeatures);
        }
    }

    private int featuresFor(OctBlockEntity entity) {
        int features = entity.isTicked() ? OCT_FEATURE_TICKED : 0;
        if (this.level != null && this.level.isClientSide) {
            features |= OctBlockRendererRegistry.get(entity.getClass()) != null ? OCT_FEATURE_RENDERED : 0;
        }
        return features;
    }

    private void setFeatures(int features) {
        int changed = this.features ^ features;
        this.features = features;
        if ((changed & OCT_FEATURE_TICKED) != 0) {
            ((LevelChunkInvoker) this.level.getChunkAt(this.worldPosition)).empiric$updateBlockEntityTicker(this);
        }
    }

    public boolean hasCustomRenderers() {
        return (this.features & OCT_FEATURE_RENDERED) != 0;
    }

    public boolean isTicked() {
        return (this.features & OCT_FEATURE_TICKED) != 0;
    }

    public void setUnsaved() {
        if (this.level != null) {
            this.level.blockEntityChanged(this.worldPosition);
        }
    }

    public void sendData() {
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(this.getBlockPos());
        }
    }
}
