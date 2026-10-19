package xyz.volcanobay.empiric.client.ui;

import xyz.volcanobay.empiric.client.editor.layout.LayoutEditorScreen;
import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.client.Minecraft;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;
import xyz.volcanobay.empiric.api.ui.surface.UiSurfaces;
import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.surface.SurfacePlane;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.networking.packet.UiCloseC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiSlotC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiSurfaceS2CPacket;
import xyz.volcanobay.empiric.system.UiSystem;

import java.util.HashMap;
import java.util.Map;

public final class UiClient {

    private static final Map<Integer, UiSystem.Target> PENDING = new HashMap<>();

    private UiClient() {
    }

    /**
     * The screen a menu with this container id was opened for. The server sends it just before the vanilla open packet.
     */
    public static UiSystem.Target takePending(int containerId) {
        return PENDING.remove(containerId);
    }

    public static <M extends Module<?>> void open(UiScreenType<M> screen, M module, boolean tracked) {
        Minecraft.getInstance().setScreen(new UiScreenHost(screen, module, screen.create(module), tracked));
    }

    public static <M extends Module<?>> void request(UiScreenType<M> screen, M module) {
        VeilPacketManager.server().sendPacket(new UiOpenC2SPacket(UiSystem.encode(screen, module)));
    }

    static void closed(UiScreenType<?> screen, Module<?> module) {
        if (Minecraft.getInstance().getConnection() != null && module.isAttached()) {
            VeilPacketManager.server().sendPacket(new UiCloseC2SPacket(UiSystem.encode(screen, module)));
        }
    }

    /**
     * Sends a click on a slot outside a menu to the server, which finds the slot by its place among the screen's slots
     * and applies it there.
     */
    public static boolean slotClick(UiScreenType<?> screen, Module<?> module, SlotWidget slot, int button) {
        int index = UiMenu.slots(slot.root().content()).indexOf(slot);
        if (index < 0 || Minecraft.getInstance().getConnection() == null) {
            return false;
        }
        VeilPacketManager.server().sendPacket(new UiSlotC2SPacket(UiSystem.encode(screen, module), index, button));
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handleOpen(UiOpenS2CPacket packet) {
        UiSystem.Target target = UiSystem.decode(packet.data(), Side.CLIENT);
        if (target == null) {
            Empiric.LOGGER.warn("The server opened a screen for a module this client doesn't have");
            return;
        }
        if (target.containerId() != 0) {
            PENDING.clear();
            PENDING.put(target.containerId(), target);
            return;
        }
        if (Minecraft.getInstance().screen instanceof UiScreenHost current) {
            current.untrack();
        }
        open((UiScreenType) target.screen(), target.module(), true);
    }

    /**
     * Opens the layout editor, on a layout or, if empty, on the list of layouts.
     */
    public static void openEditor(String layout) {
        Minecraft.getInstance().setScreen(new LayoutEditorScreen(layout));
    }

    public static void handleClose(UiCloseS2CPacket packet) {
        UiSystem.Target target = UiSystem.decode(packet.data(), Side.CLIENT);
        if (target != null && Minecraft.getInstance().screen instanceof UiScreenHost host
                && host.module() == target.module() && host.screenType() == target.screen()) {
            host.closeFromServer();
        }
    }

    /**
     * Hangs a screen in the world: {@code width} blocks wide, as tall as its content needs, centred on the point and
     * facing a player who looks along {@code yaw} towards it. The plane follows the content's measured size, so a screen
     * that grows or shrinks keeps its pixels square.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handleSurface(UiSurfaceS2CPacket packet) {
        UiSystem.Target target = UiSystem.decode(packet.data(), Side.CLIENT);
        if (target == null) {
            Empiric.LOGGER.warn("The server placed a surface for a module this client doesn't have");
            return;
        }
        double radians = Math.toRadians(packet.yaw());
        double[] right = {-Math.cos(radians), 0, -Math.sin(radians)};
        double[] down = {0, -1, 0};
        Widget content = ((UiScreenType) target.screen()).create(target.module());
        UiSurfaces.show((UiScreenType) target.screen(), content, target.module(), () -> {
            int pixelWidth = Math.max(1, content.measuredWidth());
            int pixelHeight = Math.max(1, content.measuredHeight());
            double width = packet.width();
            double height = width * pixelHeight / pixelWidth;
            double[] origin = {packet.x() - right[0] * width / 2, packet.y() + height / 2, packet.z() - right[2] * width / 2};
            return SurfacePlane.of(origin, right, down, width, height, pixelWidth, pixelHeight);
        });
    }
}
