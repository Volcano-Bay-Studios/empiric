package xyz.volcanobay.empiric.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void empiric$renderTooltip(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        TooltipSystem.renderHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
