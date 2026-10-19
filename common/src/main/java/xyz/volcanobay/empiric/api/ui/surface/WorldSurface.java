package xyz.volcanobay.empiric.api.ui.surface;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.client.ui.ClientUiHost;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.surface.SurfacePlane;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A widget tree drawn onto a rectangle in the world. The plane is asked for every frame, so it can follow a block, an
 * entity, an item in hand or a moving sublevel. It's drawn to a texture owned by the surface, and a pointer ray that
 * crosses the plane becomes the cursor, so the widgets behave exactly as they do on a screen.
 */
public final class WorldSurface {

    private static final int MAX_TEXTURE = 2048;

    private final UiScreenType<?> screen;
    private final UiRoot root;
    private final Supplier<SurfacePlane> plane;
    private final ClientUiHost host;
    private BooleanSupplier valid = () -> true;
    private double renderDistance = 24;
    private double reach = 6;
    private int density = 2;
    private boolean interactive = true;
    private boolean removed;

    private TextureTarget target;
    private long lastRender;

    public WorldSurface(Widget content, Module<?> source, Supplier<SurfacePlane> plane) {
        this(null, content, source, plane, Map.of());
    }

    /**
     * @param screen   the screen the content was built from, or null for loose widgets. Slot clicks and presence cursors
     *                 go through the server by screen, so only surfaces with one get them.
     * @param services root services for the widgets, as {@link UiRoot} takes them
     */
    public WorldSurface(UiScreenType<?> screen, Widget content, Module<?> source, Supplier<SurfacePlane> plane, Map<Class<?>, Object> services) {
        this.screen = screen;
        this.plane = plane;
        this.host = new ClientUiHost(Minecraft.getInstance().font, this::remove);
        this.root = new UiRoot(content, source, host, services);
        SurfacePlane initial = plane.get();
        this.root.resize(initial.pixelWidth(), initial.pixelHeight());
    }

    public UiScreenType<?> screen() {
        return screen;
    }

    public UiRoot root() {
        return root;
    }

    public ClientUiHost host() {
        return host;
    }

    public SurfacePlane plane() {
        return plane.get();
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public double renderDistance() {
        return renderDistance;
    }

    public double reach() {
        return reach;
    }

    /**
     * The surface is dropped as soon as this returns false, for example when its block is gone.
     */
    public WorldSurface validWhile(BooleanSupplier valid) {
        this.valid = valid;
        return this;
    }

    /**
     * How far away it still draws, in blocks.
     */
    public WorldSurface renderDistance(double blocks) {
        this.renderDistance = blocks;
        return this;
    }

    /**
     * How far away it can be pointed at, in blocks.
     */
    public WorldSurface reach(double blocks) {
        this.reach = blocks;
        return this;
    }

    /**
     * Texture pixels per widget pixel. Higher is sharper text and costs more memory.
     */
    public WorldSurface density(int density) {
        this.density = Math.max(1, density);
        return this;
    }

    /**
     * A display-only surface ignores the pointer.
     */
    public WorldSurface interactive(boolean interactive) {
        this.interactive = interactive;
        return this;
    }

    public void remove() {
        removed = true;
    }

    public boolean stillValid() {
        return !removed && valid.getAsBoolean() && root.source().isAttached();
    }

    public void tick() {
        SurfacePlane current = plane.get();
        if (current.pixelWidth() != root.width() || current.pixelHeight() != root.height()) {
            root.resize(current.pixelWidth(), current.pixelHeight());
        }
        root.tick();
    }

    /**
     * The texture the surface was last drawn into, sized to the plane. Null until it has been drawn.
     */
    public TextureTarget target() {
        return target;
    }

    public TextureTarget ensureTarget(SurfacePlane current) {
        int width = current.pixelWidth() * density;
        int height = current.pixelHeight() * density;
        double shrink = Math.min(1, (double) MAX_TEXTURE / Math.max(width, height));
        width = Math.max(1, (int) (width * shrink));
        height = Math.max(1, (int) (height * shrink));
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        return target;
    }

    public long lastRender() {
        return lastRender;
    }

    public void drawn(long now) {
        lastRender = now;
    }

    public void dispose() {
        removed = true;
        root.dispose();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
