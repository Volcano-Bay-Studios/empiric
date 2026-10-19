package xyz.volcanobay.empiric.api.ui.surface;

import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.menu.SlotActions;
import xyz.volcanobay.empiric.api.ui.menu.WorldSlots;
import xyz.volcanobay.empiric.client.ui.UiClient;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceRenderer;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.surface.SurfacePlane;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The surfaces shown in the local player's world.
 */
public final class UiSurfaces {

    private UiSurfaces() {
    }

    /**
     * Shows a screen's widgets on a plane. The module has to be on the client already, which synced modules are. Its
     * slots work with the held item, as {@link WorldSlots} describes.
     */
    public static <M extends Module<?>> WorldSurface show(UiScreenType<M> screen, M module, Supplier<SurfacePlane> plane) {
        return show(screen, screen.create(module), module, plane);
    }

    /**
     * Shows content already built from a screen, such as one whose plane needs the content's measured size.
     */
    public static <M extends Module<?>> WorldSurface show(UiScreenType<M> screen, Widget content, M module, Supplier<SurfacePlane> plane) {
        SlotActions slots = (slot, button) -> UiClient.slotClick(screen, module, slot, button);
        WorldSurface surface = new WorldSurface(screen, content, module, plane, Map.of(SlotActions.class, slots));
        SurfaceRenderer.add(surface);
        return surface;
    }

    public static WorldSurface show(Widget content, Module<?> source, Supplier<SurfacePlane> plane) {
        WorldSurface surface = new WorldSurface(content, source, plane);
        SurfaceRenderer.add(surface);
        return surface;
    }

    public static List<WorldSurface> all() {
        return SurfaceRenderer.surfaces();
    }

    public static void clear() {
        SurfaceRenderer.clear();
    }
}
