package xyz.volcanobay.empiric.api.ui.widget;

import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.ItemCanvas;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.widget.ValueWidget;

/**
 * A turning 3D preview of an item or block. It spins on its own until dragged, and dragging turns it by hand.
 */
public class ModelView extends ValueWidget<ItemStack> {

    private float yaw = 30;
    private float pitch = 20;
    private float previousYaw = 30;
    private float spin = 2;
    private boolean dragging;

    public ModelView() {
        this(ItemStack.EMPTY);
    }

    public ModelView(ItemStack stack) {
        super(ItemStack.class, stack);
    }

    /**
     * Degrees turned per tick while it isn't being dragged, 0 to hold still.
     */
    public ModelView spin(float degreesPerTick) {
        this.spin = degreesPerTick;
        return this;
    }

    public ModelView angle(float yaw, float pitch) {
        this.yaw = this.previousYaw = yaw;
        this.pitch = pitch;
        return this;
    }

    @Override
    protected boolean interactive() {
        return true;
    }

    @Override
    protected boolean pointerDown(double x, double y, int button) {
        if (button != Keys.LEFT_BUTTON) {
            return false;
        }
        dragging = true;
        return true;
    }

    @Override
    protected void pointerDrag(double x, double y, double dx, double dy, int button) {
        yaw += (float) dx * 2;
        previousYaw = yaw;
        pitch = (float) Math.max(-90, Math.min(90, pitch + dy * 2));
    }

    @Override
    protected void pointerUp(double x, double y, int button) {
        dragging = false;
    }

    @Override
    protected void tick() {
        previousYaw = yaw;
        if (!dragging) {
            yaw += spin;
        }
    }

    @Override
    protected int contentWidth() {
        return 48;
    }

    @Override
    protected int contentHeight() {
        return 48;
    }

    @Override
    protected void draw(UiCanvas canvas, float partialTick) {
        if (canvas instanceof ItemCanvas items && value() != null) {
            float shown = previousYaw + (yaw - previousYaw) * partialTick;
            items.model(value(), width() / 2f, height() / 2f, Math.min(width(), height()) * 0.6f, shown, pitch);
        }
    }
}
