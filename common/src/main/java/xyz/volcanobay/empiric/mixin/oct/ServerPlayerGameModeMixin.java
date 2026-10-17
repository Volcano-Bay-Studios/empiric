package xyz.volcanobay.empiric.mixin.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.oct.FullOctBlock;
import xyz.volcanobay.empiric.system.OctMultiblockSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void empiric$destroyOct(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FullOctBlock)) {
            return;
        }
        if (player.blockActionRestricted(level, pos, ((ServerPlayerGameMode) (Object) this).getGameModeForPlayer())) {
            cir.setReturnValue(false);
            return;
        }
        BlockPos center = OctMultiblockSystem.center(level, pos, player);
        if (center != null) {
            cir.setReturnValue(((ServerPlayerGameMode) (Object) this).destroyBlock(center));
            return;
        }
        cir.setReturnValue(FullOctBlock.destroyByPlayer(level, pos, player));
    }
}
