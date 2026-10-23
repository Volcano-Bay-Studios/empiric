package xyz.volcanobay.empiric.system;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.ui.UiBehavior;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;
import xyz.volcanobay.empiric.api.ui.menu.WorldSlots;
import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleEvents;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.ui.HeadlessHost;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiCloseS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiOpenS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiPointerS2CPacket;
import xyz.volcanobay.empiric.networking.packet.UiSurfaceS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Tracks which players have which module screens open on the server. Screen state itself is the module's synced data,
 * so this only decides who may open a screen and closes it when they no longer may. A screen with slots opens as a
 * {@link UiMenu}, and vanilla's container packets carry its items.
 */
public final class UiSystem {

    private static final Map<Module<?>, List<Viewer>> VIEWERS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_POINTER = new HashMap<>();
    // FOr debug
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final int CHECK_INTERVAL = 20;
    private static int checkTimer;

    public record Viewer(ServerPlayer player, UiScreenType<?> screen) {
    }

    /**
     * A screen and the module it shows, plus the menu's container id, or 0 for a screen without slots.
     */
    public record Target(UiScreenType<?> screen, Module<?> module, int containerId) {

        public Target(UiScreenType<?> screen, Module<?> module) {
            this(screen, module, 0);
        }
    }

    /**
     * A fake viewer whose pointer circles a screen, for checking presence cursors with one client.
     */
    private record Ghost(ServerPlayer player, byte[] target, long until) {
    }

    private record Closing(Module<?> module, Viewer viewer, boolean announce) {
    }

    private UiSystem() {
    }

    public static void register() {
        EmpiricCore.modules().listen(ModuleEvents.DETACHED, (module, ignored) -> VIEWERS.remove(module));
    }

