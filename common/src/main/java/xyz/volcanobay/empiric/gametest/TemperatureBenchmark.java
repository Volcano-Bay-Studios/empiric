package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3i;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.system.AtmosphereSystem;
import xyz.volcanobay.empiric.system.AtmosphereThermalSystem;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class TemperatureBenchmark {

    private static final int GRID = 4;
    private static final int WARMUP = 20;
    private static final int TICKS = 200;
    private static final int HEAT = 800;
    private static final int LOOKUPS = 1_000_000;
    private static final long SEED = 20261117L;

    private TemperatureBenchmark() {
    }

    private static final class Box {

        final Vector3i min;
        final Vector3i max;

        Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.min = new Vector3i(minX, minY, minZ);
            this.max = new Vector3i(maxX, maxY, maxZ);
        }
    }

    private static final class Grid {

        final ServerLevel level;
        final int chunkX;
        final int chunkZ;
        final int baseY;
        final List<Box> stone = new ArrayList<>();
        final List<Box> hot = new ArrayList<>();

        Grid(GameTestHelper helper) {
            this.level = helper.getLevel();
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            this.chunkX = (origin.getX() >> 4) + 2;
            this.chunkZ = (origin.getZ() >> 4) + 2;
            this.baseY = ((origin.getY() >> 4) + 2) << 4;
        }

        int minX() {
            return chunkX * 16;
        }

        int minZ() {
            return chunkZ * 16;
        }

        int maxX() {
            return (chunkX + GRID) * 16;
        }

        int maxZ() {
            return (chunkZ + GRID) * 16;
        }
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(test.apply("temp_bench_interior", TemperatureBenchmark::interior),
                test.apply("temp_bench_boundary", TemperatureBenchmark::boundary),
                test.apply("temp_bench_spread", TemperatureBenchmark::spread),
                test.apply("temp_bench_atmosphere_exchange", TemperatureBenchmark::atmosphereExchange));
    }

    private static void interior(GameTestHelper helper) {
        Grid grid = new Grid(helper);
        int x = grid.minX() + 16;
        int z = grid.minZ() + 16;
        grid.stone.add(new Box(x, grid.baseY, z, x + 16, grid.baseY + 16, z + 16));
        grid.hot.add(new Box(x + 2, grid.baseY + 2, z + 2, x + 14, grid.baseY + 14, z + 14));
        run(helper, grid, "interior", false);
    }

    private static void boundary(GameTestHelper helper) {
        Grid grid = new Grid(helper);
        for (int k = 1; k < GRID; k++) {
            int x = grid.minX() + k * 16;
            int z = grid.minZ() + k * 16;
            grid.stone.add(new Box(x - 4, grid.baseY - 2, grid.minZ(), x + 4, grid.baseY + 34, grid.maxZ()));
            grid.hot.add(new Box(x - 2, grid.baseY, grid.minZ(), x + 2, grid.baseY + 32, grid.maxZ()));
            grid.stone.add(new Box(grid.minX(), grid.baseY - 2, z - 4, grid.maxX(), grid.baseY + 34, z + 4));
            grid.hot.add(new Box(grid.minX(), grid.baseY, z - 2, grid.maxX(), grid.baseY + 32, z + 2));
        }
        run(helper, grid, "boundary", false);
    }

    private static void spread(GameTestHelper helper) {
        Grid grid = new Grid(helper);
        for (int i = 0; i < 4; i++) {
            int x = grid.minX() + (1 + (i & 1) * 2) * 16;
            int z = grid.minZ() + (1 + (i >> 1) * 2) * 16;
            grid.stone.add(new Box(x - 12, grid.baseY + 4, z - 12, x + 12, grid.baseY + 28, z + 12));
            grid.hot.add(new Box(x - 10, grid.baseY + 6, z - 10, x + 10, grid.baseY + 26, z + 10));
        }
        run(helper, grid, "spread", true);
    }

    private static void run(GameTestHelper helper, Grid grid, String name, boolean lookups) {
        ServerLevel level = grid.level;
        load(grid);
        try {
            for (Box box : grid.stone) {
                fill(level, box, Blocks.STONE.defaultBlockState(), 2);
            }
            TemperatureSystem.reseed(SEED);
            int ambient = TemperatureSystem.ambient(level);
            for (Box box : grid.hot) {
                heat(level, box, ambient + HEAT);
            }
            long initial = total(grid);
            long[] nanos = new long[TICKS];
            long active = 0;
            long cross = 0;
            for (int tick = 0; tick < WARMUP + TICKS; tick++) {
                boolean measured = tick >= WARMUP;
                if (measured) {
                    TemperatureSystem.startCountingCrossExchanges();
                }
                List<TemperatureModule> modules = modules(grid);
                long start = System.nanoTime();
                for (TemperatureModule module : modules) {
                    TemperatureSystem.propagate(module);
                }
                long elapsed = System.nanoTime() - start;
                if (measured) {
                    cross += TemperatureSystem.stopCountingCrossExchanges();
                    nanos[tick - WARMUP] = elapsed;
                    active += activeCells(grid);
                }
            }
            long checksum = checksum(grid);
            long finalTotal = total(grid);
            report(name, nanos, active, cross, checksum);
            helper.assertTrue(finalTotal >= 0 && finalTotal <= initial, "total heat left its bounds, " + initial + " became " + finalTotal);
            if (lookups) {
                lookups(grid);
            }
            helper.succeed();
        } finally {
            TemperatureSystem.stopCountingCrossExchanges();
            clear(grid);
            for (Box box : grid.stone) {
                fill(level, box, Blocks.AIR.defaultBlockState(), 2);
            }
            unload(grid);
        }
    }

    private static void atmosphereExchange(GameTestHelper helper) {
        Grid grid = new Grid(helper);
        ServerLevel level = grid.level;
        load(grid);
        Box shell = new Box(grid.minX() + 12, grid.baseY, grid.minZ() + 24, grid.minX() + 28, grid.baseY + 8, grid.minZ() + 32);
        Vector3i inner = new Vector3i(shell.min.x + 1, shell.min.y + 1, shell.min.z + 1);
        List<BlockPos> walls = new ArrayList<>();
        for (int x = shell.min.x; x < shell.max.x; x++) {
            for (int y = shell.min.y; y < shell.max.y; y++) {
                for (int z = shell.min.z; z < shell.max.z; z++) {
                    boolean edge = x == shell.min.x || x == shell.max.x - 1 || y == shell.min.y || y == shell.max.y - 1 || z == shell.min.z || z == shell.max.z - 1;
                    if (edge) {
                        walls.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        try {
            BlockState stone = Blocks.STONE.defaultBlockState();
            for (BlockPos wall : walls) {
                level.setBlock(wall, stone, 3);
            }
            AtmosphereNetwork room = AtmosphereSystem.ATMOSPHERE.get(level).getNetwork(new OctPos(new BlockPos(inner.x, inner.y, inner.z), 0));
            helper.assertTrue(room != null && !room.isWorldAtmosphere(), "the benchmark room didn't form an atmosphere network");
            int ambient = TemperatureSystem.ambient(level);
            long[] nanos = new long[TICKS];
            for (int tick = 0; tick < WARMUP + TICKS; tick++) {
                for (BlockPos wall : walls) {
                    TemperatureSystem.set(level, wall, ambient + 500);
                }
                long start = System.nanoTime();
                AtmosphereThermalSystem.exchange(room);
                long elapsed = System.nanoTime() - start;
                if (tick >= WARMUP) {
                    nanos[tick - WARMUP] = elapsed;
                }
            }
            long checksum = Double.doubleToLongBits(room.getVolume().getTemperature());
            for (BlockPos wall : walls) {
                checksum = checksum * 1000003L + TemperatureSystem.get(level, wall);
            }
            report("atmosphere_exchange", nanos, room.members().size() * (long) TICKS, 0, checksum);
            helper.assertTrue(room.getVolume().getTemperature() > 0 && !Double.isNaN(room.getVolume().getTemperature()), "the room temperature left its bounds");
            helper.succeed();
        } finally {
            clear(grid);
            for (BlockPos wall : walls) {
                level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            }
            unload(grid);
        }
    }

    private static void load(Grid grid) {
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                grid.level.setChunkForced(grid.chunkX + x, grid.chunkZ + z, true);
                grid.level.getChunk(grid.chunkX + x, grid.chunkZ + z);
            }
        }
    }

    private static void unload(Grid grid) {
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                grid.level.setChunkForced(grid.chunkX + x, grid.chunkZ + z, false);
            }
        }
    }

    private static void fill(ServerLevel level, Box box, BlockState state, int flags) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.min.x; x < box.max.x; x++) {
            for (int y = box.min.y; y < box.max.y; y++) {
                for (int z = box.min.z; z < box.max.z; z++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, state, flags);
                }
            }
        }
    }

    private static void heat(ServerLevel level, Box box, int value) {
        for (long x = box.min.x * 2L; x < box.max.x * 2L; x++) {
            for (long y = box.min.y * 2L; y < box.max.y * 2L; y++) {
                for (long z = box.min.z * 2L; z < box.max.z * 2L; z++) {
                    TemperatureSystem.set(level, x, y, z, value);
                }
            }
        }
    }

    private static List<TemperatureModule> modules(Grid grid) {
        List<TemperatureModule> modules = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                TemperatureModule module = TemperatureSystem.module(grid.level, grid.chunkX + x, grid.chunkZ + z, false);
                if (module != null) {
                    modules.add(module);
                }
            }
        }
        return modules;
    }

    private static long activeCells(Grid grid) {
        long cells = 0;
        for (TemperatureModule module : modules(grid)) {
            for (int section : module.sections.keySet().toIntArray()) {
                for (long word : module.active(section)) {
                    cells += Long.bitCount(word);
                }
            }
        }
        return cells;
    }

    private static long total(Grid grid) {
        long total = 0;
        for (TemperatureModule module : modules(grid)) {
            for (short[] values : module.sections.values()) {
                for (short value : values) {
                    total += value;
                }
            }
        }
        return total;
    }

    private static long checksum(Grid grid) {
        long hash = 7;
        for (TemperatureModule module : modules(grid)) {
            int[] sections = module.sections.keySet().toIntArray();
            Arrays.sort(sections);
            for (int section : sections) {
                hash = hash * 31 + section;
                for (short value : module.sections.get(section)) {
                    hash = hash * 1000003L + value;
                }
            }
        }
        return hash;
    }

    private static void clear(Grid grid) {
        for (TemperatureModule module : modules(grid)) {
            for (int section : module.sections.keySet().toIntArray()) {
                module.removeSection(section);
            }
            TemperatureSystem.TEMPERATURE.detach(module.attachment());
        }
    }

    private static void report(String name, long[] nanos, long active, long cross, long checksum) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        long sum = 0;
        for (long value : nanos) {
            sum += value;
        }
        double mean = sum / (double) nanos.length;
        double meanActive = active / (double) nanos.length;
        Empiric.LOGGER.info("TEMP_BENCH scenario={} ticks={} mean_us={} p50_us={} p95_us={} max_us={} active_cells={} cross_exchanges_per_tick={} ns_per_active_cell={} checksum={}",
                name, nanos.length, round(mean / 1000), round(sorted[sorted.length / 2] / 1000.0), round(sorted[sorted.length * 95 / 100] / 1000.0), round(sorted[sorted.length - 1] / 1000.0),
                Math.round(meanActive), round(cross / (double) nanos.length), round(meanActive == 0 ? 0 : mean / meanActive), checksum);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static void lookups(Grid grid) {
        Random random = new Random(SEED);
        int[] xs = new int[LOOKUPS];
        int[] ys = new int[LOOKUPS];
        int[] zs = new int[LOOKUPS];
        int[] indexes = new int[LOOKUPS];
        for (int i = 0; i < LOOKUPS; i++) {
            xs[i] = grid.minX() * 2 + random.nextInt(GRID * 32);
            ys[i] = grid.baseY * 2 + random.nextInt(64);
            zs[i] = grid.minZ() * 2 + random.nextInt(GRID * 32);
            indexes[i] = random.nextInt(TemperatureModule.CELLS);
        }
        TemperatureModule module = TemperatureSystem.module(grid.level, grid.chunkX + 1, grid.chunkZ + 1, false);
        int section = grid.level.getSectionIndex(grid.baseY + 8);
        long sink = 0;
        for (int i = 0; i < LOOKUPS / 10; i++) {
            sink += TemperatureSystem.get(grid.level, xs[i], ys[i], zs[i]);
            sink += module.get(section, indexes[i]);
        }
        long start = System.nanoTime();
        for (int i = 0; i < LOOKUPS; i++) {
            sink += TemperatureSystem.get(grid.level, xs[i], ys[i], zs[i]);
        }
        long get = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < LOOKUPS; i++) {
            sink += module.get(section, indexes[i]);
        }
        long raw = System.nanoTime() - start;
        Empiric.LOGGER.info("TEMP_BENCH lookup get_ns={} raw_ns={} sink={}", round(get / (double) LOOKUPS), round(raw / (double) LOOKUPS), sink);
    }
}
