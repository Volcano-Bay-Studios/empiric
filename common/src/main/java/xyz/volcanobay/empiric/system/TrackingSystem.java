package xyz.volcanobay.empiric.system;

import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.network.PacketTarget;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachment;
import xyz.volcanobay.empiric.core.data.FieldDelta;
import xyz.volcanobay.empiric.core.data.Track;
import xyz.volcanobay.empiric.core.data.spec.DeltaSpec;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.networking.packet.ModuleClearS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-player sync of modules whose type has trackers, registered with {@code ModuleType.Builder.track}.
 * <p>
 * For each tracked module the server remembers what every viewer's client holds. That being the module as a whole, and each synced
 * field, as {@link Track#SYNC} (up to date), {@link Track#PAUSE} (stale) or {@link Track#CLEAR} (nothing, or the default).
 * The trackers are asked again every {@link #INTERVAL} ticks and whenever the module syncs, and the difference between
 * the answer and what the client holds is what gets sent. A field that goes back to {@code SYNC} is always sent in full.
 */
public final class TrackingSystem {

    public static final int INTERVAL = 10;

    private static final Map<Module<?>, Map<UUID, View>> VIEWS = new WeakHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static int ticks;

    private TrackingSystem() {
    }

    /**
     * What a viewer's client holds of a module.
     */
    public static final class View {

        private Track module = Track.CLEAR;
        private final Track[] fields;
        private final Map<Object, Track>[] entries;

        @SuppressWarnings("unchecked")
        private View(int fields) {
            this.entries = new Map[fields];
            this.fields = new Track[fields];
            Arrays.fill(this.fields, Track.CLEAR);
        }

        public Track module() {
            return module;
        }

        public Track field(int index) {
            return fields[index];
        }

        /**
         * The entries of an entry-tracked field the client holds, as {@link Track#SYNC} when up to date and
         * {@link Track#PAUSE} when stale.
         */
        public Map<Object, Track> entries(int index) {
            return entries[index] == null ? Map.of() : Map.copyOf(entries[index]);
        }

        private Map<Object, Track> held(int index) {
            if (entries[index] == null) {
                entries[index] = new HashMap<>();
            }
            return entries[index];
        }

        /**
         * The synced fields the client holds up to date.
         */
        public BitSet synced() {
            BitSet synced = new BitSet();
            if (module == Track.SYNC) {
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i] == Track.SYNC) {
                        synced.set(i);
                    }
                }
            }
            return synced;
        }
    }

    /**
     * Whether the module, or a module it is a child of, has trackers.
     */
    public static boolean isTracked(Module<?> module) {
        Module<?> current = module;
        while (true) {
            if (current.type().tracks()) {
                return true;
            }
            if (!(current.attachment() instanceof ModuleAttachment child)) {
                return false;
            }
            current = child.parent();
        }
    }

    /**
     * What the player gets of the module as a whole, counting the modules it is a child of: the worst answer wins, so
     * a child of a module the player doesn't get is not sent either.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Track track(Module<?> module, ServerPlayer player) {
        Track result = Track.SYNC;
        Module<?> current = module;
        while (true) {
            if (current.type().tracks()) {
                Track track = ((ModuleType) current.type()).track(current, player);
                if (track == Track.CLEAR) {
                    return Track.CLEAR;
                }
                if (track == Track.PAUSE) {
                    result = Track.PAUSE;
                }
            }
            if (!(current.attachment() instanceof ModuleAttachment child)) {
                return result;
            }
            current = child.parent();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Track track(Module<?> module, int field, ServerPlayer player) {
        return ((ModuleType) module.type()).track(module, field, player);
    }

    /**
     * Whether the player gets this field right now, as asked of the trackers and not as last sent. Used to refuse
     * client edits to fields the player can't see.
     */
    public static boolean allows(Module<?> module, int field, ServerPlayer player) {
        return !isTracked(module) || track(module, player) == Track.SYNC && track(module, field, player) == Track.SYNC;
    }

    public static View view(Module<?> module, ServerPlayer player) {
        Map<UUID, View> views = VIEWS.get(module);
        return views == null ? null : views.get(player.getUUID());
    }

    /**
     * Forgets what the player's client held of the module and sends it what the trackers say it gets, as when the
     * player starts tracking the module's attachment.
     */
    public static void start(Module<?> module, ServerPlayer player) {
        VIEWS.computeIfAbsent(module, key -> new HashMap<>()).put(player.getUUID(), new View(module.spec().synced().length));
        update(module, player);
    }

    /**
     * Asks the trackers again and sends the player the difference. Returns the fields that were sent in full.
     */
    public static BitSet update(Module<?> module, ServerPlayer player) {
        ObjectSpec<Object> spec = module.spec();
        View view = VIEWS.computeIfAbsent(module, key -> new HashMap<>()).computeIfAbsent(player.getUUID(), id -> new View(spec.synced().length));
        PacketTarget sink = PacketTarget.player(player);
        BitSet sent = new BitSet();
        Track wanted = track(module, player);
        if (wanted == Track.CLEAR) {
            if (view.module != Track.CLEAR) {
                ModuleSyncSystem.send(sink, ModuleRemoveS2CPacket.of(module));
                view.module = Track.CLEAR;
                Arrays.fill(view.fields, Track.CLEAR);
                Arrays.fill(view.entries, null);
            }
            return sent;
        }
        if (wanted == Track.PAUSE) {
            if (view.module == Track.SYNC) {
                view.module = Track.PAUSE;
            }
            for (int i = 0; i < view.fields.length; i++) {
                if (view.fields[i] == Track.SYNC) {
                    view.fields[i] = Track.PAUSE;
                }
            }
            return sent;
        }
        boolean arriving = view.module != Track.SYNC;
        view.module = Track.SYNC;
        BitSet cleared = new BitSet();
        for (int i = 0; i < view.fields.length; i++) {
            Track field = track(module, i, player);
            if (field == Track.SYNC && view.fields[i] != Track.SYNC) {
                sent.set(i);
                view.fields[i] = Track.SYNC;
            } else if (field == Track.PAUSE && view.fields[i] == Track.SYNC) {
                view.fields[i] = Track.PAUSE;
            } else if (field == Track.CLEAR && view.fields[i] != Track.CLEAR) {
                cleared.set(i);
                view.fields[i] = Track.CLEAR;
                view.entries[i] = null;
            }
        }
        if (!cleared.isEmpty()) {
            ModuleSyncSystem.send(sink, ModuleClearS2CPacket.of(module, cleared));
        }
        ModuleType<?, ?> type = module.type();
        Map<Integer, FieldDelta> custom = new HashMap<>();
        BitSet incremental = new BitSet();
        BitSet entryFields = new BitSet();
        for (int i = 0; i < view.fields.length; i++) {
            if (view.fields[i] != Track.SYNC || !type.tracksEntries(i)) {
                continue;
            }
            entryFields.set(i);
            boolean replace = sent.get(i) || module.pendingFull(i);
            FieldDelta delta = entryDelta(module, player, view, i, replace);
            if (delta != null) {
                custom.put(i, delta);
                if (!sent.get(i)) {
                    incremental.set(i);
                }
            }
        }
        if (arriving || !sent.isEmpty()) {
            for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.dirty(module, sent, custom::get)) {
                ModuleSyncSystem.send(sink, packet);
            }
        }
        if (!incremental.isEmpty()) {
            for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.dirty(module, incremental, custom::get)) {
                ModuleSyncSystem.send(sink, packet);
            }
        }
        sent.or(entryFields);
        if (arriving && module instanceof NetworkBehavior network) {
            network.onTracked(sink);
        }
        return sent;
    }

    /**
     * Asks the entry tracker about every entry of a field and returns what the client needs to hold what the tracker
     * says, or null if it already does. Updates the entries the client is held to have.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static FieldDelta entryDelta(Module<?> module, ServerPlayer player, View view, int index, boolean replace) {
        ObjectSpec<Object> spec = module.spec();
        DeltaSpec<Object> delta = spec.delta(index);
        ModuleType type = module.type();
        Object value = spec.synced()[index].get(module.data());
        Map<Object, Track> held = view.held(index);
        FieldDelta pending = replace ? null : module.delta(index);
        if (replace) {
            held.clear();
        }
        Set<Object> changed = new LinkedHashSet<>();
        Set<Object> removed = new LinkedHashSet<>();
        Set<Object> present = new HashSet<>();
        if (value != null) {
            for (Object key : delta.keys(value)) {
                present.add(key);
                Track track = type.track(module, index, key, delta.entry(value, key), player);
                Track current = held.get(key);
                if (track == Track.SYNC) {
                    if (current != Track.SYNC || pending != null && pending.changed().contains(key)) {
                        changed.add(key);
                    }
                    held.put(key, Track.SYNC);
                } else if (track == Track.PAUSE) {
                    if (current == Track.SYNC) {
                        held.put(key, Track.PAUSE);
                    }
                } else if (current != null) {
                    removed.add(key);
                    held.remove(key);
                }
            }
        }
        for (Iterator<Object> it = held.keySet().iterator(); it.hasNext(); ) {
            Object key = it.next();
            if (!present.contains(key)) {
                removed.add(key);
                it.remove();
            }
        }
        if (!replace && changed.isEmpty() && removed.isEmpty()) {
            return null;
        }
        return new FieldDelta(replace, changed, removed);
    }

    /**
     * Sends the dirty fields of a tracked module, each viewer only the ones it gets. Returns false if the module's
     * attachment can't list its viewers, in which case the caller sends to everyone as for an untracked module.
     */
    public static boolean flush(Module<?> module, BitSet dirty) {
        Collection<ServerPlayer> viewers = ModuleSyncSystem.viewers(module);
        if (viewers == null) {
            if (WARNED.add(module.type().id())) {
                Empiric.LOGGER.warn("{} has trackers but its attachment can't list viewers, syncing it to everyone", module.type().id());
            }
            return false;
        }
        Map<BitSet, List<ServerPlayer>> groups = new HashMap<>();
        for (ServerPlayer player : List.copyOf(viewers)) {
            BitSet sent = update(module, player);
            BitSet fields = view(module, player).synced();
            fields.and(dirty);
            fields.andNot(sent);
            if (!fields.isEmpty()) {
                groups.computeIfAbsent(fields, key -> new ArrayList<>()).add(player);
            }
        }
        groups.forEach((fields, players) -> {
            for (ModuleSyncS2CPacket packet : ModuleSyncS2CPacket.dirty(module, fields)) {
                for (ServerPlayer player : players) {
                    ModuleSyncSystem.send(PacketTarget.player(player), packet);
                }
            }
        });
        return true;
    }

    /**
     * Asks the trackers of every module that has been synced to a player, as the players come and go.
     */
    public static void tick() {
        if (++ticks % INTERVAL != 0) {
            return;
        }
        for (Module<?> module : new ArrayList<>(VIEWS.keySet())) {
            Collection<ServerPlayer> viewers = module.isAttached() ? ModuleSyncSystem.viewers(module) : null;
            if (viewers == null) {
                VIEWS.remove(module);
                continue;
            }
            Set<UUID> present = new HashSet<>();
            for (ServerPlayer player : List.copyOf(viewers)) {
                present.add(player.getUUID());
                update(module, player);
            }
            Map<UUID, View> views = VIEWS.get(module);
            if (views != null) {
                views.keySet().retainAll(present);
            }
        }
    }

    /**
     * What every viewer's client holds of every module that has been synced to a player.
     */
    public static Map<Module<?>, Map<UUID, View>> snapshot() {
        Map<Module<?>, Map<UUID, View>> copy = new HashMap<>();
        VIEWS.forEach((module, views) -> copy.put(module, new HashMap<>(views)));
        return copy;
    }

    public static void forget(Module<?> module) {
        VIEWS.remove(module);
    }

    public static void clear() {
        VIEWS.clear();
        WARNED.clear();
        ticks = 0;
    }
}
