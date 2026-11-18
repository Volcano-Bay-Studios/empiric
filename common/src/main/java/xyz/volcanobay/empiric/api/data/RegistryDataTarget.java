package xyz.volcanobay.empiric.api.data;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A target for the entries of one registry, builtin or dynamic. Selectors are ids ({@code "minecraft:stone"}) or tags
 * ({@code "#minecraft:logs"}), matched against the candidate's {@link Holder}. Ids of builtin registries are checked
 * when the entry loads, ids of dynamic registries can't be and simply never match if they don't exist.
 *
 * @param <C> what lookups pass in
 * @param <V> the registry's value type
 */
public final class RegistryDataTarget<C, V> implements DataTarget<C> {

    private final ResourceKey<? extends Registry<V>> registry;
    private final String key;
    private final Function<C, Holder<V>> holders;

    public RegistryDataTarget(ResourceKey<? extends Registry<V>> registry, String key, Function<C, Holder<V>> holders) {
        this.registry = registry;
        this.key = key;
        this.holders = holders;
    }

    public ResourceKey<? extends Registry<V>> registry() {
        return registry;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public boolean cacheable() {
        return true;
    }

    @Override
    public Object keyOf(C candidate) {
        return holders.apply(candidate).unwrapKey().map(ResourceKey::location).orElse(null);
    }

    @Override
    public List<Selector<C>> compile(String typeId, String entryId, DataNode selectors, Consumer<String> warnings) {
        Registry<?> known = BuiltInRegistries.REGISTRY.get(registry.location());
        List<Selector<C>> compiled = new ArrayList<>();
        for (DataNode node : selectors.elementsOrSelf()) {
            String value = node.asString();
            IdOrTag<V> selector = IdOrTag.tryParse(value);
            if (selector == null) {
                warnings.accept(typeId + " entry " + entryId + " lists the invalid " + key + " selector " + value);
                continue;
            }
            ResourceLocation id = selector.location();
            if (selector.tag()) {
                TagKey<V> tagKey = TagKey.create(registry, id);
                compiled.add(new Selector<>() {
                    @Override
                    public boolean matches(C candidate) {
                        return holders.apply(candidate).is(tagKey);
                    }

                    @Override
                    public int specificity() {
                        return TAG;
                    }
                });
                continue;
            }
            if (known != null && !known.containsKey(id)) {
                warnings.accept(typeId + " entry " + entryId + " lists the unknown " + key + " id " + value);
                continue;
            }
            compiled.add(new Selector<>() {
                @Override
                public boolean matches(C candidate) {
                    return holders.apply(candidate).is(id);
                }

                @Override
                public int specificity() {
                    return EXACT;
                }

                @Override
                public Set<Object> exactKeys() {
                    return Set.of(id);
                }
            });
        }
        return compiled;
    }
}
