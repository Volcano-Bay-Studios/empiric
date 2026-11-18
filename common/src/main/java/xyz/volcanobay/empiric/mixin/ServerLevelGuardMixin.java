package xyz.volcanobay.empiric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import xyz.volcanobay.empiric.system.McSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerLevelGuardMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void empiric$guardAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        McSystems.guard("ServerLevel.addFreshEntity");
    }
}
