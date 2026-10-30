package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
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
     * The item it was placed from; it keeps its components while placed.
     */
    public ItemStack stack;
    public final PieceRuntime runtime;

    public Piece(UUID id, PartType type, int x, int y, int z, int rotation, ItemStack stack) {
        this(id, type, x, y, z, rotation, stack, new PieceRuntime());
    }

    private Piece(UUID id, PartType type, int x, int y, int z, int rotation, ItemStack stack, PieceRuntime runtime) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation & 3;
        this.stack = stack;
        this.runtime = runtime;
    }

    public Piece movedTo(int y) {
        return at(x, y, z);
    }

    public Piece at(int x, int y, int z) {
        Piece moved = new Piece(id, type, x, y, z, rotation, stack);
        moved.runtime.copyFrom(runtime);
        return moved;
    }

    /**
     * @return The same piece seen from another block, its pixels offset by these amounts; it shares the stack and runtime
     */
    public Piece shifted(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new Piece(id, type, x + dx, y + dy, z + dz, rotation, stack, runtime);
    }

    public static int width(PartType type, int rotation) {
        return (rotation & 1) == 0 ? type.width() : type.depth();
    }

    public static int depth(PartType type, int rotation) {
        return (rotation & 1) == 0 ? type.depth() : type.width();
    }

    public static int rotationFacing(Direction playerFacing) {
        return playerFacing.get2DDataValue();
    }

    public int width() {
        return width(type, rotation);
    }

    public int depth() {
        return depth(type, rotation);
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
