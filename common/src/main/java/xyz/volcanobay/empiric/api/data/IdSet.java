package xyz.volcanobay.empiric.api.data;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A set of ids and tags that something can be matched against, such as the blocks a rule applies to. In data it is a
 * string, a list of strings, or {@code "*"} for everything. Ids are {@code "minecraft:stone"} and tags are
 * {@code "#minecraft:logs"}. Only locations are stored, so reading one needs no registry.
 *
 * <p>It is written as a string when it has one element and as a list otherwise.
 */
public final class IdSet<T> {

    public static final IdSet<?> ANY = new IdSet<>(List.of(), true);
    public static final IdSet<?> EMPTY = new IdSet<>(List.of(), false);

    private final List<IdOrTag<T>> elements;
    private final boolean any;

    private IdSet(List<IdOrTag<T>> elements, boolean any) {
        this.elements = List.copyOf(elements);
        this.any = any;
    }

    @SuppressWarnings("unchecked")
    public static <T> IdSet<T> any() {
        return (IdSet<T>) ANY;
    }

    @SuppressWarnings("unchecked")
    public static <T> IdSet<T> empty() {
        return (IdSet<T>) EMPTY;
    }

    @SafeVarargs
    public static <T> IdSet<T> of(IdOrTag<T>... elements) {
        return elements.length == 0 ? empty() : new IdSet<>(List.of(elements), false);
    }

    public static <T> IdSet<T> of(List<IdOrTag<T>> elements) {
        return elements.isEmpty() ? empty() : new IdSet<>(elements, false);
    }

    /**
     * @throws IllegalArgumentException if an element isn't a valid id or tag
     */
    public static <T> IdSet<T> parse(List<String> texts) {
        List<IdOrTag<T>> parsed = new ArrayList<>();
        for (String text : texts) {
            if (text.trim().equals("*")) {
                return any();
            }
            parsed.add(IdOrTag.parse(text));
        }
        return of(parsed);
    }

    public boolean isAny() {
        return any;
    }

    public boolean isEmpty() {
        return !any && elements.isEmpty();
    }

    public List<IdOrTag<T>> elements() {
        return elements;
    }

    public Set<ResourceLocation> ids() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (IdOrTag<T> element : elements) {
            if (!element.tag()) {
                ids.add(element.location());
            }
        }
        return ids;
    }

    public Set<ResourceLocation> tags() {
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (IdOrTag<T> element : elements) {
            if (element.tag()) {
                tags.add(element.location());
            }
        }
        return tags;
    }

    public boolean matches(Holder<? extends T> holder) {
        if (any) {
            return true;
        }
        for (IdOrTag<T> element : elements) {
            if (element.matches(holder)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public boolean matches(Block block) {
        return matches((Holder<? extends T>) block.builtInRegistryHolder());
    }

    public boolean matches(BlockState state) {
        return matches(state.getBlock());
    }

    @SuppressWarnings("unchecked")
    public boolean matches(Item item) {
        return matches((Holder<? extends T>) item.builtInRegistryHolder());
    }

    public boolean matches(ItemStack stack) {
        return matches(stack.getItem());
    }

    @SuppressWarnings("unchecked")
    public boolean matches(Fluid fluid) {
        return matches((Holder<? extends T>) fluid.builtInRegistryHolder());
    }

    @SuppressWarnings("unchecked")
    public boolean matches(EntityType<?> type) {
        return matches((Holder<? extends T>) type.builtInRegistryHolder());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IdSet<?> other && any == other.any && elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode() * 31 + Boolean.hashCode(any);
    }

    @Override
    public String toString() {
        return any ? "*" : elements.toString();
    }
}
