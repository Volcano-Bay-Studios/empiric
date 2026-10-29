package xyz.volcanobay.empiric.client.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.model.ModelGeometry;
import xyz.volcanobay.empiric.model.ObjParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads a registered model's file into geometry.
 */
final class ModelLoader {

    private ModelLoader() {
    }

    static ModelGeometry load(EmpiricModel model, ResourceManager manager, Function<ResourceLocation, TextureAtlasSprite> sprites) throws IOException {
        if (!model.isObj()) {
            return JsonModelLoader.load(manager, sprites, model.id());
        }
        ResourceLocation file = model.file();
        String directory = file.getPath().substring(0, file.getPath().lastIndexOf('/') + 1);
        return ObjParser.parse(read(manager, file), file.getNamespace(), name -> {
            try {
                return read(manager, file.withPath(directory + name));
            } catch (IOException e) {
                Empiric.LOGGER.warn("Model {} names a material library {} that couldn't be read", model.id(), name);
                return null;
            }
        });
    }

    static String read(ResourceManager manager, ResourceLocation file) throws IOException {
        try (BufferedReader reader = manager.openAsReader(file)) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
