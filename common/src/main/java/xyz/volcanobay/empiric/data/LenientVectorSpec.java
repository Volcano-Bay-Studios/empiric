package xyz.volcanobay.empiric.data;

import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.DataException;
import xyz.volcanobay.empiric.core.data.DataPrinter;
import xyz.volcanobay.empiric.core.data.Mode;
import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.data.layout.KeyedFormat;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.data.tree.TreeDataBuffer;

import java.util.Map;

/**
 * Reads vectors and positions from structured data as {@code [x, y, z]}, as {@code {"x": 1, "y": 2, "z": 3}} and,
 * when text is allowed, as {@code "x y z"} or {@code "x,y,z"}. Everything else, including writing, is the wrapped spec.
 */
final class LenientVectorSpec<T> implements Spec<T> {

    private final Spec<T> inner;
    private final boolean text;
    private final String[] names;

    LenientVectorSpec(Spec<T> inner, boolean text, String... names) {
        this.inner = inner;
        this.text = text;
        this.names = names;
    }

    private DataBuffer normalized(DataBuffer buffer) {
        if (!buffer.structured()) {
            return buffer;
        }
        DataNode node = buffer.takeNode();
        if (node.isObject()) {
            Map<String, DataNode> index = KeyedFormat.indexKeys(node.entries());
            DataNode array = DataNode.array();
            for (String name : names) {
                DataNode part = KeyedFormat.find(index, name);
                if (part == null) {
                    throw new DataException("Expected the keys " + String.join(", ", names) + " but found " + node);
                }
                array.add(part);
            }
            return TreeDataBuffer.reader(array);
        }
        if (text && node.kind() == DataNode.Kind.STRING) {
            String[] parts = node.asString().trim().split("[\\s,]+");
            if (parts.length != names.length) {
                throw new DataException("Expected " + names.length + " numbers but found " + node);
            }
            DataNode array = DataNode.array();
            for (String part : parts) {
                array.add(DataNode.of(part));
            }
            return TreeDataBuffer.reader(array);
        }
        return TreeDataBuffer.reader(node);
    }

    @Override
    public void write(DataBuffer buffer, T value, DataContext context) {
        inner.write(buffer, value, context);
    }

    @Override
    public T read(DataBuffer buffer, DataContext context) {
        return inner.read(normalized(buffer), context);
    }

    @Override
    public T update(DataBuffer buffer, T existing, DataContext context) {
        return inner.update(normalized(buffer), existing, context);
    }

    @Override
    public boolean supportsUpdate() {
        return inner.supportsUpdate();
    }

    @Override
    public Mode defaultMode() {
        return inner.defaultMode();
    }

    @Override
    public T copy(T value) {
        return inner.copy(value);
    }

    @Override
    public T copyInto(T source, T target) {
        return inner.copyInto(source, target);
    }

    @Override
    public boolean same(T first, T second) {
        return inner.same(first, second);
    }

    @Override
    public Object capture(T value) {
        return inner.capture(value);
    }

    @Override
    public boolean matches(T value, Object captured) {
        return inner.matches(value, captured);
    }

    @Override
    public boolean keyed() {
        return inner.keyed();
    }

    @Override
    public String toKey(T value) {
        return inner.toKey(value);
    }

    @Override
    public T fromKey(String key) {
        return inner.fromKey(key);
    }

    @Override
    public void print(T value, DataPrinter printer) {
        inner.print(value, printer);
    }
}
