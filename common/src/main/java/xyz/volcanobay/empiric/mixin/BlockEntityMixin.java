package xyz.volcanobay.empiric.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.ext.BlockEntityExtension;
import xyz.volcanobay.empiric.system.WorldModuleSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements BlockEntityExtension {

    @Unique
    private BlockEntityAttachment empiric$attachment;

    @Override
    public BlockEntityAttachment empiric$attachment() {
        if (empiric$attachment == null) {
            empiric$attachment = new BlockEntityAttachment((BlockEntity) (Object) this);
        }
        return empiric$attachment;
    }

    @Inject(method = "saveWithFullMetadata", at = @At("RETURN"))
    private void empiric$save(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        WorldModuleSystem.save(empiric$attachment(), cir.getReturnValue());
    }

    @Inject(method = "loadWithComponents", at = @At("TAIL"))
    private void empiric$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        WorldModuleSystem.read(empiric$attachment(), tag);
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void empiric$setLevel(Level level, CallbackInfo ci) {
        WorldModuleSystem.blockEntityLoaded((BlockEntity) (Object) this);
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void empiric$setRemoved(CallbackInfo ci) {
        WorldModuleSystem.blockEntityUnloaded((BlockEntity) (Object) this);
    }
}
