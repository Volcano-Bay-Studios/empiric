package xyz.volcanobay.empiric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceInput;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.system.WorldModuleSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    private void empiric$levelChanged(ClientLevel level, CallbackInfo ci) {
        WorldModuleSystem.clientLevelChanged(level);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void empiric$surfaceAttack(CallbackInfoReturnable<Boolean> cir) {
        if (TooltipSystem.press(Keys.LEFT_BUTTON) || SurfaceInput.press(Keys.LEFT_BUTTON)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void empiric$surfaceUse(CallbackInfo ci) {
        if (TooltipSystem.press(Keys.RIGHT_BUTTON) || SurfaceInput.press(Keys.RIGHT_BUTTON)) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void empiric$surfaceHold(boolean leftClick, CallbackInfo ci) {
        if (leftClick && (TooltipSystem.active() || SurfaceInput.active())) {
            ci.cancel();
        }
    }
}
