package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;
import org.jetbrains.annotations.Nullable;

/**
 * Octant Block Complements are placed as oct "multiblocks"
 */
public class OctBlockComplement extends OctBlock {
    public OctBlockComplement(Properties ignored) {
        super(Properties.of().noOcclusion());
    }

    @Override
    public @Nullable Class<? extends OctBlockEntity> getOctEntityType() {
        return OctBlockEntityComplement.class;
    }

    @Override
    public @Nullable OctBlockEntity createOctEntity(BlockPos pos, int octant, BlockState state) {
        return new OctBlockEntityComplement(pos, octant, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        BlockPos center = getMultiblockCenter(level, pos, octant);
        if (center != null) {
            BlockState centerState = level.blockGetter().getBlockState(center);
            if (centerState.getBlock() instanceof OctMultiblock multiblock) {
                VoxelShape shape = multiblock.getMultiblockShape(centerState, level.blockGetter(), center);
                double x = center.getX() - pos.getX() - ((octant & 1) != 0 ? 0.5 : 0);
                double y = center.getY() - pos.getY() - ((octant & 2) != 0 ? 0.5 : 0);
                double z = center.getZ() - pos.getZ() - ((octant & 4) != 0 ? 0.5 : 0);
                return Shapes.join(shape.move(x, y, z), BOX, BooleanOp.AND);
            }
            return Shapes.empty();
        }
        OctPos source = getSource(level, pos, octant);
        if (source != null) {
            BlockState octState = level.getOctState(source);
            if (!(octState.getBlock() instanceof OctBlock octBlock)) {
                return Shapes.empty();
            }
            VoxelShape shape = octBlock.getShape(octState, level, source.pos(), source.octant(), context);
            Vec3 diff = source.sub(new OctPos(pos, octant));
            return shape.move(diff.x/2f,diff.y/2f,diff.z/2f);
        }
        return super.getShape(state, level, pos, octant, context);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, OctLevel level, BlockPos pos, int octant, Player player, BlockHitResult hitResult) {
        BlockPos center = getMultiblockCenter(level, pos, octant);
        if (center != null) {
            BlockState centerState = level.level().getBlockState(center);
            return centerState.useWithoutItem(level.level(), player, hitResult.withPosition(center));
        }
        return super.useWithoutItem(state, level, pos, octant, player, hitResult);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, OctLevel level, BlockPos pos, int octant, Player player, BlockHitResult hitResult) {
        BlockPos center = getMultiblockCenter(level, pos, octant);
        if (center != null) {
            BlockState centerState = level.level().getBlockState(center);
            InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            return centerState.useItemOn(stack, level.level(), player, hand, hitResult.withPosition(center));
        }
        return super.useItemOn(stack, state, level, pos, octant, player, hitResult);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult hit, OctGetter level, BlockPos pos, int octant, Player player) {
        BlockPos center = getMultiblockCenter(level, pos, octant);
        if (center != null) {
            BlockState centerState = level.blockGetter().getBlockState(center);
            return new ItemStack(centerState.getBlock());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onRemove(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, BlockState newState) {
        if (getMultiblockCenter(octLevel, pos, octant) != null) {
            super.onRemove(state, octLevel, level, pos, octant, newState);
            return;
        }
        OctPos source = getSource(octLevel, pos, octant);
        if (source != null) {
            BlockState sourceState = octLevel.getOctState(source);
            if (!sourceState.isAir()) {
                octLevel.setOctState(source.pos(),source.octant(),Blocks.AIR.defaultBlockState());
            }
        }
        super.onRemove(state, octLevel, level, pos, octant, newState);
    }

    public static void placeComplement(OctLevel level, OctPos placeLocation, OctPos sourceBlock) {
        BlockState complementState = EmpiricBlocks.OCT_BLOCK_COMPLEMENT.get().defaultBlockState();
        level.setOctState(placeLocation.pos(),placeLocation.octant(),complementState);
        if (level.getOctEntity(placeLocation) instanceof OctBlockEntityComplement complement) {
            complement.setSourcePos(sourceBlock);
        }
    }

    public static void placeMultiblockComplement(OctLevel level, OctPos placeLocation, BlockPos center) {
        BlockState complementState = EmpiricBlocks.OCT_BLOCK_COMPLEMENT.get().defaultBlockState();
        level.setOctState(placeLocation.pos(), placeLocation.octant(), complementState);
        if (level.getOctEntity(placeLocation) instanceof OctBlockEntityComplement complement) {
            complement.setFullBlockSource(center);
        }
    }

    public @Nullable BlockState getSourceState(OctGetter level, BlockPos pos, int octant) {
        OctPos source = getSource(level, pos, octant);
        if (source != null) {
            return level.getOctState(source);
        }
        return null;
    }

    public @Nullable OctPos getSource(OctGetter level, BlockPos pos, int octant) {
        if (level.getOctEntity(pos,octant) instanceof OctBlockEntityComplement complement) {
            return complement.getSourcePos();
        }
        return null;
    }

    public static @Nullable BlockPos getMultiblockCenter(OctGetter level, BlockPos pos, int octant) {
        if (level.getOctEntity(pos, octant) instanceof OctBlockEntityComplement complement && complement.isFullBlockSource() && complement.getSourcePos() != null) {
            return complement.getSourcePos().pos();
        }
        return null;
    }
}
