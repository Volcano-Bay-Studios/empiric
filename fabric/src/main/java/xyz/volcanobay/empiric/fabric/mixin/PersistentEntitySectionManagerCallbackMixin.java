package xyz.volcanobay.empiric.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

// Fabric's unload event skips entities in hidden sections, and entityUnloaded does nothing for an inactive entity
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public class PersistentEntitySectionManagerCallbackMixin {

    @Shadow
    @Final
    private EntityAccess entity;

    @Inject(method = "onRemove", at = @At("TAIL"))
    private void empiric$entityRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if (entity instanceof Entity removed) {
            WorldModuleSystem.entityUnloaded(removed);
        }
    }
}
