package xyz.volcanobay.empiric.mixin.oct;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelChunk.class)
public abstract class LevelChunkOctMixin {
    // If this fails to redirect, all oct block entities are going to be ticked,
    // which is not optimal but in case of some compatibility issues it guarantees
    // that functionality is preserved.
    @Redirect(
            method = "updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getTicker(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Lnet/minecraft/world/level/block/entity/BlockEntityTicker;"),
            require = 0)
    private <T extends BlockEntity> BlockEntityTicker<T> modifyTicker(BlockState instance, Level level, BlockEntityType<T> blockEntityType, @Local(argsOnly = true) T blockEntity) {
        if(blockEntity instanceof FullOctBlockEntity obe && !obe.isTicked()) {
            return null;
        }
        return instance.getTicker(level, blockEntityType);
    }
}
