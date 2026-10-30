package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.oct.OctGetter;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.client.ClientLookup;

import java.util.function.Predicate;

/**
 * The octants pieces fill. Its sounds and strength are those of the pieces whose {@link PartType#cell} it is.
 */
public class PartOctBlock extends OctBlock {

    public PartOctBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable Class<? extends OctBlockEntity> getOctEntityType() {
        return PartOctEntity.class;
    }

    @Override
    public @Nullable OctBlockEntity createOctEntity(BlockPos pos, int octant, BlockState state) {
        return new PartOctEntity(pos, octant, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        Workspace workspace = Workspace.of(level, pos);
        if (workspace.touching(octant).isEmpty()) {
            return BOX;
        }
        return shape(workspace, octant, piece -> true);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        return shape(Workspace.of(level, pos), octant, piece -> piece.type.has(PartTraits.SOLID));
    }

    private static VoxelShape shape(Workspace workspace, int octant, Predicate<Piece> include) {
        int offsetX = (octant & 1) != 0 ? 8 : 0;
        int offsetY = (octant & 2) != 0 ? 8 : 0;
        int offsetZ = (octant & 4) != 0 ? 8 : 0;
        AABB cell = new AABB(offsetX, offsetY, offsetZ, offsetX + 8, offsetY + 8, offsetZ + 8);
        VoxelShape shape = Shapes.empty();
        for (Piece piece : workspace.pieces()) {
            if (!include.test(piece)) {
                continue;
            }
            for (AABB box : piece.boxes()) {
                if (!box.intersects(cell)) {
                    continue;
                }
                AABB clipped = box.intersect(cell).move(-offsetX, -offsetY, -offsetZ);
                shape = Shapes.or(shape, Block.box(clipped.minX, clipped.minY, clipped.minZ, clipped.maxX, clipped.maxY, clipped.maxZ));
            }
        }
        return shape;
    }

    @Override
    public @Nullable VoxelShape getHitOutline(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext) || !(entityContext.getEntity() instanceof Player player)) {
            return null;
        }
        HitResult hit = ClientLookup.hitResult();
        Selection selection = Selection.of(level, pos, player, hit instanceof BlockHitResult blockHit ? blockHit : null);
        if (selection == null) {
            return null;
        }
        AABB box = selection.box();
        return Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public boolean destroyedByPlayer(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange() + 1));
        Selection selection = Selection.ray(octLevel, pos, from, to);
        if (selection == null) {
            return !Workspace.of(octLevel, pos).touching(octant).isEmpty();
        }
        if (!level.isClientSide) {
            Parts.drop(level, pos, selection.piece());
        }
        return true;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, OctLevel level, BlockPos pos, int octant, Player player, BlockHitResult hitResult) {
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return InteractionResult.PASS;
        }
        Selection selection = Selection.of(level, pos, player, hitResult);
        if (selection == null) {
            return InteractionResult.PASS;
        }
        PartContext context = new PartContext(level.level(), level, pos, Workspace.of(level, pos), selection, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (selection.layer() != null) {
            InteractionResult result = selection.layer().use(context);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        for (PartInteraction interaction : PartInteractions.all()) {
            InteractionResult result = interaction.useWithoutItem(context);
            if (result != null) {
                return result;
            }
        }
        if (player.isShiftKeyDown() != selection.piece().type.has(PartTraits.SNEAK_PICKUP) || !player.mayBuild()) {
            return InteractionResult.PASS;
        }
        Level world = level.level();
        if (!world.isClientSide) {
            ItemStack stack = Parts.remove(world, pos, selection.piece().id);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            world.playSound(null, pos, selection.piece().type.cell().defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, OctLevel level, BlockPos pos, int octant, Player player, InteractionHand hand, BlockHitResult hitResult) {
        Selection selection = Selection.of(level, pos, player, hitResult);
        if (selection == null || stack.getItem() instanceof PartTool || selection.layer() != null && !selection.layer().takesItems()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        PartContext context = new PartContext(level.level(), level, pos, Workspace.of(level, pos), selection, player, hand, stack);
        for (PartInteraction interaction : PartInteractions.all()) {
            ItemInteractionResult result = interaction.useItemOn(context);
            if (result != null) {
                return result;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, BlockState newState) {
        PartOctEntity entity = Parts.entity(octLevel, pos, octant);
        if (entity == null || level.isClientSide) {
            return;
        }
        for (Piece piece : entity.pieces()) {
            if (!piece.stack.isEmpty()) {
                Block.popResource(level, pos, piece.stack);
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, Block neighborBlock, BlockPos neighborPos) {
        if (neighborPos.equals(pos.below())) {
            Parts.requestSettle(level, pos);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult hit, OctGetter level, BlockPos pos, int octant, Player player) {
        Selection selection = Selection.of(level, pos, player, hit instanceof BlockHitResult blockHit ? blockHit : null);
        if (selection == null) {
            return ItemStack.EMPTY;
        }
        if (player.isCreative() && ClientLookup.hasControlDown()) {
            return selection.piece().stack.copyWithCount(1);
        }
        return selection.piece().type.newStack();
    }
}
