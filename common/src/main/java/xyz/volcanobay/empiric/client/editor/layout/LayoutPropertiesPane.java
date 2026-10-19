package xyz.volcanobay.empiric.client.editor.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import xyz.volcanobay.empiric.core.data.layout.FieldLayout;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.ContextEntry;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.Blueprints;
import xyz.volcanobay.empiric.core.ui.blueprint.PropertyDef;
import xyz.volcanobay.empiric.core.ui.blueprint.Props;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetType;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetTypes;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.FlexLayout;
import xyz.volcanobay.empiric.core.ui.layout.Insets;
import xyz.volcanobay.empiric.core.ui.layout.Length;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.Checkbox;
import xyz.volcanobay.empiric.core.ui.widget.ColorPicker;
import xyz.volcanobay.empiric.core.ui.widget.ContextMenu;
import xyz.volcanobay.empiric.core.ui.widget.Dropdown;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.NumberField;
import xyz.volcanobay.empiric.core.ui.widget.ScrollPanel;
import xyz.volcanobay.empiric.core.ui.widget.Section;
import xyz.volcanobay.empiric.core.ui.widget.TextArea;
import xyz.volcanobay.empiric.core.ui.widget.TextField;
import xyz.volcanobay.empiric.data.JsonNodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The selected node's settings: its type and id, then its properties grouped by who reads them.
 */
final class LayoutPropertiesPane extends ScrollPanel {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int NAME_WIDTH = 62;

    private final LayoutEditorView view;
    private String signature = "";

    LayoutPropertiesPane(LayoutEditorView view) {
        this.view = view;
        content().layout(FlexLayout.column(3).align(Align.STRETCH));
        padding(Insets.of(2));
    }

    private LayoutDocument document() {
        return view.document();
    }

    @Override
    protected void tick() {
        String next = signature();
        if (!next.equals(signature)) {
            signature = next;
            rebuild();
        }
    }

    private String signature() {
        LayoutDocument document = document();
        BlueprintNode node = document == null ? null : document.selected();
        if (node == null) {
            return "";
        }
        BlueprintNode parent = document.parentOf(node);
        return System.identityHashCode(node) + "|" + node.type() + "|" + (parent == null ? "" : parent.type()) + "|" + unknown(node);
    }

    private List<String> unknown(BlueprintNode node) {
        Set<String> known = new HashSet<>();
        WidgetType<?> type = WidgetTypes.get(node.type());
        if (type != null) {
            type.properties().forEach(def -> known.add(def.name()));
        }
        BlueprintNode parent = document().parentOf(node);
        WidgetType<?> parentType = parent == null ? null : WidgetTypes.get(parent.type());
        if (parentType != null) {
            parentType.childProperties().forEach(def -> known.add(def.name()));
        }
        Blueprints.COMMON.forEach(def -> known.add(def.name()));
        return node.props().keySet().stream().filter(key -> !known.contains(key)).toList();
    }

    private void rebuild() {
        content().clear();
        scrollTo(0);
        LayoutDocument document = document();
        BlueprintNode node = document == null ? null : document.selected();
        if (node == null) {
            return;
        }
        List<String> types = WidgetTypes.all().stream().map(WidgetType::id).toList();
        Dropdown<String> type = new Dropdown<>(String.class, types).width(Length.FILL);
        type.bind(new Property<>() {
            @Override
            public String get() {
                return node.type();
            }

            @Override
            public void set(String value) {
                document.edit(null, () -> node.type(value));
            }
        });
        type.hint("Widget type. Changing it keeps the properties; ones the new type doesn't use show under Unknown.");
        add(type);
        TextField id = new TextField().placeholder("id").width(Length.FILL);
        id.bind(new Property<>() {
            @Override
            public String get() {
                return node.id();
            }

            @Override
            public void set(String value) {
                document.edit("id@" + System.identityHashCode(node), () -> node.id(value.trim()));
            }
        });
        id.hint("Widget id. Java code finds widgets by id with @Ref, @OnClick and @ContextAction.");
        add(id);

        WidgetType<?> widgetType = WidgetTypes.get(node.type());
        BlueprintNode parent = document.parentOf(node);
        WidgetType<?> parentType = parent == null ? null : WidgetTypes.get(parent.type());
        if (widgetType != null && !widgetType.properties().isEmpty()) {
            add(section(widgetType.id(), node, widgetType.properties()));
        }
        if (parentType != null && !parentType.childProperties().isEmpty()) {
            add(section("In " + parentType.id(), node, parentType.childProperties()));
        }
        List<PropertyDef> common = new ArrayList<>(Blueprints.COMMON);
        if (parent != null) {
            common.removeIf(def -> def.name().equals("theme"));
        }
        add(section("Layout and common", node, common));
        List<String> unknown = unknown(node);
        if (!unknown.isEmpty()) {
            Section section = new Section("Unknown");
            for (String key : unknown) {
                Widget row = new Widget().layout(FlexLayout.row(3).align(Align.CENTER));
                Button remove = new Button("x", () -> document.edit(null, () -> node.prop(key, null))).width(Length.px(12));
                remove.hint("Remove this property, which this widget type doesn't use");
                row.add(remove);
                row.add(new Label(key + " = " + node.prop(key)).textColor("text_dim"));
                section.add(row);
            }
            add(section);
        }
    }

