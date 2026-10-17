package xyz.volcanobay.empiric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import xyz.volcanobay.empiric.system.WorldModuleSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    private void empiric$levelChanged(ClientLevel level, CallbackInfo ci) {
        WorldModuleSystem.clientLevelChanged(level);
    }
}
