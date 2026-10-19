package xyz.volcanobay.empiric.api.ui.menu;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * A container over an item list held somewhere else, such as a module's synced {@code List<ItemStack>} field. It looks
 * the list up on every access, because a sync can replace it.
 */
public class ListContainer implements Container {

    private final Supplier<List<ItemStack>> items;
    private final Runnable changed;

    public ListContainer(Supplier<List<ItemStack>> items, Runnable changed) {
        this.items = items;
        this.changed = changed;
    }

    private List<ItemStack> items() {
        List<ItemStack> list = items.get();
        return list == null ? List.of() : list;
    }

    @Override
    public int getContainerSize() {
        return items().size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        List<ItemStack> list = items();
        return slot >= 0 && slot < list.size() ? list.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items(), slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        List<ItemStack> list = items();
        if (slot >= 0 && slot < list.size()) {
            list.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public void setChanged() {
        changed.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        List<ItemStack> list = items();
        for (int i = 0; i < list.size(); i++) {
            list.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
