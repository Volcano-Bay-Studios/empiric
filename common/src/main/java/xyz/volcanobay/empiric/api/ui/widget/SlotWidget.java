package xyz.volcanobay.empiric.api.ui.widget;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.ItemCanvas;
import xyz.volcanobay.empiric.api.ui.menu.SlotActions;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.widget.Draw;

import java.util.function.Predicate;

/**
 * One item slot. In a menu it becomes a real vanilla slot, so shift-click, drag-splitting and hotbar keys work, and the
 * screen moves the slot to wherever the widget is laid out.
 */
public class SlotWidget extends Widget {

    public static final int SIZE = 18;

    private final Container container;
    private final int index;
    private Predicate<ItemStack> filter = stack -> true;
    private boolean inMenu;

    public SlotWidget(Container container, int index) {
        this.container = container;
        this.index = index;
    }

    public Container container() {
        return container;
    }

    public int index() {
        return index;
    }

    public ItemStack stack() {
        return container.getItem(index);
    }

    public SlotWidget filter(Predicate<ItemStack> filter) {
        this.filter = filter;
        return this;
    }

    public boolean mayPlace(ItemStack stack) {
        return filter.test(stack);
    }

    /**
     * Set once a menu owns this slot, which then draws the item itself.
     */
    public void inMenu(boolean inMenu) {
        this.inMenu = inMenu;
    }

    @Override
    protected boolean interactive() {
        return true;
    }

    /**
     * Outside a menu, a click goes to the host's {@link SlotActions}, if it offers one.
     */
    @Override
    protected boolean pointerDown(double x, double y, int button) {
        if (inMenu) {
            return false;
        }
        SlotActions actions = root().service(SlotActions.class);
        return actions != null && actions.click(this, button);
    }

    @Override
    protected int contentWidth() {
        return SIZE;
    }

    @Override
    protected int contentHeight() {
        return SIZE;
    }

    @Override
    protected void draw(UiCanvas canvas, float partialTick) {
        Draw.box(canvas, theme(), "slot", "slot_border", 0, 0, width(), height());
        if (!inMenu && canvas instanceof ItemCanvas items) {
            items.item(stack(), 1, 1);
        }
        if (!inMenu && isHovered() && root().service(SlotActions.class) != null) {
            canvas.overlay();
            canvas.fill(1, 1, width() - 1, height() - 1, theme().color("slot_hover"));
        }
    }
}
