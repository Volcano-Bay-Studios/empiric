package xyz.volcanobay.empiric.api.oct;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public final class OctBlockRendererRegistry {
    private static final Map<Class<? extends OctBlockEntity>, OctBlockRenderer<? extends OctBlockEntity>> RENDERERS = new Reference2ReferenceOpenHashMap<>();
    
    private OctBlockRendererRegistry() {
    }
    
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends OctBlockEntity> OctBlockRenderer<T> get(Class<T> type) {
        return (OctBlockRenderer<T>) RENDERERS.get(type);
    }
    
    public static <T extends OctBlockEntity> void register(Class<T> type, Supplier<OctBlockRenderer<T>> rendererSupplier) {
        RENDERERS.put(type, rendererSupplier.get());
    }
}
