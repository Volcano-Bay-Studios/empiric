package xyz.volcanobay.empiric.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
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
        modEventBus.addListener(this::registerGeometryLoaders);
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
