package xyz.volcanobay.empiric.client.editor.layout;

import xyz.volcanobay.empiric.api.ui.UiLayout;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.ContextEntry;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetType;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetTypes;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.FlexLayout;
import xyz.volcanobay.empiric.core.ui.layout.Insets;
import xyz.volcanobay.empiric.core.ui.layout.Justify;
import xyz.volcanobay.empiric.core.ui.layout.Length;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.Panel;
import xyz.volcanobay.empiric.core.ui.widget.ScrollPanel;
import xyz.volcanobay.empiric.core.ui.widget.Splitter;
import xyz.volcanobay.empiric.core.ui.widget.TextField;
import xyz.volcanobay.empiric.core.ui.widget.TreeView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The layout editor, built from widgets.
 * Save writes the file. Apply updates the game to use the new changes.
 * <p>
 * Shortcuts: control Z undoes, control Y or control shift Z redoes, control S saves, and Delete removes the selected
 * widget while the tree or the preview has focus.
 */
public final class LayoutEditorView extends Panel {

    private final EditorEnvironment environment;
    private final Widget page = new Widget().fill();
    private LayoutDocument document;
    private Module<?> module;
    private String status = "";
    private TreeView<BlueprintNode> tree;
    private LayoutPreviewPane previewPane;
    private LayoutPropertiesPane propertiesPane;
    private Widget dialog;

    public LayoutEditorView(EditorEnvironment environment) {
        this.environment = environment;
        style("panel", "panel_border");
        fill();
        padding(Insets.of(4));
        layout(FlexLayout.column(0).align(Align.STRETCH));
        add(page);
        page.layout(FlexLayout.column(3).align(Align.STRETCH));
        showBrowser();
    }

    public EditorEnvironment environment() {
        return environment;
    }

    public LayoutDocument document() {
        return document;
    }

    public Module<?> module() {
        return module;
    }

    public void module(Module<?> module) {
        this.module = module;
    }

    public String status() {
        return status;
    }

    public TreeView<BlueprintNode> tree() {
        return tree;
    }

    public PreviewCanvas previewCanvas() {
        return previewPane == null ? null : previewPane.canvas();
    }

    public ScrollPanel propertiesPane() {
        return propertiesPane;
    }

    // Opening

    private void showBrowser() {
        page.clear();
        tree = null;
        previewPane = null;
        propertiesPane = null;
        page.add(new Label("Layouts"));
        TextField search = new TextField().placeholder("Search").width(Length.FILL);
        search.hint("Filter the layouts by id");
        page.add(search);
        ScrollPanel list = new ScrollPanel().fill();
        list.content().layout(FlexLayout.column(0).align(Align.STRETCH));
        page.add(list);
        Runnable refill = () -> {
            list.content().clear();
            String filter = search.text().toLowerCase(Locale.ROOT);
            for (String id : new TreeMap<>(UiLayouts.loaded()).keySet()) {
                if (id.contains(filter)) {
                    list.add(new ListRow(id, () -> open(id)));
                }
            }
        };
        search.onChange(text -> refill.run());
        refill.run();
        Widget create = new Widget().layout(FlexLayout.row(3).align(Align.CENTER));
        TextField newId = new TextField("empiric:new_layout").maxLength(128).width(Length.FILL);
        newId.hint("Id for a new layout, as namespace:path. It saves to data/<namespace>/ui/layouts/<path>.json");
        Button make = new Button("New layout", () -> create(newId.text()));
        make.hint("Start a new layout with this id, beginning with a centred panel");
        create.add(newId);
        create.add(make);
        page.add(create);
        Label statusLabel = new Label(status).textColor("text_dim");
        statusLabel.watch(() -> status, statusLabel::setValue);
        page.add(statusLabel);
    }

    /**
     * Opens a loaded layout. Returns false if there's none with that id.
     */
    public boolean open(String id) {
        BlueprintNode root = UiLayouts.get(id);
        if (root == null) {
            return false;
        }
        UiLayout layout = UiLayouts.LAYOUTS.get(id);
        status = "";
        show(new LayoutDocument(id, layout == null ? "" : layout.description, root));
        return true;
    }

