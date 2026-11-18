package xyz.volcanobay.empiric.api.ui;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintView;
import xyz.volcanobay.empiric.core.ui.bind.UiException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class UiScreens {

    private static final Map<ResourceLocation, UiScreenType<?>> SCREENS = new LinkedHashMap<>();

    private UiScreens() {
    }

    /**
     * Registers a screen on both sides. The module type must sync, so its class has to be a {@link NetworkBehavior}.
     */
    public static <M extends Module<?>> UiScreenType<M> register(ResourceLocation id, ModuleType<M, ?> type, Function<M, ? extends Widget> factory) {
        return register(id, define(type, factory));
    }

    /**
     * Registers a screen made entirely by a layout file, whose bindings and actions do the work.
     */
    public static <M extends Module<?>> UiScreenType<M> registerLayout(ResourceLocation id, ModuleType<M, ?> type, String layout) {
        return register(id, defineLayout(type, layout));
    }

    /**
     * Makes a screen without an id, for {@code @Register} to give one. Registering it names it.
     */
    public static <M extends Module<?>> UiScreenType<M> define(ModuleType<M, ?> type, Function<M, ? extends Widget> factory) {
        return new UiScreenType<>(null, type, factory);
    }

    public static <M extends Module<?>> UiScreenType<M> defineLayout(ModuleType<M, ?> type, String layout) {
        return define(type, module -> new BlueprintView(layout));
    }

    /**
     * Registers a screen made by {@link #define} under an id.
     */
    public static synchronized <M extends Module<?>> UiScreenType<M> register(ResourceLocation id, UiScreenType<M> screen) {
        ModuleType<M, ?> type = screen.moduleType();
        if (!NetworkBehavior.class.isAssignableFrom(type.moduleClass())) {
            throw new UiException("Screen " + id + " is for " + type.id() + ", which doesn't sync because it isn't a NetworkBehavior");
        }
        if (screen.id() != null) {
            throw new UiException("Screen " + id + " is already registered as " + screen.id());
        }
        if (SCREENS.containsKey(id)) {
            throw new UiException("Screen " + id + " is already registered");
        }
        screen.assignId(id);
        SCREENS.put(id, screen);
        return screen;
    }

    public static UiScreenType<?> get(ResourceLocation id) {
        return SCREENS.get(id);
    }

    public static Collection<UiScreenType<?>> all() {
        return Collections.unmodifiableCollection(SCREENS.values());
    }
}
