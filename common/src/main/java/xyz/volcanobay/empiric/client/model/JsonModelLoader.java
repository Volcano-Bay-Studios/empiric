package xyz.volcanobay.empiric.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;
import xyz.volcanobay.empiric.model.ModelFace;
import xyz.volcanobay.empiric.model.ModelGeometry;
import xyz.volcanobay.empiric.model.ModelRenderKind;
import xyz.volcanobay.empiric.model.ModelVertex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads a JSON block model into geometry with vanilla's own parsing and quad baking, so parents, texture references,
 * element rotation and UVs behave as they do for blocks.
 * <p>
 * Part names and the render kind come from the JSON itself. The render kind defaults to cutout. A group's name wins over
 * its elements' names, and the outermost group wins if they nest.
 * <p>
 * Tint indices are ignored, since there's no block or biome to colour from. Use an instance's tint instead.
 */
final class JsonModelLoader {

    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final int MAX_PARENT_DEPTH = 32;

    private JsonModelLoader() {
    }

    private record Source(BlockModel model, JsonObject json) {
    }

    static ModelGeometry load(ResourceManager manager, Function<ResourceLocation, TextureAtlasSprite> sprites, ResourceLocation id) throws IOException {
        Map<ResourceLocation, Source> sources = new HashMap<>();
        Source root = source(manager, sources, id);
        root.model().resolveParents(location -> resolve(manager, sources, location));
        ModelRenderKind kind = renderKind(manager, sources, root);
        List<BlockElement> elements = root.model().getElements();
        String[] names = partNames(owner(manager, sources, root).json(), elements.size());
        Map<String, List<ModelFace>> parts = new LinkedHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            BlockElement element = elements.get(i);
            List<ModelFace> faces = parts.computeIfAbsent(names[i], name -> new ArrayList<>());
            for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                BlockElementFace face = entry.getValue();
                Material material = root.model().getMaterial(face.texture());
                TextureAtlasSprite sprite = sprites.apply(material.texture());
                BakedQuad quad = FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, entry.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade);
                faces.add(face(quad, material.texture(), kind));
            }
        }
        parts.values().removeIf(List::isEmpty);
        return new ModelGeometry(parts);
    }

    private static ModelFace face(BakedQuad quad, ResourceLocation texture, ModelRenderKind kind) {
        int[] data = quad.getVertices();
        Vec3i direction = quad.getDirection().getNormal();
        List<ModelVertex> vertices = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int offset = i * FaceBakery.VERTEX_INT_SIZE;
            vertices.add(new ModelVertex(
                    new Vector3f(Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2])),
                    new Vector3f(direction.getX(), direction.getY(), direction.getZ()),
                    Float.intBitsToFloat(data[offset + FaceBakery.UV_INDEX]),
                    Float.intBitsToFloat(data[offset + FaceBakery.UV_INDEX + 1])));
        }
        return new ModelFace(vertices, texture, true, kind, quad.isShade());
    }

    private static UnbakedModel resolve(ResourceManager manager, Map<ResourceLocation, Source> sources, ResourceLocation location) {
        try {
            return source(manager, sources, location).model();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Source source(ResourceManager manager, Map<ResourceLocation, Source> sources, ResourceLocation id) throws IOException {
        Source cached = sources.get(id);
        if (cached != null) {
            return cached;
        }
        Source source;
        if (id.getPath().startsWith("builtin/")) {
            source = new Source(BlockModel.fromString("{}"), new JsonObject());
        } else {
            String text = ModelLoader.read(manager, id.withPath(path -> "models/" + path + ".json"));
            source = new Source(BlockModel.fromString(text), JsonParser.parseString(text).getAsJsonObject());
        }
        sources.put(id, source);
        return source;
    }

    private static ResourceLocation parent(Source source) {
        String parent = GsonHelper.getAsString(source.json(), "parent", "");
        return parent.isEmpty() ? null : ResourceLocation.parse(parent);
    }

    private static ModelRenderKind renderKind(ResourceManager manager, Map<ResourceLocation, Source> sources, Source root) throws IOException {
        Source current = root;
        for (int depth = 0; depth < MAX_PARENT_DEPTH && current != null; depth++) {
            if (current.json().has("render_type")) {
                return ModelRenderKind.fromName(GsonHelper.getAsString(current.json(), "render_type"), ModelRenderKind.CUTOUT);
            }
            ResourceLocation parent = parent(current);
            current = parent != null ? source(manager, sources, parent) : null;
        }
        return ModelRenderKind.CUTOUT;
    }

    /**
     * The model in the parent chain that has the elements. Only its names and groups count.
     */
    private static Source owner(ResourceManager manager, Map<ResourceLocation, Source> sources, Source root) throws IOException {
        Source current = root;
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            ResourceLocation parent = parent(current);
            if (!GsonHelper.getAsJsonArray(current.json(), "elements", new JsonArray()).isEmpty() || parent == null) {
                return current;
            }
            current = source(manager, sources, parent);
        }
        return current;
    }

    private static String[] partNames(JsonObject json, int count) {
        String[] names = new String[count];
        JsonArray elements = GsonHelper.getAsJsonArray(json, "elements", new JsonArray());
        for (int i = 0; i < count; i++) {
            names[i] = i < elements.size() && elements.get(i).isJsonObject()
                    ? GsonHelper.getAsString(elements.get(i).getAsJsonObject(), "name", ModelGeometry.DEFAULT_PART)
                    : ModelGeometry.DEFAULT_PART;
        }
        for (JsonElement group : GsonHelper.getAsJsonArray(json, "groups", new JsonArray())) {
            if (group.isJsonObject()) {
                nameGroup(group.getAsJsonObject(), null, names);
            }
        }
        return names;
    }

    private static void nameGroup(JsonObject group, String outer, String[] names) {
        String own = GsonHelper.getAsString(group, "name", "");
        String name = outer != null ? outer : own.isEmpty() ? null : own;
        for (JsonElement child : GsonHelper.getAsJsonArray(group, "children", new JsonArray())) {
            if (child.isJsonObject()) {
                nameGroup(child.getAsJsonObject(), name, names);
            } else if (child.isJsonPrimitive() && name != null) {
                int index = child.getAsInt();
                if (index >= 0 && index < names.length) {
                    names[index] = name;
                }
            }
        }
    }
}
