package xyz.volcanobay.empiric.api.ui.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;
import xyz.volcanobay.empiric.client.ui.UiClient;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.HeadlessHost;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.registry.EmpiricMenus;
import xyz.volcanobay.empiric.system.UiSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The vanilla menu behind a screen that has slots. Both sides build the same widget tree, and its {@link SlotWidget}s
 * become the menu's slots in tree order, so vanilla slot syncing lines up.
 */
public class UiMenu extends AbstractContainerMenu {

    private final UiScreenType<?> screen;
    private final Module<?> module;
    private final UiRoot root;
    private final List<Run> runs = new ArrayList<>();

    private record Run(int start, int end, boolean player) {
    }

    public UiMenu(int containerId, Inventory inventory, UiScreenType<?> screen, Module<?> module, UiRoot root) {
        super(EmpiricMenus.UI.get(), containerId);
        this.screen = screen;
        this.module = module;
        this.root = root;
        for (SlotWidget widget : slots(root.content())) {
            widget.inMenu(true);
            addSlot(new UiSlot(widget));
        }
        for (int i = 0; i < slots.size(); i++) {
            int start = i;
            while (i + 1 < slots.size() && slots.get(i + 1).container == slots.get(start).container) {
                i++;
            }
            runs.add(new Run(start, i + 1, slots.get(start).container instanceof Inventory));
        }
    }

    /**
     * Builds a screen's widget tree for a player without drawing it, with the player's inventory available to widgets.
     */
    public static UiRoot build(Widget content, Module<?> module, Inventory inventory) {
        return new UiRoot(content, module, HeadlessHost.INSTANCE, Map.of(Inventory.class, inventory, Player.class, inventory.player));
    }

    public static List<SlotWidget> slots(Widget widget) {
        List<SlotWidget> slots = new ArrayList<>();
        collect(widget, slots);
        return slots;
    }

    private static void collect(Widget widget, List<SlotWidget> slots) {
        if (widget instanceof SlotWidget slot) {
            slots.add(slot);
        }
        for (Widget child : widget.children()) {
            collect(child, slots);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static UiMenu fromClient(int containerId, Inventory inventory) {
        UiSystem.Target target = UiClient.takePending(containerId);
        if (target == null) {
            return new UiMenu(containerId, inventory, null, null, build(new Widget(), null, inventory));
        }
        Widget content = ((UiScreenType) target.screen()).create(target.module());
        return new UiMenu(containerId, inventory, target.screen(), target.module(), build(content, target.module(), inventory));
    }

    public UiScreenType<?> screenType() {
        return screen;
    }

    public Module<?> module() {
        return module;
    }

    public UiRoot root() {
        return root;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromPlayer = slot.container instanceof Inventory;
        for (Run run : runs) {
            if (run.player() != fromPlayer && !stack.isEmpty()) {
                moveItemStackTo(stack, run.start(), run.end(), run.player());
            }
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (module == null || !module.isAttached()) {
            return false;
        }
        return !(player instanceof ServerPlayer serverPlayer) || UiSystem.canView(serverPlayer, screen, module);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer && module != null) {
            UiSystem.menuClosed(serverPlayer, screen, module);
        }
        root.dispose();
    }
}
