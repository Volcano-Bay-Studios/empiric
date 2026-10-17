package xyz.volcanobay.empiric.networking.handler;

import foundry.veil.api.network.handler.ClientPacketContext;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.networking.packet.DebugSubscriptionsS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.networking.packet.NetworkOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.OctBreakS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureSectionS2CPacket;
import xyz.volcanobay.empiric.client.overlay.NetworkOverlay;
import xyz.volcanobay.empiric.client.overlay.TemperatureOverlay;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.system.TemperatureSystem;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.oct.OctClientEffects;
import xyz.volcanobay.empiric.system.DataSyncSystem;
import xyz.volcanobay.empiric.system.DebugSubscriptionSystem;
import xyz.volcanobay.empiric.networking.packet.DataEntriesS2CPacket;
import net.minecraft.world.level.Level;

public class EmpiricClientPacketHandler {

    public static void handleOctBreak(OctBreakS2CPacket packet, ClientPacketContext ctx) {
        Level level = ctx.level();
        if (level == null) {
            return;
        }
        OctClientEffects.playBreak(ctx.client(), level, packet.pos().pos(), packet.pos().octant(), packet.state());
    }

    public static void handleDataEntries(DataEntriesS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleDataEntries(packet, ctx));
            return;
        }
        if (!ctx.client().hasSingleplayerServer()) {
            DataSyncSystem.apply(packet);
        }
    }

    public static void handleDebugSubscriptions(DebugSubscriptionsS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleDebugSubscriptions(packet, ctx));
            return;
        }
        if (!ctx.client().hasSingleplayerServer()) {
            DebugSubscriptionSystem.apply(packet.subscriptions(), packet.suppressed());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handleModuleRemove(ModuleRemoveS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleModuleRemove(packet, ctx));
            return;
        }
        ModuleSystem modules = EmpiricCore.modules();
        try {
            ModuleSystem.Target target = modules.readTarget(EmpiricBuffers.buffer(packet.data()), Side.CLIENT, modules.specs().context(Encoding.PACKED));
            if (target.attachment() != null) {
                ((ModuleType) target.type()).detach((Attachment) target.attachment());
            }
        } catch (RuntimeException e) {
            Empiric.LOGGER.error("Couldn't apply a module removal", e);
        }
    }

    public static void handleTemperatureSection(TemperatureSectionS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleTemperatureSection(packet, ctx));
            return;
        }
        Level level = ctx.level();
        if (level == null) {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(packet.chunk().x, packet.chunk().z);
        if (chunk == null) {
            return;
        }
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        TemperatureModule module = attachment.modules().get(TemperatureSystem.TEMPERATURE);
        if (module == null) {
            if (!TemperatureSystem.TEMPERATURE.isEnabled() || packet.data().length == 0) {
                return;
            }
            module = EmpiricCore.modules().attach(attachment, TemperatureSystem.TEMPERATURE);
        }
        try {
            module.apply(packet.section(), packet.data());
            TemperatureOverlay.INSTANCE.changed(packet.chunk(), packet.section());
        } catch (RuntimeException e) {
            Empiric.LOGGER.error("Couldn't apply a temperature section", e);
        }
    }

    public static void handleNetworkOverlay(NetworkOverlayS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleNetworkOverlay(packet, ctx));
            return;
        }
        NetworkOverlay.INSTANCE.apply(packet);
    }

    public static void handleTemperatureOverlay(TemperatureOverlayS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleTemperatureOverlay(packet, ctx));
            return;
        }
        TemperatureOverlay.INSTANCE.show(packet.radius());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handleModuleSync(ModuleSyncS2CPacket packet, ClientPacketContext ctx) {
        if (!ctx.client().isSameThread()) {
            ctx.client().execute(() -> handleModuleSync(packet, ctx));
            return;
        }
        ModuleSystem modules = EmpiricCore.modules();
        DataBuffer buffer = EmpiricBuffers.buffer(packet.data());
        DataContext context = modules.specs().context(Encoding.PACKED);
        try {
            ModuleSystem.Target target = modules.readTarget(buffer, Side.CLIENT, context);
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
            if (buffer.takeBoolean()) {
                spec.update(buffer, module, context);
            } else {
                spec.readDirty(buffer, module, context);
            }
            modules.acceptRemote(module);
        } catch (RuntimeException e) {
            Empiric.LOGGER.error("Couldn't apply a module sync", e);
        }
    }
}
