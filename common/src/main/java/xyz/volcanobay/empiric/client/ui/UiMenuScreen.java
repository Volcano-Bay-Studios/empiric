package xyz.volcanobay.empiric.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;
import xyz.volcanobay.empiric.api.ui.menu.UiSlot;
import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;

/**
 * Hosts a widget tree with slots. Vanilla handles the slots, so clicking, shift-click, drag-splitting, hotbar keys and
 * the carried item all behave as in any container. Each frame the slots move to their widgets.
 */
public class UiMenuScreen extends AbstractContainerScreen<UiMenu> {

    private final ClientUiHost.Characters characters = new ClientUiHost.Characters();
    private ClientUiHost host;

    public UiMenuScreen(UiMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private UiRoot root() {
        return menu.root();
    }

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        if (host == null) {
            host = new ClientUiHost(font, this::onClose);
            root().setHost(host);
        }
        root().resize(width, height);
    }

    @Override
    protected void containerTick() {
        if (menu.module() == null || !menu.module().isAttached()) {
            onClose();
            return;
        }
        root().tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        placeSlots();
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiCanvas canvas = new GuiCanvas(graphics, host.font(), font);
        root().renderPopups(canvas, partialTick);
        PresenceCursors.render(canvas, menu.screenType(), menu.module(), root());
        canvas.finish();
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiCanvas canvas = new GuiCanvas(graphics, host.font(), font);
        root().renderContent(canvas, partialTick);
        canvas.finish();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void placeSlots() {
        UiRoot root = root();
        root.ensureLayout();
        for (Slot slot : menu.slots) {
            if (slot instanceof UiSlot uiSlot) {
                SlotWidget widget = uiSlot.widget();
                UiPoint corner = widget.toRoot(1, 1);
                uiSlot.place((int) corner.x(), (int) corner.y(), visible(widget, corner));
            }
        }
    }

    private boolean visible(SlotWidget widget, UiPoint corner) {
        if (widget.root() != root() || !widget.isShown()) {
            return false;
        }
        for (Widget ancestor = widget.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (ancestor.clips()) {
                UiPoint first = ancestor.toLocal(corner.x(), corner.y());
                UiPoint last = ancestor.toLocal(corner.x() + 15, corner.y() + 15);
                if (!ancestor.contains(first.x(), first.y()) || !ancestor.contains(last.x(), last.y())) {
                    return false;
                }
            }
        }
        for (Widget popup : root().popups()) {
            UiPoint local = popup.toLocal(corner.x() + 8, corner.y() + 8);
            if (popup.contains(local.x(), local.y())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        root().pointerMove(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Widget hit = root().pick(mouseX, mouseY);
        if (hit instanceof SlotWidget) {
            root().focus(null);
        } else if (root().pointerDown(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (root().captured() != null) {
            return root().pointerUp(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (root().captured() != null) {
            return root().pointerDrag(mouseX, mouseY, dragX, dragY, button);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return root().scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return root().pick(mouseX, mouseY) == null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (root().key(keyCode, modifiers)) {
            return true;
        }
        if (root().focused() != null && keyCode != Keys.ESCAPE) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        int codepoint = characters.accept(character);
        return codepoint < 0 || root().character(codepoint, modifiers);
    }
}
