package xyz.volcanobay.empiric.client.editor.layout;

import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.client.ui.ClientUiHost;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.ui.UiHost;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The editor on a client: real layout files, the game's font and themes, and the client's modules.
 */
public final class ClientEditorEnvironment implements EditorEnvironment {

    @Override
    public String savePath(String id) {
        return LayoutFiles.path(id).toString();
    }

    @Override
    public String save(String id, String description, BlueprintNode root) throws IOException {
        return LayoutFiles.save(id, description, root).toString();
    }

    @Override
    public UiHost previewHost() {
        return new ClientUiHost(Minecraft.getInstance().font, () -> {
        });
    }

    @Override
    public List<Module<?>> modules() {
        List<Module<?>> modules = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (UiScreenType<?> screen : UiScreens.all()) {
            if (seen.add(screen.moduleType())) {
                EmpiricCore.modules().forEach(Side.CLIENT, screen.moduleType(), modules::add);
            }
        }
        return modules;
    }
}
