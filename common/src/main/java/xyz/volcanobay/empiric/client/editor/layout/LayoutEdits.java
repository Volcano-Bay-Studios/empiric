package xyz.volcanobay.empiric.client.editor.layout;

import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.PropertyDef;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetType;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetTypes;

/**
 * Changes to a layout's tree, each one undoable step on the document.
 */
public final class LayoutEdits {

    private LayoutEdits() {
    }

    /**
     * The tree a new layout starts with: a centred panel stacking its children.
     */
    public static BlueprintNode starter() {
        BlueprintNode root = new BlueprintNode("panel");
        root.prop("layout", DataNode.of("column")).prop("gap", DataNode.of(4)).prop("padding", DataNode.of(6)).prop("anchor", DataNode.of("center"));
        return root;
    }

    public static boolean isContainer(BlueprintNode node) {
        WidgetType<?> type = WidgetTypes.get(node.type());
        return type != null && type.isContainer();
    }

    public static void insert(LayoutDocument document, BlueprintNode parent, int index, String type) {
        document.edit(null, () -> {
            BlueprintNode created = new BlueprintNode(type);
            parent.children().add(Math.max(0, Math.min(index, parent.children().size())), created);
            document.select(created);
        });
    }

    /**
     * Puts a node inside a new container, in its place. Wrapping the root keeps its anchor on the new root.
     */
    public static void wrap(LayoutDocument document, BlueprintNode node, String type) {
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

    public static void delete(LayoutDocument document, BlueprintNode node) {
        BlueprintNode parent = document.parentOf(node);
        if (parent == null) {
            return;
        }
        document.edit(null, () -> {
            parent.children().remove(node);
            document.select(parent);
        });
    }

    public static void duplicate(LayoutDocument document, BlueprintNode node) {
        BlueprintNode parent = document.parentOf(node);
        if (parent == null) {
            return;
        }
        document.edit(null, () -> {
            BlueprintNode copy = node.copy();
            parent.children().add(parent.children().indexOf(node) + 1, copy);
            document.select(copy);
        });
    }

    /**
     * Moves a node one place among its siblings, up for a negative direction.
     */
    public static void shift(LayoutDocument document, BlueprintNode node, int direction) {
        BlueprintNode parent = document.parentOf(node);
        if (parent == null) {
            return;
        }
        int index = parent.children().indexOf(node);
        int target = index + direction;
        if (target < 0 || target >= parent.children().size()) {
            return;
        }
        document.edit(null, () -> {
            parent.children().remove(index);
            parent.children().add(target, node);
        });
    }

    /**
     * Whether a node can be dropped onto another: not the root, and not into itself.
     */
    public static boolean canMove(LayoutDocument document, BlueprintNode moved, BlueprintNode onto) {
        if (moved == onto || moved == document.root() || LayoutDocument.contains(moved, onto)) {
            return false;
        }
        return isContainer(onto) || document.parentOf(onto) != null;
    }

    /**
     * Drops a node onto another: into it as the last child if it holds children, otherwise just before it.
     */
    public static void move(LayoutDocument document, BlueprintNode moved, BlueprintNode onto) {
        if (!canMove(document, moved, onto)) {
            return;
        }
        BlueprintNode target = isContainer(onto) ? onto : document.parentOf(onto);
        document.edit(null, () -> {
            document.parentOf(moved).children().remove(moved);
            if (target == onto) {
                onto.children().add(moved);
            } else {
                target.children().add(target.children().indexOf(onto), moved);
            }
        });
    }

    /**
     * The value a property gets when it's first set: its default, or an empty value of its kind.
     */
    public static DataNode initial(PropertyDef def) {
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
}
