package xyz.volcanobay.empiric.api.part;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.part.trait.Controls;
import xyz.volcanobay.empiric.api.part.trait.Flag;
import xyz.volcanobay.empiric.api.part.trait.Hitboxes;
import xyz.volcanobay.empiric.api.part.trait.Shapes;
import xyz.volcanobay.empiric.api.part.trait.Socketed;
import xyz.volcanobay.empiric.api.part.trait.Sockets;
import xyz.volcanobay.empiric.api.part.trait.Support;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PartTraits {

    private static final Map<ResourceLocation, TraitKey<?>> KEYS = new LinkedHashMap<>();

    /**
     * Entities collide with it.
     */
    public static final TraitKey<Flag> SOLID = register(id("solid"), Flag.class);
    /**
     * It turns to face away from the player placing it.
     */
    public static final TraitKey<Flag> ROTATABLE = register(id("rotatable"), Flag.class);
    /**
     * It's only ever held, never placed.
     */
    public static final TraitKey<Flag> HANDHELD = register(id("handheld"), Flag.class);
    /**
     * Its model is drawn once, single sided, rather than as translucent back and front faces.
     */
    public static final TraitKey<Flag> OPAQUE = register(id("opaque"), Flag.class);
    /**
     * An empty hand picks it up only while sneaking.
     */
    public static final TraitKey<Flag> SNEAK_PICKUP = register(id("sneak_pickup"), Flag.class);
    /**
     * What its body is aimed at and outlined by, instead of its shapes; for sloped or turned faces.
     */
    public static final TraitKey<Hitboxes> HITBOXES = register(id("hitboxes"), Hitboxes.class);
    public static final TraitKey<Support> SUPPORT = register(id("support"), Support.class);
    public static final TraitKey<Shapes> SHAPES = register(id("shapes"), Shapes.class);
    public static final TraitKey<Sockets> SOCKETS = register(id("sockets"), Sockets.class);
    public static final TraitKey<Socketed> SOCKETED = register(id("socketed"), Socketed.class);
    public static final TraitKey<Controls> CONTROLS = register(id("controls"), Controls.class);

    private PartTraits() {
    }

    public static synchronized <T> TraitKey<T> register(ResourceLocation id, Class<T> type) {
        if (KEYS.containsKey(id)) {
            throw new IllegalArgumentException("Part trait " + id + " is already registered");
        }
        TraitKey<T> key = new TraitKey<>(id, type);
        KEYS.put(id, key);
        return key;
    }

    public static synchronized @Nullable TraitKey<?> get(ResourceLocation id) {
        return KEYS.get(id);
    }

    public static synchronized Collection<TraitKey<?>> all() {
        return Collections.unmodifiableCollection(KEYS.values());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Empiric.MODID, path);
    }
}
