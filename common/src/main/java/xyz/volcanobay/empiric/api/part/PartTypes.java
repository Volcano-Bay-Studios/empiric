package xyz.volcanobay.empiric.api.part;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every part type, by id. Register during mod init on both sides, so nothing needs syncing.
 */
public final class PartTypes {

    private static final Map<ResourceLocation, PartType> TYPES = new LinkedHashMap<>();

    private PartTypes() {
    }

    public static synchronized PartType register(PartType type) {
        if (TYPES.containsKey(type.id())) {
            throw new IllegalArgumentException("Part type " + type.id() + " is already registered");
        }
        TYPES.put(type.id(), type);
        return type;
    }

    public static synchronized @Nullable PartType get(@Nullable ResourceLocation id) {
        return id == null ? null : TYPES.get(id);
    }

    public static synchronized Collection<PartType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }
}
