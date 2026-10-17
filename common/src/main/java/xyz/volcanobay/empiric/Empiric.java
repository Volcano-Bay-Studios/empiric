package xyz.volcanobay.empiric;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.api.behavior.ServerTickBehavior;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.registry.EmpiricBlockEntities;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;
import xyz.volcanobay.empiric.registry.EmpiricChemicals;
import xyz.volcanobay.empiric.registry.EmpiricDataComponents;
import xyz.volcanobay.empiric.platform.EmpiricPlatform;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.plugin.EmpiricPlugin;
import xyz.volcanobay.empiric.gametest.EmpiricTestBlocks;
import xyz.volcanobay.empiric.gametest.EmpiricTestModules;
import xyz.volcanobay.empiric.networking.EmpiricMessages;
import xyz.volcanobay.empiric.system.BlockModuleSystem;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.NetworkOverlaySystem;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;
import xyz.volcanobay.empiric.system.OctModuleSystem;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Empiric {

    public static final String MODID = "empiric";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init(List<? extends EmpiricPlugin> discovered) {
        List<EmpiricPlugin> plugins = new ArrayList<>(discovered);
        if (EmpiricTestModules.enabled()) {
            plugins.add(new EmpiricTestModules());
            EmpiricTestBlocks.bootstrap();
        }
        LOGGER.info("Empiric is initializing with {} plugins.", plugins.size());

        BlockModuleSystem.bootstrap();
        OctModuleSystem.bootstrap();
        EmpiricChemicals.bootstrap();
        EmpiricBlocks.bootstrap();
        EmpiricBlockEntities.bootstrap();
        EmpiricDataComponents.bootstrap();
        EmpiricCore.bootstrap(new EmpiricPlatform(), plugins);
        EmpiricMessages.register();
        ModuleSyncSystem.register();
        LOGGER.info("Empiric registered {} module types.", EmpiricCore.modules().types().size());
    }

    public static void serverTick(MinecraftServer server) {
        EmpiricCore.modules().processPending(Side.SERVER);
        EmpiricCore.modules().forEach(Side.SERVER, EmpiricBehaviors.SERVER_TICK, ServerTickBehavior::serverTick);
        SpatialNetworkSystem.processDropped();
        NetworkOverlaySystem.tick(server);
        ModuleSyncSystem.flush();
    }

    public static ResourceLocation id(String loc) {
        return ResourceLocation.fromNamespaceAndPath(MODID, loc);
    }
}