    public static <M extends Module<?>> boolean open(ServerPlayer player, UiScreenType<M> screen, M module) {
        if (module == null || module.type() != screen.moduleType() || !module.isAttached() || module.side() != Side.SERVER) {
            return false;
        }
        if (!canView(player, screen, module)) {
            return false;
        }
        close(player, false);
        PacketTarget sink = PacketTarget.player(player);
        for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.full(module)) {
            ModuleSyncSystem.send(sink, packet);
        }
        UiRoot root = UiMenu.build(screen.create(module), module, player.getInventory());
        if (UiMenu.slots(root.content()).isEmpty()) {
            root.dispose();
            if (player.containerMenu instanceof UiMenu) {
                player.closeContainer();
            }
            ModuleSyncSystem.send(sink, new UiOpenS2CPacket(encode(screen, module, 0)));
        } else {
            boolean[] opened = {false};
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) -> {
                ModuleSyncSystem.send(sink, new UiOpenS2CPacket(encode(screen, module, containerId)));
                opened[0] = true;
                return new UiMenu(containerId, inventory, screen, module, root);
            }, Component.literal(screen.id().toString())));
            if (!opened[0]) {
                root.dispose();
                return false;
            }
        }
        VIEWERS.computeIfAbsent(module, key -> new ArrayList<>()).add(new Viewer(player, screen));
        if (module instanceof UiBehavior ui) {
            ui.onOpened(player, screen);
        }
        return true;
    }

    /**
     * Syncs a module to a player and hangs its screen in their world, {@code width} blocks wide and centred on the point,
     * facing a viewer who looks along {@code yaw}. The player isn't tracked as a viewer: the surface is theirs to point
     * at, and edits sync like any other client edit.
     */
    public static <M extends Module<?>> boolean showSurface(ServerPlayer player, UiScreenType<M> screen, M module, double x, double y, double z, float yaw, float width) {
        if (module == null || module.type() != screen.moduleType() || !module.isAttached() || module.side() != Side.SERVER || !canView(player, screen, module)) {
            return false;
        }
        PacketTarget sink = PacketTarget.player(player);
        for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.full(module)) {
            ModuleSyncSystem.send(sink, packet);
        }
        ModuleSyncSystem.send(sink, new UiSurfaceS2CPacket(encode(screen, module), x, y, z, yaw, width));
        return true;
    }

    public static void close(ServerPlayer player) {
        close(player, true);
    }

    private static void close(ServerPlayer player, boolean notify) {
        closeWhere((module, viewer) -> viewer.player() == player, notify);
    }

    /**
     * Removes matching viewers first and runs their close hooks after, since closing a menu calls back in here.
     */
    private static void closeWhere(BiPredicate<Module<?>, Viewer> match, boolean notify) {
        List<Closing> closing = new ArrayList<>();
        Iterator<Map.Entry<Module<?>, List<Viewer>>> entries = VIEWERS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Module<?>, List<Viewer>> entry = entries.next();
            Iterator<Viewer> viewers = entry.getValue().iterator();
            while (viewers.hasNext()) {
                Viewer viewer = viewers.next();
                if (match.test(entry.getKey(), viewer)) {
                    viewers.remove();
                    closing.add(new Closing(entry.getKey(), viewer, notify));
                }
            }
            if (entry.getValue().isEmpty()) {
                entries.remove();
            }
        }
        closing.forEach(UiSystem::closed);
    }

    private static void closed(Closing closing) {
        Module<?> module = closing.module();
        Viewer viewer = closing.viewer();
        ServerPlayer player = viewer.player();
        if (module instanceof UiBehavior ui) {
            ui.onClosed(player, viewer.screen());
        }
        if (!closing.announce()) {
            return;
        }
        if (player.containerMenu instanceof UiMenu menu && menu.module() == module) {
            player.closeContainer();
        } else {
            ModuleSyncSystem.send(PacketTarget.player(player), new UiCloseS2CPacket(encode(viewer.screen(), module)));
        }
    }

    /**
     * Called when a player's {@link UiMenu} closes, however it closed.
     */
    public static void menuClosed(ServerPlayer player, UiScreenType<?> screen, Module<?> module) {
        closeWhere((viewed, viewer) -> viewed == module && viewer.player() == player && viewer.screen() == screen, false);
    }

    public static boolean canView(ServerPlayer player, UiScreenType<?> screen, Module<?> module) {
        if (module instanceof UiBehavior ui) {
            return ui.canOpen(player, screen);
        }
        return module instanceof NetworkBehavior network && network.inRange(player);
    }

    public static List<Viewer> viewers(Module<?> module) {
        return List.copyOf(VIEWERS.getOrDefault(module, List.of()));
    }

    public static Map<Module<?>, List<Viewer>> openScreens() {
        Map<Module<?>, List<Viewer>> copy = new LinkedHashMap<>();
        VIEWERS.forEach((module, viewers) -> copy.put(module, List.copyOf(viewers)));
        return copy;
    }

    /**
     * Drops viewers that left, and closes screens for players the module no longer lets view them.
     */
    public static void tick(MinecraftServer server) {
        tickGhosts(server);
        if (++checkTimer < CHECK_INTERVAL) {
            return;
        }
        checkTimer = 0;
        LAST_POINTER.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        if (VIEWERS.isEmpty()) {
            return;
        }
        closeWhere((module, viewer) -> gone(viewer.player()), false);
        closeWhere((module, viewer) -> !canView(viewer.player(), viewer.screen(), module), true);
    }

    /**
     * Sends a player a ghost pointer that circles a screen for some seconds. Debug only.
     */
    public static void ghost(ServerPlayer player, UiScreenType<?> screen, Module<?> module, int seconds) {
        GHOSTS.add(new Ghost(player, encode(screen, module), player.serverLevel().getGameTime() + seconds * 20L));
    }

    private static void tickGhosts(MinecraftServer server) {
        if (GHOSTS.isEmpty() || server.getTickCount() % 2 != 0) {
            return;
        }
        Iterator<Ghost> ghosts = GHOSTS.iterator();
        while (ghosts.hasNext()) {
            Ghost ghost = ghosts.next();
            long now = ghost.player().serverLevel().getGameTime();
            boolean done = now >= ghost.until() || gone(ghost.player());
            double angle = now * 0.08;
            float x = done ? Float.NaN : (float) (110 + 80 * Math.cos(angle));
            float y = done ? Float.NaN : (float) (70 + 45 * Math.sin(angle * 2));
            ModuleSyncSystem.send(PacketTarget.player(ghost.player()), new UiPointerS2CPacket(ghost.target(), -1, "Ghost", x, y));
            if (done) {
                ghosts.remove();
            }
        }
    }

    private static boolean gone(ServerPlayer player) {
        return player.hasDisconnected() || player.isRemoved();
    }

    public static void clear() {
        VIEWERS.clear();
        LAST_POINTER.clear();
        GHOSTS.clear();
        checkTimer = 0;
    }

    public static void handleOpen(ServerPlayer player, UiOpenC2SPacket packet) {
        Target target = WorldModuleSystem.withLevel(player.serverLevel(), () -> decode(packet.data(), Side.SERVER));
        if (target != null) {
            openTarget(player, target);
        }
    }

    public static void handleClosed(ServerPlayer player, UiCloseC2SPacket packet) {
        Target target = WorldModuleSystem.withLevel(player.serverLevel(), () -> decode(packet.data(), Side.SERVER));
        if (target != null) {
            closeWhere((module, viewer) -> module == target.module() && viewer.player() == player && viewer.screen() == target.screen(), false);
        }
    }



    /**
     * Passes a player's pointer on a screen with presence to everyone else who may view it. Their clients drop it unless
     * they have that screen open somewhere. At most one update per player per tick goes through.
     */
    public static void pointer(ServerPlayer player, byte[] data, float x, float y) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_POINTER.put(player.getUUID(), now);
        boolean hide = Float.isNaN(x) || Float.isNaN(y);
        if ((last != null && last == now && !hide) || (!hide && (!Float.isFinite(x) || !Float.isFinite(y)))) {
            return;
        }
        Target target = WorldModuleSystem.withLevel(player.serverLevel(), () -> decode(data, Side.SERVER));
        if (target == null || !target.screen().presence() || (!hide && !canView(player, target.screen(), target.module()))) {
            return;
        }
        UiPointerS2CPacket packet = new UiPointerS2CPacket(data, player.getId(), player.getGameProfile().getName(), x, y);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && canView(other, target.screen(), target.module())) {
                ModuleSyncSystem.send(PacketTarget.player(other), packet);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean slotClick(ServerPlayer player, byte[] data, int index, int button) {
        Target target = WorldModuleSystem.withLevel(player.serverLevel(), () -> decode(data, Side.SERVER));
        if (target == null || player.isSpectator() || !canView(player, target.screen(), target.module())) {
            return false;
        }
        Widget content = ((UiScreenType) target.screen()).create(target.module());
        UiRoot root = new UiRoot(content, target.module(), HeadlessHost.INSTANCE, Map.of(Player.class, player));
        try {
            List<SlotWidget> slots = UiMenu.slots(content);
            if (index < 0 || index >= slots.size() || !WorldSlots.click(player, slots.get(index), button)) {
                return false;
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            return true;
        } finally {
            root.dispose();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean openTarget(ServerPlayer player, Target target) {
        return open(player, (UiScreenType) target.screen(), target.module());
    }

    public static byte[] encode(UiScreenType<?> screen, Module<?> module) {
        return encode(screen, module, 0);
    }

    public static byte[] encode(UiScreenType<?> screen, Module<?> module, int containerId) {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        buffer.writeResourceLocation(screen.id());
        buffer.writeVarInt(containerId);
        modules.writeLocator(EmpiricBuffers.of(buffer), module, modules.specs().context(Encoding.PACKED));
        return EmpiricBuffers.bytes(buffer);
    }

    /**
     * Reads a screen and module, or returns null if either isn't known on this side.
     */
    public static Target decode(byte[] data, Side side) {
        ModuleSystem modules = EmpiricCore.modules();
        DataContext context = modules.specs().context(Encoding.PACKED);
        try {
            RegistryFriendlyByteBuf buffer = EmpiricBuffers.create(data);
            ResourceLocation id = buffer.readResourceLocation();
            int containerId = buffer.readVarInt();
            UiScreenType<?> screen = UiScreens.get(id);
            if (screen == null) {
                Empiric.LOGGER.warn("Unknown screen {}", id);
                return null;
            }
            Module<?> module = screen.cast(modules.readLocator(EmpiricBuffers.of(buffer), side, context));
            return module == null ? null : new Target(screen, module, containerId);
        } catch (RuntimeException e) {
            Empiric.LOGGER.warn("Couldn't read a screen target: {}", e.getMessage());
            return null;
        }
    }
}
