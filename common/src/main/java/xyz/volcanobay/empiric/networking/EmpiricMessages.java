package xyz.volcanobay.empiric.networking;

import xyz.volcanobay.empiric.platform.PacketRegistrar;
import xyz.volcanobay.empiric.networking.handler.EmpiricClientPacketHandler;
import xyz.volcanobay.empiric.networking.handler.EmpiricServerPacketHandler;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncC2SPacket;
import xyz.volcanobay.empiric.networking.packet.DataEntriesS2CPacket;
import xyz.volcanobay.empiric.networking.packet.DebugSubscriptionsS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleClearS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.networking.packet.NetworkOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.OctBreakS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureOverlayS2CPacket;
import xyz.volcanobay.empiric.networking.packet.TemperatureSectionS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiEditorS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiPointerC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiPointerS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiSlotC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiSurfaceS2CPacket;

public class EmpiricMessages {

    public static void register(PacketRegistrar packets) {
        packets.serverbound(ModuleSyncC2SPacket.TYPE, ModuleSyncC2SPacket.CODEC, EmpiricServerPacketHandler::handleModuleSync);
        packets.clientbound(ModuleSyncS2CPacket.TYPE, ModuleSyncS2CPacket.CODEC, EmpiricClientPacketHandler::handleModuleSync);
        packets.clientbound(DebugSubscriptionsS2CPacket.TYPE, DebugSubscriptionsS2CPacket.CODEC, EmpiricClientPacketHandler::handleDebugSubscriptions);
        packets.clientbound(DataEntriesS2CPacket.TYPE, DataEntriesS2CPacket.CODEC, EmpiricClientPacketHandler::handleDataEntries);
        packets.clientbound(ModuleRemoveS2CPacket.TYPE, ModuleRemoveS2CPacket.CODEC, EmpiricClientPacketHandler::handleModuleRemove);
        packets.clientbound(ModuleClearS2CPacket.TYPE, ModuleClearS2CPacket.CODEC, EmpiricClientPacketHandler::handleModuleClear);
        packets.clientbound(TemperatureSectionS2CPacket.TYPE, TemperatureSectionS2CPacket.CODEC, EmpiricClientPacketHandler::handleTemperatureSection);
        packets.clientbound(OctBreakS2CPacket.TYPE, OctBreakS2CPacket.CODEC, EmpiricClientPacketHandler::handleOctBreak);
        packets.clientbound(NetworkOverlayS2CPacket.TYPE, NetworkOverlayS2CPacket.CODEC, EmpiricClientPacketHandler::handleNetworkOverlay);
        packets.serverbound(UiOpenC2SPacket.TYPE, UiOpenC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiOpen);
        packets.serverbound(UiCloseC2SPacket.TYPE, UiCloseC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiClosed);
        packets.serverbound(UiSlotC2SPacket.TYPE, UiSlotC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiSlot);
        packets.serverbound(UiPointerC2SPacket.TYPE, UiPointerC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiPointer);
        packets.clientbound(UiPointerS2CPacket.TYPE, UiPointerS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiPointer);
        packets.clientbound(UiOpenS2CPacket.TYPE, UiOpenS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiOpen);
        packets.clientbound(UiEditorS2CPacket.TYPE, UiEditorS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiEditor);
        packets.clientbound(UiCloseS2CPacket.TYPE, UiCloseS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiClose);
        packets.clientbound(UiSurfaceS2CPacket.TYPE, UiSurfaceS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiSurface);
        packets.clientbound(TemperatureOverlayS2CPacket.TYPE, TemperatureOverlayS2CPacket.CODEC, EmpiricClientPacketHandler::handleTemperatureOverlay);
    }
}
