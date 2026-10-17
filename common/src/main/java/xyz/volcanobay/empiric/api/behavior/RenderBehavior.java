package xyz.volcanobay.empiric.api.behavior;

import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.core.module.Behavior;

public interface RenderBehavior extends Behavior {

    void render(Context context);

    record Context(VeilRenderLevelStageEvent.Stage stage,
                   LevelRenderer levelRenderer,
                   MultiBufferSource.BufferSource bufferSource,
                   MatrixStack matrixStack,
                   Matrix4fc frustumMatrix,
                   Matrix4fc projectionMatrix,
                   int renderTick,
                   DeltaTracker deltaTracker,
                   Camera camera,
                   Frustum frustum) {
    }
}
