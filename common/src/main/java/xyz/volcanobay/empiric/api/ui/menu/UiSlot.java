package xyz.volcanobay.empiric.api.ui.menu;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;
import xyz.volcanobay.empiric.mixin.SlotAccessor;

/**
 * A menu slot backed by a {@link SlotWidget}. The client screen moves it to the widget each frame and turns it off while
 * the widget is hidden or scrolled out of view.
 */
public class UiSlot extends Slot {

    private final SlotWidget widget;
    private boolean active = true;

    public UiSlot(SlotWidget widget) {
        super(widget.container(), widget.index(), 0, 0);
        this.widget = widget;
    }

    public SlotWidget widget() {
        return widget;
    }

    public void place(int x, int y, boolean active) {
        ((SlotAccessor) this).empiric$setX(x);
        ((SlotAccessor) this).empiric$setY(y);
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return widget.mayPlace(stack) && super.mayPlace(stack);
    }
}
