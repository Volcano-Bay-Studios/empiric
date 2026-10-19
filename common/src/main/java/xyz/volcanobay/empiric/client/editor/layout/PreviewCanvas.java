package xyz.volcanobay.empiric.client.editor.layout;

import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.UiHost;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.bind.UiException;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.Blueprints;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The layout being edited, built into real widgets in a widget tree of its own and drawn on a canvas that pans and zooms
 * like an image editor. It's rebuilt whenever the document or the module it binds to changes.
 * <p>
 * In select mode a click picks the node a widget came from. In interact mode the pointer and keys go to the widgets, so
 * sliders and buttons can be tried against the preview module. The middle button pans (the right button too in select
 * mode), and the wheel zooms around the pointer (with control held in interact mode, where the plain wheel scrolls).
 */
public final class PreviewCanvas extends Widget {

    private static final int SELECTED = 0xFF66D39A;
    private static final int HOVERED = 0x80FFFFFF;
    private static final int BACKDROP = 0xFF16161A;
    private static final int FRAME = 0xFF5A5A66;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 8;
    private static final float ZOOM_STEP = 1.15f;
    private static final float FIT_MARGIN = 8;

    private final LayoutEditorView view;
    private final Map<Widget, BlueprintNode> nodes = new IdentityHashMap<>();
    private final Map<BlueprintNode, Widget> widgets = new IdentityHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private UiRoot preview;
    private int builtVersion = -1;
    private Object builtFor;
    private Module<?> builtSource;
    private int previewWidth = 320;
    private int previewHeight = 200;
    private boolean interact;
    private float zoom = 1;
    private float panX;
    private float panY;
    private boolean fitPending = true;
    private int panButton = -1;
    private int pressedButton = -1;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;

    PreviewCanvas(LayoutEditorView view) {
        this.view = view;
        clip(true);
    }

    public List<String> problems() {
        return problems;
    }

    public UiRoot preview() {
        return preview;
    }

    public float zoom() {
        return zoom;
    }

    public float panX() {
        return panX;
    }

    public float panY() {
        return panY;
    }

    public int previewWidth() {
        return previewWidth;
    }

    public int previewHeight() {
        return previewHeight;
    }

    public void previewSize(int width, int height) {
        previewWidth = Math.max(16, Math.min(1024, width));
        previewHeight = Math.max(16, Math.min(1024, height));
    }

    public boolean interact() {
        return interact;
    }

    public void interact(boolean interact) {
        if (this.interact != interact && preview != null) {
            preview.focus(null);
            preview.closePopups();
            preview.pointerMove(-10000, -10000);
        }
        this.interact = interact;
    }

    /**
     * Fits the whole preview in the canvas once it's next laid out.
     */
    public void fit() {
        fitPending = true;
        if (width() > 0 && height() > 0) {
            applyFit();
        }
    }

    /**
     * One canvas pixel per preview pixel, keeping the middle of the canvas in place.
     */
    public void actualSize() {
        zoomAround(width() / 2f, height() / 2f, 1);
    }

