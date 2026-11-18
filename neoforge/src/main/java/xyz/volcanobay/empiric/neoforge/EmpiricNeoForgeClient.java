package xyz.volcanobay.empiric.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import xyz.volcanobay.empiric.client.ClientRendererProviders;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import xyz.volcanobay.empiric.api.model.EmpiricModels;
import xyz.volcanobay.empiric.client.model.ModelRenderer;
import xyz.volcanobay.empiric.client.ui.UiMenuScreen;
import xyz.volcanobay.empiric.registry.EmpiricMenus;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.common.NeoForge;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.EmpiricClient;
import xyz.volcanobay.empiric.neoforge.client.FullOctUnbakedModel;

@Mod(value = Empiric.MODID, dist = Dist.CLIENT)
public class EmpiricNeoForgeClient {

    public EmpiricNeoForgeClient(IEventBus modEventBus) {
        EmpiricClient.init();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> EmpiricClient.clientTick());
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(EmpiricClient::bindRegistrations));
        modEventBus.addListener(this::registerGeometryLoaders);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener((RegisterMenuScreensEvent event) -> event.register(EmpiricMenus.UI.get(), UiMenuScreen::new));
        modEventBus.addListener((ModelEvent.RegisterAdditional event) -> {
            for (ResourceLocation id : EmpiricModels.jsonIds()) {
                event.register(ModelResourceLocation.standalone(id));
            }
        });
        modEventBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) manager -> ModelRenderer.invalidate()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (EmpiricRegistration.PendingRenderer renderer : EmpiricRegistration.renderers()) {
            if (renderer.entity()) {
                event.registerEntityRenderer((EntityType) renderer.type().get(), ClientRendererProviders.entity(renderer.rendererClass()));
            } else {
                event.registerBlockEntityRenderer((BlockEntityType) renderer.type().get(), ClientRendererProviders.blockEntity(renderer.rendererClass()));
            }
        }
    }

    private void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Empiric.id("full_oct_block"), new IGeometryLoader<FullOctUnbakedModel>() {
            @Override
            public FullOctUnbakedModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
                return new FullOctUnbakedModel();
            }
        });
    }
}
