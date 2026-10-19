package xyz.volcanobay.empiric.client.editor.layout;

import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.UiHost;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;

import java.io.IOException;
import java.util.List;

/**
 * What the layout editor needs from outside the widget tree: where files go, a host for the preview, and the modules a
 * preview can bind to. The client gives files and modules.
 */
public interface EditorEnvironment {

    /**
     * Where {@link #save} writes a layout, for showing before saving.
     */
    String savePath(String id);

    /**
     * Writes a layout file and returns where it went.
     */
    String save(String id, String description, BlueprintNode root) throws IOException;

    /**
     * A host for the preview's own widget tree, so it gets fonts and themes but can't close the editor.
     */
    UiHost previewHost();

    /**
     * Modules on this side that some screen is registered for, which the preview can bind to.
     */
    List<Module<?>> modules();
}