    /**
     * Starts a new, unsaved layout.
     */
    public void create(String id) {
        if (!id.contains(":")) {
            status = "A layout id needs a namespace, like empiric:" + id;
            return;
        }
        status = "New layout, not saved";
        show(new LayoutDocument(id, "", LayoutEdits.starter()));
    }

    private void show(LayoutDocument document) {
        this.document = document;
        page.clear();
        page.add(toolbar());
        Widget body = new Widget().fill().layout(FlexLayout.row(0).align(Align.STRETCH));
        tree = treePane();
        tree.width(Length.px(130));
        previewPane = new LayoutPreviewPane(this);
        previewPane.fill();
        propertiesPane = new LayoutPropertiesPane(this);
        propertiesPane.width(Length.px(190));
        body.add(tree);
        body.add(new Splitter(tree, true).limits(60, 600));
        body.add(previewPane);
        body.add(new Splitter(propertiesPane, false).limits(120, 600));
        body.add(propertiesPane);
        page.add(body);
        tree.select(document.selected());
    }

    /**
     * Goes back to the list, asking first if there are unsaved changes.
     */
    public void back() {
        confirmDiscard(() -> {
            document = null;
            showBrowser();
        });
    }

    /**
     * Runs {@code then} straight away if nothing is unsaved, otherwise after asking.
     */
    public void confirmDiscard(Runnable then) {
        if (document == null || !document.dirty()) {
            then.run();
            return;
        }
        closeDialog();
        Panel box = new Panel().style("panel", "focus");
        box.padding(Insets.of(6));
        box.layout(FlexLayout.column(4).align(Align.STRETCH));
        box.add(new Label(document.id() + " has unsaved changes."));
        Widget buttons = new Widget().layout(FlexLayout.row(4).justify(Justify.END));
        Button discard = new Button("Discard", () -> {
            closeDialog();
            then.run();
        });
        discard.hint("Throw the changes away");
        Button keep = new Button("Keep editing", this::closeDialog);
        keep.hint("Go back to the layout");
        buttons.add(discard);
        buttons.add(keep);
        box.add(buttons);
        dialog = box;
        root().openPopup(box, (root().width() - box.measuredWidth()) / 2.0, (root().height() - box.measuredHeight()) / 2.0);
        root().focus(keep);
    }

    private void closeDialog() {
        if (dialog != null && dialog.root() != null) {
            dialog.root().closePopup(dialog);
        }
        dialog = null;
    }

    public boolean dialogOpen() {
        return dialog != null && dialog.root() != null;
    }

    // Toolbar

    private Widget toolbar() {
        Widget bar = new Widget().layout(FlexLayout.column(2).align(Align.STRETCH));
        Widget row = new Widget().layout(FlexLayout.row(3).align(Align.CENTER));
        Button back = new Button("< Layouts", this::back);
        back.hint("Back to the list of layouts. Asks first if there are unsaved changes.");
        row.add(back);
        Label title = new TitleLabel(document.id()).width(Length.FILL);
        title.watch(() -> document == null ? "" : document.id() + (document.dirty() ? " *" : ""), title::setValue);
        title.hint("The layout being edited. A star means there are unsaved changes.");
        row.add(title);
        Button undo = new Button("Undo", this::undo);
        undo.hint("Undo the last change (Ctrl+Z)");
        undo.watch(() -> document != null && document.canUndo(), undo::enabled);
        undo.enabled(false);
        Button redo = new Button("Redo", this::redo);
        redo.hint("Redo what was undone (Ctrl+Y or Ctrl+Shift+Z)");
        redo.watch(() -> document != null && document.canRedo(), redo::enabled);
        redo.enabled(false);
        Button apply = new Button("Apply", this::apply);
        apply.hint("Use this version on this client until the next resource reload, without saving.\nScreens opened from now on show it; open ones keep the old layout.");
        Button save = new Button("Save", this::save);
        save.hint("Write the layout file and apply it (Ctrl+S). The path is shown below.");
        Button revert = new Button("Revert", this::revert);
        revert.hint("Throw away unsaved changes and reload the layout as last applied or loaded");
        row.add(undo);
        row.add(redo);
        row.add(apply);
        row.add(save);
        row.add(revert);
        bar.add(row);
        TextField description = new TextField().placeholder("Description").width(Length.FILL);
        description.bind(new Property<>() {
            @Override
            public String get() {
                return document == null ? "" : document.description();
            }

            @Override
            public void set(String value) {
                if (document != null) {
                    document.description(value);
                }
            }
        });
        description.hint("A note saved in the file, for people reading it");
        bar.add(description);
        Label statusLabel = new Label("").textColor("text_dim");
        statusLabel.watch(() -> document == null ? status : status.isEmpty() ? "Saves to " + environment.savePath(document.id()) : status, statusLabel::setValue);
        bar.add(statusLabel);
        return bar;
    }

