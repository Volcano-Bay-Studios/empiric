package xyz.volcanobay.empiric.system;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.attachment.WorldAttachment;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachment;
import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleEvents;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.SyncedFields;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncC2SPacket;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;

import java.util.BitSet;

public final class ModuleSyncSystem {

    private ModuleSyncSystem() {
    }

    public static void register() {
        EmpiricCore.modules().listen(ModuleEvents.DETACHED, (module, ignored) -> removed(module));
    }

    /**
     * Applies a module sync packet's data to the module it points at on {@code side}, attaching it if needed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void apply(byte[] data, Side side) {
        ModuleSystem modules = EmpiricCore.modules();
        DataBuffer buffer = EmpiricBuffers.buffer(data);
        DataContext context = modules.specs().context(Encoding.PACKED);
        try {
            ModuleSystem.Target target = modules.readTarget(buffer, side, context);
            Attachment attachment = target.attachment();
            ModuleType type = target.type();
            if (attachment == null) {
                return;
            }
            Module module = attachment.modules().get(type);
            if (module == null) {
                if (!type.isEnabled()) {
                    return;
                }
                module = modules.attach(attachment, type);
            }
            ObjectSpec spec = type.spec();
            BitSet fields;
            if (buffer.takeBoolean()) {
                spec.update(buffer, module, context);
                fields = SyncedFields.full(spec).fields();
            } else {
                fields = spec.readDirty(buffer, module, context);
            }
            modules.acceptRemote(module, fields);
        } catch (RuntimeException e) {
            Empiric.LOGGER.error("Couldn't apply a module sync", e);
        }
    }

    private static void removed(Module<?> module) {
        if (module.attachment().side() != Side.SERVER || !(module instanceof NetworkBehavior network)) {
            return;
        }
        if (module.attachment() instanceof NetworkAttachment attachment && attachment.id() == SpatialNetworkMap.RESTORING) {
            return;
        }
        VeilPacketManager.PacketSink sink = network.syncTarget();
        if (sink != null) {
            send(sink, ModuleRemoveS2CPacket.of(module));
        }
    }

    private static void send(VeilPacketManager.PacketSink sink, CustomPacketPayload packet) {
        try {
            sink.sendPacket(packet);
        } catch (UnsupportedOperationException e) {
            Empiric.LOGGER.debug("Skipped {} for a client that can't receive Empiric packets", packet.type().id());
        }
    }

    public static VeilPacketManager.PacketSink target(Module<?> module) {
        Attachment attachment = module.attachment();
        if (attachment instanceof ModuleAttachment moduleAttachment) {
            Module<?> parent = moduleAttachment.parent();
            return parent instanceof NetworkBehavior network ? network.syncTarget() : target(parent);
        }
        if (attachment instanceof WorldAttachment world) {
            return world.syncTarget();
        }
        return null;
    }

    public static void flush() {
        EmpiricCore.modules().flushDirty(Side.SERVER, (module, dirty) -> {
            if (module instanceof NetworkBehavior network) {
                VeilPacketManager.PacketSink sink = network.syncTarget();
                if (sink != null) {
                    for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.dirty(module, dirty)) {
                        send(sink, packet);
                    }
                }
            }
        });
    }

    public static void flushClient() {
        EmpiricCore.modules().flushDirty(Side.CLIENT, (module, dirty) -> {
            if (module instanceof NetworkBehavior) {
                VeilPacketManager.server().sendPacket(ModuleSyncC2SPacket.of(module, dirty));
            }
        });
    }

    public static void syncTo(ServerPlayer player, Attachment attachment) {
        if (!attachment.modules().hasModules()) {
            return;
        }
        VeilPacketManager.PacketSink sink = VeilPacketManager.player(player);
        attachment.modules().forEach(module -> syncModule(sink, module));
    }

    private static void syncModule(VeilPacketManager.PacketSink sink, Module<?> module) {
        if (module instanceof NetworkBehavior network) {
            for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.full(module)) {
                send(sink, packet);
            }
            network.onTracked(sink);
        }
        if (module.hasChildren()) {
            module.children().modules().forEach(child -> syncModule(sink, child));
        }
    }

    public static void playerJoined(ServerPlayer player) {
        try {
            DataSyncSystem.syncTo(VeilPacketManager.player(player));
            DebugSubscriptionSystem.syncTo(player);
        } catch (UnsupportedOperationException e) {
            Empiric.LOGGER.debug("{} can't receive Empiric packets, skipping its sync", player.getGameProfile().getName());
            return;
        }
        playerReady(player);
    }

    public static void playerReady(ServerPlayer player) {
        SpatialNetworkSystem.forgetViewer(player);
        NetworkOverlaySystem.playerReady(player);
        syncTo(player, ServerAttachment.of(Side.SERVER));
        syncTo(player, LevelAttachment.of(player.serverLevel()));
        syncTo(player, EntityAttachment.of(player));
    }

    public static void entityTracked(ServerPlayer player, Entity entity) {
        syncTo(player, EntityAttachment.of(entity));
    }

    public static void chunkSent(ServerPlayer player, LevelChunk chunk) {
        ChunkAttachment chunkAttachment = ChunkAttachment.of(chunk);
        if (chunkAttachment.isActive()) {
            syncTo(player, chunkAttachment);
            for (SpatialModuleStore<?> store : SpatialModuleStore.stores()) {
                store.forEachIn(chunkAttachment, spatial -> syncTo(player, spatial));
            }
        }
        SpatialNetworkSystem.chunkSent(player, chunk.getPos(), attachment -> syncTo(player, attachment));
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockEntityAttachment attachment = BlockEntityAttachment.of(blockEntity);
            if (attachment.isActive()) {
                syncTo(player, attachment);
            }
        }
    }
}
