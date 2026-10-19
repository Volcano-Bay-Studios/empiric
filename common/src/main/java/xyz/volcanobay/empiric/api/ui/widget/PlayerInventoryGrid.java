package xyz.volcanobay.empiric.api.ui.widget;

import net.minecraft.world.entity.player.Inventory;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.layout.FlexLayout;

/**
 * The viewing player's main inventory and hotbar. It's only filled in a menu, which provides the inventory.
 */
public class PlayerInventoryGrid extends Widget {

    public PlayerInventoryGrid() {
        layout(FlexLayout.column(4));
    }

    @Override
    protected void onAttach() {
        if (!children().isEmpty()) {
            return;
        }
        Inventory inventory = root().service(Inventory.class);
        if (inventory != null) {
            add(new SlotGrid(inventory, 9, 9, 27));
            add(new SlotGrid(inventory, 9, 0, 9));
        }
    }
}
