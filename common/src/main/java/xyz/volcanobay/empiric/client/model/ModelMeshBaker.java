package xyz.volcanobay.empiric.client.model;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.vertex.VertexArray;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import xyz.volcanobay.empiric.model.ModelFace;
import xyz.volcanobay.empiric.model.ModelGeometry;
import xyz.volcanobay.empiric.model.ModelRenderKind;
import xyz.volcanobay.empiric.model.ModelVertex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Uploads a {@link ModelGeometry} as one {@link ModelMesh} per render kind, for the whole model and for each part.
 * <p>
 * The vertex colour's red channel is how the shader knows whether a face is shaded, so it isn't a real colour.
 * Must be called on the render thread.
 */
final class ModelMeshBaker {

    private static final int INSTANCE_BINDING = 1;
    private static final int FIRST_INSTANCE_ATTRIBUTE = 5;
    private static final int SHADED = 0xFFFFFFFF;
    private static final int UNSHADED = 0xFF00FFFF;

    private ModelMeshBaker() {
    }

    static LoadedModel bake(ModelGeometry geometry, Function<ResourceLocation, TextureAtlasSprite> sprites, boolean doubleSided) {
        Map<String, ModelMesh[]> parts = new LinkedHashMap<>();
        List<ModelFace> everything = new ArrayList<>();
        for (Map.Entry<String, List<ModelFace>> entry : geometry.parts().entrySet()) {
            parts.put(entry.getKey(), meshes(entry.getValue(), sprites, doubleSided));
            everything.addAll(entry.getValue());
        }
        ModelMesh[] all = parts.size() == 1 ? parts.values().iterator().next() : meshes(everything, sprites, doubleSided);
        return new LoadedModel(parts, all);
    }

    private static ModelMesh[] meshes(List<ModelFace> faces, Function<ResourceLocation, TextureAtlasSprite> sprites, boolean doubleSided) {
        List<ModelMesh> meshes = new ArrayList<>();
        for (ModelRenderKind kind : ModelRenderKind.values()) {
            ModelMesh mesh = mesh(kind, faces, sprites, doubleSided);
            if (mesh != null) {
                meshes.add(mesh);
            }
        }
        return meshes.toArray(ModelMesh[]::new);
    }

    @Nullable
    private static ModelMesh mesh(ModelRenderKind kind, List<ModelFace> faces, Function<ResourceLocation, TextureAtlasSprite> sprites, boolean doubleSided) {
        int vertices;
        VertexArray vertexArray;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 4 * Math.max(16, faces.size()))) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (ModelFace face : faces) {
                if (face.kind() == kind) {
                    quad(builder, face, sprites.apply(face.texture()));
                }
            }
            // the mesh reads from the byte buffer, so it has to be uploaded before the buffer is closed
            MeshData mesh = builder.build();
            if (mesh == null) {
                return null;
            }
            vertices = mesh.drawState().vertexCount();
            vertexArray = VertexArray.create();
            vertexArray.upload(mesh, VertexArray.DrawUsage.STATIC);
        }
        int instanceBuffer = vertexArray.getOrCreateBuffer(VertexArray.INSTANCE_BUFFER);
        defineInstances(vertexArray.editFormat(), instanceBuffer);
        VertexArray.unbind();
        return new ModelMesh(kind, vertexArray, instanceBuffer, vertices, doubleSided);
    }

    private static void quad(BufferBuilder builder, ModelFace face, TextureAtlasSprite sprite) {
        List<ModelVertex> vertices = face.vertices();
        for (int i = 0; i < 4; i++) {
            ModelVertex vertex = vertices.get(Math.min(i, vertices.size() - 1));
            Vector3f position = vertex.position();
            Vector3f normal = vertex.normal();
            float u = face.atlasUv() ? vertex.u() : Mth.lerp(vertex.u(), sprite.getU0(), sprite.getU1());
            float v = face.atlasUv() ? vertex.v() : Mth.lerp(vertex.v(), sprite.getV0(), sprite.getV1());
            builder.addVertex(position.x(), position.y(), position.z())
                    .setColor(face.shade() ? SHADED : UNSHADED)
                    .setUv(u, v)
                    .setUv2(0, 0)
                    .setNormal(normal.x(), normal.y(), normal.z());
        }
    }

    private static void defineInstances(VertexArrayBuilder format, int instanceBuffer) {
        int pose = FIRST_INSTANCE_ATTRIBUTE;
        format.defineVertexBuffer(INSTANCE_BINDING, instanceBuffer, 0, ModelMesh.STRIDE, 1);
        for (int column = 0; column < 4; column++) {
            format.setVertexAttribute(pose + column, INSTANCE_BINDING, 4, VertexArrayBuilder.DataType.FLOAT, false, column * 16);
        }
        for (int column = 0; column < 3; column++) {
            format.setVertexAttribute(pose + 4 + column, INSTANCE_BINDING, 3, VertexArrayBuilder.DataType.FLOAT, false, 64 + column * 12);
        }
        format.setVertexIAttribute(pose + 7, INSTANCE_BINDING, 2, VertexArrayBuilder.DataType.INT, 100);
        format.setVertexAttribute(pose + 8, INSTANCE_BINDING, 4, VertexArrayBuilder.DataType.UNSIGNED_BYTE, true, 108);
    }
}
