package xyz.volcanobay.empiric.client.editor.layout;

import com.mojang.blaze3d.pipeline.TextureTarget;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiButtonFlags;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.client.ui.ClientUiHost;
import xyz.volcanobay.empiric.client.ui.OffscreenUi;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.Blueprints;
import xyz.volcanobay.empiric.core.ui.bind.UiException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The editor's live view of a layout: the tree is rebuilt into real widgets on every change and drawn into a texture
 * shown on a canvas that pans and zooms like an image editor. The texture is drawn at up to four times the widget
 * resolution as you zoom in. In select mode a click picks the widget's node, in interact mode the pointer goes to the widgets,
 * so sliders and buttons can be tried against the preview module.
 *
 * @deprecated part of the deprecated ImGui {@link LayoutEditor}; the new editor uses {@link PreviewCanvas}.
 */
@Deprecated
final class LayoutPreview {

    private static final long TICK_MILLIS = 50;
    private static final int SELECTED = 0xFF66D39A;
    private static final int HOVERED = 0x80FFFFFF;
    private static final int BACKDROP = 0xFF16161A;
    private static final int FRAME = 0xFF5A5A66;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 8;
    private static final float ZOOM_STEP = 1.15f;
    private static final float FIT_MARGIN = 12;
    private static final int MAX_DENSITY = 4;
    private static final int MAX_TEXTURE = 4096;

    private final Map<Widget, BlueprintNode> nodes = new IdentityHashMap<>();
    private final Map<BlueprintNode, Widget> widgets = new IdentityHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private UiRoot root;
    private ClientUiHost host;
    private TextureTarget target;
    private int builtVersion = -1;
    private Object builtFor;
    private Module<?> builtSource;
    private long lastTick;
    private boolean pressed;
    private int pressedButton;
    private double lastX;
    private double lastY;
    private float zoom = 1;
    private float panX;
    private float panY;
    private boolean panning;
    private int panButton;
    private boolean fitPending = true;
    private Object fittedFor;
    private float canvasWidth = 1;
    private float canvasHeight = 1;

    List<String> problems() {
        return problems;
    }

    void invalidate() {
        builtVersion = -1;
    }

    /**
     * Rebuilds the widgets if the document or the module it binds to changed.
     */
    void update(LayoutDocument document, Module<?> source, int width, int height) {
        if (root == null || document.version() != builtVersion || builtFor != document || builtSource != source) {
            rebuild(document, source);
        }
        if (root.width() != width || root.height() != height) {
            root.resize(width, height);
        }
        long now = Util.getMillis();
        if (now - lastTick >= TICK_MILLIS) {
            lastTick = now;
            root.tick();
        }
    }

    private void rebuild(LayoutDocument document, Module<?> source) {
        dispose();
        builtVersion = document.version();
        builtFor = document;
        builtSource = source;
        problems.clear();
        nodes.clear();
        widgets.clear();
        Widget content = new Widget().fill();
        try {
            Widget built = Blueprints.build(document.root(), Blueprints.Context.lenient(source, problems::add, (node, widget) -> {
                nodes.put(widget, node);
                widgets.put(node, widget);
            }));
            content.add(built);
        } catch (UiException e) {
            problems.add(e.getMessage());
        }
        host = new ClientUiHost(Minecraft.getInstance().font, () -> {
        });
        root = new UiRoot(content, source, host);
        DataNode theme = document.root().prop("theme");
        if (theme != null && !theme.isNull()) {
            root.setTheme(host.theme(theme.asString()));
        }
    }

    float zoom() {
        return zoom;
    }

    /**
     * Fits the whole preview in the canvas on the next frame.
     */
    void fit() {
        fitPending = true;
    }

    /**
     * Shows the preview at one screen pixel per widget pixel, keeping the canvas centre in place.
     */
    void actualSize() {
        zoomAround(canvasWidth / 2, canvasHeight / 2, 1);
    }

