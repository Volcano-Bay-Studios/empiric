package xyz.volcanobay.empiric.mixin.client.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.behavior.ItemRenderBehavior;
import xyz.volcanobay.empiric.api.behavior.item.ThirdPersonContext;
import xyz.volcanobay.empiric.client.ItemModuleCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void empiric$poseArms(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            List<ItemRenderBehavior> renderers = ItemModuleCache.renderers(stack);
            if (renderers.isEmpty()) {
                continue;
            }
            ThirdPersonContext context = new ThirdPersonContext(entity, (HumanoidModel<?>) (Object) this, hand, stack, ageInTicks);
            for (ItemRenderBehavior renderer : renderers) {
                renderer.poseThirdPerson(context);
            }
        }
    }
}
