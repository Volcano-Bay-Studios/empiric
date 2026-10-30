package xyz.volcanobay.empiric.oct;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.oct.OctUtil;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.ext.BlockGetterOctExtension;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.networking.packet.OctBreakS2CPacket;
import xyz.volcanobay.empiric.registry.EmpiricBlockEntities;

public class FullOctBlock extends Block implements EntityBlock, LiquidBlockContainer {
    public FullOctBlock(Properties ignored) {
        super(Properties.of().noOcclusion().dynamicShape());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return EmpiricBlockEntities.OCT_BLOCK.get().create(pos, state);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        int octant = OctUtil.getTargetedOctant(player, pos);
        BlockPos center = OctBlockComplement.getMultiblockCenter(BlockGetterOctExtension.get(level), pos, octant);
        if (center != null) {
            return level.getBlockState(center).getDestroyProgress(player, level, center);
        }
        BlockState octState = BlockGetterOctExtension.get(level).getOctState(pos, octant);
        return octState.getDestroyProgress(player, level, pos);
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            int octant = OctUtil.getTargetedOctant(player, pos);
            BlockState octState = BlockGetterOctExtension.get(level).getOctState(pos, octant);
            PacketTarget.tracking(serverLevel, pos).send(
                    new OctBreakS2CPacket(new OctPos(pos, octant), octState)
            );
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FullOctBlockEntity.get(level, pos).map(be -> be.getShape(pos, context)).orElseGet(Shapes::empty);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FullOctBlockEntity.get(level, pos).map(be -> be.getCollisionShape(pos, context)).orElseGet(Shapes::empty);
    }

    /**
     * Lets every octant know the block is going away when something other than an octant break replaces it
     * (explosions, commands, pistons), so their contents can drop. Octants already cleared one by one are air by now.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            FullOctBlockEntity.get(level, pos).ifPresent(FullOctBlockEntity::blockRemoved);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        FullOctBlockEntity.get(level, pos).ifPresent(be -> {
            for (int octant = 0; octant < 8; octant++) {
                BlockState octState = be.getOctState(octant);
                if (octState.getBlock() instanceof OctBlock octBlock) {
                    octBlock.neighborChanged(octState, LevelOctExtension.get(level), level, pos, octant, neighborBlock, neighborPos);
                }
            }
        });
    }

    /**
     * What the whole block drops when it isn't broken octant by octant (explosions): every octant's own drops.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FullOctBlockEntity be) {
            for (int octant = 0; octant < 8; octant++) {
                BlockState octState = be.getOctState(octant);
                if (!octState.isAir()) {
                    drops.addAll(octState.getDrops(params));
                }
            }
        }
        return drops;
    }

    // fluids neither replace nor fill oct blocks, so their contents can't be washed away

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        int octant = OctUtil.getOctant(hitResult);
        return FullOctBlockEntity.get(level, pos)
                .flatMap(be -> be.getOctOnlyState(octant))
                .map(octState -> ((OctBlock) octState.getBlock())
                        .useWithoutItem(
                                octState,
                                LevelOctExtension.get(level),
                                pos,
                                octant,
                                player,
                                hitResult
                        )
                ).orElse(InteractionResult.PASS);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        int octant = OctUtil.getOctant(hitResult);
        return FullOctBlockEntity.get(level, pos)
                .flatMap(be -> be.getOctOnlyState(octant))
                .map(octState -> ((OctBlock) octState.getBlock())
                        .useItemOn(
                                stack,
                                octState,
                                LevelOctExtension.get(level),
                                pos,
                                octant,
                                player,
                                hand,
                                hitResult
                        )
                ).orElse(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }

    public static int destroyedOctant(Player player, BlockPos pos) {
        double range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 1;
        HitResult hit = player.pick(range, 1, false);
        Vec3 location = hit.getLocation();
        if (hit instanceof BlockHitResult blockHit) {
            if (!pos.equals(blockHit.getBlockPos())) {
                return -1;
            }
            location = location.relative(blockHit.getDirection(), -1 / 32.0);
        }
        return OctUtil.getOctant(pos, location);
    }

    public static boolean destroyByPlayer(Level level, BlockPos pos, Player player) {
        int octant = destroyedOctant(player, pos);
        if (octant < 0) {
            return false;
        }
        return FullOctBlockEntity.get(level, pos).map(be -> {
            BlockState octState = be.getOctState(octant);
            if (octState.isAir()) {
                return false;
            }
            if (octState.getBlock() instanceof OctBlock octBlock
                    && octBlock.destroyedByPlayer(octState, LevelOctExtension.get(level), level, pos, octant, player)) {
                return true;
            }
            if (level instanceof ServerLevel serverLevel) {
                PacketTarget.tracking(serverLevel, pos).send(new OctBreakS2CPacket(new OctPos(pos, octant), octState));
            }
            ItemStack tool = player.getMainHandItem();
            ItemStack used = tool.copy();
            boolean harvest = !player.isCreative() && player.hasCorrectToolForDrops(octState);
            BlockState removed = be.destroyBlock(octant);
            if (!level.isClientSide && !player.isCreative()) {
                tool.mineBlock(level, removed, pos, player);
                if (harvest) {
                    Block.dropResources(removed, level, pos, null, player, used);
                }
            }
            return !removed.isAir();
        }).orElse(false);
    }

    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        int octant = entity != null ? OctUtil.getOctant(entity.position()) : 0;
        return FullOctBlockEntity.get(level, pos)
                .flatMap(be -> be.getOctOnlyState(octant))
                .map(state1 -> ((OctBlock) state1.getBlock())
                        .getSoundType(
                                state1,
                                BlockGetterOctExtension.get(level),
                                pos,
                                octant,
                                entity)
                ).orElse(SoundType.EMPTY);
    }

    public ItemStack getCloneItemStack(BlockState state, HitResult hit, LevelReader level, BlockPos pos, Player player) {
        Vec3 location = hit.getLocation();
        if (hit instanceof BlockHitResult blockHit) {
            if (!pos.equals(blockHit.getBlockPos())) {
                return ItemStack.EMPTY;
            }
            location = location.relative(blockHit.getDirection(), -1 / 32.0);
        }
        int octant = OctUtil.getOctant(pos, location);
        return FullOctBlockEntity.get(level, pos)
                .flatMap(be -> be.getOctOnlyState(octant))
                .map(state1 -> ((OctBlock) state1.getBlock())
                        .getCloneItemStack(
                                state1,
                                hit,
                                BlockGetterOctExtension.get(level),
                                pos,
                                octant,
                                player)
                ).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level instanceof Level world && world.isClientSide) {
            HitResult hit = ClientLookup.hitResult();
            Player player = ClientLookup.player();
            if (hit != null && player != null) {
                return getCloneItemStack(state, hit, level, pos, player);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FullOctBlockEntity.get(level, pos).ifPresent(be -> {
            int octant = random.nextInt(8);
            BlockState state1 = be.getOctState(octant);
            if (state1.getBlock() instanceof OctBlock octBlock) {
                octBlock.randomTick(state1, level, pos, octant, random);
            }
        });
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                            BlockState state,
                                                                            BlockEntityType<T> blockEntityType) {
        return TickableBlockEntity.getTickerHelper(level);
    }
}
