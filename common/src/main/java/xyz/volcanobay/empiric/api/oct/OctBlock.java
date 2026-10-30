package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.oct.FullOctVoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Octant Block base class. All oct blocks MUST inherit from this class.
 * OctBlock inherits from Block to provide support for basic vanilla features,
 * such as harvest levels.
 * Not all (probably most) methods of the base Block model are usable.
 */
public class OctBlock extends Block {

    public static final VoxelShape BOX = Block.box(0, 0, 0, 8, 8, 8);

    protected final FullOctVoxelShape fullShape;
    private Item item;

    public OctBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.fullShape = new FullOctVoxelShape();
    }

    public static boolean isIn(BlockState state) {
        return state.getBlock() instanceof OctBlock || state.getBlock() == Blocks.AIR;
    }

    @Nullable
    public static Class<? extends OctBlockEntity> getEntityType(BlockState state) {
        if (state.getBlock() instanceof OctBlock octBlock) {
            return octBlock.getOctEntityType();
        }
        return null;
    }

    @Override
    public Item asItem() {
        return this.item == null ? Items.AIR : this.item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    @Nullable
    public Class<? extends OctBlockEntity> getOctEntityType() {
        return null;
    }

    @Nullable
    public OctBlockEntity createOctEntity(BlockPos pos, int octant, BlockState state) {
        return null;
    }

    public boolean canPlaceOct(BlockState state, OctLevel level, BlockPos pos, int octant) {
        List<OctPos> complements = getComplements(state, level, new OctPos(pos,octant));
        for (OctPos complement : complements) {
            if (!level.getBlockState(complement).canBeReplaced() ){
                return false;
            }
        }
        return true;
    }

    /**
     * Called after the given oct block is placed in the level.
     */
    public void onPlace(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, Player player, ItemStack stack) {
        OctPos ourPos = new OctPos(pos, octant);
        List<OctPos> complements = getComplements(state, octLevel, ourPos);
        for (OctPos complement : complements) {
            OctBlockComplement.placeComplement(octLevel,complement,ourPos);
        }
    }

    /**
     * Called directly before the given oct block is removed from the level.
     */
    public void onRemove(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, BlockState newState) {

    }

    /**
     * Called directly after the given oct block is removed from the level.
     * @param state This is the only access to the block state as the block has already been removed in this context.
     */
    public void afterRemove(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, BlockState newState) {
        List<OctPos> complements = getComplements(state, octLevel, new OctPos(pos, octant));
        for (OctPos complement : complements) {
            octLevel.setOctState(complement.pos(),complement.octant(),Blocks.AIR.defaultBlockState());
        }
    }

    public FullOctVoxelShape getFullShape(BlockState state, OctGetter level, BlockPos pos,
                                          int octant, CollisionContext context) {
        return fullShape.setShape(this.getShape(state, level, pos, octant, context));
    }

    public VoxelShape getShape(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        return BOX;
    }

    /**
     * The part of this octant that entities collide with, in octant-local coordinates like {@link #getShape}.
     * Defaults to the shape, so a block that should be walked through (small props) returns less here.
     */
    public VoxelShape getCollisionShape(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        return getShape(state, level, pos, octant, context);
    }

    /**
     * Called on the server when a block next to the oct block holding this octant changes.
     */
    public void neighborChanged(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, Block neighborBlock, BlockPos neighborPos) {

    }

    /**
     * The outline drawn around what the player is looking at, in block coordinates (0 to 1, not octant-local).
     * Return null to outline this octant's part of {@link #getShape}, which is what a block gets by default.
     * A block that fills several octants uses this to outline itself as one.
     */
    public @Nullable VoxelShape getHitOutline(BlockState state, OctGetter level, BlockPos pos, int octant, CollisionContext context) {
        return null;
    }

    /**
     * Called when a player breaks this octant, before anything is removed.
     *
     * @return true if the block dealt with the break itself and the octant should stay (for example it removed one
     * part of several); this runs on both sides, so it should only change the world on the server
     */
    public boolean destroyedByPlayer(BlockState state, OctLevel octLevel, Level level, BlockPos pos, int octant, Player player) {
        return false;
    }

    public List<OctPos> getComplements(BlockState state, OctLevel level, OctPos pos) {
        return new ArrayList<>();
    }

    public InteractionResult useWithoutItem(BlockState state, OctLevel level, BlockPos pos, int octant, Player
            player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, OctLevel level, BlockPos pos,
                                           int octant, Player player, BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Like {@link #useItemOn(ItemStack, BlockState, OctLevel, BlockPos, int, Player, BlockHitResult)}, with the hand
     * holding the stack. This is the one the oct block calls; by default it hands over to the one without a hand.
     */
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, OctLevel level, BlockPos pos,
                                           int octant, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return useItemOn(stack, state, level, pos, octant, player, hitResult);
    }

    public SoundType getSoundType(BlockState state, OctGetter level, BlockPos pos, int octant,
                                  @Nullable Entity entity) {
        return soundType;
    }

    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, int octant, RandomSource random) {

    }

    public ItemStack getCloneItemStack(BlockState state, HitResult hit, OctGetter level, BlockPos pos,
                                       int octant, Player player) {
        return new ItemStack(this);
    }
}
