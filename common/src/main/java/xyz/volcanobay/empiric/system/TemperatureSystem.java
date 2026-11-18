package xyz.volcanobay.empiric.system;

import com.google.common.annotations.VisibleForTesting;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.EmpiricApi;
import xyz.volcanobay.empiric.api.data.BlockDataType;
import xyz.volcanobay.empiric.api.temperature.TemperatureBlockData;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.core.module.ModuleEvent;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public final class TemperatureSystem {

    @Register("temperature")
    public static final ChunkModuleType<TemperatureModule> TEMPERATURE = ChunkModuleType.builder(TemperatureModule.class, TemperatureModule::new).build();

    /**
     * Fired with the section index when a section's temperatures change: on the server when it sends them, and on the client when it receives them.
     */
    public static final ModuleEvent<Integer> SECTION_CHANGED = ModuleEvent.create(Empiric.id("temperature_section_changed")::toString);

    public static final BlockDataType<TemperatureBlockData> BLOCK_DATA = new BlockDataType<>(Empiric.id("temperature"), TemperatureBlockData.class, TemperatureBlockData::new);

    private static final Vector3ic[] FACES = {new Vector3i(0, 1, 0), new Vector3i(0, -1, 0), new Vector3i(0, 0, 1), new Vector3i(0, 0, -1), new Vector3i(1, 0, 0), new Vector3i(-1, 0, 0)};
    private static final RandomSource RANDOM = RandomSource.create();
    private static boolean counting;
    private static long crossExchanges;
    private static ToIntFunction<Level> ambient = level -> AtmosphereSystem.levelAtmosphere(level).ambient();
    private static ToDoubleFunction<Level> dimensionCooling = level -> level.dimension() == Level.END ? 0.002 : 0.05;

    private TemperatureSystem() {
    }

    @VisibleForTesting
    public static void startCountingCrossExchanges() {
        crossExchanges = 0;
        counting = true;
    }

    @VisibleForTesting
    public static long stopCountingCrossExchanges() {
        counting = false;
        return crossExchanges;
    }

    public static ToDoubleFunction<Level> getCooling() {
        return dimensionCooling;
    }

    @VisibleForTesting
    public static void reseed(long seed) {
        RANDOM.setSeed(seed);
    }

    /** @deprecated registration is automatic, so this does nothing. */
    @Deprecated
    public static void register(ModuleSystem modules) {
    }

    public static void subscribe(String modId) {
        EmpiricApi.subscribe(modId, TEMPERATURE);
    }

    public static void unsubscribe(String modId) {
        EmpiricApi.unsubscribe(modId, TEMPERATURE);
    }

    /**
     * Overrides the resting temperature of a dimension in tenths of a degree Celsius (240 is 24 C), which otherwise comes from its level atmosphere entry. Cells drift back to it and everything else is stored relative to it.
     * Must give the same answer on the server and the client.
     */
    public static void setAmbient(ToIntFunction<Level> temperature) {
        ambient = temperature;
    }

    public static int ambient(Level level) {
        return Mth.clamp(ambient.applyAsInt(level), TemperatureModule.MIN, TemperatureModule.MAX);
    }

    public static void setCooling(ToDoubleFunction<Level> chance) {
        dimensionCooling = chance;
    }

    public static void registerData(DataEntrySystem data) {
        BLOCK_DATA.register(data);
    }

    public static byte conductivityOf(BlockState state) {
        if (state.isAir()) {
            return -1;
        }
        double conductivity = BLOCK_DATA.get(state).conductivity;
        return (byte) Math.round(Math.max(0, Math.min(1, conductivity)) * 100);
    }

    public static TemperatureModule module(Level level, int chunkX, int chunkZ, boolean create) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        TemperatureModule module = TEMPERATURE.get(attachment);
        if (module == null && create && TEMPERATURE.isEnabled() && attachment.isActive()) {
            module = TEMPERATURE.attach(attachment);
        }
        return module;
    }

    /**
     * A view of one section of a chunk's temperatures, for reading or writing many cells at once.
     *
     * @param create attach the chunk's temperature module when it has none, under the same conditions as {@link #set}
     * @return the view, or null when the chunk isn't loaded, the section is out of the level, or the chunk has no module and {@code create} is false or can't attach one
     */
    public static TemperatureSection section(Level level, int chunkX, int sectionIndex, int chunkZ, boolean create) {
        if (sectionIndex < 0 || sectionIndex >= level.getSectionsCount()) {
            return null;
        }
        TemperatureModule module = module(level, chunkX, chunkZ, create);
        return module == null ? null : new TemperatureSection(module, sectionIndex);
    }

    private static int section(Level level, long octY) {
        return level.getSectionIndex((int) Math.floorDiv(octY, 2));
    }

    private static boolean inBuildHeight(Level level, long octY) {
        int y = (int) Math.floorDiv(octY, 2);
        return y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight();
    }

    private static int index(long x, long y, long z) {
        return TemperatureModule.index((int) Math.floorMod(x, 32L), (int) Math.floorMod(y, 32L), (int) Math.floorMod(z, 32L));
    }

    public static short get(Level level, long x, long y, long z) {
        return (short) (ambient(level) + delta(level, x, y, z));
    }

    public static void set(Level level, long x, long y, long z, int value) {
        setDelta(level, x, y, z, value - ambient(level));
    }

    private static short delta(Level level, long x, long y, long z) {
        if (!inBuildHeight(level, y)) {
            return 0;
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), false);
        return module == null ? 0 : module.get(section(level, y), index(x, y, z));
    }

    private static void setDelta(Level level, long x, long y, long z, int value) {
        if (!inBuildHeight(level, y)) {
            return;
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), value != 0);
        if (module != null) {
            module.set(section(level, y), index(x, y, z), value);
        }
    }

    public static byte conductivity(Level level, long x, long y, long z) {
        if (!inBuildHeight(level, y)) {
            return -1;
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), false);
        if (module != null) {
            return module.conductivity(section(level, y))[index(x, y, z)];
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow((int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L));
        if (chunk == null) {
            return -1;
        }
        return conductivityOf(OctLevel.get(level).getBlockState(new OctPos(x, y, z)));
    }

    public static short get(Level level, OctPos pos) {
        return get(level, pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    public static void set(Level level, OctPos pos, int value) {
        set(level, pos.getLongX(), pos.getLongY(), pos.getLongZ(), value);
    }

    public static void add(Level level, OctPos pos, int value) {
        set(level, pos, get(level, pos) + value);
    }

    public static short get(Level level, BlockPos pos) {
        int total = 0;
        for (int octant = 0; octant < 8; octant++) {
            total += get(level, new OctPos(pos, octant));
        }
        return (short) (total / 8);
    }

    public static void set(Level level, BlockPos pos, int value) {
        for (int octant = 0; octant < 8; octant++) {
            set(level, new OctPos(pos, octant), value);
        }
    }

    public static void add(Level level, BlockPos pos, int value) {
        for (int octant = 0; octant < 8; octant++) {
            add(level, new OctPos(pos, octant), value);
        }
    }

    public static void blockChanged(Level level, BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        TemperatureModule module = module(level, chunkX, chunkZ, false);
        if (module != null) {
            module.refresh(pos);
        }
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (localX == 0) {
            invalidateBorder(level, chunkX - 1, chunkZ, pos, 4);
        } else if (localX == 15) {
            invalidateBorder(level, chunkX + 1, chunkZ, pos, 5);
        }
        if (localZ == 0) {
            invalidateBorder(level, chunkX, chunkZ - 1, pos, 2);
        } else if (localZ == 15) {
            invalidateBorder(level, chunkX, chunkZ + 1, pos, 3);
        }
    }

    private static void invalidateBorder(Level level, int chunkX, int chunkZ, BlockPos pos, int face) {
        TemperatureModule module = module(level, chunkX, chunkZ, false);
        if (module != null) {
            module.invalidateBorder(level.getSectionIndex(pos.getY()), face);
        }
    }

    public static void propagate(TemperatureModule module) {
        Level level = module.level();
        double chance = dimensionCooling.applyAsDouble(level);
        int chunkX = module.chunk().getPos().x;
        int chunkZ = module.chunk().getPos().z;
        long baseX = chunkX * 32L;
        long baseZ = chunkZ * 32L;
        int ambient = ambient(level);
        int low = Math.max(TemperatureModule.MIN - ambient, Short.MIN_VALUE);
        int high = Math.min(TemperatureModule.MAX - ambient, Short.MAX_VALUE);
        int sectionCount = level.getSectionsCount();
        Neighbour[] slots = new Neighbour[6];
        for (int f = 0; f < 6; f++) {
            slots[f] = new Neighbour();
        }
        for (int section : module.sections.keySet().toIntArray()) {
            short[] values = module.sections.get(section);
            if (values == null) {
                continue;
            }
            byte[] conductivity = module.conductivity(section);
            long[] active = module.active(section);
            long baseY = (long) (level.getMinSection() + section) * 32L;
            for (Neighbour slot : slots) {
                slot.reset();
            }
            boolean any = false;
            boolean changed = false;
            for (int word = 0; word < active.length; word++) {
                long bits = active[word];
                while (bits != 0) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    bits &= bits - 1;
                    int i = (word << 6) | bit;
                    if (values[i] == 0) {
                        active[word] &= ~(1L << bit);
                        continue;
                    }
                    any = true;
                    if (RANDOM.nextDouble() < chance) {
                        values[i] += values[i] > 0 ? -1 : 1;
                        changed = true;
                    }
                    if (values[i] == 0) {
                        active[word] &= ~(1L << bit);
                        continue;
                    }
                    int own = conductivity[i];
                    if (own <= 0) {
                        continue;
                    }
                    int lx = (i >> 10) & 31;
                    int ly = (i >> 5) & 31;
                    int lz = i & 31;
                    int face = RANDOM.nextInt(6);
                    for (int f = face; f < 6; f++) {
                        int nx = lx + FACES[f].x();
                        int ny = ly + FACES[f].y();
                        int nz = lz + FACES[f].z();
                        if (nx >= 0 && nx < 32 && ny >= 0 && ny < 32 && nz >= 0 && nz < 32) {
                            int neighbour = TemperatureModule.index(nx, ny, nz);
                            int other = conductivity[neighbour];
                            if (other > 0) {
                                int kept = share(values[i], values[neighbour], Math.min(own, other) / 100f);
                                values[neighbour] = (short) (values[i] + values[neighbour] - kept);
                                values[i] = (short) kept;
                                if (values[neighbour] != 0) {
                                    active[neighbour >> 6] |= 1L << neighbour;
                                }
                                changed = true;
                            }
                            continue;
                        }
                        Neighbour slot = slots[f];
                        if (!slot.resolved) {
                            slot.resolve(level, module, f, section, sectionCount, chunkX, chunkZ);
                        }
                        if (slot.blocked) {
                            continue;
                        }
                        if (slot.module == null) {
                            int plane = f >= 4 ? ny * 32 + nz : nx * 32 + ny;
                            int other = slot.border[plane];
                            if (other > 0) {
                                long x = baseX + nx;
                                long y = baseY + ny;
                                long z = baseZ + nz;
                                int second = delta(level, x, y, z);
                                int kept = share(values[i], second, Math.min(own, other) / 100f);
                                setDelta(level, x, y, z, values[i] + second - kept);
                                values[i] = (short) kept;
                                changed = true;
                                if (TEMPERATURE.get(ChunkAttachment.of(slot.chunk)) != null) {
                                    slot.resolved = false;
                                }
                                if (counting) {
                                    crossExchanges++;
                                }
                            }
                            continue;
                        }
                        int neighbour = TemperatureModule.index(nx & 31, ny & 31, nz & 31);
                        int other = slot.conductivity[neighbour];
                        if (other > 0) {
                            int second = slot.values == null ? 0 : slot.values[neighbour];
                            int kept = share(values[i], second, Math.min(own, other) / 100f);
                            slot.write(neighbour, values[i] + second - kept, low, high);
                            values[i] = (short) kept;
                            changed = true;
                            if (counting) {
                                crossExchanges++;
                            }
                        }
                    }
                }
            }
            if (!any) {
                module.removeSection(section);
            } else if (changed) {
                module.touch(section);
            }
        }
        if (module.isEmpty()) {
            TEMPERATURE.detach(module.attachment());
        }
    }

    private static final class Neighbour {

        boolean resolved;
        boolean blocked;
        TemperatureModule module;
        int section;
        short[] values;
        byte[] conductivity;
        long[] active;
        LevelChunk chunk;
        byte[] border;

        void reset() {
            resolved = false;
            blocked = false;
            module = null;
            values = null;
            conductivity = null;
            active = null;
            chunk = null;
            border = null;
        }

        void resolve(Level level, TemperatureModule own, int face, int ownSection, int sectionCount, int chunkX, int chunkZ) {
            reset();
            resolved = true;
            section = ownSection;
            if (face < 2) {
                section += face == 0 ? 1 : -1;
                if (section < 0 || section >= sectionCount) {
                    blocked = true;
                    return;
                }
                module = own;
            } else {
                int dx = face == 4 ? 1 : face == 5 ? -1 : 0;
                int dz = face == 2 ? 1 : face == 3 ? -1 : 0;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                if (chunk == null) {
                    blocked = true;
                    return;
                }
                module = TEMPERATURE.get(ChunkAttachment.of(chunk));
                if (module == null) {
                    this.chunk = chunk;
                    border = own.border(ownSection, face, chunk);
                    return;
                }
            }
            values = module.sections.get(section);
            conductivity = module.conductivity(section);
        }

        void write(int index, int value, int low, int high) {
            short clamped = (short) Mth.clamp(value, low, high);
            short[] cells = values;
            if (cells == null) {
                if (clamped == 0) {
                    return;
                }
                cells = module.values(section, true);
                values = cells;
            }
            if (cells[index] != clamped) {
                cells[index] = clamped;
                module.touch(section);
                if (clamped != 0) {
                    if (active == null) {
                        active = module.active(section);
                    }
                    active[index >> 6] |= 1L << index;
                }
            }
        }
    }

    private static int share(int first, int second, float conductivity) {
        float target = first - (first - second) * conductivity / 2f;
        int floor = (int) Math.floor(target);
        return RANDOM.nextFloat() < target - floor ? floor + 1 : floor;
    }
}
