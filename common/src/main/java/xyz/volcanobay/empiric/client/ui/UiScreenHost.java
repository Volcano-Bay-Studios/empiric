package xyz.volcanobay.empiric.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;

/**
 * Hosts a widget tree without slots as a vanilla screen. It closes itself once its module is detached.
 */
public class UiScreenHost extends Screen {

    private final UiScreenType<?> screenType;
    private final Module<?> module;
    private final Widget content;
    private final ClientUiHost.Characters characters = new ClientUiHost.Characters();
    private boolean tracked;
    private UiRoot root;
    private ClientUiHost host;

    public UiScreenHost(UiScreenType<?> screenType, Module<?> module, Widget content, boolean tracked) {
        super(Component.literal(screenType.id().toString()));
        this.screenType = screenType;
        this.module = module;
        this.content = content;
        this.tracked = tracked;
    }

    public UiScreenType<?> screenType() {
        return screenType;
    }

    public Module<?> module() {
        return module;
    }

    public UiRoot root() {
        return root;
    }

    @Override
    protected void init() {
        if (root == null) {
            host = new ClientUiHost(font, this::onClose);
            root = new UiRoot(content, module, host);
        }
        root.resize(width, height);
    }

    @Override
    public void tick() {
        if (!module.isAttached()) {
            onClose();
            return;
        }
        root.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        GuiCanvas canvas = new GuiCanvas(graphics, host.font(), font);
        root.render(canvas, partialTick);
        PresenceCursors.render(canvas, screenType, module, root);
        canvas.finish();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        root.pointerMove(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return root.pointerDown(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return root.pointerUp(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return root.pointerDrag(mouseX, mouseY, dragX, dragY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return root.scroll(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (root.key(keyCode, modifiers)) {
            return true;
        }
        if (root.focused() == null && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        int codepoint = characters.accept(character);
        return codepoint < 0 || root.character(codepoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (root != null) {
            root.dispose();
        }
        if (tracked) {
            UiClient.closed(screenType, module);
        }
    }

    void closeFromServer() {
        tracked = false;
        onClose();
    }

    /**
     * The server already replaced this screen, so closing it shouldn't report back.
     */
    void untrack() {
        tracked = false;
    }
}
