package xyz.volcanobay.empiric.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void empiric$prepareSurfaces(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            SurfaceRenderer.prepare();
        }
    }
}
