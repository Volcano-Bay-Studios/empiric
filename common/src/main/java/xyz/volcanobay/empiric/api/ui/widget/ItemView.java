package xyz.volcanobay.empiric.api.ui.widget;

import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.ItemCanvas;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.widget.ValueWidget;

/**
 * Shows an item stack as in an inventory, scaled by whole steps. Bind it to an {@code ItemStack} field to show it live.
 */
public class ItemView extends ValueWidget<ItemStack> {

    private int scale = 1;

    public ItemView() {
        this(ItemStack.EMPTY);
    }

    public ItemView(ItemStack stack) {
        super(ItemStack.class, stack);
    }

    public ItemView scale(int scale) {
        this.scale = Math.max(1, scale);
        requestLayout();
        return this;
    }

    @Override
    protected int contentWidth() {
        return 16 * scale;
    }

    @Override
    protected int contentHeight() {
        return 16 * scale;
    }

    @Override
    protected void draw(UiCanvas canvas, float partialTick) {
        if (!(canvas instanceof ItemCanvas items) || value() == null) {
            return;
        }
        canvas.push();
        canvas.translate((width() - 16 * scale) / 2f, (height() - 16 * scale) / 2f);
        canvas.scale(scale, scale);
        items.item(value(), 0, 0);
        canvas.pop();
    }
}
