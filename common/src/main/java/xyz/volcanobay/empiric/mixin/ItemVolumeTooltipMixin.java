package xyz.volcanobay.empiric.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import xyz.volcanobay.empiric.api.atmosphere.VolumeHelper;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;
import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(Item.class)
public abstract class ItemVolumeTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void empiric$appendVolume(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag, CallbackInfo ci) {
        ItemVolume itemVolume = VolumeHelper.getItemVolume(stack);

        if (itemVolume != null) {
            Style detailStyle = Style.EMPTY.withColor(0xAAAAAA);
            Map<Chemical, ChemicalComponent> chemicalComponentsList = itemVolume.getChemicalComponentsList();
            boolean isEmpty = true;
            for (ChemicalComponent value : chemicalComponentsList.values()) {
                String name = value.chemical.getName();
                ResourceLocation location = ResourceLocation.parse(name);
                if (value.getAmount(Volume.State.GAS) > 0) {
                    isEmpty = false;
                    tooltipComponents.add(Component.literal(value.getDisplayAmount(Volume.State.GAS)+" moles of "+Component.translatable("chemical."+location.getNamespace()+"."+location.getPath()).getString()).withStyle(Style.EMPTY.withColor(value.chemical.getColor())));
                }
            }
            if (isEmpty) {
                tooltipComponents.add(Component.literal("It is empty.").withStyle(detailStyle));
            } else {
                tooltipComponents.add(1,Component.literal("It is filled with").withStyle(detailStyle));
            }
        }
    }
}
