package xyz.volcanobay.empiric.api.oct;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.function.Supplier;

public interface OctBlockRenderer<T extends OctBlockEntity> {
    void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay);
    
    static <T extends OctBlockEntity> void register(Class<T> type, Supplier<OctBlockRenderer<T>> renderer) {
        OctBlockRendererRegistry.register(type, renderer);
    }
}
