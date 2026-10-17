package xyz.volcanobay.empiric.client.overlay;

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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * Draws debug overlays. Each visible layer is drawn opaque, with its own depth, into an offscreen
 * target and then blended over the frame. Layers that don't draw through blocks start from a copy
 * of the world's depth, so blocks still hide them.
 */
public final class OverlayRenderer {

    private static final List<OverlayLayer> LAYERS = List.of(NetworkOverlay.INSTANCE, TemperatureOverlay.INSTANCE);

    private static TextureTarget target;
    private static Level level;

    private OverlayRenderer() {
    }

    public static void clientTick() {
        Level current = Minecraft.getInstance().level;
        if (current != level) {
            level = current;
            LAYERS.forEach(OverlayLayer::clear);
        }
        if (current != null) {
            LAYERS.forEach(OverlayLayer::tick);
        }
    }

    public static void render(VeilRenderLevelStageEvent.Stage stage, Matrix4fc view, Matrix4fc projection, Camera camera) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL || LAYERS.stream().noneMatch(OverlayLayer::isVisible)) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        try {
            for (OverlayLayer layer : LAYERS) {
                if (layer.isVisible()) {
                    draw(layer, main, view, projection, camera);
                }
            }
        } finally {
            main.bindWrite(true);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private static void draw(OverlayLayer layer, RenderTarget main, Matrix4fc view, Matrix4fc projection, Camera camera) {
        target.clear(Minecraft.ON_OSX);
        if (!layer.throughBlocks()) {
            target.copyDepthFrom(main);
        }
        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
        if (!layer.throughBlocks()) {
            // Cells share planes with the blocks they cover, so pull them slightly towards the camera.
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1, -10);
        }
        layer.render(view, projection, camera.getPosition());
        RenderSystem.disablePolygonOffset();
        main.bindWrite(true);
        composite(layer.opacity());
    }

    private static void composite(float opacity) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(-1, -1, 0).setUv(0, 0).setColor(1, 1, 1, opacity);
        builder.addVertex(1, -1, 0).setUv(1, 0).setColor(1, 1, 1, opacity);
        builder.addVertex(1, 1, 0).setUv(1, 1).setColor(1, 1, 1, opacity);
        builder.addVertex(-1, 1, 0).setUv(0, 1).setColor(1, 1, 1, opacity);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
