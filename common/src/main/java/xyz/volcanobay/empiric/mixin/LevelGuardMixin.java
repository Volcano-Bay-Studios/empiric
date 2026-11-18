package xyz.volcanobay.empiric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.system.McSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelGuardMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"))
    private void empiric$guardGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        McSystems.guard("Level.getBlockState");
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void empiric$guardSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        McSystems.guard("Level.setBlock");
    }
}
