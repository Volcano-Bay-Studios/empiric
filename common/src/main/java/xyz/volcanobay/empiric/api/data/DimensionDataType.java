package xyz.volcanobay.empiric.api.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;

import java.util.function.Supplier;

/**
 * Data entries that apply to dimensions. Each entry lists the dimension ids it covers under {@code "dimensions"}. When
 * several entries claim a dimension the one with the highest {@code "priority"} wins.
 *
 * @deprecated a thin wrapper over a {@link GameDataType} with the dimensions target. It adds the overworld fallback.
 */
@Deprecated
public class DimensionDataType<T> {

    public static final String DIMENSIONS = "dimensions";
    public static final String PRIORITY = "priority";

    private final GameDataType<T> entries;

    public DimensionDataType(ResourceLocation id, Class<T> type, Supplier<T> defaults) {
        this.entries = GameDataType.builder(id, type, defaults).dimensions().build();
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
        T value = find(dimension);
        if (value == null) {
            value = find(Level.OVERWORLD.location());
        }
        return value == null ? entries.defaultValue() : value;
    }

    /**
     * The entry that lists this dimension, or null.
     */
    public T find(ResourceLocation dimension) {
        return entries.find(DataTargets.DIMENSIONS, ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
