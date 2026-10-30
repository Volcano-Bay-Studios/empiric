package xyz.volcanobay.empiric.api.model;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

/**
 * Layers order the draws of one object that share a sort point, such as the back of a glass, then what's inside, then
 * its front. Opaque and cutout meshes ignore the order.
 *
 * @param sortPoint Camera-relative point to sort by, or null for the pose's origin
 * @param layer     Lower layers draw first among draws at the same distance
 * @param cull      Which faces to draw
 */
public record RenderOrder(@Nullable Vector3fc sortPoint, int layer, Cull cull) {

    public static final RenderOrder DEFAULT = new RenderOrder(null, 0, Cull.DEFAULT);

    public RenderOrder(@Nullable Vector3fc sortPoint, int layer) {
        this(sortPoint, layer, Cull.DEFAULT);
    }

    public enum Cull {
        /**
         * As the model says: back faces culled unless it's double sided.
         */
        DEFAULT,
        /**
         * Only the faces turned away from the camera, the far insides of a hollow model.
         */
        BACK_FACES_ONLY,
        /**
         * Only the faces turned towards the camera.
         */
        FRONT_FACES_ONLY
    }
}
