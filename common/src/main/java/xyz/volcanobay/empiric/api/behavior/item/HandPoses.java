package xyz.volcanobay.empiric.api.behavior.item;

import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class HandPoses {

    private HandPoses() {
    }

    public static void translate(FirstPersonContext context, float x, float y, float z) {
        context.poseStack().translate(x * context.side(), y, z);
    }

    public static void rotate(FirstPersonContext context, Axis axis, float degrees) {
        boolean mirrored = axis == Axis.YP || axis == Axis.YN || axis == Axis.ZP || axis == Axis.ZN;
        context.poseStack().mulPose(axis.rotationDegrees(mirrored ? degrees * context.side() : degrees));
    }

    public static void raise(FirstPersonContext context, float amount) {
        context.poseStack().translate(0, amount, 0);
    }

    public static void center(FirstPersonContext context, float amount) {
        context.poseStack().translate(-0.56f * amount * context.side(), 0, 0);
    }

    public static void hideWhileEquipping(FirstPersonContext context) {
        context.poseStack().translate(0, -0.6f * context.equipProgress(), 0);
    }

    public static void scale(ItemRenderContext context, float scale) {
        context.poseStack().scale(scale, scale, scale);
    }

    public static void spin(ItemRenderContext context, float degreesPerTick, float ticks) {
        context.poseStack().mulPose(Axis.YP.rotationDegrees(degreesPerTick * ticks % 360));
    }

    public static void aim(ThirdPersonContext context) {
        ModelPart arm = context.armPart();
        ModelPart head = context.head();
        arm.xRot = head.xRot - Mth.HALF_PI;
        arm.yRot = head.yRot;
        arm.zRot = 0;
    }

    public static void aimBoth(ThirdPersonContext context) {
        ModelPart head = context.head();
        ModelPart arm = context.armPart();
        ModelPart other = context.otherArmPart();
        arm.xRot = head.xRot - Mth.HALF_PI;
        other.xRot = head.xRot - Mth.HALF_PI;
        arm.yRot = head.yRot - 0.1f * context.side();
        other.yRot = head.yRot + 0.4f * context.side();
        arm.zRot = 0;
        other.zRot = 0;
    }

    public static void raise(ThirdPersonContext context, float radians) {
        context.armPart().xRot = -radians;
    }

    public static void hold(ThirdPersonContext context, float xRot, float yRot, float zRot) {
        ModelPart arm = context.armPart();
        arm.xRot = xRot;
        arm.yRot = yRot * context.side();
        arm.zRot = zRot * context.side();
    }

    public static void forward(ThirdPersonContext context) {
        hold(context, -Mth.HALF_PI, 0, 0);
    }

    public static void bob(ThirdPersonContext context, float amount) {
        context.armPart().xRot += Mth.sin(context.ageInTicks() * 0.067f) * amount;
    }
}
