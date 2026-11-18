package xyz.volcanobay.empiric.api.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;
import xyz.volcanobay.empiric.core.entry.TargetedDataType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Data entries that apply to blocks, with {@code "blocks"} and {@code "priority"} keys.
 *
 * @deprecated a thin wrapper over a {@link GameDataType} with the blocks target. Use that directly.
 */
@Deprecated
public class BlockDataType<T> {

    public static final String BLOCKS = "blocks";
    public static final String PRIORITY = "priority";
    private static final List<BlockDataType<?>> TYPES = new CopyOnWriteArrayList<>();

    private final GameDataType<T> entries;

    public BlockDataType(ResourceLocation id, Class<T> type, Supplier<T> defaults) {
        this.entries = GameDataType.builder(id, type, defaults).blocks().build();
    }

    public static void clearCaches() {
        TargetedDataType.clearAllCaches();
    }

    public static List<BlockDataType<?>> types() {
        return List.copyOf(TYPES);
    }

    public BlockDataType<T> register(DataEntrySystem data) {
        data.register(entries);
        TYPES.add(this);
        return this;
    }

    public DataEntryType<T> entries() {
        return entries;
    }

    public T defaultValue() {
        return entries.defaultValue();
    }

    public T get(BlockState state) {
        return entries.get(state);
    }

    public T get(Block block) {
        return entries.get(block);
    }
}
