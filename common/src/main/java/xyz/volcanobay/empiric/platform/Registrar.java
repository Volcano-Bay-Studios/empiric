package xyz.volcanobay.empiric.platform;

import java.util.function.Supplier;

/**
 * Registers objects into one vanilla registry under Empiric's namespace. Get one from {@link Platform#registrar}.
 */
public interface Registrar<T> {

    /**
     * Registers what {@code factory} makes as {@code empiric:name}. Don't call {@code get} on the result before
     * registration has run.
     */
    <R extends T> Supplier<R> register(String name, Supplier<? extends R> factory);
}
