package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The item a part is placed from. In creative it still leaves the hand unless placed while sneaking, so the inventory
 * stays tidy.
 */
public class PartItem extends Item {

    private final Supplier<PartType> type;

    public PartItem(Supplier<PartType> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public PartType type() {
        return type.get();
    }

    /**
     * @return Whether to draw where it would go while the player aims with it; the hit is the block aimed at
     */
    public boolean showsPlacement(Level level, Player player, BlockHitResult hit) {
        return !type().has(PartTraits.HANDHELD);
    }

    /**
     * Must return PASS, not FAIL, when it can't place, so the item's own use still runs.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        PartType part = type();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        Direction facing = player != null ? player.getDirection() : Direction.NORTH;
        Placement.Result result = Placement.find(level, part, hit, facing);
        if (!result.ok()) {
            String key = result.failure() == null ? null : result.failure().key();
            if (player != null && !level.isClientSide && key != null) {
                player.displayClientMessage(Component.translatable(key), true);
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (Parts.place(level, result.spot(), part, stack.copyWithCount(1)) == null) {
                return InteractionResult.PASS;
            }
            level.playSound(null, result.spot().host(), part.cell().defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 1.0f);
        } else {
            Placement.clientPlaced.placed(result.spot(), part, stack.copyWithCount(1));
        }
        consume(player, context.getHand(), stack);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected static void consume(@Nullable Player player, InteractionHand hand, ItemStack stack) {
        if (player == null || !player.isCreative()) {
            stack.shrink(1);
        } else if (!player.isShiftKeyDown()) {
            player.setItemInHand(hand, stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY);
        }
    }
}
