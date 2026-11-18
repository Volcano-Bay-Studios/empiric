package xyz.volcanobay.empiric.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import xyz.volcanobay.empiric.core.registry.ClassScanner;
import xyz.volcanobay.empiric.core.registry.ScanResult;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;
import xyz.volcanobay.empiric.registry.ScanFilter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.volcanobay.empiric.api.data.BlockDataType;
import xyz.volcanobay.empiric.data.DataEntryLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.gametest.EmpiricTestModules;
import xyz.volcanobay.empiric.gametest.registry.RegistryFixtures;
import xyz.volcanobay.empiric.command.EmpiricCommands;
import xyz.volcanobay.empiric.core.plugin.EmpiricPlugin;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

public class EmpiricFabric implements ModInitializer {

    private static void registerReloadListener(String name, PreparableReloadListener listener) {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return Empiric.id(name);
            }

            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier, ResourceManager manager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
                return listener.reload(barrier, manager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
            }
        });
    }

    private static List<Path> rootsOf(ModContainer container) {
        List<Path> roots = new ArrayList<>(container.getRootPaths());
        if (container.getOrigin().getKind() == ModOrigin.Kind.PATH) {
            for (Path path : container.getOrigin().getPaths()) {
                if (Files.isDirectory(path) && !roots.contains(path)) {
                    roots.add(path);
                }
            }
        }
        return roots;
    }

    private static void addCodeSource(Map<Path, String> roots, Class<?> cls, String namespace) {
        try {
            Path code = Path.of(cls.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(code)) {
                roots.putIfAbsent(code, namespace);
            }
        } catch (URISyntaxException | RuntimeException e) {
            Empiric.LOGGER.warn("Couldn't find where the classes of {} are", cls.getName(), e);
        }
    }

    private static boolean skipped(String id) {
        return id.equals("java") || id.equals("minecraft") || id.equals("fabricloader") || id.startsWith("fabric-");
    }

    private static ScanResult scan(Map<Path, String> roots) {
        Set<String> seen = new HashSet<>();
        List<ScanResult> results = new ArrayList<>();
        for (Map.Entry<Path, String> root : roots.entrySet()) {
            ClassScanner scanner = new ClassScanner();
            scanner.scan(root.getKey(), root.getValue());
            results.add(ScanFilter.keepClasses(scanner.result(), seen::add));
        }
        return ScanResult.merge(results.toArray(new ScanResult[0]));
    }

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        List<EmpiricPlugin> plugins = new ArrayList<>();
        Map<Path, String> roots = new LinkedHashMap<>();
        for (EntrypointContainer<EmpiricPlugin> container : loader.getEntrypointContainers("empiric", EmpiricPlugin.class)) {
            EmpiricPlugin plugin = container.getEntrypoint();
            plugins.add(plugin);
            addCodeSource(roots, plugin.getClass(), container.getProvider().getMetadata().getId());
        }
        for (ModContainer container : loader.getAllMods()) {
            String id = container.getMetadata().getId();
            if (skipped(id)) {
                continue;
            }
            for (Path root : rootsOf(container)) {
                roots.putIfAbsent(root, id);
            }
        }
        addCodeSource(roots, Empiric.class, Empiric.MODID);
        Empiric.init(plugins, scan(roots), registry -> -1, key -> FabricRegistryBuilder.createSimple(key).buildAndRegister());
        RegistryEntryAddedCallback.event(BuiltInRegistries.REGISTRY).register((rawId, id, registry) -> EmpiricRegistration.runForeign());
        if (EmpiricTestModules.enabled()) {
            FabricRegistryBuilder.createSimple(RegistryFixtures.Foreign.KEY).buildAndRegister();
        }
        EmpiricRegistration.runForeign();
        for (EmpiricRegistration.PendingAttributes attributes : EmpiricRegistration.attributes()) {
            AttributeSupplier.Builder builder = attributes.builder().get();
            if (builder != null) {
                FabricDefaultAttributeRegistry.register(attributes.type().get(), builder);
            }
        }
        for (Map.Entry<ResourceKey<CreativeModeTab>, List<Supplier<? extends ItemLike>>> tab : EmpiricRegistration.creativeTabs().entrySet()) {
            ItemGroupEvents.modifyEntriesEvent(tab.getKey()).register(entries -> {
                for (Supplier<? extends ItemLike> item : tab.getValue()) {
                    entries.accept(item.get());
                }
            });
        }

        ServerLifecycleEvents.SERVER_STARTING.register(server -> EmpiricRegistration.requireResolved());
        ServerLifecycleEvents.SERVER_STARTING.register(WorldModuleSystem::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WorldModuleSystem.serverStopped());
        ServerTickEvents.END_SERVER_TICK.register(Empiric::serverTick);
        ServerWorldEvents.LOAD.register((server, level) -> WorldModuleSystem.loadLevel(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> WorldModuleSystem.unloadLevel(level));
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof FullOctBlockEntity octBlockEntity) {
                octBlockEntity.onLoad();
            }
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> WorldModuleSystem.chunkLoaded(chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> WorldModuleSystem.chunkUnloaded(chunk));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> WorldModuleSystem.entityLoaded(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> WorldModuleSystem.entityUnloaded(entity));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ModuleSyncSystem.playerJoined(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ModuleSyncSystem.playerReady(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> ModuleSyncSystem.playerReady(player));
        registerReloadListener("data_entries", DataEntryLoader.INSTANCE);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> BlockDataType.clearCaches());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EmpiricCommands.registerCommands(dispatcher));
    }
}
