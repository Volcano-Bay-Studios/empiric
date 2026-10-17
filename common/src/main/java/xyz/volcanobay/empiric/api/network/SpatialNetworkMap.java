package xyz.volcanobay.empiric.api.network;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.system.WorldModuleSystem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This map will network world coordinates to networks.
 */
public class SpatialNetworkMap<N extends NetworkModule> extends ManagedSpatialNetwork {
    private final Int2ObjectOpenHashMap<NetworkAttachment> attachments = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<Frontier>> frontiers = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<Saved>> pending = new Long2ObjectOpenHashMap<>();
    private final SpatialNetworkType<N> type;
    private final Level level;

    private static final int MAX_SIZE = 100_000;
    public static final int RESTORING = -3;
    private static final Comparator<SpatialPos> ANCHOR_ORDER = Comparator.comparingLong(SpatialPos::y).thenComparingLong(SpatialPos::x).thenComparingLong(SpatialPos::z);

    public SpatialNetworkMap(Level level, SpatialNetworkType<N> type) {
        super(1000);
        this.type = type;
        this.level = level;
    }

    public SpatialNetworkType<N> type() {
        return type;
    }

    public Level level() {
        return level;
    }

    public NetworkAttachment attachment(int id) {
        NetworkAttachment attachment = attachments.get(id);
        if (attachment == null) {
            attachment = new NetworkAttachment(this, id);
            attachments.put(id, attachment);
        }
        return attachment;
    }

    public NetworkAttachment existing(int id) {
        return attachments.get(id);
    }

    private void invalidate(int id) {
        NetworkAttachment attachment = attachments.get(id);
        if (attachment != null) {
            attachment.invalidateChunks();
        }
    }

    public List<NetworkAttachment> attachments() {
        return new ArrayList<>(attachments.values());
    }

    @Nullable
    public N getNetwork(int id) {
        if (!type.isEnabled()) {
            return null;
        }
        NetworkAttachment attachment = attachment(id);
        N network = attachment.modules().get(type.moduleType());
        if (network == null) {
            network = EmpiricCore.modules().attach(attachment, type.moduleType());
            network.update();
        }
        return network;
    }

    /**
     * Returns a network of blocks at a given oct coordinate.
     * This will try to find an existing network, otherwise it will create a new one.
     * @param x Long position of x
     * @param y Long position of y
     * @param z Long position of z
     * @return Either a network, or null if none can be found or created.
     */
    @Nullable
    public N getNetwork(long x, long y, long z) {
        if (!type.isEnabled()) {
            return null;
        }
        int id = getOrDiscover(x, y, z);
        if (id == -1) {
            return null;
        }
        return getNetwork(id);
    }

    /**
     * Returns a network of blocks at a given oct coordinate.
     * This will try to find an existing network, and will NOT create a new one if it does not find one.
     * @param x Long position of x
     * @param y Long position of y
     * @param z Long position of z
     * @return Either a network, or null if none can be found or created.
     */
    @Nullable
    public N getNetworkSoft(long x, long y, long z) {
        int id = get(x, y, z);
        if (id == -1) {
            return null;
        }
        if (id == -2) {
            if (allowDefaultNetwork()) {
                return getNetwork(-2);
            } else {
                return null;
            }
        }
        return getNetwork(id);
    }

