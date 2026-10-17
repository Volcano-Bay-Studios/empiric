package xyz.volcanobay.empiric.api.behavior.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemRenderContext {

    private final ItemStack stack;
    private final ItemDisplayContext displayContext;
    private final boolean leftHand;
    private final PoseStack poseStack;
    private final MultiBufferSource buffers;
    private final int light;
    private final int overlay;
    private final BakedModel model;
    private boolean cancelled;

    public ItemRenderContext(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, BakedModel model) {
        this.stack = stack;
        this.displayContext = displayContext;
        this.leftHand = leftHand;
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.light = light;
        this.overlay = overlay;
        this.model = model;
    }

    public ItemStack stack() {
        return stack;
    }

    public ItemDisplayContext displayContext() {
        return displayContext;
    }

    public boolean leftHand() {
        return leftHand;
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

    public int overlay() {
        return overlay;
    }

    public BakedModel model() {
        return model;
    }

    public boolean isGui() {
        return displayContext == ItemDisplayContext.GUI;
    }

    public boolean isFirstPerson() {
        return displayContext.firstPerson();
    }

    public boolean isThirdPerson() {
        return displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    public boolean isHeld() {
        return isFirstPerson() || isThirdPerson();
    }

    public boolean isOnEntity() {
        return isThirdPerson() || displayContext == ItemDisplayContext.HEAD || displayContext == ItemDisplayContext.GROUND;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
