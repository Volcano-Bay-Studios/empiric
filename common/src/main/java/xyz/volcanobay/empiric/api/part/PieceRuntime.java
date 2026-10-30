package xyz.volcanobay.empiric.api.part;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What a piece carries besides its stack: status flags, saved and synced with it, and state that lives only in memory.
 */
public final class PieceRuntime {

    private static final Map<ResourceLocation, Integer> FLAGS = new LinkedHashMap<>();

    /**
     * Bits of {@link #flag registered flags}.
     */
    public int status;
    private final Map<State<?>, Object> state = new HashMap<>();

    /**
     * Kept in memory only, made on first use.
     */
    public record State<T>(ResourceLocation id, Supplier<T> factory) {
    }

    /**
     * Registers a status flag. Register them in the same order on both sides, during mod init.
     *
     * @return The flag's bit
     */
    public static synchronized int flag(ResourceLocation id) {
        Integer bit = FLAGS.get(id);
        if (bit != null) {
            return bit;
        }
        if (FLAGS.size() >= 31) {
            throw new IllegalStateException("No room for piece flag " + id);
        }
        bit = 1 << FLAGS.size();
        FLAGS.put(id, bit);
        return bit;
    }

    public boolean has(int flag) {
        return (status & flag) != 0;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(State<T> key) {
        return (T) state.computeIfAbsent(key, k -> k.factory().get());
    }

    void copyFrom(PieceRuntime other) {
        status = other.status;
        state.clear();
        state.putAll(other.state);
    }
}
