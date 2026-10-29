package xyz.volcanobay.empiric.client.model;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A loaded model, holding the meshes for the whole model and for each named part. A model with a single part
 * shares its meshes between the two.
 */
final class LoadedModel implements AutoCloseable {

    static final LoadedModel EMPTY = new LoadedModel(Map.of(), new ModelMesh[0]);
    private static final ModelMesh[] NONE = new ModelMesh[0];

    private final Map<String, ModelMesh[]> parts;
    private final ModelMesh[] all;

    LoadedModel(Map<String, ModelMesh[]> parts, ModelMesh[] all) {
        this.parts = parts;
        this.all = all;
    }

    /**
     * The meshes to draw for a part, or for the whole model when the part is null.
     */
    ModelMesh[] meshes(@Nullable String part) {
        if (part == null) {
            return all;
        }
        return parts.getOrDefault(part, NONE);
    }

    Set<String> partNames() {
        return Set.copyOf(parts.keySet());
    }

    int meshCount() {
        return distinct().size();
    }

    int vertices() {
        int total = 0;
        for (ModelMesh mesh : distinct()) {
            total += mesh.vertices();
        }
        return total;
    }

    private Set<ModelMesh> distinct() {
        Set<ModelMesh> meshes = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(meshes, all);
        for (ModelMesh[] part : parts.values()) {
            Collections.addAll(meshes, part);
        }
        return meshes;
    }

    @Override
    public void close() {
        for (ModelMesh mesh : distinct()) {
            mesh.close();
        }
    }
}
