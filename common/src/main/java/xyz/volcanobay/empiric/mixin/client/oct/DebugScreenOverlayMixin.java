package xyz.volcanobay.empiric.mixin.client.oct;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Shadow
    private HitResult block;

    @Shadow
    protected abstract Level getLevel();

    @Shadow @Final private LocalSampleLogger bandwidthLogger;

    @Shadow protected abstract String getPropertyValueString(Map.Entry<Property<?>, Comparable<?>> entry);

    @WrapOperation(method = "getSystemInformation", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    public BlockState getBlockState(ClientLevel instance, BlockPos pos, Operation<BlockState> original, @Local(ordinal = 0)List<String> list) {
        if (block instanceof BlockHitResult result) {
            OctPos octPos = new OctPos(result);
            OctLevel octLevel = LevelOctExtension.get(instance);
            BlockState blockState = octLevel.getBlockState(octPos);
            if (blockState.getBlock() instanceof OctBlock) {
                list.add("");
                list.add(ChatFormatting.UNDERLINE + "Targeted Oct: " + octPos.getLongX() +", " + octPos.getLongY() +", "+ octPos.getLongZ() +" / " + octPos.octant()); list.add(String.valueOf(BuiltInRegistries.BLOCK.getKey(blockState.getBlock())));

                for (Map.Entry<Property<?>, Comparable<?>> entry : blockState.getValues().entrySet()) {
                    list.add(this.getPropertyValueString(entry));
                }

                blockState.getTags().map(p_339277_ -> "#" + p_339277_.location()).forEach(list::add);
            }
        }
        return original.call(instance, pos);
    }
}
