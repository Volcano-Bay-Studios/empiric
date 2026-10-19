package xyz.volcanobay.empiric.gametest;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.client.editor.layout.EditorEnvironment;
import xyz.volcanobay.empiric.client.editor.layout.LayoutDocument;
import xyz.volcanobay.empiric.client.editor.layout.LayoutEdits;
import xyz.volcanobay.empiric.client.editor.layout.LayoutEditorView;
import xyz.volcanobay.empiric.client.editor.layout.PreviewCanvas;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiHost;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.Checkbox;
import xyz.volcanobay.empiric.core.ui.widget.ContextMenu;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.NumberField;
import xyz.volcanobay.empiric.core.ui.widget.ScrollPanel;
import xyz.volcanobay.empiric.core.ui.widget.Section;
import xyz.volcanobay.empiric.core.ui.widget.Splitter;
import xyz.volcanobay.empiric.core.ui.widget.TextArea;
import xyz.volcanobay.empiric.core.ui.widget.TextField;
import xyz.volcanobay.empiric.core.ui.widget.TreeView;
import xyz.volcanobay.empiric.data.JsonNodes;
import xyz.volcanobay.empiric.gametest.UiGameTests.Console;
import xyz.volcanobay.empiric.gametest.UiGameTests.TestHost;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the widget-based layout editor the way a person would, through pointer and key input on its widget tree.
 */
final class UiEditorGameTests {

    private static final String ID = "empiric:test_editor";

    private UiEditorGameTests() {
    }