    /**
     * Returns a network of blocks at a given oct coordinate.
     * This will try to find an existing network, otherwise it will create a new one.
     * @param pos Oct position in the world
     * @return Either a network, or null if none can be found or created.
     */
    @Nullable
    public N getNetwork(OctPos pos) {
        return getNetwork(pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    @Override
    protected boolean allowDefaultNetwork() {
        return type.allowDefaultNetwork();
    }

    @Override
    protected int maxSize() {
        return MAX_SIZE;
    }

    private static long chunkKey(long x, long z) {
        return ChunkPos.asLong((int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L));
    }

    private boolean loaded(long x, long z) {
        return level.getChunkSource().getChunkNow((int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L)) != null;
    }

    @Override
    protected boolean canConnect(long x1, long y1, long z1, long x2, long y2, long z2) {
        if (!loaded(x1, z1) || !loaded(x2, z2)) {
            return false;
        }
        OctLevel octLevel = LevelOctExtension.get(level);
        OctPos first = new OctPos(x1, y1, z1);
        OctPos second = new OctPos(x2, y2, z2);
        return type.shouldConnect(octLevel.getBlockState(first), octLevel.getBlockState(second), level, octLevel, first, second);
    }

    @Override
    protected boolean isMember(long x, long y, long z) {
        if (!loaded(x, z)) {
            return false;
        }
        OctLevel octLevel = LevelOctExtension.get(level);
        return type.isMember(x, y, z, octLevel, level);
    }

    @Override
    protected Discovery discoverNetworkMembers(long x, long y, long z) {
        Deque<SpatialPos> queue = new ArrayDeque<>();
        Set<SpatialPos> visited = new HashSet<>();
        List<SpatialPos> result = new ArrayList<>();

        SpatialPos start = new SpatialPos(x, y, z);
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            SpatialPos current = queue.poll();
            result.add(current);

            for (int i = 0; i < 6; i++) {
                long nx = current.x() + (i == 0 ? 1 : i == 1 ? -1 : 0);
                long ny = current.y() + (i == 2 ? 1 : i == 3 ? -1 : 0);
                long nz = current.z() + (i == 4 ? 1 : i == 5 ? -1 : 0);

                SpatialPos neighbor = new SpatialPos(nx, ny, nz);

                if (visited.contains(neighbor)) {
                    continue;
                }

                visited.add(neighbor);
                if (visited.size() > MAX_SIZE) {
                    return new Discovery(true, result);
                }
                if (!loaded(nx, nz)) {
                    frontiers.computeIfAbsent(chunkKey(nx, nz), key -> new ArrayList<>()).add(new Frontier(current, neighbor));
                    continue;
                }
                if (canConnect(current.x(), current.y(), current.z(), nx, ny, nz)) {
                    queue.add(neighbor);
                }
            }
        }
        return new Discovery(false, result);
    }

    @Override
    public void merge(int first, int second) {
        invalidate(first);
        N kept = getNetwork(first);
        NetworkAttachment absorbedAttachment = attachments.get(second);
        N absorbed = absorbedAttachment == null ? null : absorbedAttachment.modules().get(type.moduleType());
        if (kept != null && absorbed != null) {
            kept.onMerged(absorbed);
            type.moduleType().fire(kept, NetworkEvents.MERGED, absorbed);
        }
        if (absorbedAttachment != null) {
            EmpiricCore.modules().detachAll(absorbedAttachment);
            attachments.remove(second);
        }
        if (kept != null) {
            kept.update();
        }
    }

    @Override
    public void update(int id) {
        invalidate(id);
        N network = getNetwork(id);
        if (network != null) {
            network.update();
            type.moduleType().fire(network, NetworkEvents.UPDATED, null);
        }
    }

    @Override
    public void split(int first, int second) {
        invalidate(first);
        invalidate(second);
        N original = getNetwork(first);
        N split = getNetwork(second);
        if (original != null && split != null) {
            original.onSplit(split);
            type.moduleType().fire(original, NetworkEvents.SPLIT, split);
        }
        if (original != null) {
            original.update();
        }
        if (split != null) {
            split.update();
        }
    }

    /**
     * Refreshes the eight octants of a block that changed in the world.
     */
    public void blockChanged(BlockPos pos) {
        List<SpatialPos> points = new ArrayList<>(8);
        for (int octant = 0; octant < 8; octant++) {
            OctPos oct = new OctPos(pos, octant);
            points.add(new SpatialPos(oct.getLongX(), oct.getLongY(), oct.getLongZ()));
        }
        refreshPoints(points);
    }

    public void octChanged(OctPos pos) {
        refreshPoints(List.of(new SpatialPos(pos.getLongX(), pos.getLongY(), pos.getLongZ())));
    }

    public void chunkLoaded(ChunkPos chunk) {
        List<Frontier> reached = frontiers.remove(chunk.toLong());
        if (reached != null) {
            for (Frontier frontier : reached) {
                SpatialPos from = frontier.from();
                SpatialPos to = frontier.to();
                if (cache.get(from.x(), from.y(), from.z()) == -1 || cache.get(to.x(), to.y(), to.z()) != -1) {
                    continue;
                }
                if (canConnect(from.x(), from.y(), from.z(), to.x(), to.y(), to.z())) {
                    int id = getOrDiscover(to.x(), to.y(), to.z());
                    if (id >= 0) {
                        update(id);
                    }
                }
            }
        }
        List<Saved> saved = pending.remove(chunk.toLong());
        if (saved != null) {
            for (Saved entry : saved) {
                restore(entry);
            }
        }
    }

    public void load(Saved saved) {
        SpatialPos anchor = saved.anchor();
        pending.computeIfAbsent(chunkKey(anchor.x(), anchor.z()), key -> new ArrayList<>()).add(saved);
        if (loaded(anchor.x(), anchor.z())) {
            chunkLoaded(new ChunkPos((int) Math.floorDiv(anchor.x(), 32L), (int) Math.floorDiv(anchor.z(), 32L)));
        }
    }

    private void restore(Saved saved) {
        SpatialPos anchor = saved.anchor();
        if (!type.isEnabled()) {
            pending.computeIfAbsent(chunkKey(anchor.x(), anchor.z()), key -> new ArrayList<>()).add(saved);
            return;
        }
        int id = getOrDiscover(anchor.x(), anchor.y(), anchor.z());
        if (id < 0) {
            Empiric.LOGGER.warn("Dropped saved {} network data, {} {} {} isn't part of a network anymore", type.id(), anchor.x(), anchor.y(), anchor.z());
            return;
        }
        ModuleSystem modules = EmpiricCore.modules();
        NetworkAttachment attachment = attachment(id);
        N existing = attachment.modules().get(type.moduleType());
        if (existing == null) {
            modules.load(attachment, EmpiricBuffers.buffer(saved.data()), modules.specs().context(Encoding.KEYED));
            N network = attachment.modules().get(type.moduleType());
            if (network != null) {
                network.update();
            }
            return;
        }
        NetworkAttachment absorbed = new NetworkAttachment(this, RESTORING);
        modules.load(absorbed, EmpiricBuffers.buffer(saved.data()), modules.specs().context(Encoding.KEYED));
        N network = absorbed.modules().get(type.moduleType());
        if (network != null) {
            existing.onMerged(network);
        }
        modules.detachAll(absorbed);
        existing.update();
    }

    public List<Saved> save() {
        List<Saved> saved = new ArrayList<>();
        for (List<Saved> entries : pending.values()) {
            saved.addAll(entries);
        }
        for (NetworkAttachment attachment : attachments.values()) {
            if (attachment.id() < 0 || attachment.modules().isEmpty()) {
                continue;
            }
            SpatialPos anchor = null;
            for (SpatialPos member : getNetworkMembers(attachment.id())) {
                if (anchor == null || ANCHOR_ORDER.compare(member, anchor) < 0) {
                    anchor = member;
                }
            }
            if (anchor != null) {
                saved.add(new Saved(anchor, WorldModuleSystem.encode(attachment)));
            }
        }
        return saved;
    }

    public void unload() {
        ModuleSystem modules = EmpiricCore.modules();
        for (NetworkAttachment attachment : attachments.values()) {
            modules.unload(attachment);
        }
        attachments.clear();
    }

    private record Frontier(SpatialPos from, SpatialPos to) {
    }

    public record Saved(SpatialPos anchor, byte[] data) {
    }
}
