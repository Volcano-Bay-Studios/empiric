package xyz.volcanobay.empiric.api.behavior;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import xyz.volcanobay.empiric.api.behavior.item.FirstPersonContext;
import xyz.volcanobay.empiric.api.behavior.item.ItemRenderContext;
import xyz.volcanobay.empiric.api.behavior.item.ThirdPersonContext;
import xyz.volcanobay.empiric.core.module.Behavior;

public interface ItemRenderBehavior extends Behavior {

    default void renderItem(ItemRenderContext context) {
    }

    default void renderFirstPerson(FirstPersonContext context) {
    }

    default void poseThirdPerson(ThirdPersonContext context) {
    }

    default HumanoidModel.ArmPose armPose(AbstractClientPlayer player, InteractionHand hand) {
        return null;
    }
}
