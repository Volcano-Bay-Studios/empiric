package xyz.volcanobay.empiric.api.behavior;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.core.module.Behavior;

public interface RenderBehavior extends Behavior {

    void render(Context context);

    record Context(Stage stage,
                   LevelRenderer levelRenderer,
                   MultiBufferSource.BufferSource bufferSource,
                   PoseStack poseStack,
                   Matrix4fc frustumMatrix,
                   Matrix4fc projectionMatrix,
                   int renderTick,
                   DeltaTracker deltaTracker,
                   Camera camera,
                   Frustum frustum) {
    }

    /**
     * The points in level rendering where {@link #render} is called, in order.
     */
    enum Stage {
        AFTER_SKY,
        AFTER_SOLID_BLOCKS,
        AFTER_CUTOUT_MIPPED_BLOCKS,
        AFTER_CUTOUT_BLOCKS,
        AFTER_ENTITIES,
        AFTER_BLOCK_ENTITIES,
        AFTER_TRANSLUCENT_BLOCKS,
        AFTER_TRIPWIRE_BLOCKS,
        AFTER_PARTICLES,
        AFTER_WEATHER,
        AFTER_LEVEL
    }
}