    private Section section(String title, BlueprintNode node, List<PropertyDef> defs) {
        Section section = new Section(title);
        for (PropertyDef def : defs) {
            section.add(row(node, def));
        }
        return section;
    }

    private Widget row(BlueprintNode node, PropertyDef def) {
        String name = def.name();
        LayoutDocument document = document();
        Checkbox set = new Checkbox("");
        set.bind(new Property<>() {
            @Override
            public Boolean get() {
                return node.prop(name) != null;
            }

            @Override
            public void set(Boolean value) {
                DataNode initial = value ? LayoutEdits.initial(def) : null;
                document.edit(null, () -> node.prop(name, initial));
            }
        });
        set.hint(() -> node.prop(name) != null ? "Set in the file. Untick to remove it and use the default." : "Not set, so the default shown is used. Tick to set it.");
        Label label = new NameLabel(name).width(Length.px(NAME_WIDTH));
        if (!def.help().isEmpty()) {
            label.hint(def.help());
        }
        Widget header = new Widget().layout(FlexLayout.row(3).align(Align.CENTER));
        header.add(set);
        header.add(label);
        Widget editor = editor(node, def);
        if (inline(def.kind())) {
            editor.width(Length.FILL);
            header.add(editor);
            return header;
        }
        Widget block = new Widget().layout(FlexLayout.column(2).align(Align.STRETCH));
        block.add(header);
        block.add(editor);
        return block;
    }

    private static boolean inline(PropertyDef.Kind kind) {
        return switch (kind) {
            case INSETS, STRINGS, ACTIONS -> false;
            default -> true;
        };
    }

    private Widget editor(BlueprintNode node, PropertyDef def) {
        return switch (def.kind()) {
            case INT -> new NumberField().integer(true).bind(value(node, def, 0.0, v -> v.asNumber().doubleValue(), v -> DataNode.of(Math.round(v))));
            case NUMBER -> new NumberField().step(0.1).bind(value(node, def, 0.0, v -> v.asNumber().doubleValue(), DataNode::of));
            case BOOL -> new Checkbox("").bind(value(node, def, false, DataNode::asBoolean, DataNode::of));
            case CHOICE -> new Dropdown<>(String.class, def.options()).bind(value(node, def, "", DataNode::asString, DataNode::of));
            case COLOR -> new ColorPicker().alpha(true).bind(value(node, def, 0xFFFFFFFF, Props::parseColor,
                    argb -> DataNode.of(String.format("#%08X", argb))));
            case LENGTH -> length(node, def);
            case INSETS -> insets(node, def);
            case FIELD -> field(node, def);
            case STRINGS -> strings(node, def);
            case ACTIONS -> actions(node, def);
            default -> new TextField().bind(value(node, def, "", DataNode::asString, DataNode::of));
        };
    }

