package xyz.volcanobay.empiric.data;

import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.Spec;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public record MutableSpec<T>(BiConsumer<DataBuffer, T> writer, BiConsumer<DataBuffer, T> reader, Supplier<T> factory, BiConsumer<T, T> setter) implements Spec<T> {

    public static <T> MutableSpec<T> of(BiConsumer<DataBuffer, T> writer, BiConsumer<DataBuffer, T> reader, Supplier<T> factory, BiConsumer<T, T> setter) {
        return new MutableSpec<>(writer, reader, factory, setter);
    }

    @Override
    public void write(DataBuffer buffer, T value, DataContext context) {
        writer.accept(buffer, value);
    }

    @Override
    public T read(DataBuffer buffer, DataContext context) {
        T value = factory.get();
        reader.accept(buffer, value);
        return value;
    }

    @Override
    public T update(DataBuffer buffer, T existing, DataContext context) {
        reader.accept(buffer, existing);
        return existing;
    }

    @Override
    public boolean supportsUpdate() {
        return true;
    }

    @Override
    public T copy(T value) {
        T copy = factory.get();
        setter.accept(copy, value);
        return copy;
    }

    @Override
    public T copyInto(T source, T target) {
        setter.accept(target, source);
        return target;
    }
}
