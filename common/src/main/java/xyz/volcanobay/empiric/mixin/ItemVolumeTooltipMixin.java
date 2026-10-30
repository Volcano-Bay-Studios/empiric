package xyz.volcanobay.empiric.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import xyz.volcanobay.empiric.api.atmosphere.VolumeHelper;
import xyz.volcanobay.empiric.api.atmosphere.VolumeTooltip;
import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Item.class)
public abstract class ItemVolumeTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void empiric$appendVolume(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag, CallbackInfo ci) {
        ItemVolume itemVolume = VolumeHelper.getItemVolume(stack);

        if (itemVolume != null) {
            VolumeTooltip.append(itemVolume, tooltipComponents);
        }
    }
}
