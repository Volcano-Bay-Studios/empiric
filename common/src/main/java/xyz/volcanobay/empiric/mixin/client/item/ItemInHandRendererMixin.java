package xyz.volcanobay.empiric.mixin.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.behavior.ItemRenderBehavior;
import xyz.volcanobay.empiric.api.behavior.item.FirstPersonContext;
import xyz.volcanobay.empiric.client.ItemModuleCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Unique
    private final Deque<Boolean> empiric$pushed = new ArrayDeque<>();

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void empiric$beforeArm(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
        List<ItemRenderBehavior> renderers = ItemModuleCache.renderers(stack);
        if (renderers.isEmpty()) {
            empiric$pushed.push(false);
            return;
        }
        poseStack.pushPose();
        FirstPersonContext context = new FirstPersonContext(player, hand, stack, partialTicks, pitch, swingProgress, equippedProgress, poseStack, buffer, combinedLight);
        for (ItemRenderBehavior renderer : renderers) {
            renderer.renderFirstPerson(context);
        }
        if (context.cancelled()) {
            poseStack.popPose();
            ci.cancel();
            return;
        }
        empiric$pushed.push(true);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void empiric$afterArm(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
        if (!empiric$pushed.isEmpty() && empiric$pushed.pop()) {
            poseStack.popPose();
        }
    }
}
