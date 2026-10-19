package xyz.volcanobay.empiric.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import xyz.volcanobay.empiric.api.ui.ItemCanvas;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.UiFont;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws widgets with {@link GuiGraphics}. Every draw pushes the current depth.
 */
public final class GuiCanvas implements UiCanvas, ItemCanvas {

    /**
     * Vanilla draws shadowed text 0.03 forward, so each layer steps past that.
     */
    private static final float LAYER = 0.05f;
    /**
     * Items render up to about 250 forward. Vanilla tooltips sit at 400, above popups.
     */
    private static final float OVERLAY = 300;

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private final GuiGraphics graphics;
    private final UiFont font;
    private final Font minecraftFont;
    private VertexConsumer quads;
    private float depth;
    private final Deque<int[]> clips = new ArrayDeque<>();
    private final float pixelsPerUnitX;
    private final float pixelsPerUnitY;
    private final int targetHeight;
    private final boolean offscreen;

    public GuiCanvas(GuiGraphics graphics, UiFont font, Font minecraftFont) {
        this(graphics, font, minecraftFont, false, 1, 1, 0);
    }

    /**
     * Draws into a texture rather than the window. Clipping can't use {@link GuiGraphics}'s scissor, which maps through
     * the window's size and scale, so it is applied in the texture's own pixels.
     *
     * @param uiWidth       the widget space the texture covers
     * @param uiHeight      the widget space the texture covers
     * @param targetWidth   the texture's size in pixels
     * @param targetHeight  the texture's size in pixels
     */
    public static GuiCanvas offscreen(GuiGraphics graphics, UiFont font, Font minecraftFont, int uiWidth, int uiHeight, int targetWidth, int targetHeight) {
        return new GuiCanvas(graphics, font, minecraftFont, true, (float) targetWidth / uiWidth, (float) targetHeight / uiHeight, targetHeight);
    }

    private GuiCanvas(GuiGraphics graphics, UiFont font, Font minecraftFont, boolean offscreen, float pixelsPerUnitX, float pixelsPerUnitY, int targetHeight) {
        this.graphics = graphics;
        this.font = font;
        this.minecraftFont = minecraftFont;
        this.offscreen = offscreen;
        this.pixelsPerUnitX = pixelsPerUnitX;
        this.pixelsPerUnitY = pixelsPerUnitY;
        this.targetHeight = targetHeight;
    }

    public GuiGraphics graphics() {
        flushQuads();
        return graphics;
    }

    public float depth() {
        return depth;
    }

    @Override
    public UiFont font() {
        return font;
    }

    @Override
    public void nextLayer() {
        depth += LAYER;
    }

    @Override
    public void overlay() {
        flushQuads();
        depth += OVERLAY;
    }

    private PoseStack raise() {
        flushQuads();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, depth);
        return pose;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        PoseStack pose = raise();
        graphics.fill(x0, y0, x1, y1, color);
        pose.popPose();
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        PoseStack pose = raise();
        graphics.fillGradient(x0, y0, x1, y1, top, bottom);
        pose.popPose();
    }

    @Override
    public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c0, int c1, int c2, int c3) {
        if (quads == null) {
            quads = graphics.bufferSource().getBuffer(RenderType.gui());
        }
        Matrix4f pose = graphics.pose().last().pose();
        float cross = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        quads.addVertex(pose, x0, y0, depth).setColor(c0);
        if (cross > 0) {
            quads.addVertex(pose, x3, y3, depth).setColor(c3);
            quads.addVertex(pose, x2, y2, depth).setColor(c2);
            quads.addVertex(pose, x1, y1, depth).setColor(c1);
        } else {
            quads.addVertex(pose, x1, y1, depth).setColor(c1);
            quads.addVertex(pose, x2, y2, depth).setColor(c2);
            quads.addVertex(pose, x3, y3, depth).setColor(c3);
        }
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        PoseStack pose = raise();
        graphics.drawString(minecraftFont, text, x, y, color, shadow);
        pose.popPose();
    }

    @Override
    public void blit(String texture, int x, int y, int width, int height, float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight, int tint) {
        PoseStack pose = raise();
        ResourceLocation location = TEXTURES.computeIfAbsent(texture, ResourceLocation::parse);
        boolean tinted = tint != 0xFFFFFFFF;
        if (tinted) {
            RenderSystem.setShaderColor((tint >> 16 & 0xFF) / 255f, (tint >> 8 & 0xFF) / 255f, (tint & 0xFF) / 255f, (tint >>> 24) / 255f);
        }
        graphics.blit(location, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        if (tinted) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        pose.popPose();
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        PoseStack pose = raise();
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(minecraftFont, stack, x, y);
        pose.popPose();
    }

    @Override
    public void model(ItemStack stack, float x, float y, float size, float yaw, float pitch) {
        if (stack.isEmpty()) {
            return;
        }
        flushQuads();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, depth + 100 + size);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (!model.usesBlockLight()) {
            Lighting.setupForFlatItems();
        }
        minecraft.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        graphics.flush();
        if (!model.usesBlockLight()) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    @Override
    public void push() {
        graphics.pose().pushPose();
    }

    @Override
    public void pop() {
        graphics.pose().popPose();
    }

    @Override
    public void translate(float x, float y) {
        graphics.pose().translate(x, y, 0);
    }

    @Override
    public void scale(float x, float y) {
        graphics.pose().scale(x, y, 1);
    }

    @Override
    public void rotate(float radians) {
        graphics.pose().mulPose(Axis.ZP.rotation(radians));
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        flushQuads();
        Matrix4f pose = graphics.pose().last().pose();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 4; i++) {
            pose.transformPosition(i % 2 == 0 ? x : x + width, i < 2 ? y : y + height, 0, corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }
        int x0 = (int) Math.floor(minX);
        int y0 = (int) Math.floor(minY);
        int x1 = (int) Math.ceil(maxX);
        int y1 = (int) Math.ceil(maxY);
        if (!offscreen) {
            graphics.enableScissor(x0, y0, x1, y1);
            return;
        }
        int[] top = clips.peek();
        if (top != null) {
            x0 = Math.max(x0, top[0]);
            y0 = Math.max(y0, top[1]);
            x1 = Math.min(x1, top[2]);
            y1 = Math.min(y1, top[3]);
        }
        clips.push(new int[]{x0, y0, x1, y1});
        applyScissor(clips.peek());
    }

    private void applyScissor(int[] clip) {
        graphics.flush();
        if (clip == null) {
            RenderSystem.disableScissor();
            return;
        }
        int left = Math.round(clip[0] * pixelsPerUnitX);
        int right = Math.round(clip[2] * pixelsPerUnitX);
        int top = Math.round(clip[1] * pixelsPerUnitY);
        int bottom = Math.round(clip[3] * pixelsPerUnitY);
        RenderSystem.enableScissor(left, targetHeight - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    @Override
    public void popClip() {
        flushQuads();
        if (!offscreen) {
            graphics.disableScissor();
            return;
        }
        clips.pop();
        applyScissor(clips.peek());
    }

    public void finish() {
        flushQuads();
    }

    private void flushQuads() {
        if (quads != null) {
            quads = null;
            graphics.flush();
        }
    }
}
