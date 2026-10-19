package xyz.volcanobay.empiric.client.editor.layout;

import net.minecraft.Util;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * One layout open in the editor: its tree, the selected node, and undo history as snapshots of the whole tree. Edits to
 * the same thing in quick succession, like typing into one field, share one undo step.
 */
public final class LayoutDocument {

    private static final int HISTORY = 200;
    private static final long MERGE_MILLIS = 1000;

    private final String id;
    private String description;
    private BlueprintNode root;
    private BlueprintNode selected;
    private final Deque<DataNode> undo = new ArrayDeque<>();
    private final Deque<DataNode> redo = new ArrayDeque<>();
    private DataNode saved;
    private String lastKey;
    private long lastEdit;
    private int version;

    public LayoutDocument(String id, String description, BlueprintNode root) {
        this.id = id;
        this.description = description;
        this.root = root;
        this.selected = root;
        this.saved = root.write();
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public void description(String description) {
        this.description = description;
        version++;
    }

    public BlueprintNode root() {
        return root;
    }

    /**
     * Swaps the whole tree, such as when the root is wrapped in a new container. Call it inside {@link #edit}.
     */
    public void replaceRoot(BlueprintNode root) {
        this.root = root;
    }

    public BlueprintNode selected() {
        return selected;
    }

    public void select(BlueprintNode node) {
        this.selected = node;
    }

    /**
     * Bumped on every change, so the preview knows to rebuild.
     */
    public int version() {
        return version;
    }

    public boolean dirty() {
        return !root.write().equals(saved);
    }

    public void markSaved() {
        saved = root.write();
    }

    /**
     * Runs a change, keeping the tree as it was for undo. Changes with the same key less than a second apart merge.
     */
    public void edit(String key, Runnable change) {
        long now = Util.getMillis();
        if (key == null || !Objects.equals(key, lastKey) || now - lastEdit > MERGE_MILLIS) {
            push(undo, root.write());
            redo.clear();
        }
        lastKey = key;
        lastEdit = now;
        change.run();
        version++;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        if (!undo.isEmpty()) {
            push(redo, root.write());
            restore(undo.pop());
        }
    }

    public void redo() {
        if (!redo.isEmpty()) {
            push(undo, root.write());
            restore(redo.pop());
        }
    }

    private void restore(DataNode snapshot) {
        String selectedPath = path(root, selected, "");
        root = BlueprintNode.parse(snapshot);
        selected = selectedPath == null ? root : byPath(root, selectedPath);
        lastKey = null;
        version++;
    }

    private static void push(Deque<DataNode> stack, DataNode snapshot) {
        stack.push(snapshot);
        while (stack.size() > HISTORY) {
            stack.removeLast();
        }
    }

    /**
     * The parent of a node in this document, or null for the root or a node that isn't here.
     */
    public BlueprintNode parentOf(BlueprintNode node) {
        return parentOf(root, node);
    }

    private static BlueprintNode parentOf(BlueprintNode from, BlueprintNode node) {
        for (BlueprintNode child : from.children()) {
            if (child == node) {
                return from;
            }
            BlueprintNode found = parentOf(child, node);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static boolean contains(BlueprintNode from, BlueprintNode node) {
        if (from == node) {
            return true;
        }
        for (BlueprintNode child : from.children()) {
            if (contains(child, node)) {
                return true;
            }
        }
        return false;
    }

    private static String path(BlueprintNode from, BlueprintNode node, String prefix) {
        if (from == node) {
            return prefix;
        }
        for (int i = 0; i < from.children().size(); i++) {
            String found = path(from.children().get(i), node, prefix + "/" + i);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static BlueprintNode byPath(BlueprintNode root, String path) {
        BlueprintNode node = root;
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            int index = Integer.parseInt(part);
            if (index >= node.children().size()) {
                return node;
            }
            node = node.children().get(index);
        }
        return node;
    }
}
