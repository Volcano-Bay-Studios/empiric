package xyz.volcanobay.empiric.api.ui.menu;

import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;
import xyz.volcanobay.empiric.core.ui.Keys;

/**
 * Slot clicks without a menu, where there is no cursor stack, so the held item stands in for it. Use puts one held item
 * in, or the whole held stack while sneaking, and takes the slot's stack into an empty hand. Attack takes one item out,
 * or the whole stack while sneaking, into the inventory. Runs on the server.
 */
public final class WorldSlots {

    private WorldSlots() {
    }

    /**
     * Returns true if any items moved.
     */
    public static boolean click(Player player, SlotWidget slot, int button) {
        if (slot.container() instanceof Inventory) {
            return false;
        }
        boolean all = player.isShiftKeyDown();
        if (button == Keys.RIGHT_BUTTON) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                return take(player, slot, slot.stack().getCount(), true);
            }
            return insert(slot, held, all ? held.getCount() : 1);
        }
        if (button == Keys.LEFT_BUTTON) {
            return take(player, slot, all ? slot.stack().getCount() : 1, false);
        }
        return false;
    }

    private static boolean insert(SlotWidget slot, ItemStack held, int amount) {
        if (!slot.mayPlace(held)) {
            return false;
        }
        Container container = slot.container();
        ItemStack current = slot.stack();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, held)) {
            return false;
        }
        int moved = Math.min(amount, container.getMaxStackSize(held) - current.getCount());
        if (moved <= 0) {
            return false;
        }
        ItemStack placed = held.copyWithCount(current.getCount() + moved);
        held.shrink(moved);
        container.setItem(slot.index(), placed);
        return true;
    }

    private static boolean take(Player player, SlotWidget slot, int amount, boolean toHand) {
        if (slot.stack().isEmpty() || amount <= 0) {
            return false;
        }
        ItemStack taken = slot.container().removeItem(slot.index(), amount);
        if (taken.isEmpty()) {
            return false;
        }
        if (toHand && player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, taken);
        } else {
            player.getInventory().placeItemBackInInventory(taken);
        }
        return true;
    }
}
