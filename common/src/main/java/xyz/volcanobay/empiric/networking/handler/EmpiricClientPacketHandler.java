package xyz.volcanobay.empiric.networking.handler;

import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.networking.packet.DebugSubscriptionsS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleClearS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.networking.packet.NetworkOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.OctBreakS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureSectionS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiEditorS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiPointerS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiSurfaceS2CPacket;
import xyz.volcanobay.empiric.client.ui.PresenceCursors;
import xyz.volcanobay.empiric.client.ui.UiClient;
import xyz.volcanobay.empiric.client.overlay.NetworkOverlay;
import xyz.volcanobay.empiric.client.overlay.TemperatureOverlay;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.system.TemperatureSystem;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.oct.OctClientEffects;
import xyz.volcanobay.empiric.system.DataSyncSystem;
import xyz.volcanobay.empiric.system.DebugSubscriptionSystem;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.networking.packet.DataEntriesS2CPacket;
import net.minecraft.world.level.Level;

import java.util.BitSet;

public class EmpiricClientPacketHandler {

    public static void handleUiOpen(UiOpenS2CPacket packet) {
        UiClient.handleOpen(packet);
    }

    public static void handleUiEditor(UiEditorS2CPacket packet) {
        UiClient.openEditor(packet.layout());
    }

    public static void handleUiSurface(UiSurfaceS2CPacket packet) {
        UiClient.handleSurface(packet);
    }

    public static void handleUiPointer(UiPointerS2CPacket packet) {
        PresenceCursors.handle(packet);
    }

    public static void handleUiClose(UiCloseS2CPacket packet) {
        UiClient.handleClose(packet);
    }

    public static void handleOctBreak(OctBreakS2CPacket packet) {
        Level level = ClientLookup.level();
        if (level == null) {
            return;
        }
        OctClientEffects.playBreak(Minecraft.getInstance(), level, packet.pos().pos(), packet.pos().octant(), packet.state());
    }

    public static void handleDataEntries(DataEntriesS2CPacket packet) {
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            DataSyncSystem.apply(packet);
        }
    }

    public static void handleDebugSubscriptions(DebugSubscriptionsS2CPacket packet) {
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            DebugSubscriptionSystem.apply(packet.subscriptions(), packet.suppressed());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handleModuleRemove(ModuleRemoveS2CPacket packet) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handleModuleClear(ModuleClearS2CPacket packet) {
        ModuleSystem modules = EmpiricCore.modules();
        try {
            DataBuffer buffer = EmpiricBuffers.buffer(packet.data());
            ModuleSystem.Target target = modules.readTarget(buffer, Side.CLIENT, modules.specs().context(Encoding.PACKED));
            if (target.attachment() == null) {
                return;
            }
            Module module = ((Attachment) target.attachment()).modules().get((ModuleType) target.type());
            if (module == null) {
                return;
            }
            BitSet fields = new BitSet();
            for (int i = buffer.takeVarInt(); i > 0; i--) {
                fields.set(buffer.takeVarInt());
            }
            modules.resetFields(module, fields);
        } catch (RuntimeException e) {
            Empiric.LOGGER.error("Couldn't apply a module clear", e);
        }
    }

    public static void handleTemperatureSection(TemperatureSectionS2CPacket packet) {
        Level level = ClientLookup.level();
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
            TemperatureSystem.TEMPERATURE.fire(module, TemperatureSystem.SECTION_CHANGED, packet.section());
        } catch (RuntimeException e) {
            Empiric.LOGGER.error("Couldn't apply a temperature section", e);
        }
    }

    public static void handleNetworkOverlay(NetworkOverlayS2CPacket packet) {
        NetworkOverlay.INSTANCE.apply(packet);
    }

    public static void handleTemperatureOverlay(TemperatureOverlayS2CPacket packet) {
        TemperatureOverlay.INSTANCE.show(packet.radius());
    }

    public static void handleModuleSync(ModuleSyncS2CPacket packet) {
        ModuleSyncSystem.apply(packet.data(), Side.CLIENT);
    }
}
