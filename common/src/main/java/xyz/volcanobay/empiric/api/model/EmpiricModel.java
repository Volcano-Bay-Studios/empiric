package xyz.volcanobay.empiric.api.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.client.model.ModelRenderer;

import java.util.Set;

/**
 * A model drawn from anywhere you have a pose, such as a block entity renderer. Get one from
 * {@link EmpiricModels#register}.
 * <p>
 * {@code render} only queues the model, and it's drawn after block entities, or after translucent blocks for
 * translucent meshes. Call it every frame. {@code renderNow} draws right away with the current projection, for places
 * outside level rendering such as a GUI. A model that failed to load draws nothing.
 * <p>
 * Tint is ARGB and multiplies the texture.
 */
public final class EmpiricModel {

    public static final int NO_TINT = 0xFFFFFFFF;

    private final ResourceLocation id;
    private final boolean obj;
    private volatile boolean doubleSided;

    EmpiricModel(ResourceLocation id) {
        this.id = id;
        this.obj = id.getPath().endsWith(".obj");
    }

    /**
     * Draws the back of faces too. Backfaces are culled by default, as in Blockbench and for blocks, so only models
     * built from single planes need this. Call it when registering, before the model is first drawn.
     */
    public EmpiricModel doubleSided() {
        doubleSided = true;
        return this;
    }

    public boolean isDoubleSided() {
        return doubleSided;
    }

    public ResourceLocation id() {
        return id;
    }

    public boolean isObj() {
        return obj;
    }

    /**
     * The resource this model is read from, {@code models/<path>.json} or {@code models/<path>} for an OBJ.
     */
    public ResourceLocation file() {
        return id.withPath(path -> "models/" + path + (obj ? "" : ".json"));
    }

    /**
     * A named piece of this model. In an OBJ that's a group or object, and in a JSON model a Blockbench group or element name.
     * The name isn't checked until the model is drawn, and an unknown name draws nothing.
     */
    public ModelPart part(String name) {
        return new ModelPart(this, name);
    }

    /**
     * The names of this model's parts, once it has been loaded. Loading happens the first time it's drawn, and
     * this triggers it on the render thread.
     */
    public Set<String> partNames() {
        return ModelRenderer.partNames(this);
    }

    public void render(PoseStack pose, int light, int overlay) {
        render(pose, light, overlay, NO_TINT);
    }

    public void render(PoseStack pose, int light, int overlay, int tint) {
        PoseStack.Pose last = pose.last();
        ModelRenderer.queue(this, null, last.pose(), last.normal(), light, overlay, tint);
    }

    public void render(Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        ModelRenderer.queue(this, null, pose, normal, light, overlay, tint);
    }

    /**
     * Queues the model with its translucent faces sorted by the order, against every other translucent draw.
     */
    public void render(PoseStack pose, int light, int overlay, int tint, RenderOrder order) {
        PoseStack.Pose last = pose.last();
        ModelRenderer.queue(this, null, last.pose(), last.normal(), light, overlay, tint, order);
    }

    public void renderNow(PoseStack pose, int light, int overlay) {
        renderNow(pose, light, overlay, NO_TINT);
    }

    public void renderNow(PoseStack pose, int light, int overlay, int tint) {
        PoseStack.Pose last = pose.last();
        ModelRenderer.drawNow(this, null, last.pose(), last.normal(), light, overlay, tint);
    }

    /**
     * Draws only some of the faces now, so a hollow translucent model can be drawn back, then contents, then front.
     */
    public void renderNow(PoseStack pose, int light, int overlay, int tint, RenderOrder.Cull cull) {
        PoseStack.Pose last = pose.last();
        ModelRenderer.drawNow(this, null, last.pose(), last.normal(), light, overlay, tint, cull);
    }

    @Override
    public String toString() {
        return "EmpiricModel[" + id + "]";
    }
}
