package xyz.volcanobay.empiric.system;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;
import xyz.volcanobay.empiric.networking.packet.NetworkOverlayS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Streams network members to players viewing a network overlay, resending whenever what they'd
 * see changes.
 */
public final class NetworkOverlaySystem {

    public static final int RADIUS = 96;
    public static final int MEMBER_LIMIT = 32768;
    private static final int INTERVAL = 10;

    private static final Map<UUID, Map<SpatialNetworkType<?>, View>> VIEWS = new HashMap<>();
    private static int timer;

    private NetworkOverlaySystem() {
    }

    /**
     * Shows a player the networks of a type around them, or only the one at {@code focus}.
     *
     * @return how many members are shown now
     */
    public static int show(ServerPlayer player, SpatialNetworkType<?> type, @Nullable OctPos focus) {
        View view = new View(focus);
        VIEWS.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(type, view);
        return refresh(player, type, view);
    }

    public static boolean hide(ServerPlayer player, SpatialNetworkType<?> type) {
        Map<SpatialNetworkType<?>, View> views = VIEWS.get(player.getUUID());
        if (views == null || views.remove(type) == null) {
            return false;
        }
        if (views.isEmpty()) {
            VIEWS.remove(player.getUUID());
        }
        send(player, NetworkOverlayS2CPacket.hidden(type.id().toString()));
        return true;
    }

    /**
     * Resends everything a player views, after their client dropped its level.
     */
    public static void playerReady(ServerPlayer player) {
        Map<SpatialNetworkType<?>, View> views = VIEWS.get(player.getUUID());
        if (views != null) {
            views.values().forEach(view -> view.sent = null);
        }
    }

    public static void tick(MinecraftServer server) {
        if (VIEWS.isEmpty() || ++timer < INTERVAL) {
            return;
        }
        timer = 0;
        Iterator<Map.Entry<UUID, Map<SpatialNetworkType<?>, View>>> players = VIEWS.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Map<SpatialNetworkType<?>, View>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                players.remove();
                continue;
            }
            entry.getValue().forEach((type, view) -> refresh(player, type, view));
        }
    }

    private static int refresh(ServerPlayer player, SpatialNetworkType<?> type, View view) {
        List<NetworkOverlayS2CPacket.Network> networks = collect(player, type, view.focus);
        int members = networks.stream().mapToInt(network -> network.members().size()).sum();
        Map<Integer, Set<SpatialPos>> snapshot = new HashMap<>();
        for (NetworkOverlayS2CPacket.Network network : networks) {
            snapshot.put(network.id(), new HashSet<>(network.members()));
        }
        if (!snapshot.equals(view.sent)) {
            view.sent = snapshot;
            send(player, new NetworkOverlayS2CPacket(type.id().toString(), true, networks));
        }
        return members;
    }

    private static List<NetworkOverlayS2CPacket.Network> collect(ServerPlayer player, SpatialNetworkType<?> type, @Nullable OctPos focus) {
        SpatialNetworkMap<?> map = type.get(player.serverLevel());
        List<NetworkOverlayS2CPacket.Network> networks = new ArrayList<>();
        int[] budget = {MEMBER_LIMIT};
        if (focus != null) {
            NetworkModule network = map.getNetwork(focus);
            if (network != null) {
                add(networks, player, network, budget);
            }
            return networks;
        }
        for (NetworkAttachment attachment : map.attachments()) {
            NetworkModule network = attachment.modules().get(type.moduleType());
            if (network != null && budget[0] > 0) {
                add(networks, player, network, budget);
            }
        }
        return networks;
    }

    private static void add(List<NetworkOverlayS2CPacket.Network> networks, ServerPlayer player, NetworkModule network, int[] budget) {
        double px = player.getX() * 2;
        double py = player.getY() * 2;
        double pz = player.getZ() * 2;
        double range = RADIUS * 2.0;
        List<SpatialPos> members = new ArrayList<>();
        for (SpatialPos member : network.members()) {
            if (budget[0] <= 0) {
                break;
            }
            if (Math.abs(member.x() - px) <= range && Math.abs(member.y() - py) <= range && Math.abs(member.z() - pz) <= range) {
                members.add(member);
                budget[0]--;
            }
        }
        if (!members.isEmpty()) {
            networks.add(new NetworkOverlayS2CPacket.Network(network.id(), members));
        }
    }

    private static void send(ServerPlayer player, NetworkOverlayS2CPacket packet) {
        try {
            VeilPacketManager.player(player).sendPacket(packet);
        } catch (UnsupportedOperationException e) {
            Empiric.LOGGER.debug("{} can't receive network overlays", player.getGameProfile().getName());
        }
    }

    private static final class View {

        private final OctPos focus;
        private Map<Integer, Set<SpatialPos>> sent;

        View(@Nullable OctPos focus) {
            this.focus = focus;
        }
    }
}
