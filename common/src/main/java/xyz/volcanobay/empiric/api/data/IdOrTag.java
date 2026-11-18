package xyz.volcanobay.empiric.api.data;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

/**
 * An id such as {@code "minecraft:stone"} or a tag such as {@code "#minecraft:logs"}. Only the location is stored, so
 * nothing has to be loaded to read one. The type parameter only says what the id is meant to select.
 *
 * @param location the id or tag id
 * @param tag      whether this is a tag
 */
public record IdOrTag<T>(ResourceLocation location, boolean tag) {

    /**
     * @return the parsed selector, or null if the text isn't a valid id or tag
     */
    public static <T> IdOrTag<T> tryParse(String text) {
        boolean tag = text.startsWith("#");
        ResourceLocation location = ResourceLocation.tryParse(tag ? text.substring(1) : text);
        return location == null ? null : new IdOrTag<>(location, tag);
    }

    /**
     * @throws IllegalArgumentException if the text isn't a valid id or tag
     */
    public static <T> IdOrTag<T> parse(String text) {
        IdOrTag<T> parsed = tryParse(text);
        if (parsed == null) {
            throw new IllegalArgumentException("\"" + text + "\" is not a valid id or #tag");
        }
        return parsed;
    }

    public boolean matches(Holder<? extends T> holder) {
        if (tag) {
            return holder.tags().anyMatch(key -> key.location().equals(location));
        }
        return holder.is(location);
    }

    @Override
    public String toString() {
        return tag ? "#" + location : location.toString();
    }
}
