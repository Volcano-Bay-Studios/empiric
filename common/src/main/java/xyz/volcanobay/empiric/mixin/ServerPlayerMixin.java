package xyz.volcanobay.empiric.mixin;

import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.system.EntityDropSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void empiric$restoreFrom(ServerPlayer previous, boolean keepEverything, CallbackInfo ci) {
        EntityDropSystem.restored((ServerPlayer) (Object) this, previous, keepEverything);
    }
}
