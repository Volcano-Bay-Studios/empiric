package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
