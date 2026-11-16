package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import xyz.volcanobay.empiric.api.part.trait.Hitboxes;
import xyz.volcanobay.empiric.api.part.trait.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One placed part. Its corner is in pixels of the block it's seen from: 0 to 15 in the block that stores it, and
 * shifted by 16 per block when seen from a neighbour.
 */
public final class Piece {

    public final UUID id;
    public final PartType type;
    public final int x;
    public final int y;
    public final int z;
    /**
     * Quarter turns, 0 to 3.
     */
    public final int rotation;
    /**
     * Degrees turned past the quarter turns, about the middle of the footprint. The footprint is the box that holds
     * the turned part, so it grows with the turn.
     */
    public final float turn;
    /**
     * The item it was placed from; it keeps its components while placed.
     */
    public ItemStack stack;
    public final PieceRuntime runtime;

    public Piece(UUID id, PartType type, int x, int y, int z, int rotation, ItemStack stack) {
        this(id, type, x, y, z, rotation, 0, stack, new PieceRuntime());
    }

    public Piece(UUID id, PartType type, int x, int y, int z, int rotation, float turn, ItemStack stack) {
        this(id, type, x, y, z, rotation, turn, stack, new PieceRuntime());
    }

    private Piece(UUID id, PartType type, int x, int y, int z, int rotation, float turn, ItemStack stack, PieceRuntime runtime) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation & 3;
        this.turn = normalizeTurn(turn);
        this.stack = stack;
        this.runtime = runtime;
    }

    public Piece movedTo(int y) {
        return at(x, y, z);
    }

    public Piece at(int x, int y, int z) {
        Piece moved = new Piece(id, type, x, y, z, rotation, turn, stack);
        moved.runtime.copyFrom(runtime);
        return moved;
    }

    /**
     * @return The same piece seen from another block, its pixels offset by these amounts; it shares the stack and runtime
     */
    public Piece shifted(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new Piece(id, type, x + dx, y + dy, z + dz, rotation, turn, stack, runtime);
    }

    public static int width(PartType type, int rotation) {
        return (rotation & 1) == 0 ? type.width() : type.depth();
    }

    public static int depth(PartType type, int rotation) {
        return (rotation & 1) == 0 ? type.depth() : type.width();
    }

    /**
     * @return Pixels along x of the box holding the part once it is turned
     */
    public static int width(PartType type, int rotation, float turn) {
        if (isSquare(turn)) {
            return width(type, rotation);
        }
        double radians = Math.toRadians(turn);
        return (int) Math.ceil(width(type, rotation) * Math.abs(Math.cos(radians)) + depth(type, rotation) * Math.abs(Math.sin(radians)) - 1.0E-3);
    }

    /**
     * @return Pixels along z of the box holding the part once it is turned
     */
    public static int depth(PartType type, int rotation, float turn) {
        if (isSquare(turn)) {
            return depth(type, rotation);
        }
        double radians = Math.toRadians(turn);
        return (int) Math.ceil(width(type, rotation) * Math.abs(Math.sin(radians)) + depth(type, rotation) * Math.abs(Math.cos(radians)) - 1.0E-3);
    }

    public static boolean isSquare(float turn) {
        return Math.abs(turn) < 1.0E-3f;
    }

    public static float normalizeTurn(float turn) {
        float wrapped = turn % 360;
        if (wrapped >= 180) {
            wrapped -= 360;
        } else if (wrapped < -180) {
            wrapped += 360;
        }
        return isSquare(wrapped) ? 0 : wrapped;
    }

    public static int rotationFacing(Direction playerFacing) {
        return playerFacing.get2DDataValue();
    }

    public int width() {
        return width(type, rotation, turn);
    }

    public int depth() {
        return depth(type, rotation, turn);
    }

    /**
     * @return Everything it is turned by, in degrees
     */
    public float yaw() {
        return 90 * rotation + turn;
    }

    public int height() {
        return type.height();
    }

    public int maxX() {
        return x + width();
    }

    public int maxY() {
        return y + height();
    }

    public int maxZ() {
        return z + depth();
    }

    public int anchor() {
        return Parts.octant(x, y, z);
    }

    public AABB box() {
        return new AABB(x, y, z, maxX(), maxY(), maxZ());
    }

    /**
     * @return The middle of its footprint, at its bottom, in block pixels
     */
    public Vec3 centre() {
        return new Vec3(x + width() / 2.0, y, z + depth() / 2.0);
    }

    /**
     * @return A point in the piece's own unrotated pixels as block pixels
     */
    public Vec3 toBlock(double u, double h, double v) {
        double w = type.width();
        double d = type.depth();
        if (turn != 0) {
            Vector3f point = toBlockMatrix().transformPosition((float) u, (float) h, (float) v, new Vector3f());
            return new Vec3(point.x, point.y, point.z);
        }
        double bx;
        double bz;
        switch (rotation) {
            case 1 -> {
                bx = d - v;
                bz = u;
            }
            case 2 -> {
                bx = w - u;
                bz = d - v;
            }
            case 3 -> {
                bx = v;
                bz = w - u;
            }
            default -> {
                bx = u;
                bz = v;
            }
        }
        return new Vec3(x + bx, y + h, z + bz);
    }

    /**
     * @return From the piece's own unrotated pixels to block pixels
     */
    public Matrix4f toBlockMatrix() {
        float w = type.width();
        float d = type.depth();
        if (turn != 0) {
            return new Matrix4f().translation(x + width() / 2f, y, z + depth() / 2f)
                    .rotateY((float) -Math.toRadians(yaw())).translate(-w / 2, 0, -d / 2);
        }
        Matrix4f matrix = new Matrix4f().translation(x, y, z);
        switch (rotation) {
            case 1 -> matrix.translate(d, 0, 0).rotateY((float) (-Math.PI / 2));
            case 2 -> matrix.translate(w, 0, d).rotateY((float) Math.PI);
            case 3 -> matrix.translate(0, 0, w).rotateY((float) (Math.PI / 2));
            default -> {
            }
        }
        return matrix;
    }

    /**
     * @return What its body is aimed at and outlined by, in block pixels: its {@link PartTraits#HITBOXES} if it has
     * them, else its shapes
     */
    public List<OrientedBox> hitBoxes() {
        Hitboxes hitboxes = type.get(PartTraits.HITBOXES);
        List<OrientedBox> boxes = new ArrayList<>();
        if ((hitboxes == null || hitboxes.boxes().isEmpty()) && turn != 0) {
            Shapes shapes = type.get(PartTraits.SHAPES);
            if (shapes == null || shapes.boxes().isEmpty()) {
                boxes.add(new OrientedBox(new AABB(0, 0, 0, type.width(), type.height(), type.depth()), toBlockMatrix()));
            } else {
                for (AABB shape : shapes.boxes()) {
                    boxes.add(new OrientedBox(shape, toBlockMatrix()));
                }
            }
            return boxes;
        }
        if (hitboxes == null || hitboxes.boxes().isEmpty()) {
            for (AABB shape : shapes()) {
                boxes.add(OrientedBox.aligned(shape));
            }
            return boxes;
        }
        for (Hitbox hitbox : hitboxes.boxes()) {
            boxes.add(hitbox.in(this));
        }
        return boxes;
    }

    public AABB toBlock(AABB local) {
        if (turn != 0) {
            Matrix4f matrix = toBlockMatrix();
            Vector3f corner = new Vector3f();
            AABB bounds = null;
            for (int i = 0; i < 8; i++) {
                matrix.transformPosition((float) ((i & 1) == 0 ? local.minX : local.maxX), (float) ((i & 2) == 0 ? local.minY : local.maxY),
                        (float) ((i & 4) == 0 ? local.minZ : local.maxZ), corner);
                AABB point = new AABB(corner.x, corner.y, corner.z, corner.x, corner.y, corner.z);
                bounds = bounds == null ? point : bounds.minmax(point);
            }
            return bounds;
        }
        Vec3 a = toBlock(local.minX, local.minY, local.minZ);
        Vec3 b = toBlock(local.maxX, local.maxY, local.maxZ);
        return new AABB(a, b);
    }

    /**
     * @return The boxes the piece is made of, in block pixels
     */
    public List<AABB> shapes() {
        Shapes shapes = type.get(PartTraits.SHAPES);
        if (shapes == null || shapes.boxes().isEmpty()) {
            return List.of(box());
        }
        List<AABB> boxes = new ArrayList<>();
        for (AABB shape : shapes.boxes()) {
            boxes.add(toBlock(shape));
        }
        return boxes;
    }

    /**
     * @return Boxes of its hit layers that take up room, in block pixels
     */
    public List<AABB> occupied() {
        List<AABB> boxes = new ArrayList<>();
        for (HitLayer layer : type.hooks(HitLayer.class)) {
            if (layer.occupies()) {
                boxes.addAll(layer.boxes(this));
            }
        }
        return boxes;
    }

    /**
     * @return Its box grown to everything that takes up room with it, such as items on top
     */
    public AABB bodyBox() {
        AABB box = box();
        for (AABB extra : occupied()) {
            box = box.minmax(extra);
        }
        return box;
    }

    /**
     * @return The boxes of its block shape: what {@link #boxes()} gives, but stepped to the turn when it is turned
     */
    public List<AABB> shapeBoxes() {
        if (turn == 0) {
            return boxes();
        }
        List<AABB> boxes = new ArrayList<>();
        for (OrientedBox box : hitBoxes()) {
            boxes.addAll(box.voxels());
        }
        for (HitLayer layer : type.hooks(HitLayer.class)) {
            boxes.addAll(layer.boxes(this));
        }
        return boxes;
    }

    /**
     * @return Every box that can be aimed at, in block pixels
     */
    public List<AABB> boxes() {
        List<AABB> boxes = new ArrayList<>(shapes());
        for (HitLayer layer : type.hooks(HitLayer.class)) {
            boxes.addAll(layer.boxes(this));
        }
        return boxes;
    }

    public boolean intersects(AABB other) {
        for (AABB shape : shapes()) {
            if (shape.intersects(other)) {
                return true;
            }
        }
        for (AABB box : occupied()) {
            if (box.intersects(other)) {
                return true;
            }
        }
        return false;
    }
}
