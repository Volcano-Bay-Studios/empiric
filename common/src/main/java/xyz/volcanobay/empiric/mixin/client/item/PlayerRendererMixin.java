package xyz.volcanobay.empiric.mixin.client.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import xyz.volcanobay.empiric.api.behavior.ItemRenderBehavior;
import xyz.volcanobay.empiric.client.ItemModuleCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "getArmPose", at = @At("RETURN"), cancellable = true)
    private static void empiric$armPose(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        for (ItemRenderBehavior renderer : ItemModuleCache.renderers(player.getItemInHand(hand))) {
            HumanoidModel.ArmPose pose = renderer.armPose(player, hand);
            if (pose != null) {
                cir.setReturnValue(pose);
                return;
            }
        }
    }
}
