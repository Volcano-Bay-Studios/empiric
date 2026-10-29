package xyz.volcanobay.empiric.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the model renderer has loaded and drawn.
 */
public final class ModelStats {

    private static final Map<ResourceLocation, Info> MODELS = new ConcurrentHashMap<>();
    private static volatile int instances;
    private static volatile int drawCalls;

    private ModelStats() {
    }

    public static void loaded(ResourceLocation id, Info info) {
        MODELS.put(id, info);
    }

    public static void frame(int instanceCount, int drawCallCount) {
        instances = instanceCount;
        drawCalls = drawCallCount;
    }

    public static void clear() {
        MODELS.clear();
    }

    public static Map<ResourceLocation, Info> models() {
        return new TreeMap<>(MODELS);
    }

    /**
     * Model instances queued in the last frame.
     */
    public static int instances() {
        return instances;
    }

    public static int drawCalls() {
        return drawCalls;
    }

    public record Info(int parts, int meshes, int vertices, boolean failed) {
    }
}
