package xyz.volcanobay.empiric.api.part.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.model.RenderOrder;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.Piece;

public final class PartRendering {

    /**
     * Blocks every piece is drawn above its spot, off the surface it stands on.
     */
    public static final float LIFT = 0.002f;
    /**
     * Pixels each piece is drawn short of its box on its sides and top, so touching pieces never share a plane.
     */
    public static final float INSET = 0.03f;
    /**
     * {@link RenderOrder} layers of a translucent part's back and front faces; what's inside it goes between.
     */
    public static final int LAYER_BACK = 0;
    public static final int LAYER_FRONT = 3;

    private PartRendering() {
    }

    /**
     * @param pose At the piece's minimum corner in its own frame
     * @param tint ARGB
     */
    public static void renderModel(PartType type, PoseStack pose, int light, int overlay, int tint) {
        EmpiricModel model = type.model();
        if (model == null) {
            return;
        }
        pose.pushPose();
        shrink(pose, type);
        if (type.has(PartTraits.OPAQUE)) {
            toModel(pose, type);
            model.render(pose, light, overlay, tint);
        } else {
            Vector3f centre = centre(type, pose);
            toModel(pose, type);
            model.render(pose, light, overlay, tint, new RenderOrder(centre, LAYER_BACK, RenderOrder.Cull.BACK_FACES_ONLY));
            model.render(pose, light, overlay, tint, new RenderOrder(centre, LAYER_FRONT, RenderOrder.Cull.FRONT_FACES_ONLY));
        }
        pose.popPose();
    }

    public static void shrink(PoseStack pose, PartType type) {
        float width = type.width();
        float height = type.height();
        float depth = type.depth();
        pose.translate(INSET / 16, 0, INSET / 16);
        pose.scale((width - 2 * INSET) / width, (height - INSET) / height, (depth - 2 * INSET) / depth);
    }

    /**
     * Moves from the piece's corner to its model's origin; OBJ models are exported centred on their footprint.
     */
    public static void toModel(PoseStack pose, PartType type) {
        EmpiricModel model = type.model();
        if (model != null && model.isObj()) {
            pose.translate(type.width() / 32f, 0, type.depth() / 32f);
        }
    }

    /**
     * @return The middle of a piece, camera relative, with the pose at its minimum corner
     */
    public static Vector3f centre(PartType type, PoseStack pose) {
        return pose.last().pose().transformPosition(type.width() / 32f, type.height() / 32f, type.depth() / 32f, new Vector3f());
    }

    /**
     * Moves from the block's corner to the piece's minimum corner in its own frame.
     *
     * @param x In block pixels
     */
    public static void toPiece(PoseStack pose, PartType type, int x, int y, int z, int rotation) {
        float width = Piece.width(type, rotation);
        float depth = Piece.depth(type, rotation);
        pose.translate((x + width / 2) / 16f, y / 16f, (z + depth / 2) / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(-90 * rotation));
        pose.translate(-type.width() / 32f, 0, -type.depth() / 32f);
    }
}