    static void editor(GameTestHelper helper) {
        Console console = UiGameTests.attachConsole(helper);
        console.power = false;
        console.gain = 3;
        UiLayouts.override(ID, BlueprintNode.parse(JsonNodes.toNode(JsonParser.parseString(UiGameTests.TEST_LAYOUT))));
        TestEnvironment environment = new TestEnvironment(console);
        LayoutEditorView view = new LayoutEditorView(environment);
        UiRoot root = new UiRoot(view, null, new TestHost());
        root.resize(720, 420);
        settle(root);

        click(root, UiGameTests.first(view, TextField.class), 2, 2);
        UiGameTests.typeText(root, "demo_panel");
        settle(root);
        ScrollPanel list = UiGameTests.first(view, ScrollPanel.class);
        helper.assertTrue(list.content().children().size() == 1, "searching the layouts left " + list.content().children().size() + " rows");
        click(root, list.content().children().getFirst(), 4, 3);
        settle(root);
        helper.assertTrue(view.document() != null && view.document().id().equals("empiric:demo_panel"), "clicking a layout in the list didn't open it");
        view.back();
        helper.assertTrue(view.document() == null, "going back from an unchanged layout asked first");
        helper.assertTrue(view.open(ID), "the test layout didn't open");
        settle(root);
        LayoutDocument document = view.document();
        TreeView<BlueprintNode> tree = view.tree();
        helper.assertTrue(tree.visibleItems().size() == 6, "the tree showed " + tree.visibleItems().size() + " rows");

        click(root, row(tree, document.root().find("gain")), 30, 3);
        settle(root);
        helper.assertTrue(document.selected() == document.root().find("gain"), "clicking a tree row didn't select its node");
        TextField id = UiGameTests.first(view.propertiesPane(), TextField.class);
        helper.assertTrue(id.text().equals("gain"), "the id field showed " + id.text());
        click(root, id, 2, 2);
        root.key(Keys.A, Keys.CONTROL);
        UiGameTests.typeText(root, "level");
        settle(root);
        helper.assertTrue(document.root().find("level") != null && tree.visibleItems().contains(document.root().find("level")), "typing an id didn't rename the node");
        root.key(Keys.Z, Keys.CONTROL);
        settle(root);
        helper.assertTrue(document.root().find("gain") != null && document.root().find("level") == null, "one undo didn't take back the typed id");
        root.key(Keys.Y, Keys.CONTROL);
        helper.assertTrue(document.root().find("level") != null, "redo didn't bring the id back");
        root.key(Keys.Z, Keys.CONTROL);
        root.focus(null);
        settle(root);

        BlueprintNode gain = document.root().find("gain");
        helper.assertTrue(document.selected() == gain, "undo lost the selection");
        Widget visibleRow = propertyRow(root, view.propertiesPane(), "visible");
        Checkbox set = (Checkbox) visibleRow.children().get(0);
        Checkbox visible = (Checkbox) visibleRow.children().get(2);
        helper.assertTrue(!set.checked() && visible.checked() && visible.readOnly(), "an unset property didn't show its default, read-only");
        click(root, set, 1, 1);
        settle(root);
        helper.assertTrue(gain.prop("visible") != null && gain.prop("visible").asBoolean() && !visible.readOnly(), "ticking the checkbox didn't set the default");
        click(root, visible, 1, 1);
        settle(root);
        PreviewCanvas canvas = view.previewCanvas();
        helper.assertTrue(!gain.prop("visible").asBoolean() && canvas.widgetFor(gain) != null && !canvas.widgetFor(gain).isVisible(),
                "editing the property didn't reach the preview");

        click(root, row(tree, document.root()), 30, 3);
        settle(root);
        NumberField gap = (NumberField) propertyRow(root, view.propertiesPane(), "gap").children().get(2);
        click(root, gap.field(), 2, 2);
        root.key(Keys.A, Keys.CONTROL);
        UiGameTests.typeText(root, "7");
        helper.assertTrue(document.root().prop("gap").asNumber().intValue() == 7, "typing a number gave gap " + document.root().prop("gap"));
        click(root, gap.children().get(2), 2, 2);
        settle(root);
        helper.assertTrue(document.root().prop("gap").asNumber().intValue() == 8, "the step button gave gap " + document.root().prop("gap"));
        Section first = UiGameTests.first(view.propertiesPane(), Section.class);
        reveal(root, view.propertiesPane(), first);
        click(root, first.children().getFirst(), 3, 3);
        helper.assertTrue(!first.isOpen() && !first.body().isVisible(), "clicking a section header didn't fold it");
        click(root, first.children().getFirst(), 3, 3);

        int before = document.root().children().size();
        UiPoint menuAt = row(tree, document.root()).toRoot(30, 3);
        root.pointerDown(menuAt.x(), menuAt.y(), Keys.RIGHT_BUTTON);
        helper.assertTrue(root.popups().size() == 1 && root.popups().getFirst() instanceof ContextMenu, "right clicking a tree row didn't open its menu");
        hover(root, root.popups().getFirst().children().getFirst());
        helper.assertTrue(root.popups().size() == 2, "resting on Add child didn't open the categories");
        hover(root, root.popups().get(1).children().getFirst());
        helper.assertTrue(root.popups().size() == 3, "resting on a category didn't open its types");
        click(root, root.popups().get(2).children().getFirst(), 4, 3);
        settle(root);
        BlueprintNode added = document.root().children().getLast();
        helper.assertTrue(root.popups().isEmpty() && document.root().children().size() == before + 1 && document.selected() == added,
                "choosing a type didn't add a child and select it");

        BlueprintNode reset = document.root().find("reset");
        BlueprintNode power = document.root().find("power");
        UiPoint from = row(tree, reset).toRoot(30, 3);
        UiPoint to = row(tree, power).toRoot(30, 3);
        root.pointerDown(from.x(), from.y(), Keys.LEFT_BUTTON);
        root.pointerDrag(from.x(), from.y() - 5, 0, -5, Keys.LEFT_BUTTON);
        root.pointerDrag(to.x(), to.y(), to.x() - from.x(), to.y() - from.y() + 5, Keys.LEFT_BUTTON);
        root.pointerUp(to.x(), to.y(), Keys.LEFT_BUTTON);
        settle(root);
        helper.assertTrue(document.root().children().indexOf(reset) == 0 && document.root().children().indexOf(power) == 1,
                "dragging reset onto power left it at " + document.root().children().indexOf(reset));

        click(root, row(tree, added), 30, 3);
        root.key(Keys.DELETE, 0);
        settle(root);
        helper.assertTrue(!document.root().children().contains(added), "Delete didn't remove the selected widget");

        helper.assertTrue(!canvas.problems().isEmpty(), "unbound bindings weren't listed as problems");
        view.module(console);
        settle(root);
        helper.assertTrue(canvas.problems().isEmpty(), "bindings against the console still had problems: " + canvas.problems());
        BlueprintNode echo = document.root().find("echo");
        clickPreview(root, canvas, canvas.widgetFor(echo));
        helper.assertTrue(document.selected() == echo, "clicking the preview selected " + document.selected().describe());

        Checkbox interact = UiGameTests.all(view, Checkbox.class).stream().filter(box -> !inside(box, view.propertiesPane())).findFirst().orElseThrow();
        click(root, interact, 1, 1);
        helper.assertTrue(canvas.interact(), "the Interact checkbox didn't switch modes");
        clickPreview(root, canvas, canvas.widgetFor(power));
        helper.assertTrue(console.power, "a click in interact mode didn't reach the switch");
        click(root, interact, 1, 1);

        click(root, row(tree, reset), 30, 3);
        settle(root);
        TextArea actions = UiGameTests.first(view.propertiesPane(), TextArea.class);
        reveal(root, view.propertiesPane(), actions);
        click(root, actions, 5, 5);
        root.key(Keys.A, Keys.CONTROL);
        UiGameTests.typeText(root, "[");
        settle(root);
        helper.assertTrue(reset.prop("on_click").elementsOrSelf().size() == 2 && hasLabel(view.propertiesPane(), "Not valid yet"),
                "half-typed actions were applied or showed no reason");
        UiGameTests.typeText(root, "]");
        settle(root);
        helper.assertTrue(reset.prop("on_click").isArray() && reset.prop("on_click").elementsOrSelf().isEmpty() && !hasLabel(view.propertiesPane(), "Not valid yet"),
                "valid actions weren't applied");
        root.focus(null);

        Splitter splitter = UiGameTests.first(view, Splitter.class);
        int treeWidth = tree.width();
        UiPoint grip = splitter.toRoot(2, 20);
        root.pointerDown(grip.x(), grip.y(), Keys.LEFT_BUTTON);
        root.pointerDrag(grip.x() + 20, grip.y(), 20, 0, Keys.LEFT_BUTTON);
        root.pointerUp(grip.x() + 20, grip.y(), Keys.LEFT_BUTTON);
        settle(root);
        helper.assertTrue(tree.width() == treeWidth + 20, "dragging the splitter gave the tree width " + tree.width());

        BlueprintNode main = document.root();
        LayoutEdits.wrap(document, main, "panel");
        helper.assertTrue(document.root() != main && document.root().children().getFirst() == main && main.prop("anchor") == null,
                "wrapping the root didn't put it inside a new one");
        root.key(Keys.Z, Keys.CONTROL);
        helper.assertTrue(document.root().describe().equals("panel#main"), "undo didn't unwrap the root");

        root.key(Keys.S, Keys.CONTROL);
        helper.assertTrue(environment.saved.containsKey(ID) && !document.dirty() && view.status().startsWith("Saved to"), "saving gave status " + view.status());
        helper.assertTrue(UiLayouts.get(ID).children().getFirst().id().equals("reset"), "saving didn't apply the layout");

        LayoutEdits.duplicate(document, document.root().find("echo"));
        Button back = button(view, "< Layouts");
        click(root, back, 2, 2);
        helper.assertTrue(view.dialogOpen(), "going back with unsaved changes didn't ask");
        click(root, button(root.popups().getFirst(), "Keep editing"), 2, 2);
        helper.assertTrue(!view.dialogOpen() && view.document() == document, "Keep editing didn't stay on the layout");
        click(root, back, 2, 2);
        click(root, button(root.popups().getFirst(), "Discard"), 2, 2);
        helper.assertTrue(view.document() == null, "Discard didn't go back to the list");

        root.dispose();
        console.power = false;
        console.gain = 0;
        helper.succeed();
    }

