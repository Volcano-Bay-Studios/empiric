package xyz.volcanobay.empiric.client.model;

import foundry.veil.api.client.render.vertex.VertexArray;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import xyz.volcanobay.empiric.model.ModelRenderKind;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The faces of one render kind of a model or part, uploaded once, plus the instances queued to draw it this frame.
 * <p>
 * The instance layout is shared with {@code model.vsh}, so a change to one needs the other changed to match.
 */
final class ModelMesh implements AutoCloseable {

    static final int STRIDE = 112;
    private static final int NORMAL_OFFSET = 64;
    private static final int LIGHT_OFFSET = 100;
    private static final int OVERLAY_OFFSET = 104;
    private static final int TINT_OFFSET = 108;
    private static final int INITIAL_INSTANCES = 16;

    private static ByteBuffer scratch = MemoryUtil.memAlloc(STRIDE * INITIAL_INSTANCES);

    private final ModelRenderKind kind;
    private final VertexArray vertexArray;
    private final int instanceBuffer;
    private final int vertices;
    private final boolean doubleSided;
    private ByteBuffer staging = MemoryUtil.memAlloc(STRIDE * INITIAL_INSTANCES);
    private float[] depths = new float[INITIAL_INSTANCES];
    private int count;

    ModelMesh(ModelRenderKind kind, VertexArray vertexArray, int instanceBuffer, int vertices, boolean doubleSided) {
        this.kind = kind;
        this.vertexArray = vertexArray;
        this.instanceBuffer = instanceBuffer;
        this.vertices = vertices;
        this.doubleSided = doubleSided;
    }

    ModelRenderKind kind() {
        return kind;
    }

    boolean doubleSided() {
        return doubleSided;
    }

    int vertices() {
        return vertices;
    }

    /**
     * Queues an instance.
     *
     * @return whether it's the first since the mesh was last drawn or cleared
     */
    boolean add(Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        if ((count + 1) * STRIDE > staging.capacity()) {
            staging = MemoryUtil.memRealloc(staging, staging.capacity() * 2);
            depths = Arrays.copyOf(depths, depths.length * 2);
        }
        write(staging, count * STRIDE, pose, normal, light, overlay, tint);
        depths[count] = pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32();
        return count++ == 0;
    }

    void clear() {
        count = 0;
    }

    /**
     * Draws the queued instances, far ones first if the mesh is translucent, and empties the queue.
     */
    void flush() {
        if (count == 0) {
            return;
        }
        ByteBuffer data = kind == ModelRenderKind.TRANSLUCENT && count > 1 ? sorted() : staging;
        draw(data, count);
        count = 0;
    }

    /**
     * Draws one instance now, apart from the queue.
     */
    void drawSingle(Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        write(scratch, 0, pose, normal, light, overlay, tint);
        draw(scratch, 1);
    }

    private ByteBuffer sorted() {
        long[] keys = new long[count];
        for (int i = 0; i < count; i++) {
            keys[i] = (long) Float.floatToIntBits(depths[i]) << 32 | i;
        }
        Arrays.sort(keys);
        if (count * STRIDE > scratch.capacity()) {
            scratch = MemoryUtil.memRealloc(scratch, Math.max(count * STRIDE, scratch.capacity() * 2));
        }
        for (int i = 0; i < count; i++) {
            int source = (int) keys[count - 1 - i];
            MemoryUtil.memCopy(MemoryUtil.memAddress(staging, source * STRIDE), MemoryUtil.memAddress(scratch, i * STRIDE), STRIDE);
        }
        return scratch;
    }

    private void draw(ByteBuffer data, int instances) {
        data.limit(instances * STRIDE);
        data.position(0);
        VertexArray.upload(instanceBuffer, data, VertexArray.DrawUsage.STREAM);
        data.clear();
        vertexArray.bind();
        vertexArray.drawInstanced(instances);
    }

    private static void write(ByteBuffer buffer, int base, Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        pose.get(base, buffer);
        normal.get(base + NORMAL_OFFSET, buffer);
        buffer.putInt(base + LIGHT_OFFSET, light);
        buffer.putInt(base + OVERLAY_OFFSET, overlay);
        buffer.put(base + TINT_OFFSET, (byte) (tint >> 16));
        buffer.put(base + TINT_OFFSET + 1, (byte) (tint >> 8));
        buffer.put(base + TINT_OFFSET + 2, (byte) tint);
        buffer.put(base + TINT_OFFSET + 3, (byte) (tint >>> 24));
    }

    @Override
    public void close() {
        vertexArray.free();
        MemoryUtil.memFree(staging);
        staging = null;
        count = 0;
    }
}
