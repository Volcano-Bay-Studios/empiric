package xyz.volcanobay.empiric.networking.handler;

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
            BitSet applied = spec.readFramed(buffer, module, context, index -> spec.isClientMutable(index)
                    && network.acceptClientUpdate(player, spec.synced()[index].id()));
            for (int index = applied.nextSetBit(0); index >= 0; index = applied.nextSetBit(index + 1)) {
                module.markDirty(spec.synced()[index].id());
            }
            if (!applied.isEmpty()) {
                modules.fire(module, ModuleEvents.SYNCED, new SyncedFields(spec, applied));
            }
            return applied;
        } catch (RuntimeException e) {
            Empiric.LOGGER.warn("Rejected a module update from {}: {}", player.getGameProfile().getName(), e.getMessage());
            return new BitSet();
        }
    }
}
