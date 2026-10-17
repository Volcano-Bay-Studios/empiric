package xyz.volcanobay.empiric.api.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class BlockDataType<T> {

    public static final String BLOCKS = "blocks";
    public static final String PRIORITY = "priority";
    private static final List<BlockDataType<?>> TYPES = new CopyOnWriteArrayList<>();

    private final DataEntryType<T> entries;
    private final Map<Block, T> cache = new ConcurrentHashMap<>();
    private volatile List<Rule<T>> rules = List.of();

    public BlockDataType(ResourceLocation id, Class<T> type, Supplier<T> defaults) {
        this.entries = new DataEntryType<>(id.toString(), type, defaults, BLOCKS, PRIORITY);
        this.entries.onReload(this::rebuild);
    }

    public static void clearCaches() {
        for (BlockDataType<?> type : TYPES) {
            type.cache.clear();
        }
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
        return get(state.getBlock());
    }

    public T get(Block block) {
        return cache.computeIfAbsent(block, this::resolve);
    }

    private T resolve(Block block) {
        for (Rule<T> rule : rules) {
            if (rule.matches(block)) {
                return rule.value();
            }
        }
        return entries.defaultValue();
    }

    private void rebuild() {
        List<Rule<T>> built = new ArrayList<>();
        for (DataEntry<T> entry : entries.entries()) {
            DataNode extras = entry.extras();
            int priority = extras.get(PRIORITY) == null ? 0 : extras.get(PRIORITY).asNumber().intValue();
            DataNode blocks = extras.get(BLOCKS);
            if (blocks == null) {
                Empiric.LOGGER.warn("{} entry {} doesn't list any blocks", entries.id(), entry.id());
                continue;
            }
            List<Block> exact = new ArrayList<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (DataNode selector : blocks.elementsOrSelf()) {
                String value = selector.asString();
                if (value.startsWith("#")) {
                    tags.add(TagKey.create(Registries.BLOCK, ResourceLocation.parse(value.substring(1))));
                    continue;
                }
                Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(value));
                if (block.isPresent()) {
                    exact.add(block.get());
                } else {
                    Empiric.LOGGER.warn("{} entry {} lists the unknown block {}", entries.id(), entry.id(), value);
                }
            }
            if (!exact.isEmpty()) {
                built.add(new Rule<>(Set.copyOf(exact), List.of(), priority, true, entry.value()));
            }
            if (!tags.isEmpty()) {
                built.add(new Rule<>(Set.of(), List.copyOf(tags), priority, false, entry.value()));
            }
        }
        built.sort(Comparator.comparingInt((Rule<T> rule) -> rule.priority()).reversed().thenComparing(rule -> !rule.exact()));
        rules = List.copyOf(built);
        cache.clear();
    }

    private record Rule<T>(Set<Block> blocks, List<TagKey<Block>> tags, int priority, boolean exact, T value) {

        boolean matches(Block block) {
            if (blocks.contains(block)) {
                return true;
            }
            for (TagKey<Block> tag : tags) {
                if (block.builtInRegistryHolder().is(tag)) {
                    return true;
                }
            }
            return false;
        }
    }
}
