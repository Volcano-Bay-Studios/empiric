package xyz.volcanobay.empiric.mixin.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.behavior.ItemRenderBehavior;
import xyz.volcanobay.empiric.api.behavior.item.ItemRenderContext;
import xyz.volcanobay.empiric.client.ItemModuleCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Unique
    private final Deque<Boolean> empiric$pushed = new ArrayDeque<>();

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"), cancellable = true)
    private void empiric$beforeRender(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        List<ItemRenderBehavior> renderers = ItemModuleCache.renderers(stack);
        if (renderers.isEmpty()) {
            empiric$pushed.push(false);
            return;
        }
        poseStack.pushPose();
        ItemRenderContext context = new ItemRenderContext(stack, displayContext, leftHand, poseStack, buffers, light, overlay, model);
        for (ItemRenderBehavior renderer : renderers) {
            renderer.renderItem(context);
        }
        if (context.cancelled()) {
            poseStack.popPose();
            ci.cancel();
            return;
        }
        empiric$pushed.push(true);
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("RETURN"))
    private void empiric$afterRender(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!empiric$pushed.isEmpty() && empiric$pushed.pop()) {
            poseStack.popPose();
        }
    }
}
