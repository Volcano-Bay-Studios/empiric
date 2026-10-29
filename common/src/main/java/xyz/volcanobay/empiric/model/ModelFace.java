package xyz.volcanobay.empiric.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A triangle or quad. {@code texture} is a block atlas sprite, and {@code shade} turns on directional shading.
 */
public record ModelFace(List<ModelVertex> vertices,
                        ResourceLocation texture,
                        boolean atlasUv,
                        ModelRenderKind kind,
                        boolean shade) {

    public ModelFace {
        if (vertices.size() != 3 && vertices.size() != 4) {
            throw new IllegalArgumentException("A face has 3 or 4 vertices, not " + vertices.size());
        }
        vertices = List.copyOf(vertices);
    }
}
