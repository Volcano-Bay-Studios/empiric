package xyz.volcanobay.empiric.api.part;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.Empiric;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Why a part can't be placed. Each has a lang key {@code message.<namespace>.place.<path>} shown to the player.
 */
public final class PlacementFailure {

    private static final Map<ResourceLocation, PlacementFailure> FAILURES = new LinkedHashMap<>();

    /**
     * Nothing to place or show, such as aiming at the side of a plain block; no message.
     */
    public static final PlacementFailure NONE = register(id("none"), false);
    public static final PlacementFailure NO_SURFACE = register(id("no_surface"));
    public static final PlacementFailure PARTIAL_SURFACE = register(id("partial_surface"));
    public static final PlacementFailure BLOCKED = register(id("blocked"));
    public static final PlacementFailure UNSTABLE = register(id("unstable"));

    private final ResourceLocation id;
    private final @Nullable String key;

    private PlacementFailure(ResourceLocation id, @Nullable String key) {
        this.id = id;
        this.key = key;
    }

    public static PlacementFailure register(ResourceLocation id) {
        return register(id, true);
    }

    private static synchronized PlacementFailure register(ResourceLocation id, boolean shown) {
        if (FAILURES.containsKey(id)) {
            throw new IllegalArgumentException("Placement failure " + id + " is already registered");
        }
        PlacementFailure failure = new PlacementFailure(id, shown ? "message." + id.getNamespace() + ".place." + id.getPath() : null);
        FAILURES.put(id, failure);
        return failure;
    }

    public static synchronized @Nullable PlacementFailure get(ResourceLocation id) {
        return FAILURES.get(id);
    }

    public ResourceLocation id() {
        return id;
    }

    /**
     * @return The lang key of the message, or null when nothing is shown
     */
    public @Nullable String key() {
        return key;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Empiric.MODID, path);
    }
}
