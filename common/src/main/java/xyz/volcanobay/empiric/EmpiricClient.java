package xyz.volcanobay.empiric;

import foundry.veil.Veil;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.api.behavior.ClientTickBehavior;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.api.behavior.RenderBehavior;
import xyz.volcanobay.empiric.client.editor.EmpiricInspector;
import xyz.volcanobay.empiric.client.editor.layout.LayoutEditor;
import xyz.volcanobay.empiric.client.overlay.OverlayRenderer;
import xyz.volcanobay.empiric.client.ui.PresenceCursors;
import xyz.volcanobay.empiric.client.ui.UiKeyboard;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceRenderer;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;

public class EmpiricClient {

    @SuppressWarnings("deprecation")
    public static void init() {
        VeilEventPlatform.INSTANCE.onVeilRendererAvailable(renderer -> {
            if (Veil.IMGUIMC) {
                renderer.getEditorManager().add(new EmpiricInspector());
                // Kept while the widget-based editor (/empiric ui editor) settles in.
                renderer.getEditorManager().add(new LayoutEditor());
            }
        });
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage((stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum) -> {
            OverlayRenderer.render(stage, frustumMatrix, projectionMatrix, camera);
            SurfaceRenderer.render(stage, frustumMatrix, projectionMatrix, camera);
            if (stage == VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
                TooltipSystem.captureView(frustumMatrix, projectionMatrix, camera.getPosition());
            }
            ModuleSystem modules = EmpiricCore.modules();
            if (modules.count(Side.CLIENT, EmpiricBehaviors.RENDER) == 0) {
                return;
            }
            RenderBehavior.Context context = new RenderBehavior.Context(stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum);
            modules.forEach(Side.CLIENT, EmpiricBehaviors.RENDER, (RenderBehavior behavior) -> behavior.render(context));
        });
    }

    public static void clientTick() {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        ModuleSystem modules = EmpiricCore.modules();
        modules.processPending(Side.CLIENT);
        OverlayRenderer.clientTick();
        SurfaceRenderer.clientTick();
        TooltipSystem.clientTick();
        UiKeyboard.tick();
        PresenceCursors.tick();
        modules.forEach(Side.CLIENT, EmpiricBehaviors.CLIENT_TICK, ClientTickBehavior::clientTick);
        ModuleSyncSystem.flushClient();
    }
}
