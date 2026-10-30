package xyz.volcanobay.empiric.api.part;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * A box that can be turned, such as one lying on a sloped face.
 *
 * @param box       In its own pixels
 * @param transform From its own pixels to the part's own unrotated pixels; must be rigid (turns and moves only)
 */
public record Hitbox(AABB box, Matrix4fc transform) {

    private static final Matrix4fc IDENTITY = new Matrix4f();

    public Hitbox {
        transform = new Matrix4f(transform);
    }

    public static Hitbox of(AABB box) {
        return new Hitbox(box, IDENTITY);
    }

    /**
     * A box tipped about a line along x.
     *
     * @param pivotY  In the part's pixels
     * @param degrees Positive lifts the box's +z end
     */
    public static Hitbox tilted(AABB box, double pivotY, double pivotZ, double degrees) {
        Matrix4f transform = new Matrix4f().translation(0, (float) pivotY, (float) pivotZ).rotateX((float) Math.toRadians(-degrees));
        return new Hitbox(box, transform);
    }

    public boolean isAligned() {
        return (transform.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0;
    }

    /**
     * @return A point in the box's own pixels as the part's own pixels
     */
    public Vec3 toPart(double x, double y, double z) {
        Vector3f point = transform.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return new Vec3(point.x, point.y, point.z);
    }

    /**
     * @return The box in the piece's block pixels
     */
    public OrientedBox in(Piece piece) {
        if (isAligned()) {
            return OrientedBox.aligned(piece.toBlock(box));
        }
        return new OrientedBox(box, piece.toBlockMatrix().mul(transform));
    }
}
