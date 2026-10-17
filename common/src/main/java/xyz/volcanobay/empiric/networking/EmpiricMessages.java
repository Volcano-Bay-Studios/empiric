package xyz.volcanobay.empiric.networking;

import foundry.veil.api.network.VeilPacketManager;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.networking.handler.EmpiricClientPacketHandler;
import xyz.volcanobay.empiric.networking.handler.EmpiricServerPacketHandler;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncC2SPacket;
import xyz.volcanobay.empiric.networking.packet.DataEntriesS2CPacket;
import xyz.volcanobay.empiric.networking.packet.DebugSubscriptionsS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.networking.packet.NetworkOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.OctBreakS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureSectionS2CPacket;

public class EmpiricMessages {

    private static final VeilPacketManager INSTANCE = VeilPacketManager.create(Empiric.MODID, "1");

    public static void register() {
        INSTANCE.registerServerbound(ModuleSyncC2SPacket.TYPE, ModuleSyncC2SPacket.CODEC, EmpiricServerPacketHandler::handleModuleSync);
        INSTANCE.registerClientbound(ModuleSyncS2CPacket.TYPE, ModuleSyncS2CPacket.CODEC, EmpiricClientPacketHandler::handleModuleSync);
        INSTANCE.registerClientbound(DebugSubscriptionsS2CPacket.TYPE, DebugSubscriptionsS2CPacket.CODEC, EmpiricClientPacketHandler::handleDebugSubscriptions);
        INSTANCE.registerClientbound(DataEntriesS2CPacket.TYPE, DataEntriesS2CPacket.CODEC, EmpiricClientPacketHandler::handleDataEntries);
        INSTANCE.registerClientbound(ModuleRemoveS2CPacket.TYPE, ModuleRemoveS2CPacket.CODEC, EmpiricClientPacketHandler::handleModuleRemove);
        INSTANCE.registerClientbound(TemperatureSectionS2CPacket.TYPE, TemperatureSectionS2CPacket.CODEC, EmpiricClientPacketHandler::handleTemperatureSection);
        INSTANCE.registerClientbound(OctBreakS2CPacket.TYPE, OctBreakS2CPacket.CODEC, EmpiricClientPacketHandler::handleOctBreak);
        INSTANCE.registerClientbound(NetworkOverlayS2CPacket.TYPE, NetworkOverlayS2CPacket.CODEC, EmpiricClientPacketHandler::handleNetworkOverlay);
        INSTANCE.registerClientbound(TemperatureOverlayS2CPacket.TYPE, TemperatureOverlayS2CPacket.CODEC, EmpiricClientPacketHandler::handleTemperatureOverlay);
    }
}
