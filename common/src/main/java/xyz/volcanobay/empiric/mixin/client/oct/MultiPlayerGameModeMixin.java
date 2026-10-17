package xyz.volcanobay.empiric.mixin.client.oct;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.oct.FullOctBlock;
import xyz.volcanobay.empiric.system.OctMultiblockSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void empiric$destroyOct(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.getBlock() instanceof FullOctBlock) {
            BlockPos center = OctMultiblockSystem.center(minecraft.level, pos, minecraft.player);
            if (center != null) {
                cir.setReturnValue(((MultiPlayerGameMode) (Object) this).destroyBlock(center));
                return;
            }
            cir.setReturnValue(FullOctBlock.destroyByPlayer(minecraft.level, pos, minecraft.player));
        }
    }
}
