package xyz.volcanobay.empiric;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.api.behavior.ServerTickBehavior;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.registry.ScanResult;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;
import xyz.volcanobay.empiric.registry.ScanFilter;
import xyz.volcanobay.empiric.registry.EmpiricBlockEntities;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;
import xyz.volcanobay.empiric.registry.EmpiricDataComponents;
import xyz.volcanobay.empiric.registry.EmpiricMenus;
import xyz.volcanobay.empiric.platform.EmpiricPlatform;
import xyz.volcanobay.empiric.platform.ModCompat;
import xyz.volcanobay.empiric.system.SubLevelModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.system.SystemPhase;
import xyz.volcanobay.empiric.core.plugin.EmpiricPlugin;
import xyz.volcanobay.empiric.gametest.EmpiricTestBlocks;
import xyz.volcanobay.empiric.gametest.EmpiricTestModules;
import xyz.volcanobay.empiric.networking.EmpiricMessages;
import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.system.BlockModuleSystem;
import xyz.volcanobay.empiric.system.McSystems;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.NetworkOverlaySystem;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;
import xyz.volcanobay.empiric.system.TrackingSystem;
import xyz.volcanobay.empiric.system.UiSystem;
import xyz.volcanobay.empiric.system.OctModuleSystem;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public class Empiric {

    public static final String MODID = "empiric";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String GAMETEST_PACKAGE = "xyz.volcanobay.empiric.gametest.";

    public static void init(List<? extends EmpiricPlugin> discovered, ScanResult scan, ToIntFunction<String> rank, Function<ResourceKey<Registry<Object>>, Registry<Object>> registryFactory) {
        List<EmpiricPlugin> plugins = new ArrayList<>(discovered);
        if (EmpiricTestModules.enabled()) {
            plugins.add(new EmpiricTestModules());
            EmpiricTestBlocks.bootstrap();
            EmpiricTestBlocks.parts();
        }
        LOGGER.info("Empiric is initializing with {} plugins.", plugins.size());

        BlockModuleSystem.bootstrap();
        OctModuleSystem.bootstrap();
        EmpiricBlocks.bootstrap();
        EmpiricBlockEntities.bootstrap();
        EmpiricDataComponents.bootstrap();
        EmpiricMenus.bootstrap();
        EmpiricRegistration.setup(EmpiricCore.engine(), EmpiricTestModules.enabled() ? scan : withoutGametests(scan), rank, registryFactory);
        EmpiricCore.engine().devMode(Platform.isDevelopment() || EmpiricTestModules.enabled());
        McSystems.configure(EmpiricCore.engine().systems());
        EmpiricCore.bootstrap(new EmpiricPlatform(), plugins);
        EmpiricRegistration.runVanilla();
        EmpiricMessages.register(Platform.packets("1"));
        ModuleSyncSystem.register();
        UiSystem.register();
        if (ModCompat.SABLE) {
            SubLevelModuleSystem.bootstrap();
        }
        LOGGER.info("Empiric registered {} module types.", EmpiricCore.modules().types().size());
    }

    private static ScanResult withoutGametests(ScanResult scan) {
        return ScanFilter.keepClasses(scan, name -> !name.startsWith(GAMETEST_PACKAGE));
    }

    public static void serverTick(MinecraftServer server) {
        EmpiricCore.modules().tick(Side.SERVER);
        EmpiricCore.modules().processPending(Side.SERVER);
        EmpiricCore.engine().systems().run(Side.SERVER, SystemPhase.DEFAULT, 1.0 / 20);
        EmpiricCore.modules().forEach(Side.SERVER, EmpiricBehaviors.SERVER_TICK, ServerTickBehavior::serverTick);
        EmpiricCore.engine().systems().run(Side.SERVER, SystemPhase.LATE, 1.0 / 20);
        SpatialNetworkSystem.processDropped();
        NetworkOverlaySystem.tick(server);
        UiSystem.tick(server);
        TrackingSystem.tick();
        ModuleSyncSystem.flush();
    }

    public static ResourceLocation id(String loc) {
        return ResourceLocation.fromNamespaceAndPath(MODID, loc);
    }
}
