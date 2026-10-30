package xyz.volcanobay.empiric.api.part;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A box placed in a block by a rigid transform.
 */
public final class OrientedBox {

    private static final Matrix4fc IDENTITY = new Matrix4f();
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final double EDGE_STEP = 0.25;
    private static final double EDGE_PROBE = 0.05;

    private final AABB box;
    private final Matrix4fc toBlock;
    private final Matrix4fc fromBlock;
    private final boolean aligned;

    /**
     * @param box     In its own pixels
     * @param toBlock From its own pixels to block pixels
     */
    public OrientedBox(AABB box, Matrix4fc toBlock) {
        this.box = box;
        this.toBlock = new Matrix4f(toBlock);
        this.fromBlock = new Matrix4f(toBlock).invert();
        this.aligned = (toBlock.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0;
    }

    /**
     * @param box In block pixels
     */
    public static OrientedBox aligned(AABB box) {
        return new OrientedBox(box, IDENTITY);
    }

    public AABB box() {
        return box;
    }

    public Matrix4fc toBlock() {
        return toBlock;
    }

    public boolean isAligned() {
        return aligned;
    }

    /**
     * @return The eight corners in block pixels; bit 0 of the index is max x, bit 1 max y, bit 2 max z
     */
    public Vec3[] corners() {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = toBlock((i & 1) != 0 ? box.maxX : box.minX, (i & 2) != 0 ? box.maxY : box.minY, (i & 4) != 0 ? box.maxZ : box.minZ);
        }
        return corners;
    }

    /**
     * @return The smallest block-aligned box around it, in block pixels
     */
    public AABB bounds() {
        if (aligned) {
            return box;
        }
        Vec3[] corners = corners();
        AABB bounds = new AABB(corners[0], corners[0]);
        for (Vec3 corner : corners) {
            bounds = bounds.minmax(new AABB(corner, corner));
        }
        return bounds;
    }

    public Vec3 toBlock(double x, double y, double z) {
        if (aligned) {
            return new Vec3(x, y, z);
        }
        Vector3f point = toBlock.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return new Vec3(point.x, point.y, point.z);
    }

    private Vec3 toLocal(Vec3 point) {
        if (aligned) {
            return point;
        }
        Vector3f local = fromBlock.transformPosition((float) point.x, (float) point.y, (float) point.z, new Vector3f());
        return new Vec3(local.x, local.y, local.z);
    }

    /**
     * @param point In block pixels
     */
    public boolean contains(Vec3 point, double epsilon) {
        Vec3 local = toLocal(point);
        return local.x >= box.minX - epsilon && local.x <= box.maxX + epsilon
                && local.y >= box.minY - epsilon && local.y <= box.maxY + epsilon
                && local.z >= box.minZ - epsilon && local.z <= box.maxZ + epsilon;
    }

    /**
     * @param start In block pixels
     * @return Where a segment first meets the box, in block pixels
     */
    public Optional<Vec3> clip(Vec3 start, Vec3 end) {
        if (aligned) {
            return box.contains(start) ? Optional.of(start) : box.clip(start, end);
        }
        Vec3 from = toLocal(start);
        Vec3 to = toLocal(end);
        if (box.contains(from)) {
            return Optional.of(start);
        }
        return box.clip(from, to).map(hit -> toBlock(hit.x, hit.y, hit.z));
    }

    /**
     * @return The creases of the boxes taken as one shape, as {from, to} pairs in block pixels: edges inside the shape,
     * or where two boxes meet in one flat face, are left out
     */
    public static List<Vec3[]> outlineEdges(List<OrientedBox> boxes) {
        List<Vec3[]> segments = new ArrayList<>();
        for (OrientedBox owner : boxes) {
            Vec3[] corners = owner.corners();
            for (int[] edge : EDGES) {
                Vec3 a = corners[edge[0]];
                Vec3 b = corners[edge[1]];
                double length = a.distanceTo(b);
                if (length < 1.0E-6) {
                    continue;
                }
                Vec3[] normals = faceNormals(owner, edge);
                int steps = Math.max(1, (int) Math.ceil(length / EDGE_STEP));
                int runStart = -1;
                for (int i = 0; i <= steps; i++) {
                    boolean crease = i < steps && crease(boxes, a.lerp(b, (i + 0.5) / steps), normals[0], normals[1]);
                    if (crease && runStart < 0) {
                        runStart = i;
                    } else if (!crease && runStart >= 0) {
                        segments.add(new Vec3[]{a.lerp(b, (double) runStart / steps), a.lerp(b, (double) i / steps)});
                        runStart = -1;
                    }
                }
            }
        }
        return segments;
    }

    /**
     * @return The outward normals, in block space, of the two faces of the box that meet at an edge
     */
    private static Vec3[] faceNormals(OrientedBox box, int[] edge) {
        int fixed = ~(edge[0] ^ edge[1]) & 7;
        Vec3[] normals = new Vec3[2];
        int found = 0;
        for (int bit = 0; bit < 3 && found < 2; bit++) {
            if ((fixed & (1 << bit)) == 0) {
                continue;
            }
            double sign = (edge[0] & (1 << bit)) != 0 ? 1 : -1;
            Vector3f local = new Vector3f(bit == 0 ? (float) sign : 0, bit == 1 ? (float) sign : 0, bit == 2 ? (float) sign : 0);
            box.toBlock.transformDirection(local);
            normals[found++] = new Vec3(local.x, local.y, local.z);
        }
        return normals;
    }

    /**
     * @param u Outward normal of one face of the box the edge belongs to
     * @param v Outward normal of the other face
     */
    private static boolean crease(List<OrientedBox> boxes, Vec3 point, Vec3 u, Vec3 v) {
        boolean[] filled = new boolean[4];
        int count = 0;
        for (int i = 0; i < 4; i++) {
            Vec3 probe = point.add(u.scale((i & 1) != 0 ? EDGE_PROBE : -EDGE_PROBE)).add(v.scale((i & 2) != 0 ? EDGE_PROBE : -EDGE_PROBE));
            for (OrientedBox box : boxes) {
                if (box.contains(probe, 0)) {
                    filled[i] = true;
                    count++;
                    break;
                }
            }
        }
        if (count == 1 || count == 3) {
            return true;
        }
        return count == 2 && filled[0] == filled[3];
    }
}