    /**
     * Draws the preview on a canvas filling the rest of the space, then handles the mouse over it.
     */
    BlueprintNode render(LayoutDocument document, boolean interact) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImVec2 available = ImGui.getContentRegionAvail();
        canvasWidth = Math.max(1, available.x);
        canvasHeight = Math.max(1, available.y);
        ImGui.invisibleButton("##canvas", canvasWidth, canvasHeight,
                ImGuiButtonFlags.MouseButtonLeft | ImGuiButtonFlags.MouseButtonRight | ImGuiButtonFlags.MouseButtonMiddle);
        boolean hovered = ImGui.isItemHovered();
        int width = root.width();
        int height = root.height();
        if (fitPending || fittedFor != document) {
            fitPending = false;
            fittedFor = document;
            float fitted = Math.min((canvasWidth - 2 * FIT_MARGIN) / width, (canvasHeight - 2 * FIT_MARGIN) / height);
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fitted));
            panX = (canvasWidth - width * zoom) / 2;
            panY = (canvasHeight - height * zoom) / 2;
        }
        ImVec2 mouse = ImGui.getMousePos();
        float localX = mouse.x - origin.x;
        float localY = mouse.y - origin.y;
        float wheel = ImGui.getIO().getMouseWheel();
        boolean zoomWheel = hovered && wheel != 0 && (!interact || ImGui.getIO().getKeyCtrl());
        if (zoomWheel) {
            zoomAround(localX, localY, zoom * (float) Math.pow(ZOOM_STEP, wheel));
        }
        if (hovered && !panning && (ImGui.isMouseClicked(2) || (!interact && ImGui.isMouseClicked(1)))) {
            panning = true;
            panButton = ImGui.isMouseClicked(2) ? 2 : 1;
        }
        if (panning) {
            if (ImGui.isMouseDown(panButton)) {
                panX += ImGui.getIO().getMouseDeltaX();
                panY += ImGui.getIO().getMouseDeltaY();
            } else {
                panning = false;
            }
        }

        int density = Math.max(1, Math.min(MAX_DENSITY, (int) Math.ceil(zoom)));
        int textureWidth = Math.min(MAX_TEXTURE, width * density);
        int textureHeight = Math.min(MAX_TEXTURE, height * density);
        if (target == null) {
            target = new TextureTarget(textureWidth, textureHeight, true, Minecraft.ON_OSX);
            target.setClearColor(0.1f, 0.1f, 0.12f, 1);
        } else if (target.width != textureWidth || target.height != textureHeight) {
            target.resize(textureWidth, textureHeight, Minecraft.ON_OSX);
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        OffscreenUi.draw(target, host.font(), width, height, canvas -> root.render(canvas, partialTick));

        ImDrawList draw = ImGui.getWindowDrawList();
        draw.pushClipRect(origin.x, origin.y, origin.x + canvasWidth, origin.y + canvasHeight, true);
        draw.addRectFilled(origin.x, origin.y, origin.x + canvasWidth, origin.y + canvasHeight, abgr(BACKDROP));
        ImVec2 image = new ImVec2(origin.x + panX, origin.y + panY);
        draw.addImage(target.getColorTextureId(), image.x, image.y, image.x + width * zoom, image.y + height * zoom, 0, 1, 1, 0);
        draw.addRect(image.x - 1, image.y - 1, image.x + width * zoom + 1, image.y + height * zoom + 1, abgr(FRAME));
        double x = (localX - panX) / zoom;
        double y = (localY - panY) / zoom;
        Widget selected = widgets.get(document.selected());
        if (selected != null) {
            outline(draw, selected, image, zoom, SELECTED);
        }
        BlueprintNode clicked = null;
        if (interact) {
            pointer(hovered && !panning, x, y, !zoomWheel);
        } else if (hovered && !panning) {
            Widget under = deepest(root.content(), x, y);
            if (under != null) {
                outline(draw, under, image, zoom, HOVERED);
                if (ImGui.isMouseClicked(0)) {
                    clicked = nodes.get(under);
                }
            }
        }
        draw.addText(origin.x + 4, origin.y + canvasHeight - ImGui.getTextLineHeight() - 2, abgr(FRAME), Math.round(zoom * 100) + "%");
        draw.popClipRect();
        return clicked;
    }

    private void zoomAround(float localX, float localY, float wanted) {
        float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, wanted));
        float pointX = (localX - panX) / zoom;
        float pointY = (localY - panY) / zoom;
        zoom = next;
        panX = localX - pointX * zoom;
        panY = localY - pointY * zoom;
    }

    private void pointer(boolean hovered, double x, double y, boolean wheelToWidgets) {
        if (x != lastX || y != lastY) {
            if (pressed) {
                root.pointerDrag(x, y, x - lastX, y - lastY, pressedButton);
            } else if (hovered) {
                root.pointerMove(x, y);
            }
            lastX = x;
            lastY = y;
        }
        if (pressed && !ImGui.isMouseDown(pressedButton == Keys.LEFT_BUTTON ? 0 : 1)) {
            root.pointerUp(x, y, pressedButton);
            pressed = false;
        }
        if (!hovered) {
            return;
        }
        for (int button = 0; button < 2; button++) {
            if (!pressed && ImGui.isMouseClicked(button)) {
                pressedButton = button == 0 ? Keys.LEFT_BUTTON : Keys.RIGHT_BUTTON;
                pressed = root.pointerDown(x, y, pressedButton);
            }
        }
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0 && wheelToWidgets) {
            root.scroll(x, y, wheel);
        }
    }

    /**
     * The deepest widget made from a node under the point, so clicks pick what was drawn on top.
     */
    private Widget deepest(Widget widget, double x, double y) {
        if (!widget.isShown()) {
            return null;
        }
        List<Widget> children = widget.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget found = deepest(children.get(i), x, y);
            if (found != null) {
                return found;
            }
        }
        if (!nodes.containsKey(widget)) {
            return null;
        }
        UiPoint local = widget.toLocal(x, y);
        return widget.contains(local.x(), local.y()) ? widget : null;
    }

    private static void outline(ImDrawList draw, Widget widget, ImVec2 origin, float scale, int color) {
        if (!widget.isShown()) {
            return;
        }
        UiPoint topLeft = widget.toRoot(0, 0);
        UiPoint bottomRight = widget.toRoot(widget.width(), widget.height());
        draw.addRect(origin.x + (float) topLeft.x() * scale, origin.y + (float) topLeft.y() * scale,
                origin.x + (float) bottomRight.x() * scale, origin.y + (float) bottomRight.y() * scale, abgr(color));
    }

    /**
     * ImGui packs colours as ABGR.
     */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    void dispose() {
        if (root != null) {
            root.dispose();
            root = null;
        }
        pressed = false;
    }

    void free() {
        dispose();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
