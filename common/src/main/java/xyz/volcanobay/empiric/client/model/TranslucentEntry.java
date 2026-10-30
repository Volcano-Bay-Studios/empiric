package xyz.volcanobay.empiric.client.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import xyz.volcanobay.empiric.api.model.RenderOrder;
import xyz.volcanobay.empiric.api.model.TranslucentDraw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * One queued translucent draw: an instance of a mesh, or custom geometry.
 */
@ApiStatus.Internal
public final class TranslucentEntry {

    /**
     * Far ones first, then lower layers, then in the order they were queued.
     */
    static final Comparator<TranslucentEntry> ORDER = Comparator.comparingDouble((TranslucentEntry entry) -> -entry.depth)
            .thenComparingInt(entry -> entry.layer)
            .thenComparingLong(entry -> entry.sequence);

    final double depth;
    final int layer;
    final long sequence;
    @Nullable
    final ModelMesh mesh;
    final RenderOrder.Cull cull;
    final Matrix4f pose;
    final Matrix3f normal;
    final int light;
    final int overlay;
    final int tint;
    @Nullable
    final Consumer<TranslucentDraw.Context> custom;

    private TranslucentEntry(double depth, int layer, long sequence, @Nullable ModelMesh mesh, RenderOrder.Cull cull, Matrix4f pose,
                             Matrix3f normal, int light, int overlay, int tint, @Nullable Consumer<TranslucentDraw.Context> custom) {
        this.depth = depth;
        this.layer = layer;
        this.sequence = sequence;
        this.mesh = mesh;
        this.cull = cull;
        this.pose = pose;
        this.normal = normal;
        this.light = light;
        this.overlay = overlay;
        this.tint = tint;
        this.custom = custom;
    }

    static TranslucentEntry mesh(double depth, int layer, long sequence, ModelMesh mesh, RenderOrder.Cull cull, Matrix4fc pose,
                                 Matrix3fc normal, int light, int overlay, int tint) {
        return new TranslucentEntry(depth, layer, sequence, mesh, cull, new Matrix4f(pose), new Matrix3f(normal), light, overlay, tint, null);
    }

    static TranslucentEntry custom(double depth, int layer, long sequence, Consumer<TranslucentDraw.Context> draw) {
        return new TranslucentEntry(depth, layer, sequence, null, RenderOrder.Cull.DEFAULT, new Matrix4f(), new Matrix3f(), 0, 0, 0, draw);
    }

    /**
     * The order custom draws at these squared distances and layers would be drawn in, for tests.
     *
     * @return Indices into the arrays, first drawn first
     */
    public static int[] order(double[] depths, int[] layers) {
        List<TranslucentEntry> entries = new ArrayList<>();
        for (int i = 0; i < depths.length; i++) {
            entries.add(custom(depths[i], layers[i], i, context -> {
            }));
        }
        entries.sort(ORDER);
        int[] order = new int[entries.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = (int) entries.get(i).sequence;
        }
        return order;
    }

    /**
     * Whether this and the next entry can go in one instanced draw.
     */
    boolean batchesWith(TranslucentEntry other) {
        return mesh != null && mesh == other.mesh && cull == other.cull;
    }
}
