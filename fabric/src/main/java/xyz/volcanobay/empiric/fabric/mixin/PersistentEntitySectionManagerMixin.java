package xyz.volcanobay.empiric.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

// Activates on join like NeoForge, even in hidden sections
@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin {

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void empiric$entityAdded(EntityAccess entity, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof Entity added) {
            WorldModuleSystem.entityLoaded(added);
        }
    }
}