    /**
     * A property of the node seen as a value of one type. It reads the default while the property isn't set, and is
     * read-only until it is. Writes in quick succession to the same property share an undo step.
     */
    private <T> Property<T> value(BlueprintNode node, PropertyDef def, T empty, Function<DataNode, T> read, Function<T, DataNode> write) {
        LayoutDocument document = document();
        String name = def.name();
        return new Property<>() {
            @Override
            public T get() {
                DataNode current = node.prop(name);
                DataNode shown = current == null ? def.fallback() : current;
                if (shown == null || shown.isNull()) {
                    return empty;
                }
                try {
                    return read.apply(shown);
                } catch (RuntimeException e) {
                    return empty;
                }
            }

            @Override
            public void set(T value) {
                if (!writable()) {
                    return;
                }
                DataNode next;
                try {
                    next = write.apply(value);
                } catch (RuntimeException e) {
                    return;
                }
                if (next == null || next.equals(node.prop(name))) {
                    return;
                }
                document.edit(name + "@" + System.identityHashCode(node), () -> node.prop(name, next));
            }

            @Override
            public boolean writable() {
                return node.prop(name) != null;
            }
        };
    }

    private Length currentLength(BlueprintNode node, PropertyDef def) {
        DataNode current = node.prop(def.name());
        DataNode shown = current == null ? def.fallback() : current;
        try {
            return shown == null || shown.isNull() ? Length.FIT : Props.parseLength(shown);
        } catch (RuntimeException e) {
            return Length.FIT;
        }
    }

    private Widget length(BlueprintNode node, PropertyDef def) {
        Widget box = new Widget().layout(FlexLayout.row(2).align(Align.STRETCH));
        Dropdown<String> kind = new Dropdown<>(String.class, List.of("fit", "fill", "px"));
        kind.bind(value(node, def, "fit", value -> switch (Props.parseLength(value).kind()) {
            case FIT -> "fit";
            case FILL -> "fill";
            default -> "px";
        }, choice -> switch (choice) {
            case "fit" -> DataNode.of("fit");
            case "fill" -> DataNode.of("fill");
            default -> DataNode.of(40);
        }));
        kind.hint("fit: as big as the content. fill: a share of the free space, by weight. px: a fixed size.");
        NumberField amount = new NumberField().compact().width(Length.FILL);
        amount.bind(value(node, def, 0.0, value -> (double) Props.parseLength(value).value(), number -> {
            Length length = currentLength(node, def);
            return switch (length.kind()) {
                case FILL -> Props.writeLength(Length.fill((float) Math.max(0.1, number)));
                case PIXELS -> DataNode.of(Math.max(0, Math.round(number)));
                default -> null;
            };
        }));
        amount.watch(() -> currentLength(node, def).kind(), shown -> {
            amount.visible(shown != Length.Kind.FIT);
            amount.step(shown == Length.Kind.FILL ? 0.5 : 1);
            amount.hint(shown == Length.Kind.FILL ? "Weight: how big a share of the free space it takes" : "Pixels");
        });
        Length.Kind initial = currentLength(node, def).kind();
        amount.visible(initial != Length.Kind.FIT);
        amount.step(initial == Length.Kind.FILL ? 0.5 : 1);
        box.add(kind);
        box.add(amount);
        return box;
    }

    private Widget insets(BlueprintNode node, PropertyDef def) {
        Widget box = new Widget().layout(FlexLayout.row(2).align(Align.STRETCH));
        String[] names = {"Left", "Top", "Right", "Bottom"};
        for (int i = 0; i < 4; i++) {
            int side = i;
            NumberField field = new NumberField().integer(true).compact().width(Length.FILL);
            field.bind(value(node, def, 0.0, value -> (double) sides(Props.parseInsets(value))[side], number -> {
                DataNode current = node.prop(def.name());
                int[] sides = sides(current == null ? Insets.NONE : Props.parseInsets(current));
                sides[side] = (int) Math.round(number);
                return writeInsets(sides);
            }));
            field.hint(names[i]);
            box.add(field);
        }
        return box;
    }

    private static int[] sides(Insets insets) {
        return new int[]{insets.left(), insets.top(), insets.right(), insets.bottom()};
    }

