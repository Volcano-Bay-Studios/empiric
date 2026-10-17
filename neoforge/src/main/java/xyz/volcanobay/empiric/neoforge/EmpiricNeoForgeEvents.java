package xyz.volcanobay.empiric.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import xyz.volcanobay.empiric.api.data.BlockDataType;
import xyz.volcanobay.empiric.data.DataEntryLoader;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.command.EmpiricCommands;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

@EventBusSubscriber(modid = Empiric.MODID)
public class EmpiricNeoForgeEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        WorldModuleSystem.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WorldModuleSystem.serverStopped();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Empiric.serverTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WorldModuleSystem.loadLevel(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WorldModuleSystem.unloadLevel(level);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            WorldModuleSystem.chunkLoaded(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            WorldModuleSystem.chunkUnloaded(chunk);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        WorldModuleSystem.entityLoaded(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        WorldModuleSystem.entityUnloaded(event.getEntity());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModuleSyncSystem.playerJoined(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModuleSyncSystem.playerReady(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModuleSyncSystem.playerReady(player);
        }
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ChemicalRegistry.Reloader.INSTANCE);
        event.addListener(DataEntryLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        BlockDataType.clearCaches();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        EmpiricCommands.registerCommands(event.getDispatcher());
    }
}
