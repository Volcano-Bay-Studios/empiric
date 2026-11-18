package xyz.volcanobay.empiric.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.volcanobay.empiric.system.McSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class BlockEntityGuardMixin {

    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void empiric$guardSetChanged(CallbackInfo ci) {
        McSystems.guard("BlockEntity.setChanged");
    }
}