    /**
     * Rebuilds the preview from the document if anything it depends on changed.
     */
    void update() {
        LayoutDocument document = view.document();
        if (document == null) {
            dispose();
            return;
        }
        Module<?> source = view.module();
        if (preview == null || document.version() != builtVersion || builtFor != document || builtSource != source) {
            if (builtFor != document) {
                fitPending = true;
            }
            rebuild(document, source);
        }
        if (preview.width() != previewWidth || preview.height() != previewHeight) {
            preview.resize(previewWidth, previewHeight);
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
        UiHost host = view.environment().previewHost();
        preview = new UiRoot(content, source, host);
        DataNode theme = document.root().prop("theme");
        if (theme != null && !theme.isNull()) {
            preview.setTheme(host.theme(theme.asString()));
        }
        preview.resize(previewWidth, previewHeight);
        lastX = Double.NaN;
        lastY = Double.NaN;
        pressedButton = -1;
    }

    void dispose() {
        if (preview != null) {
            preview.dispose();
            preview = null;
        }
        builtFor = null;
    }

    /**
     * The widget built from a node, for tests and outlines.
     */
    public Widget widgetFor(BlueprintNode node) {
        return widgets.get(node);
    }

    public double toPreviewX(double x) {
        return (x - panX) / zoom;
    }

    public double toPreviewY(double y) {
        return (y - panY) / zoom;
    }

    private void applyFit() {
        fitPending = false;
        float fitted = Math.min((width() - 2 * FIT_MARGIN) / previewWidth, (height() - 2 * FIT_MARGIN) / previewHeight);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fitted));
        panX = (width() - previewWidth * zoom) / 2;
        panY = (height() - previewHeight * zoom) / 2;
    }

    private void zoomAround(float x, float y, float wanted) {
        float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, wanted));
        float pointX = (x - panX) / zoom;
        float pointY = (y - panY) / zoom;
        zoom = next;
        panX = x - pointX * zoom;
        panY = y - pointY * zoom;
    }

    /**
     * The deepest widget built from a node under a preview point, so a click picks what was drawn on top.
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

    /**
     * The node whose widget is at a point on the canvas, or null.
     */
    public BlueprintNode nodeAt(double x, double y) {
        if (preview == null) {
            return null;
        }
        Widget under = deepest(preview.content(), toPreviewX(x), toPreviewY(y));
        return under == null ? null : nodes.get(under);
    }

    @Override
    protected void onLayout() {
        if (fitPending && width() > 0 && height() > 0) {
            applyFit();
        }
    }

    @Override
    protected void tick() {
        update();
        if (preview != null) {
            preview.tick();
        }
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean capturesKeyboard() {
        return interact;
    }

    @Override
    protected boolean interactive() {
        return true;
    }

    @Override
    protected boolean pointerDown(double x, double y, int button) {
        update();
        if (button == Keys.MIDDLE_BUTTON || (button == Keys.RIGHT_BUTTON && !interact)) {
            panButton = button;
            return true;
        }
        if (interact) {
            pressedButton = button;
            preview.pointerDown(toPreviewX(x), toPreviewY(y), button);
            return true;
        }
        if (button == Keys.LEFT_BUTTON) {
            BlueprintNode clicked = nodeAt(x, y);
            if (clicked != null) {
                view.document().select(clicked);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void pointerDrag(double x, double y, double dx, double dy, int button) {
        if (button == panButton) {
            panX += (float) dx;
            panY += (float) dy;
            return;
        }
        if (interact && button == pressedButton && preview != null) {
            preview.pointerDrag(toPreviewX(x), toPreviewY(y), dx / zoom, dy / zoom, button);
            lastX = x;
            lastY = y;
        }
    }

    @Override
    protected void pointerUp(double x, double y, int button) {
        if (button == panButton) {
            panButton = -1;
            return;
        }
        if (button == pressedButton && preview != null) {
            preview.pointerUp(toPreviewX(x), toPreviewY(y), button);
        }
        pressedButton = -1;
    }

    @Override
    protected boolean scroll(double x, double y, double amount) {
        boolean control = root() != null && root().host().controlDown();
        if (interact && !control && preview != null) {
            preview.scroll(toPreviewX(x), toPreviewY(y), amount);
            return true;
        }
        zoomAround((float) x, (float) y, zoom * (float) Math.pow(ZOOM_STEP, amount));
        return true;
    }

    @Override
    protected boolean key(int key, int modifiers) {
        return interact && preview != null && preview.key(key, modifiers);
    }

    @Override
    protected boolean character(int codepoint, int modifiers) {
        return interact && preview != null && preview.character(codepoint, modifiers);
    }

    @Override
    protected void focusChanged(boolean focused) {
        if (!focused && preview != null) {
            preview.focus(null);
        }
    }

    @Override
    protected void hoverChanged(boolean hovered) {
        if (!hovered && preview != null && interact && pressedButton < 0) {
            preview.pointerMove(-10000, -10000);
            lastX = Double.NaN;
        }
    }

    /**
     * Passes pointer movement to the preview while interacting, since the editor's tree only reports hovering.
     */
    private void trackPointer() {
        if (!interact || preview == null || root() == null || !isHovered() || pressedButton >= 0) {
            return;
        }
        UiPoint local = toLocal(root().pointerX(), root().pointerY());
        if (local.x() != lastX || local.y() != lastY) {
            lastX = local.x();
            lastY = local.y();
            preview.pointerMove(toPreviewX(local.x()), toPreviewY(local.y()));
        }
    }

    @Override
    protected void draw(UiCanvas canvas, float partialTick) {
        update();
        trackPointer();
        int width = width();
        int height = height();
        canvas.fill(0, 0, width, height, BACKDROP);
        if (preview == null) {
            return;
        }
        canvas.pushClip(0, 0, width, height);
        float left = panX;
        float top = panY;
        float right = panX + previewWidth * zoom;
        float bottom = panY + previewHeight * zoom;
        canvas.outline(Math.round(left) - 1, Math.round(top) - 1, Math.round(right - left) + 2, Math.round(bottom - top) + 2, FRAME);
        canvas.push();
        canvas.translate(panX, panY);
        canvas.scale(zoom, zoom);
        canvas.pushClip(0, 0, previewWidth, previewHeight);
        preview.render(canvas, partialTick);
        canvas.popClip();
        canvas.pop();
        canvas.nextLayer();
        LayoutDocument document = view.document();
        Widget selected = document == null ? null : widgets.get(document.selected());
        if (selected != null) {
            outline(canvas, selected, SELECTED);
        }
        if (!interact && isHovered() && panButton < 0 && root() != null) {
            UiPoint local = toLocal(root().pointerX(), root().pointerY());
            Widget under = deepest(preview.content(), toPreviewX(local.x()), toPreviewY(local.y()));
            if (under != null && under != selected) {
                outline(canvas, under, HOVERED);
            }
        }
        canvas.text(Math.round(zoom * 100) + "%", 4, height - font().lineHeight() - 2, FRAME, false);
        canvas.popClip();
    }

    private void outline(UiCanvas canvas, Widget widget, int color) {
        if (!widget.isShown() || widget.root() == null) {
            return;
        }
        UiPoint topLeft = widget.toRoot(0, 0);
        UiPoint bottomRight = widget.toRoot(widget.width(), widget.height());
        int x0 = (int) Math.floor(panX + topLeft.x() * zoom);
        int y0 = (int) Math.floor(panY + topLeft.y() * zoom);
        int x1 = (int) Math.ceil(panX + bottomRight.x() * zoom);
        int y1 = (int) Math.ceil(panY + bottomRight.y() * zoom);
        canvas.outline(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), color);
    }

    @Override
    protected void onDetach() {
        dispose();
    }
}
