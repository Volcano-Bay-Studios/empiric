package xyz.volcanobay.empiric.platform.veil;

import foundry.veil.Veil;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import xyz.volcanobay.empiric.EmpiricClient;
import xyz.volcanobay.empiric.api.behavior.RenderBehavior;
import xyz.volcanobay.empiric.client.editor.EmpiricInspector;
import xyz.volcanobay.empiric.client.editor.layout.LayoutEditor;

public final class VeilClientHooks {

    private static final RenderBehavior.Stage[] STAGES = stages();

    private VeilClientHooks() {
    }

    public static void register() {
        VeilEventPlatform.INSTANCE.onVeilRendererAvailable(renderer -> {
            if (Veil.IMGUIMC) {
                renderer.getEditorManager().add(new EmpiricInspector());
                // Kept while the widget-based editor (/empiric ui editor) settles in.
                renderer.getEditorManager().add(new LayoutEditor());
            }
        });
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage((stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum) ->
                EmpiricClient.renderStage(new RenderBehavior.Context(STAGES[stage.ordinal()], levelRenderer, bufferSource, matrixStack.toPoseStack(),
                        frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum)));
    }

    private static RenderBehavior.Stage[] stages() {
        VeilRenderLevelStageEvent.Stage[] veil = VeilRenderLevelStageEvent.Stage.values();
        RenderBehavior.Stage[] stages = new RenderBehavior.Stage[veil.length];
        for (VeilRenderLevelStageEvent.Stage stage : veil) {
            stages[stage.ordinal()] = RenderBehavior.Stage.valueOf(stage.name());
        }
        return stages;
    }
}