    private static void settle(UiRoot root) {
        for (int i = 0; i < 2; i++) {
            root.tick();
            root.layout();
        }
    }

    private static void click(UiRoot root, Widget widget, double x, double y) {
        UiPoint point = widget.toRoot(x, y);
        root.pointerDown(point.x(), point.y(), Keys.LEFT_BUTTON);
        root.pointerUp(point.x(), point.y(), Keys.LEFT_BUTTON);
    }

    private static void hover(UiRoot root, Widget widget) {
        UiPoint point = widget.toRoot(widget.width() / 2.0, widget.height() / 2.0);
        root.pointerMove(point.x(), point.y());
    }

    /**
     * Clicks the middle of a widget in the preview, mapped through the canvas's pan and zoom.
     */
    private static void clickPreview(UiRoot root, PreviewCanvas canvas, Widget target) {
        UiPoint inPreview = target.toRoot(target.width() / 2.0, target.height() / 2.0);
        UiPoint point = canvas.toRoot(canvas.panX() + inPreview.x() * canvas.zoom(), canvas.panY() + inPreview.y() * canvas.zoom());
        root.pointerMove(point.x(), point.y());
        root.pointerDown(point.x(), point.y(), Keys.LEFT_BUTTON);
        root.pointerUp(point.x(), point.y(), Keys.LEFT_BUTTON);
    }

