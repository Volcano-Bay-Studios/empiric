package xyz.volcanobay.empiric.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceInput;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void empiric$surfaceScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset != 0 && (TooltipSystem.scroll(yOffset) || SurfaceInput.scroll(yOffset))) {
            ci.cancel();
        }
    }
}
