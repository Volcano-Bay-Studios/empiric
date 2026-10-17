package xyz.volcanobay.empiric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import xyz.volcanobay.empiric.system.EntityDropSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "respawn", at = @At("HEAD"))
    private void empiric$respawn(ServerPlayer player, boolean keepEverything, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir) {
        EntityDropSystem.respawning(player);
    }
}