    private static Widget row(TreeView<BlueprintNode> tree, BlueprintNode node) {
        return tree.content().children().get(tree.visibleItems().indexOf(node));
    }

    /**
     * The row holding a property's checkbox, name and editor, scrolled into view.
     */
    private static Widget propertyRow(UiRoot root, ScrollPanel pane, String name) {
        for (Label label : UiGameTests.all(pane, Label.class)) {
            if (label.text().equals(name) && label.parent().children().size() >= 3) {
                reveal(root, pane, label.parent());
                return label.parent();
            }
        }
        throw new IllegalStateException("No property row " + name);
    }

    /**
     * Scrolls a pane so a widget in it sits near the top, as a person would before clicking it.
     */
    private static void reveal(UiRoot root, ScrollPanel pane, Widget widget) {
        double offset = widget.toRoot(0, 0).y() - pane.toRoot(0, 0).y();
        pane.scrollTo((float) (pane.scrollOffset() + offset - 4));
        root.layout();
    }

    private static boolean hasLabel(Widget pane, String start) {
        return UiGameTests.all(pane, Label.class).stream().anyMatch(label -> label.isShown() && label.text().startsWith(start));
    }

    private static Button button(Widget widget, String text) {
        return UiGameTests.all(widget, Button.class).stream().filter(button -> button.text().equals(text)).findFirst().orElseThrow();
    }

    private static boolean inside(Widget widget, Widget container) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current == container) {
                return true;
            }
        }
        return false;
    }

    private static final class TestEnvironment implements EditorEnvironment {

        private final Module<?> module;
        private final Map<String, DataNode> saved = new HashMap<>();

        TestEnvironment(Module<?> module) {
            this.module = module;
        }

        @Override
        public String savePath(String id) {
            return "memory/" + id;
        }

        @Override
        public String save(String id, String description, BlueprintNode root) {
            saved.put(id, UiLayouts.write(description, root));
            return savePath(id);
        }

        @Override
        public UiHost previewHost() {
            return new TestHost();
        }

        @Override
        public List<Module<?>> modules() {
            return List.of(module);
        }
    }
}
