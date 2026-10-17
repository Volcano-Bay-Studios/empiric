package xyz.volcanobay.empiric.system;

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
import xyz.volcanobay.empiric.core.module.ModuleEvent;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public final class TemperatureSystem {

    public static final ChunkModuleType<TemperatureModule> TEMPERATURE = ChunkModuleType.builder(Empiric.id("temperature"), TemperatureModule.class, TemperatureModule::new).build();

    /**
     * Fired with the section index when a section's temperatures change: on the server when it sends them, and on the client when it receives them.
     */
    public static final ModuleEvent<Integer> SECTION_CHANGED = ModuleEvent.create(Empiric.id("temperature_section_changed")::toString);

    public static final BlockDataType<TemperatureBlockData> BLOCK_DATA = new BlockDataType<>(Empiric.id("temperature"), TemperatureBlockData.class, TemperatureBlockData::new);

    private static final Vector3ic[] FACES = {new Vector3i(0, 1, 0), new Vector3i(0, -1, 0), new Vector3i(0, 0, 1), new Vector3i(0, 0, -1), new Vector3i(1, 0, 0), new Vector3i(-1, 0, 0)};
    private static final RandomSource RANDOM = RandomSource.create();
    private static ToIntFunction<Level> ambient = level -> AtmosphereSystem.levelAtmosphere(level).ambient();
    private static ToDoubleFunction<Level> dimensionCooling = level -> level.dimension() == Level.END ? 0.002 : 0.05;

    private TemperatureSystem() {
    }

    public static void register(ModuleSystem modules) {
        modules.register(TEMPERATURE);
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
        return conductivityOf(LevelOctExtension.get(level).getBlockState(new OctPos(x, y, z)));
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
        TemperatureModule module = module(level, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false);
        if (module != null) {
            module.refresh(pos);
        }
    }

    public static void propagate(TemperatureModule module) {
        Level level = module.level();
        double chance = dimensionCooling.applyAsDouble(level);
        long baseX = module.chunk().getPos().x * 32L;
        long baseZ = module.chunk().getPos().z * 32L;
        for (int section : module.sections.keySet().toIntArray()) {
            short[] values = module.sections.get(section);
            if (values == null) {
                continue;
            }
            byte[] conductivity = module.conductivity(section);
            long[] active = module.active(section);
            long baseY = (long) (level.getMinSection() + section) * 32L;
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
                        long x = baseX + nx;
                        long y = baseY + ny;
                        long z = baseZ + nz;
                        int other = conductivity(level, x, y, z);
                        if (other > 0) {
                            int second = delta(level, x, y, z);
                            int kept = share(values[i], second, Math.min(own, other) / 100f);
                            setDelta(level, x, y, z, values[i] + second - kept);
                            values[i] = (short) kept;
                            changed = true;
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

    private static int share(int first, int second, float conductivity) {
        float target = first - (first - second) * conductivity / 2f;
        int floor = (int) Math.floor(target);
        return RANDOM.nextFloat() < target - floor ? floor + 1 : floor;
    }
}
