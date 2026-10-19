package xyz.volcanobay.empiric.client.editor.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import foundry.veil.api.client.editor.SingleWindowInspector;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiComboFlags;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import xyz.volcanobay.empiric.api.ui.UiLayout;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.client.ui.UiClient;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.layout.FieldLayout;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.Blueprints;
import xyz.volcanobay.empiric.core.ui.blueprint.PropertyDef;
import xyz.volcanobay.empiric.core.ui.blueprint.Props;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetType;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetTypes;
import xyz.volcanobay.empiric.core.ui.layout.Insets;
import xyz.volcanobay.empiric.core.ui.layout.Length;
import xyz.volcanobay.empiric.data.JsonNodes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Imgui editor window for layout files
 *
 * @deprecated replaced by {@link LayoutEditorView}, the editor built from the UI system itself, opened with
 * {@code /empiric ui editor}. This window still works and will be removed later.
 */
@Deprecated
public final class LayoutEditor extends SingleWindowInspector {

    private static final Component TITLE = Component.literal("Empiric Layouts (deprecated)");
    private static final String DRAG = "empiric_layout_node";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] LENGTHS = {"fit", "fill", "px"};
    private static final int ERROR = 0xFF6666FF;

    private final LayoutPreview preview = new LayoutPreview();
    private final ImString search = new ImString(64);
    private final ImString newId = new ImString("empiric:new_layout", 128);
    private final ImInt previewWidth = new ImInt(320);
    private final ImInt previewHeight = new ImInt(200);
    private final Map<String, ImString> buffers = new HashMap<>();
    private final Map<String, String> bufferErrors = new HashMap<>();
    private LayoutDocument document;
    private BlueprintNode buffersFor;
    private boolean interact;
    private Module<?> source;
    private String status = "";
    private Runnable pending;

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    protected void renderComponents() {
        if (source != null && !source.isAttached()) {
            source = null;
        }
        deprecationBanner();
        if (document == null) {
            browser();
            return;
        }
        if (buffersFor != document.selected()) {
            clearBuffers();
        }
        toolbar();
        if (ImGui.beginTable("##layout_editor", 3, ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV)) {
            ImGui.tableSetupColumn("Tree", ImGuiTableColumnFlags.WidthStretch, 0.25f);
            ImGui.tableSetupColumn("Preview", ImGuiTableColumnFlags.WidthStretch, 0.5f);
            ImGui.tableSetupColumn("Properties", ImGuiTableColumnFlags.WidthStretch, 0.25f);
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            if (ImGui.beginChild("##tree", 0, 0, false)) {
                tree(document.root(), 0);
            }
            ImGui.endChild();
            ImGui.tableNextColumn();
            previewPane();
            ImGui.tableNextColumn();
            if (ImGui.beginChild("##properties", 0, 0, false)) {
                properties(document.selected());
            }
            ImGui.endChild();
            ImGui.endTable();
        }
        shortcuts();
        if (pending != null) {
            Runnable run = pending;
            pending = null;
            run.run();
        }
    }

    @Override
    public void onHide() {
        super.onHide();
        preview.free();
    }

    @Override
    public void free() {
        preview.free();
    }

    private void deprecationBanner() {
        ImGui.textColored(ERROR, "Deprecated: use the new editor (/empiric ui editor)");
        ImGui.sameLine();
        boolean open = ImGui.smallButton("Open new editor");
        tip("Open the editor built from the UI system, on this layout if one is open.\nUnsaved changes here aren't carried over: Apply or Save first.");
        if (open) {
            String id = document == null ? "" : document.id();
            Minecraft.getInstance().execute(() -> UiClient.openEditor(id));
        }
        ImGui.separator();
    }

    // Opening

    private void browser() {
        ImGui.text("Layouts");
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search", search);
        tip("Filter the layouts by id");
        String filter = search.get().toLowerCase(Locale.ROOT);
        if (ImGui.beginChild("##layouts", 0, -ImGui.getFrameHeightWithSpacing() * 2, true)) {
            for (String id : new TreeMap<>(UiLayouts.loaded()).keySet()) {
                if (id.contains(filter) && ImGui.selectable(id)) {
                    open(id);
                }
            }
        }
        ImGui.endChild();
        ImGui.setNextItemWidth(-ImGui.calcTextSize("New layout").x - 24);
        ImGui.inputText("##new", newId);
        tip("Id for a new layout, as namespace:path. It saves to data/<namespace>/ui/layouts/<path>.json");
        ImGui.sameLine();
        boolean create = ImGui.button("New layout");
        tip("Start a new layout with this id, beginning with a centred panel");
        if (create && newId.get().contains(":")) {
            BlueprintNode root = new BlueprintNode("panel");
            root.prop("layout", DataNode.of("column")).prop("gap", DataNode.of(4)).prop("padding", DataNode.of(6)).prop("anchor", DataNode.of("center"));
            document = new LayoutDocument(newId.get(), "", root);
            status = "New layout, not saved";
        }
        if (!status.isEmpty()) {
            ImGui.textDisabled(status);
        }
    }

    private void open(String id) {
        BlueprintNode root = UiLayouts.get(id);
        if (root == null) {
            return;
        }
        UiLayout layout = UiLayouts.LAYOUTS.get(id);
        document = new LayoutDocument(id, layout == null ? "" : layout.description, root);
        preview.invalidate();
        status = "";
    }

    private void toolbar() {
        boolean back = ImGui.button("< Layouts");
        tip("Back to the list of layouts. Asks first if there are unsaved changes.");
        if (back) {
            if (document.dirty()) {
                ImGui.openPopup("Discard changes?");
            } else {
                document = null;
                return;
            }
        }
        if (ImGui.beginPopupModal("Discard changes?")) {
            ImGui.text(document.id() + " has unsaved changes.");
            if (ImGui.button("Discard")) {
                document = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Keep editing")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
            if (document == null) {
                return;
            }
        }
        ImGui.sameLine();
        ImGui.text(document.id() + (document.dirty() ? " *" : ""));
        ImGui.sameLine();
        ImGui.beginDisabled(!document.canUndo());
        if (ImGui.button("Undo")) {
            undo();
        }
        ImGui.endDisabled();
        tip("Undo the last change (Ctrl+Z)");
        ImGui.sameLine();
        ImGui.beginDisabled(!document.canRedo());
        if (ImGui.button("Redo")) {
            redo();
        }
        ImGui.endDisabled();
        tip("Redo what was undone (Ctrl+Y or Ctrl+Shift+Z)");
        ImGui.sameLine();
        if (ImGui.button("Apply")) {
            UiLayouts.override(document.id(), document.root());
            status = "Applied. Screens opened from now on use it.";
        }
        tip("Use this version on this client until the next resource reload, without saving.\nScreens opened from now on show it; open ones keep the old layout.");
        ImGui.sameLine();
        if (ImGui.button("Save")) {
            save();
        }
        tip("Write the layout file and apply it (Ctrl+S). The path is shown below.");
        ImGui.sameLine();
        if (ImGui.button("Revert")) {
            open(document.id());
        }
        tip("Throw away unsaved changes and reload the layout as last applied or loaded");
        ImString description = buffer("description", document.description());
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextWithHint("##description", "Description", description)) {
            document.description(description.get());
        }
        tip("A note saved in the file, for people reading it");
        ImGui.textDisabled(status.isEmpty() ? "Saves to " + LayoutFiles.path(document.id()) : status);
    }

    private void save() {
        try {
            Path path = LayoutFiles.save(document.id(), document.description(), document.root());
            UiLayouts.override(document.id(), document.root());
            document.markSaved();
            status = "Saved to " + path;
        } catch (IOException e) {
            status = "Couldn't save: " + e.getMessage();
        }
    }

    private void undo() {
        document.undo();
        clearBuffers();
    }

    private void redo() {
        document.redo();
        clearBuffers();
    }

    private void shortcuts() {
        if (!ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) || ImGui.isAnyItemActive()) {
            return;
        }
        boolean ctrl = ImGui.getIO().getKeyCtrl();
        boolean shift = ImGui.getIO().getKeyShift();
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.Z)) {
            if (shift) {
                redo();
            } else {
                undo();
            }
        } else if (ctrl && ImGui.isKeyPressed(ImGuiKey.Y)) {
            redo();
        } else if (ctrl && ImGui.isKeyPressed(ImGuiKey.S)) {
            save();
        } else if (ImGui.isKeyPressed(ImGuiKey.Delete) && document.selected() != document.root()) {
            BlueprintNode selected = document.selected();
            pending = () -> delete(selected);
        }
    }

    // Tree

    private void tree(BlueprintNode node, int depth) {
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (node.children().isEmpty()) {
            flags |= ImGuiTreeNodeFlags.Leaf;
        }
        if (node == document.selected()) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        boolean known = WidgetTypes.get(node.type()) != null;
        String label = node.describe() + (known ? "" : " (unknown type)");
        boolean open = ImGui.treeNodeEx("##node" + System.identityHashCode(node), flags, label);
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
            document.select(node);
        }
        if (node != document.root() && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(DRAG, node);
            ImGui.text(node.describe());
            ImGui.endDragDropSource();
        }
        if (ImGui.beginDragDropTarget()) {
            BlueprintNode dropped = ImGui.acceptDragDropPayload(DRAG, BlueprintNode.class);
            if (dropped != null) {
                pending = () -> move(dropped, node);
            }
            ImGui.endDragDropTarget();
        }
        if (ImGui.beginPopupContextItem()) {
            nodeMenu(node);
            ImGui.endPopup();
        }
        if (open) {
            for (BlueprintNode child : List.copyOf(node.children())) {
                tree(child, depth + 1);
            }
            ImGui.treePop();
        }
    }

    private void nodeMenu(BlueprintNode node) {
        BlueprintNode parent = document.parentOf(node);
        WidgetType<?> type = WidgetTypes.get(node.type());
        if (type != null && type.isContainer() && menu("Add child", "Add a new widget inside this one, at the end")) {
            String chosen = typeMenu(false);
            if (chosen != null) {
                pending = () -> insert(node, node.children().size(), chosen);
            }
            ImGui.endMenu();
        }
        if (parent != null && menu("Insert before", "Add a new widget just before this one")) {
            String chosen = typeMenu(false);
            if (chosen != null) {
                pending = () -> insert(parent, parent.children().indexOf(node), chosen);
            }
            ImGui.endMenu();
        }
        if (parent != null && menu("Insert after", "Add a new widget just after this one")) {
            String chosen = typeMenu(false);
            if (chosen != null) {
                pending = () -> insert(parent, parent.children().indexOf(node) + 1, chosen);
            }
            ImGui.endMenu();
        }
        if (menu("Wrap in", "Put this widget inside a new container, in its place")) {
            String chosen = typeMenu(true);
            if (chosen != null) {
                pending = () -> wrap(node, chosen);
            }
            ImGui.endMenu();
        }
        ImGui.separator();
        if (parent != null) {
            int index = parent.children().indexOf(node);
            if (item(ImGui.menuItem("Move up", "", false, index > 0), "Move before the previous sibling")) {
                pending = () -> document.edit(null, () -> {
                    parent.children().remove(index);
                    parent.children().add(index - 1, node);
                });
            }
            if (item(ImGui.menuItem("Move down", "", false, index < parent.children().size() - 1), "Move after the next sibling")) {
                pending = () -> document.edit(null, () -> {
                    parent.children().remove(index);
                    parent.children().add(index + 1, node);
                });
            }
            if (item(ImGui.menuItem("Duplicate"), "Copy this widget and everything in it, just after it")) {
                pending = () -> document.edit(null, () -> {
                    BlueprintNode copy = node.copy();
                    parent.children().add(index + 1, copy);
                    document.select(copy);
                });
            }
            if (item(ImGui.menuItem("Delete", "Del"), "Remove this widget and everything in it")) {
                pending = () -> delete(node);
            }
        }
    }

    /**
     * Lists the widget types by category, and returns the one clicked.
     */
    /**
     * A submenu with a tooltip while it's closed.
     */
    private static boolean menu(String label, String help) {
        boolean open = ImGui.beginMenu(label);
        if (!open) {
            tip(help);
        }
        return open;
    }

    /**
     * Passes a menu item's result through, adding its tooltip.
     */
    private static boolean item(boolean clicked, String help) {
        tip(help);
        return clicked;
    }

    private static void tip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }

    private static String typeMenu(boolean containersOnly) {
        Map<String, List<WidgetType<?>>> categories = new LinkedHashMap<>();
        for (WidgetType<?> type : WidgetTypes.all()) {
            if (!containersOnly || type.isContainer()) {
                categories.computeIfAbsent(type.category(), key -> new ArrayList<>()).add(type);
            }
        }
        String chosen = null;
        for (Map.Entry<String, List<WidgetType<?>>> category : categories.entrySet()) {
            if (ImGui.beginMenu(category.getKey())) {
                for (WidgetType<?> type : category.getValue()) {
                    if (item(ImGui.menuItem(type.id()), type.isContainer() ? "A " + type.id() + ", which can hold other widgets" : "A " + type.id())) {
                        chosen = type.id();
                    }
                }
                ImGui.endMenu();
            }
        }
        return chosen;
    }

    private void insert(BlueprintNode parent, int index, String type) {
        document.edit(null, () -> {
            BlueprintNode created = new BlueprintNode(type);
            parent.children().add(Math.max(0, Math.min(index, parent.children().size())), created);
            document.select(created);
        });
    }

    private void wrap(BlueprintNode node, String type) {
        document.edit(null, () -> {
            BlueprintNode wrapper = new BlueprintNode(type);
            BlueprintNode parent = document.parentOf(node);
            if (parent == null) {
                wrapper.prop("anchor", node.prop("anchor"));
                node.prop("anchor", null);
                document.replaceRoot(wrapper);
            } else {
                parent.children().set(parent.children().indexOf(node), wrapper);
            }
            wrapper.add(node);
            document.select(wrapper);
        });
    }

    private void delete(BlueprintNode node) {
        BlueprintNode parent = document.parentOf(node);
        if (parent == null) {
            return;
        }
        document.edit(null, () -> {
            parent.children().remove(node);
            document.select(parent);
        });
    }

    /**
     * Drops a node onto another: into it as the last child if it holds children, otherwise just before it.
     */
    private void move(BlueprintNode moved, BlueprintNode onto) {
        if (moved == onto || LayoutDocument.contains(moved, onto) || moved == document.root()) {
            return;
        }
        WidgetType<?> type = WidgetTypes.get(onto.type());
        BlueprintNode target = type != null && type.isContainer() ? onto : document.parentOf(onto);
        if (target == null) {
            return;
        }
        document.edit(null, () -> {
            document.parentOf(moved).children().remove(moved);
            if (target == onto) {
                onto.children().add(moved);
            } else {
                target.children().add(target.children().indexOf(onto), moved);
            }
        });
    }

    // Preview

    private void previewPane() {
        ImGui.setNextItemWidth(60);
        ImGui.inputInt("##width", previewWidth, 0);
        tip("Preview width in widget pixels: the size of the screen the layout is shown in");
        ImGui.sameLine();
        ImGui.text("x");
        ImGui.sameLine();
        ImGui.setNextItemWidth(60);
        ImGui.inputInt("##height", previewHeight, 0);
        tip("Preview height in widget pixels");
        ImGui.sameLine();
        if (ImGui.button("Fit")) {
            preview.fit();
        }
        tip("Zoom so the whole preview fits the canvas");
        ImGui.sameLine();
        if (ImGui.button("100%")) {
            preview.actualSize();
        }
        tip("One screen pixel per widget pixel");
        ImGui.sameLine();
        if (ImGui.checkbox("Interact", interact)) {
            interact = !interact;
        }
        tip("Off: clicking the preview selects widgets. On: the mouse uses the widgets, so you can drag sliders and press buttons.\n"
                + "Pan with the middle mouse button (or the right button when off). Zoom with the wheel (Ctrl and the wheel when on).");
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        sourceCombo();
        tip("The module bindings read from and write to. Pick one to see real values; without one, bindings are listed as problems.");
        previewWidth.set(Math.max(16, Math.min(1024, previewWidth.get())));
        previewHeight.set(Math.max(16, Math.min(1024, previewHeight.get())));

        List<String> problems = preview.problems();
        float problemsHeight = problems.isEmpty() ? 0 : Math.min(5, problems.size()) * ImGui.getTextLineHeightWithSpacing() + 8;
        int canvasFlags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        if (ImGui.beginChild("##preview", 0, -problemsHeight, false, canvasFlags)) {
            preview.update(document, source, previewWidth.get(), previewHeight.get());
            BlueprintNode clicked = preview.render(document, interact);
            if (clicked != null) {
                document.select(clicked);
            }
        }
        ImGui.endChild();
        if (!problems.isEmpty() && ImGui.beginChild("##problems", 0, 0, true)) {
            for (String problem : problems) {
                ImGui.textColored(ERROR, problem);
            }
        }
        if (!problems.isEmpty()) {
            ImGui.endChild();
        }
    }

    /**
     * Picks the module bindings resolve against: any module on this client that some screen is registered for.
     */
    private void sourceCombo() {
        String label = source == null ? "No module (bindings show as problems)" : source.type().id().toString();
        if (!ImGui.beginCombo("##source", label)) {
            return;
        }
        if (ImGui.selectable("No module", source == null)) {
            source = null;
        }
        Set<Object> seen = new HashSet<>();
        for (UiScreenType<?> screen : UiScreens.all()) {
            if (!seen.add(screen.moduleType())) {
                continue;
            }
            int[] index = {0};
            EmpiricCore.modules().forEach(Side.CLIENT, screen.moduleType(), module -> {
                String name = module.type().id() + " #" + index[0]++ + "##" + System.identityHashCode(module);
                if (ImGui.selectable(name, module == source)) {
                    source = module;
                }
            });
        }
        ImGui.endCombo();
    }

    // Properties

    private void properties(BlueprintNode node) {
        if (node == null) {
            return;
        }
        String[] types = WidgetTypes.all().stream().map(WidgetType::id).toArray(String[]::new);
        ImInt typeIndex = new ImInt(List.of(types).indexOf(node.type()));
        ImGui.setNextItemWidth(-1);
        boolean retyped = ImGui.combo("##type", typeIndex, types);
        tip("Widget type. Changing it keeps the properties; ones the new type doesn't use show under Unknown.");
        if (retyped && typeIndex.get() >= 0) {
            String chosen = types[typeIndex.get()];
            document.edit(null, () -> node.type(chosen));
        }
        ImString id = buffer("id", node.id());
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextWithHint("##id", "id", id)) {
            document.edit("id", () -> node.id(id.get().trim()));
        }
        tip("Widget id. Java code finds widgets by id with @Ref, @OnClick and @ContextAction.");
        WidgetType<?> type = WidgetTypes.get(node.type());
        BlueprintNode parent = document.parentOf(node);
        WidgetType<?> parentType = parent == null ? null : WidgetTypes.get(parent.type());
        Set<String> shown = new HashSet<>();
        if (type != null && !type.properties().isEmpty() && ImGui.collapsingHeader(type.id(), ImGuiTreeNodeFlags.DefaultOpen)) {
            rows(node, type.properties(), shown);
        } else if (type != null) {
            type.properties().forEach(def -> shown.add(def.name()));
        }
        if (parentType != null && !parentType.childProperties().isEmpty() && ImGui.collapsingHeader("In " + parentType.id(), ImGuiTreeNodeFlags.DefaultOpen)) {
            rows(node, parentType.childProperties(), shown);
        } else if (parentType != null) {
            parentType.childProperties().forEach(def -> shown.add(def.name()));
        }
        List<PropertyDef> common = new ArrayList<>(Blueprints.COMMON);
        if (parent != null) {
            common.removeIf(def -> def.name().equals("theme"));
        }
        if (ImGui.collapsingHeader("Layout and common", ImGuiTreeNodeFlags.DefaultOpen)) {
            rows(node, common, shown);
        } else {
            common.forEach(def -> shown.add(def.name()));
        }
        List<String> unknown = node.props().keySet().stream().filter(key -> !shown.contains(key)).toList();
        if (!unknown.isEmpty() && ImGui.collapsingHeader("Unknown", ImGuiTreeNodeFlags.DefaultOpen)) {
            for (String key : unknown) {
                ImGui.pushID(key);
                if (ImGui.smallButton("x")) {
                    document.edit(null, () -> node.prop(key, null));
                }
                tip("Remove this property, which this widget type doesn't use");
                ImGui.sameLine();
                ImGui.textDisabled(key + " = " + node.prop(key));
                ImGui.popID();
            }
        }
    }

    private void rows(BlueprintNode node, List<PropertyDef> defs, Set<String> shown) {
        if (!ImGui.beginTable("##rows" + defs.hashCode(), 2, ImGuiTableFlags.SizingStretchProp)) {
            return;
        }
        ImGui.tableSetupColumn("name", ImGuiTableColumnFlags.WidthFixed, 100);
        ImGui.tableSetupColumn("value", ImGuiTableColumnFlags.WidthStretch);
        for (PropertyDef def : defs) {
            shown.add(def.name());
            row(node, def);
        }
        ImGui.endTable();
    }

    private void row(BlueprintNode node, PropertyDef def) {
        String name = def.name();
        DataNode current = node.prop(name);
        boolean set = current != null && !current.isNull();
        ImGui.pushID(name);
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        boolean toggled = ImGui.checkbox("##set", set);
        tip(set ? "Set in the file. Untick to remove it and use the default." : "Not set, so the default shown is used. Tick to set it.");
        if (toggled) {
            DataNode initial = set ? null : initial(def);
            document.edit(null, () -> node.prop(name, initial));
            buffers.remove(name);
        }
        ImGui.sameLine();
        ImGui.text(name);
        if (ImGui.isItemHovered() && !def.help().isEmpty()) {
            ImGui.setTooltip(def.help());
        }
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-1);
        ImGui.beginDisabled(!set);
        DataNode value = set ? current : def.fallback();
        DataNode changed;
        try {
            changed = edit(def, value);
        } catch (RuntimeException e) {
            ImGui.textColored(ERROR, e.getMessage());
            changed = null;
        }
        ImGui.endDisabled();
        if (changed != null && set) {
            DataNode result = changed;
            document.edit(name, () -> node.prop(name, result));
        }
        ImGui.popID();
    }

    private static DataNode initial(PropertyDef def) {
        if (!def.fallback().isNull()) {
            return def.fallback().copy();
        }
        return switch (def.kind()) {
            case INT, NUMBER -> DataNode.of(0);
            case BOOL -> DataNode.of(false);
            case STRINGS, ACTIONS -> DataNode.array();
            default -> DataNode.of("");
        };
    }

    /**
     * Shows an editor for one value and returns the new value if it changed this frame.
     */
    private DataNode edit(PropertyDef def, DataNode value) {
        String name = def.name();
        return switch (def.kind()) {
            case INT -> {
                ImInt number = new ImInt(value.isNull() ? 0 : value.asNumber().intValue());
                yield ImGui.inputInt("##v", number) ? DataNode.of(number.get()) : null;
            }
            case NUMBER -> {
                ImDouble number = new ImDouble(value.isNull() ? 0 : value.asNumber().doubleValue());
                yield ImGui.inputDouble("##v", number, 0.1, 1, "%.3f") ? DataNode.of(number.get()) : null;
            }
            case BOOL -> ImGui.checkbox("##v", !value.isNull() && value.asBoolean()) ? DataNode.of(value.isNull() || !value.asBoolean()) : null;
            case CHOICE -> {
                String[] options = def.options().toArray(String[]::new);
                ImInt index = new ImInt(Math.max(0, def.options().indexOf(value.isNull() ? "" : value.asString())));
                yield ImGui.combo("##v", index, options) ? DataNode.of(options[index.get()]) : null;
            }
            case COLOR -> color(value);
            case LENGTH -> length(value);
            case INSETS -> insets(value);
            case FIELD -> field(name, value);
            case STRINGS -> strings(name, value);
            case ACTIONS -> actions(name, value);
            default -> {
                ImString text = buffer(name, value.isNull() ? "" : value.asString());
                yield ImGui.inputText("##v", text) ? DataNode.of(text.get()) : null;
            }
        };
    }

    private static DataNode color(DataNode value) {
        int argb = value.isNull() ? 0xFFFFFFFF : Props.parseColor(value);
        float[] rgba = {(argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f};
        if (!ImGui.colorEdit4("##v", rgba, ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf)) {
            return null;
        }
        int edited = Math.round(rgba[3] * 255) << 24 | Math.round(rgba[0] * 255) << 16 | Math.round(rgba[1] * 255) << 8 | Math.round(rgba[2] * 255);
        return DataNode.of(String.format("#%08X", edited));
    }

    private static DataNode length(DataNode value) {
        Length length = value.isNull() ? Length.FIT : Props.parseLength(value);
        ImInt kind = new ImInt(switch (length.kind()) {
            case FIT -> 0;
            case FILL -> 1;
            default -> 2;
        });
        ImGui.setNextItemWidth(60);
        if (ImGui.combo("##kind", kind, LENGTHS)) {
            return switch (kind.get()) {
                case 0 -> DataNode.of("fit");
                case 1 -> DataNode.of("fill");
                default -> DataNode.of(40);
            };
        }
        if (kind.get() == 0) {
            return null;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        if (kind.get() == 1) {
            ImDouble weight = new ImDouble(length.value());
            return ImGui.inputDouble("##weight", weight, 0.5, 1, "%.1f") ? Props.writeLength(Length.fill((float) Math.max(0.1, weight.get()))) : null;
        }
        ImInt pixels = new ImInt((int) length.value());
        return ImGui.inputInt("##pixels", pixels) ? DataNode.of(Math.max(0, pixels.get())) : null;
    }

    private static DataNode insets(DataNode value) {
        Insets insets = value.isNull() ? Insets.NONE : Props.parseInsets(value);
        int[] sides = {insets.left(), insets.top(), insets.right(), insets.bottom()};
        if (!ImGui.inputInt4("##v", sides)) {
            return null;
        }
        if (sides[0] == sides[1] && sides[1] == sides[2] && sides[2] == sides[3]) {
            return DataNode.of(sides[0]);
        }
        if (sides[0] == sides[2] && sides[1] == sides[3]) {
            return DataNode.array(List.of(DataNode.of(sides[0]), DataNode.of(sides[1])));
        }
        return DataNode.array(List.of(DataNode.of(sides[0]), DataNode.of(sides[1]), DataNode.of(sides[2]), DataNode.of(sides[3])));
    }

    /**
     * A field id, with the preview module's synced fields to pick from.
     */
    private DataNode field(String name, DataNode value) {
        String current = value.isNull() ? "" : value.asString();
        ImString text = buffer(name, current);
        if (source == null) {
            return ImGui.inputText("##v", text) ? DataNode.of(text.get()) : null;
        }
        ImGui.setNextItemWidth(-28);
        DataNode changed = ImGui.inputText("##v", text) ? DataNode.of(text.get()) : null;
        ImGui.sameLine();
        boolean picking = ImGui.beginCombo("##fields", "", ImGuiComboFlags.NoPreview | ImGuiComboFlags.PopupAlignLeft);
        tip("Pick a synced field of the preview module");
        if (picking) {
            for (FieldLayout field : source.type().spec().layout().fields()) {
                if (field.sync() && ImGui.selectable(field.id() + " (" + field.type().getSimpleName() + ")", field.id().equals(current))) {
                    changed = DataNode.of(field.id());
                    buffers.remove(name);
                }
            }
            ImGui.endCombo();
        }
        return changed;
    }

    private DataNode strings(String name, DataNode value) {
        StringBuilder lines = new StringBuilder();
        for (DataNode element : value.isNull() ? List.<DataNode>of() : value.elementsOrSelf()) {
            lines.append(element.asString()).append('\n');
        }
        ImString text = buffer(name, lines.toString());
        if (!ImGui.inputTextMultiline("##v", text, -1, ImGui.getTextLineHeight() * 5)) {
            return null;
        }
        DataNode list = DataNode.array();
        for (String line : text.get().split("\n")) {
            if (!line.isBlank()) {
                list.add(DataNode.of(line.trim()));
            }
        }
        return list;
    }

    /**
     * Actions as JSON, applied whenever the text parses, with a menu that appends a template for each action.
     */
    private DataNode actions(String name, DataNode value) {
        String json = GSON.toJson(JsonNodes.toJson(value.isNull() ? DataNode.array() : value));
        ImString text = buffer(name, json);
        DataNode changed = null;
        if (ImGui.inputTextMultiline("##v", text, -1, ImGui.getTextLineHeight() * 8)) {
            try {
                DataNode parsed = JsonNodes.toNode(JsonParser.parseString(text.get()));
                if (!parsed.isArray()) {
                    throw new JsonParseException("must be a list");
                }
                changed = parsed;
                bufferErrors.remove(name);
            } catch (RuntimeException e) {
                bufferErrors.put(name, "Not valid yet: " + e.getMessage());
            }
        }
        if (bufferErrors.containsKey(name)) {
            ImGui.textColored(ERROR, bufferErrors.get(name));
        }
        boolean adding = ImGui.beginCombo("##add_action", "Add action");
        tip("Append an action with its settings filled in, then edit the JSON above");
        if (adding) {
            for (String action : Blueprints.actions()) {
                if (ImGui.selectable(action)) {
                    DataNode list = value.isNull() ? DataNode.array() : value.copy();
                    DataNode entry = DataNode.object().put("action", DataNode.of(action));
                    switch (action) {
                        case "set" -> entry.put("field", DataNode.of("")).put("value", DataNode.of(0));
                        case "toggle" -> entry.put("field", DataNode.of(""));
                        case "add" -> entry.put("field", DataNode.of("")).put("amount", DataNode.of(1));
                        case "sound" -> entry.put("key", DataNode.of("click"));
                        default -> {
                        }
                    }
                    list.add(entry);
                    changed = list;
                    buffers.remove(name);
                }
            }
            ImGui.endCombo();
        }
        return changed;
    }

    /**
     * Text being typed is kept between frames, so a half-typed value isn't replaced by what's stored.
     */
    private ImString buffer(String key, String value) {
        ImString buffer = buffers.get(key);
        if (buffer == null) {
            buffer = new ImString(value, Math.max(256, value.length() * 2));
            buffers.put(key, buffer);
        } else if (!ImGui.isAnyItemActive() && !buffer.get().equals(value) && !bufferErrors.containsKey(key)) {
            buffer.set(value);
        }
        return buffer;
    }

    private void clearBuffers() {
        buffers.clear();
        bufferErrors.clear();
        buffersFor = document == null ? null : document.selected();
    }
}
