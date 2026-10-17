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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.data.BlockDataType;
import xyz.volcanobay.empiric.data.DataEntryLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import xyz.volcanobay.empiric.Empiric;
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

    @Override
    public void onInitialize() {
        Empiric.init(FabricLoader.getInstance().getEntrypoints("empiric", EmpiricPlugin.class));

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
        registerReloadListener("chemistry", ChemicalRegistry.Reloader.INSTANCE);
        registerReloadListener("data_entries", DataEntryLoader.INSTANCE);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> BlockDataType.clearCaches());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EmpiricCommands.registerCommands(dispatcher));
    }
}
