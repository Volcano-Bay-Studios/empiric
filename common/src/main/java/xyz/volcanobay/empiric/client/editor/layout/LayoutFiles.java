package xyz.volcanobay.empiric.client.editor.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import foundry.veil.Veil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.data.JsonNodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where the editor saves layouts. In a development environment that's the mod's own source resources, found by walking
 * up from the game directory, so a save lands in the repository. Otherwise it's {@code <gameDir>/empiric/layouts}.
 */
final class LayoutFiles {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SEARCH_DEPTH = 4;
    private static final List<String> RESOURCE_DIRS = List.of("common/src/main/resources", "src/main/resources");

    private LayoutFiles() {
    }

    static Path path(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        String relative = "data/" + location.getNamespace() + "/ui/layouts/" + location.getPath() + ".json";
        Path game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        if (Veil.platform().isDevelopmentEnvironment()) {
            Path resources = sourceResources(game, location.getNamespace());
            if (resources != null) {
                return resources.resolve(relative);
            }
        }
        return game.resolve("empiric/layouts").resolve(location.getNamespace()).resolve(location.getPath() + ".json");
    }

    private static Path sourceResources(Path game, String namespace) {
        Path fallback = null;
        Path dir = game;
        for (int depth = 0; dir != null && depth < SEARCH_DEPTH; depth++, dir = dir.getParent()) {
            for (String candidate : RESOURCE_DIRS) {
                Path resources = dir.resolve(candidate);
                if (Files.isDirectory(resources.resolve("data").resolve(namespace))) {
                    return resources;
                }
                if (fallback == null && Files.isDirectory(resources)) {
                    fallback = resources;
                }
            }
        }
        return fallback;
    }

    static Path save(String id, String description, BlueprintNode root) throws IOException {
        Path path = path(id);
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(JsonNodes.toJson(UiLayouts.write(description, root))) + "\n");
        return path;
    }
}
