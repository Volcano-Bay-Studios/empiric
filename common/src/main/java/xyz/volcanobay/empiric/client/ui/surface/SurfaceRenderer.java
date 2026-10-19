package xyz.volcanobay.empiric.client.ui.surface;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.api.ui.surface.WorldSurface;
import xyz.volcanobay.empiric.client.ui.GuiCanvas;
import xyz.volcanobay.empiric.client.ui.OffscreenUi;
import xyz.volcanobay.empiric.client.ui.PresenceCursors;
import xyz.volcanobay.empiric.core.ui.surface.SurfacePlane;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws every {@link WorldSurface}: widgets go to the surface's own texture through the same {@link GuiCanvas} a screen
 * uses, and that texture is drawn as a quad in the world, hidden by blocks like any other geometry. Surfaces nobody is
 * pointing at only redraw a few times a second, and only a few redraw in any one frame.
 */
public final class SurfaceRenderer {

    private static final long IDLE_REDRAW_MILLIS = 100;
    private static final int REDRAWS_PER_FRAME = 4;

    private static final List<WorldSurface> SURFACES = new ArrayList<>();
    private static Level level;

    private SurfaceRenderer() {
    }

    public static void add(WorldSurface surface) {
        SURFACES.add(surface);
    }

    public static List<WorldSurface> surfaces() {
        return List.copyOf(SURFACES);
    }

    public static void clear() {
        SURFACES.forEach(WorldSurface::dispose);
        SURFACES.clear();
        SurfaceInput.reset();
    }

    public static void clientTick() {
        Level current = Minecraft.getInstance().level;
        if (current != level) {
            level = current;
            clear();
        }
        Iterator<WorldSurface> iterator = SURFACES.iterator();
        while (iterator.hasNext()) {
            WorldSurface surface = iterator.next();
            if (!surface.stillValid()) {
                surface.dispose();
                iterator.remove();
                continue;
            }
            surface.tick();
        }
        SurfaceInput.update();
    }

    /**
     * Redraws surface textures at the start of a frame, outside level rendering. The world pass only draws the finished quads.
     */
    public static void prepare() {
        Minecraft minecraft = Minecraft.getInstance();
        if (SURFACES.isEmpty() || minecraft.level == null) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        long now = Util.getMillis();
        int redraws = 0;
        try {
            for (WorldSurface surface : SURFACES) {
                if (!visibleFrom(surface.plane(), surface, eye)) {
                    continue;
                }
                boolean fresh = surface.target() == null;
                boolean due = SurfaceInput.isTarget(surface) || PresenceCursors.any(surface.screen(), surface.root().source())
                        || now - surface.lastRender() >= IDLE_REDRAW_MILLIS;
                if (fresh || (due && redraws < REDRAWS_PER_FRAME)) {
                    redraw(minecraft, surface);
                    surface.drawn(now);
                    redraws++;
                }
            }
        } finally {
            main.bindWrite(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    public static void render(VeilRenderLevelStageEvent.Stage stage, Matrix4fc view, Matrix4fc projection, Camera camera) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL || SURFACES.isEmpty()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        SurfaceInput.update();
        Vec3 eye = camera.getPosition();
        try {
            for (WorldSurface surface : SURFACES) {
                if (surface.target() != null && visibleFrom(surface.plane(), surface, eye)) {
                    drawQuad(surface, view, projection, eye);
                }
            }
        } finally {
            main.bindWrite(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private static boolean visibleFrom(SurfacePlane plane, WorldSurface surface, Vec3 eye) {
        if (!plane.facesPoint(eye.x, eye.y, eye.z)) {
            return false;
        }
        double[] centre = plane.toWorld(plane.pixelWidth() / 2.0, plane.pixelHeight() / 2.0);
        double distance = eye.distanceToSqr(centre[0], centre[1], centre[2]);
        return distance <= surface.renderDistance() * surface.renderDistance();
    }

    private static void redraw(Minecraft minecraft, WorldSurface surface) {
        SurfacePlane plane = surface.plane();
        TextureTarget target = surface.ensureTarget(plane);
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        OffscreenUi.draw(target, surface.host().font(), plane.pixelWidth(), plane.pixelHeight(), canvas -> {
            surface.root().render(canvas, partialTick);
            PresenceCursors.render(canvas, surface.screen(), surface.root().source(), surface.root());
        });
    }

    private static void drawQuad(WorldSurface surface, Matrix4fc view, Matrix4fc projection, Vec3 eye) {
        SurfacePlane plane = surface.plane();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1, -10);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, surface.target().getColorTextureId());
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(projection), VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        double[] topLeft = plane.toWorld(0, 0);
        double[] topRight = plane.toWorld(plane.pixelWidth(), 0);
        double[] bottomRight = plane.toWorld(plane.pixelWidth(), plane.pixelHeight());
        double[] bottomLeft = plane.toWorld(0, plane.pixelHeight());
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        corner(builder, topLeft, eye, 0, 1);
        corner(builder, bottomLeft, eye, 0, 0);
        corner(builder, bottomRight, eye, 1, 0);
        corner(builder, topRight, eye, 1, 1);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
    }

    private static void corner(BufferBuilder builder, double[] world, Vec3 eye, float u, float v) {
        builder.addVertex((float) (world[0] - eye.x), (float) (world[1] - eye.y), (float) (world[2] - eye.z)).setUv(u, v).setColor(1f, 1f, 1f, 1f);
    }
}
