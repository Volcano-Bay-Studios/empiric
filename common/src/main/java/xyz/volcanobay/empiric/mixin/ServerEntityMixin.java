package xyz.volcanobay.empiric.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void empiric$addPairing(ServerPlayer player, CallbackInfo ci) {
        ModuleSyncSystem.entityTracked(player, entity);
    }

    @Inject(method = "removePairing", at = @At("TAIL"))
    private void empiric$removePairing(ServerPlayer player, CallbackInfo ci) {
        ModuleSyncSystem.entityUntracked(player, entity);
    }
}
