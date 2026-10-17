package xyz.volcanobay.empiric.mixin.client.oct;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import xyz.volcanobay.empiric.oct.FullOctBlock;
import xyz.volcanobay.empiric.oct.OctClientEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    protected ClientLevel level;

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void empiric$crackOct(BlockPos pos, Direction side, CallbackInfo ci) {
        if (level.getBlockState(pos).getBlock() instanceof FullOctBlock && OctClientEffects.addHitEffects(level, pos, side, (ParticleEngine) (Object) this)) {
            ci.cancel();
        }
    }
}
