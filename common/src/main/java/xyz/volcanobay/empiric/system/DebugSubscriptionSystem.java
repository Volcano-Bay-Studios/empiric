package xyz.volcanobay.empiric.system;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.networking.packet.DebugSubscriptionsS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class DebugSubscriptionSystem {

    public static final String SUBSCRIBER = "empiric_debug";

    private static final Map<String, Set<String>> SERVER = new HashMap<>();
    private static final Map<String, Set<String>> SUPPRESSED = new HashMap<>();
    private static final Map<String, Set<String>> APPLIED = new HashMap<>();
    private static final Map<String, Set<String>> APPLIED_SUPPRESSED = new HashMap<>();

    private DebugSubscriptionSystem() {
    }

    public static boolean isSubscribed(ModuleType<?, ?> type) {
        return EmpiricCore.modules().subscribers(type).contains(SUBSCRIBER);
    }

    public static boolean subscribe(ModuleType<?, ?> type) {
        return subscribe(type, SUBSCRIBER);
    }

    public static boolean unsubscribe(ModuleType<?, ?> type) {
        return unsubscribe(type, SUBSCRIBER);
    }

    public static boolean subscribe(ModuleType<?, ?> type, String subscriber) {
        boolean changed = EmpiricCore.modules().subscribe(subscriber, type);
        if (changed) {
            if (!remove(SUPPRESSED, type.id(), subscriber)) {
                SERVER.computeIfAbsent(type.id(), key -> new LinkedHashSet<>()).add(subscriber);
            }
            broadcast();
        }
        return changed;
    }

    public static boolean unsubscribe(ModuleType<?, ?> type, String subscriber) {
        boolean changed = EmpiricCore.modules().unsubscribe(subscriber, type);
        if (changed) {
            if (!remove(SERVER, type.id(), subscriber)) {
                SUPPRESSED.computeIfAbsent(type.id(), key -> new LinkedHashSet<>()).add(subscriber);
            }
            broadcast();
        }
        return changed;
    }

    public static Set<String> suppressed(ModuleType<?, ?> type) {
        Set<String> subscribers = SUPPRESSED.get(type.id());
        return subscribers == null ? Set.of() : Set.copyOf(subscribers);
    }

    private static boolean remove(Map<String, Set<String>> map, String id, String subscriber) {
        Set<String> subscribers = map.get(id);
        if (subscribers == null || !subscribers.remove(subscriber)) {
            return false;
        }
        if (subscribers.isEmpty()) {
            map.remove(id);
        }
        return true;
    }

    public static List<String> types() {
        List<String> types = new ArrayList<>();
        for (ModuleType<?, ?> type : EmpiricCore.modules().types()) {
            if (isSubscribed(type)) {
                types.add(type.id());
            }
        }
        return types;
    }

    private static Map<String, List<String>> copy(Map<String, Set<String>> map) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        map.forEach((type, subscribers) -> copy.put(type, List.copyOf(subscribers)));
        return copy;
    }

    private static DebugSubscriptionsS2CPacket packet() {
        return new DebugSubscriptionsS2CPacket(copy(SERVER), copy(SUPPRESSED));
    }

    public static void syncTo(ServerPlayer player) {
        VeilPacketManager.player(player).sendPacket(packet());
    }

    private static void broadcast() {
        MinecraftServer server = WorldModuleSystem.server();
        if (server == null) {
            return;
        }
        DebugSubscriptionsS2CPacket packet = packet();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                VeilPacketManager.player(player).sendPacket(packet);
            } catch (UnsupportedOperationException e) {
                continue;
            }
        }
    }

    public static void apply(Map<String, List<String>> subscriptions, Map<String, List<String>> suppressed) {
        ModuleSystem modules = EmpiricCore.modules();
        reconcile(APPLIED, subscriptions, (type, subscriber) -> modules.unsubscribe(subscriber, type));
        reconcile(APPLIED_SUPPRESSED, suppressed, (type, subscriber) -> modules.subscribe(subscriber, type));
        record(APPLIED, subscriptions, (type, subscriber) -> modules.subscribe(subscriber, type));
        record(APPLIED_SUPPRESSED, suppressed, (type, subscriber) -> modules.unsubscribe(subscriber, type));
    }

    private static void reconcile(Map<String, Set<String>> applied, Map<String, List<String>> wanted, BiConsumer<ModuleType<?, ?>, String> undo) {
        for (Map.Entry<String, Set<String>> entry : applied.entrySet()) {
            ModuleType<?, ?> type = EmpiricCore.modules().type(entry.getKey());
            List<String> still = wanted.getOrDefault(entry.getKey(), List.of());
            for (String subscriber : entry.getValue()) {
                if (type != null && !still.contains(subscriber)) {
                    undo.accept(type, subscriber);
                }
            }
        }
        applied.clear();
    }

    private static void record(Map<String, Set<String>> applied, Map<String, List<String>> wanted, BiConsumer<ModuleType<?, ?>, String> action) {
        wanted.forEach((id, subscribers) -> {
            ModuleType<?, ?> type = EmpiricCore.modules().type(id);
            if (type == null) {
                return;
            }
            for (String subscriber : subscribers) {
                action.accept(type, subscriber);
                applied.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(subscriber);
            }
        });
    }

    public static void clear() {
        ModuleSystem modules = EmpiricCore.modules();
        SERVER.forEach((id, subscribers) -> {
            ModuleType<?, ?> type = modules.type(id);
            if (type != null) {
                for (String subscriber : subscribers) {
                    modules.unsubscribe(subscriber, type);
                }
            }
        });
        SERVER.clear();
        SUPPRESSED.forEach((id, subscribers) -> {
            ModuleType<?, ?> type = modules.type(id);
            if (type != null) {
                for (String subscriber : subscribers) {
                    modules.subscribe(subscriber, type);
                }
            }
        });
        SUPPRESSED.clear();
    }
}
