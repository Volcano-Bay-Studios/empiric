package xyz.volcanobay.empiric.api.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.client.ui.UiClient;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.system.UiSystem;

import java.util.function.Function;

/**
 * A screen for one module type. The factory builds the root widget on the client, and every player viewing the same
 * module sees and edits the same synced fields.
 */
public final class UiScreenType<M extends Module<?>> {

    private final ResourceLocation id;
    private final ModuleType<M, ?> moduleType;
    private final Function<M, ? extends Widget> factory;
    private boolean presence;

    UiScreenType(ResourceLocation id, ModuleType<M, ?> moduleType, Function<M, ? extends Widget> factory) {
        this.id = id;
        this.moduleType = moduleType;
        this.factory = factory;
    }

    public ResourceLocation id() {
        return id;
    }

    public ModuleType<M, ?> moduleType() {
        return moduleType;
    }

    /**
     * Shows every other viewer's pointer on this screen, with their name, wherever it's open: as a screen, a menu or a
     * world surface. Off by default.
     */
    public UiScreenType<M> presence(boolean presence) {
        this.presence = presence;
        return this;
    }

    public boolean presence() {
        return presence;
    }

    public Widget create(M module) {
        return factory.apply(module);
    }

    /**
     * Opens the screen for a player from the server, if the module allows it.
     */
    public boolean open(ServerPlayer player, M module) {
        return UiSystem.open(player, this, module);
    }

    /**
     * Asks the server to open the screen for the local player.
     */
    public void request(M module) {
        UiClient.request(this, module);
    }

    /**
     * Opens the screen on the client only. The server doesn't track it as a viewer, but edits still sync.
     */
    public void openLocal(M module) {
        UiClient.open(this, module, false);
    }

    @SuppressWarnings("unchecked")
    public M cast(Module<?> module) {
        return module != null && module.type() == moduleType ? (M) module : null;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