    public void undo() {
        if (document != null) {
            document.undo();
        }
    }

    public void redo() {
        if (document != null) {
            document.redo();
        }
    }

    public void apply() {
        if (document != null) {
            UiLayouts.override(document.id(), document.root());
            status = "Applied. Screens opened from now on use it.";
        }
    }

    public void save() {
        if (document == null) {
            return;
        }
        try {
            String path = environment.save(document.id(), document.description(), document.root());
            UiLayouts.override(document.id(), document.root());
            document.markSaved();
            status = "Saved to " + path;
        } catch (IOException e) {
            status = "Couldn't save: " + e.getMessage();
        }
    }

    public void revert() {
        if (document != null && !open(document.id())) {
            status = "Nothing to revert to: it has never been applied or saved";
        }
    }

    // Tree

    private TreeView<BlueprintNode> treePane() {
        TreeView<BlueprintNode> view = new TreeView<>(() -> document == null ? null : document.root(), BlueprintNode::children);
        view.label(node -> node.describe() + (WidgetTypes.get(node.type()) == null ? " (unknown type)" : ""));
        view.labelColor(node -> WidgetTypes.get(node.type()) == null ? "error" : "text");
        view.menu(this::nodeMenu);
        view.onDrop(node -> document != null && node != document.root(), (moved, onto) -> LayoutEdits.canMove(document, moved, onto),
                (moved, onto) -> LayoutEdits.move(document, moved, onto));
        view.onSelect(node -> {
            if (document != null) {
                document.select(node);
            }
        });
        view.hints(node -> "Drag onto another widget to move it: into a container, or just before anything else. Right click for more.");
        return view;
    }

    private List<ContextEntry> nodeMenu(BlueprintNode node) {
        if (document == null) {
            return List.of();
        }
        LayoutDocument document = this.document;
        BlueprintNode parent = document.parentOf(node);
        List<ContextEntry> entries = new ArrayList<>();
        if (LayoutEdits.isContainer(node)) {
            entries.add(ContextEntry.submenu("Add child", "Add a new widget inside this one, at the end",
                    () -> typeMenu(false, type -> LayoutEdits.insert(document, node, node.children().size(), type))));
        }
        if (parent != null) {
            entries.add(ContextEntry.submenu("Insert before", "Add a new widget just before this one",
                    () -> typeMenu(false, type -> LayoutEdits.insert(document, parent, parent.children().indexOf(node), type))));
            entries.add(ContextEntry.submenu("Insert after", "Add a new widget just after this one",
                    () -> typeMenu(false, type -> LayoutEdits.insert(document, parent, parent.children().indexOf(node) + 1, type))));
        }
        entries.add(ContextEntry.submenu("Wrap in", "Put this widget inside a new container, in its place",
                () -> typeMenu(true, type -> LayoutEdits.wrap(document, node, type))));
        if (parent != null) {
            int index = parent.children().indexOf(node);
            entries.add(ContextEntry.SEPARATOR);
            entries.add(ContextEntry.of("Move up", "Move before the previous sibling", () -> LayoutEdits.shift(document, node, -1))
                    .enabledWhen(() -> index > 0));
            entries.add(ContextEntry.of("Move down", "Move after the next sibling", () -> LayoutEdits.shift(document, node, 1))
                    .enabledWhen(() -> index < parent.children().size() - 1));
            entries.add(ContextEntry.of("Duplicate", "Copy this widget and everything in it, just after it", () -> LayoutEdits.duplicate(document, node)));
            entries.add(ContextEntry.of("Delete", "Remove this widget and everything in it (Del)", () -> LayoutEdits.delete(document, node)));
        }
        return entries;
    }

