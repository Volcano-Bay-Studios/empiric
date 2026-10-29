package xyz.volcanobay.empiric.api.model;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The models mods have asked for. Register them while the mod initialises, before the first resource load.
 * <p>
 * A JSON model's textures are added to the block atlas for you. An OBJ's aren't, so they have to be in the atlas
 * already, for example through {@code assets/minecraft/atlases/blocks.json}.
 */
public final class EmpiricModels {

    private static final Map<ResourceLocation, EmpiricModel> MODELS = new LinkedHashMap<>();

    private EmpiricModels() {
    }

    /**
     * Registers a model, or returns the one already registered under the same id. A path ending in {@code .obj}
     * is an OBJ, and any other is a JSON block model without its extension.
     */
    public static synchronized EmpiricModel register(ResourceLocation id) {
        return MODELS.computeIfAbsent(id, EmpiricModel::new);
    }

    public static synchronized List<EmpiricModel> all() {
        return new ArrayList<>(MODELS.values());
    }

    /**
     * The ids of the JSON models, which the loaders hand to vanilla so their textures are stitched.
     */
    public static synchronized List<ResourceLocation> jsonIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (EmpiricModel model : MODELS.values()) {
            if (!model.isObj()) {
                ids.add(model.id());
            }
        }
        return ids;
    }
}
