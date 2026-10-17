package xyz.volcanobay.empiric.api.behavior.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public final class FirstPersonContext {

    private final AbstractClientPlayer player;
    private final InteractionHand hand;
    private final HumanoidArm arm;
    private final ItemStack stack;
    private final float partialTick;
    private final float pitch;
    private final float swingProgress;
    private final float equipProgress;
    private final PoseStack poseStack;
    private final MultiBufferSource buffers;
    private final int light;
    private boolean cancelled;

    public FirstPersonContext(AbstractClientPlayer player, InteractionHand hand, ItemStack stack, float partialTick, float pitch, float swingProgress, float equipProgress, PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.player = player;
        this.hand = hand;
        this.arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        this.stack = stack;
        this.partialTick = partialTick;
        this.pitch = pitch;
        this.swingProgress = swingProgress;
        this.equipProgress = equipProgress;
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.light = light;
    }

    public AbstractClientPlayer player() {
        return player;
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

    public ItemStack stack() {
        return stack;
    }

    public float partialTick() {
        return partialTick;
    }

    public float pitch() {
        return pitch;
    }

    public float swingProgress() {
        return swingProgress;
    }

    public float equipProgress() {
        return equipProgress;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    public MultiBufferSource buffers() {
        return buffers;
    }

    public int light() {
        return light;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
