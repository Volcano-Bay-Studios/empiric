package xyz.volcanobay.empiric.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.client.ui.UiMenuScreen;
import xyz.volcanobay.empiric.registry.EmpiricMenus;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.EmpiricClient;
import xyz.volcanobay.empiric.fabric.client.oct.FullOctUnbakedModel;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

public class EmpiricFabricClient implements ClientModInitializer {

    private static final ResourceLocation FULL_OCT_MODEL = Empiric.id("block/full_oct_block");

    @Override
    public void onInitializeClient() {
        EmpiricClient.init();
        MenuScreens.register(EmpiricMenus.UI.get(), UiMenuScreen::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> EmpiricClient.clientTick());
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof FullOctBlockEntity octBlockEntity) {
                octBlockEntity.onLoad();
            }
        });
        ModelLoadingPlugin.register(context -> context.modifyModelOnLoad().register((model, modelContext) -> {
            if (FULL_OCT_MODEL.equals(modelContext.resourceId())) {
                return new FullOctUnbakedModel();
            }
            return model;
        }));
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> WorldModuleSystem.chunkLoaded(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> WorldModuleSystem.chunkUnloaded(chunk));
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> WorldModuleSystem.entityLoaded(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> WorldModuleSystem.entityUnloaded(entity));
    }
}
