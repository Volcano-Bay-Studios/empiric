package xyz.volcanobay.empiric.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import xyz.volcanobay.empiric.system.McSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheGuardMixin {

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"))
    private void empiric$guardGetChunk(int x, int z, ChunkStatus status, boolean requireChunk, CallbackInfoReturnable<ChunkAccess> cir) {
        McSystems.guard("ServerChunkCache.getChunk");
    }
}
