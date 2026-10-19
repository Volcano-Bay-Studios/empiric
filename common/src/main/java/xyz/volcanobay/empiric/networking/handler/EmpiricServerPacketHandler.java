package xyz.volcanobay.empiric.networking.handler;

import foundry.veil.api.network.VeilPacketManager;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleEvents;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.module.SyncedFields;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncC2SPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiPointerC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiSlotC2SPacket;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.UiSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.BitSet;

public class EmpiricServerPacketHandler {

    public static void handleModuleSync(ModuleSyncC2SPacket packet, ServerPacketContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        if (!player.server.isSameThread()) {
            player.server.execute(() -> handleModuleSync(packet, ctx));
            return;
        }
        apply(player, packet);
    }

    public static void handleUiOpen(UiOpenC2SPacket packet, ServerPacketContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        if (!player.server.isSameThread()) {
            player.server.execute(() -> handleUiOpen(packet, ctx));
            return;
        }
        UiSystem.handleOpen(player, packet);
    }

    public static void handleUiClosed(UiCloseC2SPacket packet, ServerPacketContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        if (!player.server.isSameThread()) {
            player.server.execute(() -> handleUiClosed(packet, ctx));
            return;
        }
        UiSystem.handleClosed(player, packet);
    }

    public static void handleUiSlot(UiSlotC2SPacket packet, ServerPacketContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        if (!player.server.isSameThread()) {
            player.server.execute(() -> handleUiSlot(packet, ctx));
            return;
        }
        UiSystem.slotClick(player, packet.data(), packet.slot(), packet.button());
    }

    public static void handleUiPointer(UiPointerC2SPacket packet, ServerPacketContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        if (!player.server.isSameThread()) {
            player.server.execute(() -> handleUiPointer(packet, ctx));
            return;
        }
        UiSystem.pointer(player, packet.data(), packet.x(), packet.y());
    }

    /**
     * Applies the client mutable fields the player may change. Fields it may not change are sent back with the server's
     * value, so the client doesn't keep showing an edit that never happened.
     */
    public static BitSet apply(ServerPlayer player, ModuleSyncC2SPacket packet) {
        return WorldModuleSystem.withLevel(player.serverLevel(), () -> read(player, packet));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BitSet read(ServerPlayer player, ModuleSyncC2SPacket packet) {
        ModuleSystem modules = EmpiricCore.modules();
        DataBuffer buffer = EmpiricBuffers.buffer(packet.data());
        DataContext context = modules.specs().context(Encoding.PACKED);
        try {
            Module<?> module = modules.readLocator(buffer, Side.SERVER, context);
            if (!(module instanceof NetworkBehavior network)) {
                return new BitSet();
            }
            ObjectSpec spec = module.type().spec();
            BitSet rejected = new BitSet();
            BitSet applied = spec.readFramed(buffer, module, context, index -> {
                boolean accepted = spec.isClientMutable(index) && network.acceptClientUpdate(player, spec.synced()[index].id());
                if (!accepted) {
                    rejected.set(index);
                }
                return accepted;
            });
            for (int index = applied.nextSetBit(0); index >= 0; index = applied.nextSetBit(index + 1)) {
                network.onClientUpdate(player, spec.synced()[index].id());
                module.markDirty(spec.synced()[index].id());
            }
            if (!applied.isEmpty()) {
                modules.fire(module, ModuleEvents.SYNCED, new SyncedFields(spec, applied));
            }
            if (!rejected.isEmpty()) {
                VeilPacketManager.PacketSink sink = VeilPacketManager.player(player);
                for (ModuleSyncS2CPacket correction : ModuleSyncS2CPacket.dirty(module, rejected)) {
                    ModuleSyncSystem.send(sink, correction);
                }
            }
            return applied;
        } catch (RuntimeException e) {
            Empiric.LOGGER.warn("Rejected a module update from {}: {}", player.getGameProfile().getName(), e.getMessage());
            return new BitSet();
        }
    }
}
