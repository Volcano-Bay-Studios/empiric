package xyz.volcanobay.empiric.client.overlay;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * Something drawn by {@link OverlayRenderer}. A layer draws opaque geometry into the overlay target,
 * which is then blended over the world at the layer's opacity.
 */
public interface OverlayLayer {

    boolean isVisible();

    /**
     * Whether the layer is drawn over blocks. Otherwise it's hidden by the world like normal geometry.
     */
    boolean throughBlocks();

    float opacity();

    void tick();

    void render(Matrix4fc view, Matrix4fc projection, Vec3 camera);

    void clear();
}
