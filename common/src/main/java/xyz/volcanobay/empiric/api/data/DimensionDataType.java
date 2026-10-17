package xyz.volcanobay.empiric.api.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Data entries that apply to dimensions. Each entry lists the dimension ids it covers under {@code "dimensions"}; when several entries claim a dimension the
 * one with the highest {@code "priority"} wins.
 */
public class DimensionDataType<T> {

    public static final String DIMENSIONS = "dimensions";
    public static final String PRIORITY = "priority";

    private final DataEntryType<T> entries;
    private volatile Map<ResourceLocation, T> byDimension = Map.of();

    public DimensionDataType(ResourceLocation id, Class<T> type, Supplier<T> defaults) {
        this.entries = new DataEntryType<>(id.toString(), type, defaults, DIMENSIONS, PRIORITY);
        this.entries.onReload(this::rebuild);
    }

    public DimensionDataType<T> register(DataEntrySystem data) {
        data.register(entries);
        return this;
    }

    public DataEntryType<T> entries() {
        return entries;
    }

    public T defaultValue() {
        return entries.defaultValue();
    }

    /**
     * The entry for a dimension, the overworld's entry if it has none, or the type's defaults.
     */
    public T get(Level level) {
        return get(level.dimension().location());
    }

    public T get(ResourceLocation dimension) {
        T value = byDimension.get(dimension);
        if (value == null) {
            value = byDimension.get(Level.OVERWORLD.location());
        }
        return value == null ? entries.defaultValue() : value;
    }

    /**
     * The entry that lists this dimension, or null.
     */
    public T find(ResourceLocation dimension) {
        return byDimension.get(dimension);
    }

    private void rebuild() {
        List<DataEntry<T>> sorted = new ArrayList<>(entries.entries());
        sorted.sort(Comparator.comparingInt(entry -> priority(entry.extras())));
        Map<ResourceLocation, T> built = new HashMap<>();
        for (DataEntry<T> entry : sorted) {
            DataNode dimensions = entry.extras().get(DIMENSIONS);
            if (dimensions == null) {
                Empiric.LOGGER.warn("{} entry {} doesn't list any dimensions", entries.id(), entry.id());
                continue;
            }
            for (DataNode dimension : dimensions.elementsOrSelf()) {
                built.put(ResourceLocation.parse(dimension.asString()), entry.value());
            }
        }
        byDimension = Map.copyOf(built);
    }

    private static int priority(DataNode extras) {
        return extras.get(PRIORITY) == null ? 0 : extras.get(PRIORITY).asNumber().intValue();
    }
}
