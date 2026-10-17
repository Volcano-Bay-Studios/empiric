package xyz.volcanobay.empiric.api.behavior.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ThirdPersonContext {

    private final LivingEntity entity;
    private final HumanoidModel<?> model;
    private final InteractionHand hand;
    private final HumanoidArm arm;
    private final ItemStack stack;
    private final float ageInTicks;

    public ThirdPersonContext(LivingEntity entity, HumanoidModel<?> model, InteractionHand hand, ItemStack stack, float ageInTicks) {
        this.entity = entity;
        this.model = model;
        this.hand = hand;
        this.arm = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        this.stack = stack;
        this.ageInTicks = ageInTicks;
    }

    public LivingEntity entity() {
        return entity;
    }

    public HumanoidModel<?> model() {
        return model;
    }

    public InteractionHand hand() {
        return hand;
    }

    public HumanoidArm arm() {
        return arm;
    }

    public int side() {
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    public ModelPart armPart() {
        return arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    }

    public ModelPart otherArmPart() {
        return arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
    }

    public ModelPart head() {
        return model.head;
    }

    public ItemStack stack() {
        return stack;
    }

    public float ageInTicks() {
        return ageInTicks;
    }
}
