package xyz.volcanobay.empiric;

import foundry.veil.Veil;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.api.behavior.ClientTickBehavior;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.api.behavior.RenderBehavior;
import xyz.volcanobay.empiric.client.editor.EmpiricInspector;
import xyz.volcanobay.empiric.client.overlay.OverlayRenderer;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;

public class EmpiricClient {

    public static void init() {
        VeilEventPlatform.INSTANCE.onVeilRendererAvailable(renderer -> {
            if (Veil.IMGUIMC) {
                renderer.getEditorManager().add(new EmpiricInspector());
            }
        });
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage((stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum) -> {
            OverlayRenderer.render(stage, frustumMatrix, projectionMatrix, camera);
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
        modules.forEach(Side.CLIENT, EmpiricBehaviors.CLIENT_TICK, ClientTickBehavior::clientTick);
        ModuleSyncSystem.flushClient();
    }
}
