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
    public static synchronized <M extends Module<?>> UiScreenType<M> register(ResourceLocation id, ModuleType<M, ?> type, Function<M, ? extends Widget> factory) {
        if (!NetworkBehavior.class.isAssignableFrom(type.moduleClass())) {
            throw new UiException("Screen " + id + " is for " + type.id() + ", which doesn't sync because it isn't a NetworkBehavior");
        }
        if (SCREENS.containsKey(id)) {
            throw new UiException("Screen " + id + " is already registered");
        }
        UiScreenType<M> screen = new UiScreenType<>(id, type, factory);
        SCREENS.put(id, screen);
        return screen;
    }

    /**
     * Registers a screen made entirely by a layout file, whose bindings and actions do the work.
     */
    public static <M extends Module<?>> UiScreenType<M> registerLayout(ResourceLocation id, ModuleType<M, ?> type, String layout) {
        return register(id, type, module -> new BlueprintView(layout));
    }

    public static UiScreenType<?> get(ResourceLocation id) {
        return SCREENS.get(id);
    }

    public static Collection<UiScreenType<?>> all() {
        return Collections.unmodifiableCollection(SCREENS.values());
    }
}
