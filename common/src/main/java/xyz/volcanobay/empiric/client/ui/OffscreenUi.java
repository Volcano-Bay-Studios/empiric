package xyz.volcanobay.empiric.client.ui;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import xyz.volcanobay.empiric.core.ui.UiFont;

import java.util.function.Consumer;

/**
 * Draws widgets into a texture instead of the window, for world surfaces and the layout editor's preview. Run it
 * outside level rendering, since Veil can redirect render output while the level draws. Leaves the main target bound.
 */
public final class OffscreenUi {

    private OffscreenUi() {
    }

    /**
     * Clears the target and draws into it with a canvas {@code uiWidth} by {@code uiHeight} widget pixels across.
     */
    public static void draw(TextureTarget target, UiFont font, int uiWidth, int uiHeight, Consumer<GuiCanvas> draw) {
        Minecraft minecraft = Minecraft.getInstance();
        // Clearing needs the depth mask on, and it unbinds the target when done, so bind it again after.
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, uiWidth, uiHeight, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        try {
            GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            GuiCanvas canvas = GuiCanvas.offscreen(graphics, font, minecraft.font, uiWidth, uiHeight, target.width, target.height);
            draw.accept(canvas);
            canvas.finish();
            graphics.flush();
        } finally {
            RenderSystem.disableScissor();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }
}
