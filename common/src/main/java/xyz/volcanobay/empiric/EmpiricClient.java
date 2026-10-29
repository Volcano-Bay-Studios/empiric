package xyz.volcanobay.empiric;

import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.api.behavior.ClientTickBehavior;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.api.behavior.RenderBehavior;
import xyz.volcanobay.empiric.client.model.ModelRenderer;
import xyz.volcanobay.empiric.client.overlay.OverlayRenderer;
import xyz.volcanobay.empiric.client.ui.PresenceCursors;
import xyz.volcanobay.empiric.client.ui.UiKeyboard;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceRenderer;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;

public class EmpiricClient {

    public static void init() {
        Platform.initClient();
    }

    /**
     * Called by the platform at each stage of level rendering.
     */
    public static void renderStage(RenderBehavior.Context context) {
        ModelRenderer.renderStage(context.stage(), context.frustumMatrix(), context.projectionMatrix());
        OverlayRenderer.render(context.stage(), context.frustumMatrix(), context.projectionMatrix(), context.camera());
        SurfaceRenderer.render(context.stage(), context.frustumMatrix(), context.projectionMatrix(), context.camera());
        if (context.stage() == RenderBehavior.Stage.AFTER_LEVEL) {
            TooltipSystem.captureView(context.frustumMatrix(), context.projectionMatrix(), context.camera().getPosition());
        }
        ModuleSystem modules = EmpiricCore.modules();
        if (modules.count(Side.CLIENT, EmpiricBehaviors.RENDER) == 0) {
            return;
        }
        modules.forEach(Side.CLIENT, EmpiricBehaviors.RENDER, (RenderBehavior behavior) -> behavior.render(context));
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