    private static DataNode writeInsets(int[] sides) {
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
    private Widget field(BlueprintNode node, PropertyDef def) {
        Widget box = new Widget().layout(FlexLayout.row(2).align(Align.STRETCH));
        TextField text = new TextField().width(Length.FILL);
        text.bind(value(node, def, "", DataNode::asString, DataNode::of));
        Button pick = new Button("...").width(Length.px(14));
        pick.hint(() -> view.module() == null ? "Pick a module for the preview to choose from its synced fields" : "Pick a synced field of the preview module");
        pick.watch(() -> view.module() != null && node.prop(def.name()) != null, pick::enabled);
        pick.enabled(false);
        pick.onPress(() -> {
            Module<?> module = view.module();
            if (module == null || pick.root() == null) {
                return;
            }
            List<ContextEntry> entries = new ArrayList<>();
            for (FieldLayout field : module.type().spec().layout().fields()) {
                if (field.sync()) {
                    String id = field.id();
                    entries.add(ContextEntry.of(id + " (" + field.type().getSimpleName() + ")",
                            () -> document().edit(null, () -> node.prop(def.name(), DataNode.of(id)))));
                }
            }
            if (entries.isEmpty()) {
                entries.add(new ContextEntry("No synced fields", () -> {
                }, () -> false));
            }
            UiPoint below = pick.toRoot(0, pick.height());
            pick.root().openPopup(new ContextMenu(entries), below.x(), below.y());
        });
        box.add(text);
        box.add(pick);
        return box;
    }

    private Widget strings(BlueprintNode node, PropertyDef def) {
        TextArea area = new TextArea().rows(4);
        area.bind(value(node, def, "", value -> {
            StringBuilder lines = new StringBuilder();
            for (DataNode element : value.elementsOrSelf()) {
                if (!lines.isEmpty()) {
                    lines.append('\n');
                }
                lines.append(element.asString());
            }
            return lines.toString();
        }, text -> {
            DataNode list = DataNode.array();
            for (String line : text.split("\n")) {
                if (!line.isBlank()) {
                    list.add(DataNode.of(line.trim()));
                }
            }
            return list;
        }));
        area.hint("One entry per line");
        return area;
    }

    /**
     * Actions as JSON, applied whenever the text parses, with a menu that appends a template for each action. Text that
     * doesn't parse yet is kept, with the reason shown under it.
     */
    private Widget actions(BlueprintNode node, PropertyDef def) {
        String name = def.name();
        LayoutDocument document = document();
        String[] pending = {null};
        String[] error = {null};
        Widget box = new Widget().layout(FlexLayout.column(2).align(Align.STRETCH));
        TextArea area = new TextArea().rows(7);
        area.bind(new Property<>() {
            @Override
            public String get() {
                if (pending[0] != null) {
                    return pending[0];
                }
                DataNode current = node.prop(name);
                return GSON.toJson(JsonNodes.toJson(current == null || current.isNull() ? DataNode.array() : current));
            }

            @Override
            public void set(String text) {
                if (!writable()) {
                    return;
                }
                try {
                    DataNode parsed = JsonNodes.toNode(JsonParser.parseString(text));
                    if (!parsed.isArray()) {
                        throw new JsonParseException("must be a list");
                    }
                    pending[0] = null;
                    error[0] = null;
                    if (!parsed.equals(node.prop(name))) {
                        document.edit(name + "@" + System.identityHashCode(node), () -> node.prop(name, parsed));
                    }
                } catch (RuntimeException e) {
                    pending[0] = text;
                    error[0] = "Not valid yet: " + e.getMessage();
                }
            }

            @Override
            public boolean writable() {
                return node.prop(name) != null;
            }
        });
        Label problem = new Label("").textColor("error").wrap(true);
        problem.watch(() -> error[0], message -> {
            problem.setValue(message == null ? "" : message);
            problem.visible(message != null);
        });
        problem.visible(false);
        Button add = new Button("Add action");
        add.hint("Append an action with its settings filled in, then edit the JSON above");
        add.watch(() -> node.prop(name) != null, add::enabled);
        add.enabled(node.prop(name) != null);
        add.onPress(() -> {
            List<ContextEntry> entries = new ArrayList<>();
            for (String action : Blueprints.actions().stream().sorted().toList()) {
                entries.add(ContextEntry.of(action, () -> {
                    DataNode current = node.prop(name);
                    DataNode list = current == null || current.isNull() ? DataNode.array() : current.copy();
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
                    pending[0] = null;
                    error[0] = null;
                    document.edit(null, () -> node.prop(name, list));
                }));
            }
            UiPoint below = add.toRoot(0, add.height());
            add.root().openPopup(new ContextMenu(entries), below.x(), below.y());
        });
        box.add(area);
        box.add(problem);
        box.add(add);
        return box;
    }

    /**
     * A property name that shows its help when the pointer rests on it.
     */
    private static final class NameLabel extends Label {

        NameLabel(String text) {
            super(text);
        }

        @Override
        protected boolean interactive() {
            return true;
        }
    }
}
