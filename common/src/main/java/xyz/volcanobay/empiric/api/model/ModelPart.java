package xyz.volcanobay.empiric.api.model;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.client.model.ModelRenderer;

/**
 * One named part of an {@link EmpiricModel}, drawn with its own pose. Positions in the part stay in the model's
 * space, so a part that should spin about its own center needs the pose to translate there and back.
 */
public final class ModelPart {

    private final EmpiricModel model;
    private final String name;

    ModelPart(EmpiricModel model, String name) {
        this.model = model;
        this.name = name;
    }

    public EmpiricModel model() {
        return model;
    }

    public String name() {
        return name;
    }

    public void render(PoseStack pose, int light, int overlay) {
        render(pose, light, overlay, EmpiricModel.NO_TINT);
    }

    public void render(PoseStack pose, int light, int overlay, int tint) {
        PoseStack.Pose last = pose.last();
        ModelRenderer.queue(model, name, last.pose(), last.normal(), light, overlay, tint);
    }

    public void render(Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        ModelRenderer.queue(model, name, pose, normal, light, overlay, tint);
    }

    public void renderNow(PoseStack pose, int light, int overlay) {
        renderNow(pose, light, overlay, EmpiricModel.NO_TINT);
    }

    public void renderNow(PoseStack pose, int light, int overlay, int tint) {
        PoseStack.Pose last = pose.last();
        ModelRenderer.drawNow(model, name, last.pose(), last.normal(), light, overlay, tint);
    }

    @Override
    public String toString() {
        return "ModelPart[" + model.id() + "#" + name + "]";
    }
}
