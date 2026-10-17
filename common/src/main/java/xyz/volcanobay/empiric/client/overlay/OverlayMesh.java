package xyz.volcanobay.empiric.client.overlay;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;

/**
 * A baked mesh of cubic cells. Only faces between a cell and empty space are emitted, each shaded
 * by its direction and darkened per vertex by ambient occlusion from neighbouring cells.
 */
public final class OverlayMesh implements AutoCloseable {

    private static final float[] AO = {0.45f, 0.65f, 0.82f, 1.0f};

    private final VertexBuffer buffer;
    private final double originX;
    private final double originY;
    private final double originZ;

    private OverlayMesh(VertexBuffer buffer, double originX, double originY, double originZ) {
        this.buffer = buffer;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    /**
     * Bakes the given cells. Returns null when nothing is visible.
     *
     * @param cellSize size of one cell in blocks
     * @param cells    the cells to draw, in cell coordinates
     * @param filled   whether a cell is occupied, used for face culling and occlusion
     * @param color    RGB color of an occupied cell
     */
    public static OverlayMesh build(double cellSize, Iterable<SpatialPos> cells, Filled filled, Color color) {
        SpatialPos origin = null;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4 * 256)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (SpatialPos cell : cells) {
                if (origin == null) {
                    origin = cell;
                }
                int rgb = color.color(cell.x(), cell.y(), cell.z());
                for (Direction face : Direction.values()) {
                    if (!filled.filled(cell.x() + face.getStepX(), cell.y() + face.getStepY(), cell.z() + face.getStepZ())) {
                        face(builder, filled, cell, origin, face, rgb, (float) cellSize);
                    }
                }
            }
            MeshData mesh = builder.build();
            if (mesh == null || origin == null) {
                return null;
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            return new OverlayMesh(buffer, origin.x() * cellSize, origin.y() * cellSize, origin.z() * cellSize);
        }
    }

    private static void face(BufferBuilder builder, Filled filled, SpatialPos cell, SpatialPos origin, Direction face, int rgb, float size) {
        Direction.Axis normal = face.getAxis();
        Direction.Axis uAxis = normal == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis vAxis = normal == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        float shade = shade(face);
        long nx = cell.x() + face.getStepX();
        long ny = cell.y() + face.getStepY();
        long nz = cell.z() + face.getStepZ();
        float[] x = new float[4];
        float[] y = new float[4];
        float[] z = new float[4];
        int[] ao = new int[4];
        for (int corner = 0; corner < 4; corner++) {
            int su = corner == 1 || corner == 2 ? 1 : 0;
            int sv = corner >= 2 ? 1 : 0;
            int du = su * 2 - 1;
            int dv = sv * 2 - 1;
            boolean side1 = filled.filled(nx + step(uAxis, Direction.Axis.X, du), ny + step(uAxis, Direction.Axis.Y, du), nz + step(uAxis, Direction.Axis.Z, du));
            boolean side2 = filled.filled(nx + step(vAxis, Direction.Axis.X, dv), ny + step(vAxis, Direction.Axis.Y, dv), nz + step(vAxis, Direction.Axis.Z, dv));
            boolean diagonal = filled.filled(nx + step(uAxis, Direction.Axis.X, du) + step(vAxis, Direction.Axis.X, dv),
                    ny + step(uAxis, Direction.Axis.Y, du) + step(vAxis, Direction.Axis.Y, dv),
                    nz + step(uAxis, Direction.Axis.Z, du) + step(vAxis, Direction.Axis.Z, dv));
            ao[corner] = side1 && side2 ? 0 : 3 - (side1 ? 1 : 0) - (side2 ? 1 : 0) - (diagonal ? 1 : 0);
            int plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            x[corner] = (cell.x() - origin.x() + offset(Direction.Axis.X, normal, uAxis, plane, su, sv)) * size;
            y[corner] = (cell.y() - origin.y() + offset(Direction.Axis.Y, normal, uAxis, plane, su, sv)) * size;
            z[corner] = (cell.z() - origin.z() + offset(Direction.Axis.Z, normal, uAxis, plane, su, sv)) * size;
        }
        // Quads split along 0-2; start at corner 1 instead when that diagonal hides the occlusion gradient.
        int start = ao[0] + ao[2] < ao[1] + ao[3] ? 1 : 0;
        for (int i = 0; i < 4; i++) {
            int corner = (start + i) & 3;
            float light = shade * AO[ao[corner]];
            builder.addVertex(x[corner], y[corner], z[corner]).setColor(channel(rgb, 16, light), channel(rgb, 8, light), channel(rgb, 0, light), 255);
        }
    }

    private static long step(Direction.Axis axis, Direction.Axis component, int sign) {
        return axis == component ? sign : 0;
    }

    private static int offset(Direction.Axis component, Direction.Axis normal, Direction.Axis uAxis, int plane, int su, int sv) {
        if (component == normal) {
            return plane;
        }
        return component == uAxis ? su : sv;
    }

    private static int channel(int rgb, int shift, float light) {
        return (int) (((rgb >> shift) & 255) * light);
    }

    private static float shade(Direction face) {
        return switch (face) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.65f;
        };
    }

    public void draw(Matrix4fc view, Matrix4fc projection, Vec3 camera) {
        Matrix4f modelView = new Matrix4f(view).translate((float) (originX - camera.x), (float) (originY - camera.y), (float) (originZ - camera.z));
        buffer.bind();
        buffer.drawWithShader(modelView, new Matrix4f(projection), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
    }

    @Override
    public void close() {
        buffer.close();
    }

    @FunctionalInterface
    public interface Filled {
        boolean filled(long x, long y, long z);
    }

    @FunctionalInterface
    public interface Color {
        int color(long x, long y, long z);
    }
}
