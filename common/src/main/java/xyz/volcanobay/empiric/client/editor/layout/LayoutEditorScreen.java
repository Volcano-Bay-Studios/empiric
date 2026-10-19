package xyz.volcanobay.empiric.client.editor.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.volcanobay.empiric.client.ui.ClientUiHost;
import xyz.volcanobay.empiric.client.ui.GuiCanvas;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiRoot;

/**
 * Hosts the {@link LayoutEditorView} as a screen. It draws at a GUI scale of at most two whatever the game's is, so
 * the three panes have room. Escape closes popups and focus first, and asks before throwing away unsaved changes.
 */
public final class LayoutEditorScreen extends Screen {

    private static final double EDITOR_SCALE = 2;

    private final String initial;
    private final ClientUiHost.Characters characters = new ClientUiHost.Characters();
    private LayoutEditorView view;
    private UiRoot root;
    private ClientUiHost host;
    private float factor = 1;

    /**
     * @param initial the layout to open straight away, or null or empty for the list
     */
    public LayoutEditorScreen(String initial) {
        super(Component.literal("Empiric Layouts"));
        this.initial = initial;
    }

    @Override
    protected void init() {
        if (root == null) {
            host = new ClientUiHost(font, this::onClose);
            view = new LayoutEditorView(new ClientEditorEnvironment());
            root = new UiRoot(view, null, host);
            if (initial != null && !initial.isEmpty() && !view.open(initial)) {
                view.create(initial);
            }
        }
        double guiScale = minecraft == null ? EDITOR_SCALE : minecraft.getWindow().getGuiScale();
        factor = (float) (Math.min(guiScale, EDITOR_SCALE) / guiScale);
        root.resize(Math.round(width / factor), Math.round(height / factor));
    }

    private double scaled(double value) {
        return value / factor;
    }

    @Override
    public void tick() {
        root.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().scale(factor, factor, 1);
        GuiCanvas canvas = new GuiCanvas(graphics, host.font(), font);
        root.render(canvas, partialTick);
        canvas.finish();
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        root.pointerMove(scaled(mouseX), scaled(mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return root.pointerDown(scaled(mouseX), scaled(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return root.pointerUp(scaled(mouseX), scaled(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return root.pointerDrag(scaled(mouseX), scaled(mouseY), scaled(dragX), scaled(dragY), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return root.scroll(scaled(mouseX), scaled(mouseY), scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (root.key(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == Keys.ESCAPE) {
            if (!view.dialogOpen()) {
                view.confirmDiscard(this::onClose);
            }
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
    }
}
