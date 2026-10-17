package xyz.volcanobay.empiric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import xyz.volcanobay.empiric.system.WorldModuleSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void empiric$blockChanged(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old != null) {
            LevelChunk chunk = (LevelChunk) (Object) this;
            WorldModuleSystem.blockChanged(chunk.getLevel(), pos, old, state);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("TAIL"))
    private void empiric$promote(ServerLevel level, ProtoChunk chunk, LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        WorldModuleSystem.promoteChunk(chunk, (LevelChunk) (Object) this);
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void empiric$removeBlockEntity(BlockPos pos, CallbackInfo ci) {
        BlockEntity blockEntity = ((LevelChunk) (Object) this).getBlockEntities().get(pos);
        if (blockEntity != null) {
            WorldModuleSystem.blockEntityDestroyed(blockEntity);
        }
    }
}
