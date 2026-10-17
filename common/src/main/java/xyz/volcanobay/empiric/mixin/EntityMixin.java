package xyz.volcanobay.empiric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.ext.EntityExtension;
import xyz.volcanobay.empiric.system.EntityDropSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements EntityExtension {

    @Unique
    private EntityAttachment empiric$attachment;

    @Override
    public EntityAttachment empiric$attachment() {
        if (empiric$attachment == null) {
            empiric$attachment = new EntityAttachment((Entity) (Object) this);
        }
        return empiric$attachment;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void empiric$save(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        WorldModuleSystem.save(empiric$attachment(), cir.getReturnValue());
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void empiric$load(CompoundTag compound, CallbackInfo ci) {
        WorldModuleSystem.read(empiric$attachment(), compound);
    }

    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void empiric$beginRestore(Entity entity, CallbackInfo ci) {
        EntityDropSystem.transferring(entity);
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void empiric$endRestore(Entity entity, CallbackInfo ci) {
        EntityDropSystem.transferring(null);
    }
}
