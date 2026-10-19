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
        INSTANCE.registerServerbound(UiOpenC2SPacket.TYPE, UiOpenC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiOpen);
        INSTANCE.registerServerbound(UiCloseC2SPacket.TYPE, UiCloseC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiClosed);
        INSTANCE.registerServerbound(UiSlotC2SPacket.TYPE, UiSlotC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiSlot);
        INSTANCE.registerServerbound(UiPointerC2SPacket.TYPE, UiPointerC2SPacket.CODEC, EmpiricServerPacketHandler::handleUiPointer);
        INSTANCE.registerClientbound(UiPointerS2CPacket.TYPE, UiPointerS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiPointer);
        INSTANCE.registerClientbound(UiOpenS2CPacket.TYPE, UiOpenS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiOpen);
        INSTANCE.registerClientbound(UiEditorS2CPacket.TYPE, UiEditorS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiEditor);
        INSTANCE.registerClientbound(UiCloseS2CPacket.TYPE, UiCloseS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiClose);
        INSTANCE.registerClientbound(UiSurfaceS2CPacket.TYPE, UiSurfaceS2CPacket.CODEC, EmpiricClientPacketHandler::handleUiSurface);
        INSTANCE.registerClientbound(TemperatureOverlayS2CPacket.TYPE, TemperatureOverlayS2CPacket.CODEC, EmpiricClientPacketHandler::handleTemperatureOverlay);
    }
}