    /**
     * The widget types by category, each choosing a type when clicked.
     */
    private static List<ContextEntry> typeMenu(boolean containersOnly, Consumer<String> chosen) {
        Map<String, List<WidgetType<?>>> categories = new LinkedHashMap<>();
        for (WidgetType<?> type : WidgetTypes.all()) {
            if (!containersOnly || type.isContainer()) {
                categories.computeIfAbsent(type.category(), key -> new ArrayList<>()).add(type);
            }
        }
        List<ContextEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<WidgetType<?>>> category : categories.entrySet()) {
            List<WidgetType<?>> types = category.getValue();
            entries.add(ContextEntry.submenu(category.getKey(), null, () -> {
                List<ContextEntry> items = new ArrayList<>();
                for (WidgetType<?> type : types) {
                    String hint = type.isContainer() ? "A " + type.id() + ", which can hold other widgets" : "A " + type.id();
                    items.add(ContextEntry.of(type.id(), hint, () -> chosen.accept(type.id())));
                }
                return items;
            }));
        }
        return entries;
    }

    // Input

    /**
     * Handles the editor's shortcuts, whatever has focus. Returns whether the key was used.
     */
    public boolean shortcut(int key, int modifiers) {
        if (document == null || dialogOpen()) {
            return false;
        }
        boolean control = Keys.control(modifiers);
        boolean shift = Keys.shift(modifiers);
        if (control && key == Keys.Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (control && key == Keys.Y) {
            redo();
            return true;
        }
        if (control && key == Keys.S) {
            save();
            return true;
        }
        if (key == Keys.DELETE && document.selected() != document.root() && deleteAllowed()) {
            LayoutEdits.delete(document, document.selected());
            return true;
        }
        return false;
    }

    /**
     * Delete only removes widgets while focus isn't in something that edits text or values.
     */
    private boolean deleteAllowed() {
        Widget focused = root() == null ? null : root().focused();
        if (focused == null) {
            return true;
        }
        PreviewCanvas canvas = previewCanvas();
        if (canvas != null && focused == canvas) {
            return !canvas.interact();
        }
        return tree != null && inside(focused, tree);
    }

    private static boolean inside(Widget widget, Widget container) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current == container) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean key(int key, int modifiers) {
        return shortcut(key, modifiers);
    }

    @Override
    protected void tick() {
        if (module != null && !module.isAttached()) {
            module = null;
        }
        if (document != null && tree != null && tree.selected() != document.selected()) {
            tree.select(document.selected());
        }
    }

    /**
     * A clickable line in the list of layouts.
     */
    private static final class ListRow extends Widget {

        private final String text;
        private final Runnable action;

        ListRow(String text, Runnable action) {
            this.text = text;
            this.action = action;
            hint("Open " + text);
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
            sound("click");
            action.run();
            return true;
        }

        @Override
        protected int contentWidth() {
            return root() == null ? 0 : font().width(text) + 8;
        }

        @Override
        protected int contentHeight() {
            return root() == null ? 0 : font().lineHeight() + 4;
        }

        @Override
        protected void draw(UiCanvas canvas, float partialTick) {
            if (isHovered()) {
                canvas.fill(0, 0, width(), height(), color("widget_hover"));
            }
            canvas.text(text, 4, (height() - font().lineHeight()) / 2 + 1, color("text"), false);
        }
    }

    /**
     * A label that shows its hint when the pointer rests on it.
     */
    private static final class TitleLabel extends Label {

        TitleLabel(String text) {
            super(text);
        }

        @Override
        protected boolean interactive() {
            return true;
        }
    }
}
