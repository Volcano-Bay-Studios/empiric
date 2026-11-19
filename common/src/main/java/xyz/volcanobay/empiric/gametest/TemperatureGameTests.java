package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3i;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.temperature.*;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.system.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class TemperatureGameTests {

    private static final double AMBIENT = 293.15;
    private static final double HOT = 800;
    private static final double SIGMA = ThermalMaterials.STEFAN_BOLTZMANN;

    private TemperatureGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        List<TestFunction> all = List.of(test.apply("temperature_conservation", TemperatureGameTests::conservation),
                test.apply("temperature_isotropy", TemperatureGameTests::isotropy),
                test.apply("temperature_equilibrium", TemperatureGameTests::equilibrium),
                test.apply("temperature_newton_cooling", TemperatureGameTests::newtonCooling),
                test.apply("temperature_radiation", TemperatureGameTests::radiation),
                test.apply("temperature_semi_infinite", TemperatureGameTests::semiInfinite),
                test.apply("temperature_settle", TemperatureGameTests::settle),
                test.apply("temperature_determinism", TemperatureGameTests::determinism),
                test.apply("temperature_stability", TemperatureGameTests::stability),
                test.apply("temperature_into_moduleless_chunk", TemperatureGameTests::intoModulelessChunk),
                test.apply("temperature_border_invalidation", TemperatureGameTests::borderInvalidation),
                test.apply("temperature_section_view", TemperatureGameTests::sectionView),
                test.apply("temperature_space_bounded", TemperatureGameTests::spaceBounded),
                test.apply("temperature_sealed_room", TemperatureGameTests::sealedRoom),
                test.apply("temperature_wool_cap", TemperatureGameTests::woolCap),
                test.apply("temperature_shifted_borders", TemperatureGameTests::shiftedBorders),
                test.apply("temperature_persistence", TemperatureGameTests::persistence),
                test.apply("temperature_sync_range", TemperatureGameTests::syncRange),
                test.apply("temperature_zero_time_scale", TemperatureGameTests::zeroTimeScale),
                test.apply("temperature_parallel_determinism", TemperatureGameTests::parallelDeterminism),
                test.apply("temperature_substeps_by_material", TemperatureGameTests::substepsByMaterial),
                test.apply("temperature_config", TemperatureGameTests::config));
        return all;
    }

    private static final class Site {

        final ServerLevel level;
        final int chunkX;
        final int chunkZ;
        final int width;
        final int boundary;

        Site(GameTestHelper helper, int offsetX, int offsetZ, int width) {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            this.level = helper.getLevel();
            this.chunkX = (origin.getX() >> 4) + offsetX;
            this.chunkZ = (origin.getZ() >> 4) + offsetZ;
            this.width = width;
            this.boundary = ((origin.getY() >> 4) + 2) << 4;
            for (int x = 0; x < width; x++) {
                level.setChunkForced(chunkX + x, chunkZ, true);
                level.getChunk(chunkX + x, chunkZ);
            }
        }

        List<TemperatureModule> modules() {
            List<TemperatureModule> modules = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                TemperatureModule module = TemperatureSystem.module(level, chunkX + x, chunkZ, false);
                if (module != null) {
                    modules.add(module);
                }
            }
            return modules;
        }

        void step(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                TemperatureSystem.step(level, modules());
            }
        }

        void fill(BlockPos min, BlockPos max, Block block) {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, block.defaultBlockState(), 2);
            }
        }

        void finish(BlockPos min, BlockPos max) {
            reset();
            for (TemperatureModule module : modules()) {
                for (int section : module.sections.keySet().toIntArray()) {
                    module.removeSection(section);
                }
                TemperatureSystem.TEMPERATURE.detach(module.attachment());
            }
            fill(min, max, Blocks.AIR);
        }
    }

    private static void environment(double ambient, double convection, double sink, double timeScale, double emissivity) {
        TemperatureSystem.setEnvironment(level -> new ThermalEnvironment(ambient, convection, sink));
        TemperatureSystem.setTimeScale(level -> timeScale);
        TemperatureSystem.setEmissivityScale(emissivity);
    }

    private static void reset() {
        TemperatureSystem.setEnvironment(null);
        TemperatureSystem.setTimeScale(null);
        TemperatureSystem.setSettleThreshold(TemperatureSystem.SETTLE);
        TemperatureSystem.setEmissivityScale(1);
    }

    private static void log(String test, String format, Object... values) {
        Empiric.LOGGER.info("TEMP_TEST {} " + format, prepend(test, values));
    }

    private static Object[] prepend(String first, Object[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    private static double kelvin(ServerLevel level, BlockPos pos, int octant) {
        return TemperatureSystem.kelvin(level, new OctPos(pos, octant));
    }

    private static double capacity(ServerLevel level, BlockPos pos, int octant) {
        OctPos oct = new OctPos(pos, octant);
        return TemperatureSystem.heatCapacity(level, oct.getLongX(), oct.getLongY(), oct.getLongZ());
    }

    private static double energy(ServerLevel level, BlockPos min, BlockPos max) {
        double total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            for (int octant = 0; octant < 8; octant++) {
                total += capacity(level, pos, octant) * kelvin(level, pos, octant);
            }
        }
        return total;
    }

    private static void heat(ServerLevel level, BlockPos pos, double kelvin) {
        TemperatureSystem.setKelvin(level, pos, kelvin);
    }

    private static double erfc(double x) {
        double t = 1 / (1 + 0.3275911 * x);
        double poly = ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592;
        return poly * t * Math.exp(-x * x);
    }

    private static void conservation(GameTestHelper helper) {
        Site site = new Site(helper, 20, 20, 2);
        ServerLevel level = site.level;
        int edge = (site.chunkX + 1) * 16;
        int z = site.chunkZ * 16 + 6;
        BlockPos min = new BlockPos(edge - 2, site.boundary - 2, z);
        BlockPos max = new BlockPos(edge + 1, site.boundary + 1, z + 3);
        try {
            environment(AMBIENT, 0, AMBIENT, 200, 0);
            site.fill(min, max, Blocks.STONE);
            BlockPos hot = new BlockPos(edge - 1, site.boundary - 1, z + 1);
            heat(level, hot, HOT);
            double before = energy(level, min, max);
            site.step(400);
            double after = energy(level, min, max);
            double error = Math.abs(after - before) / before;
            double across = kelvin(level, new BlockPos(edge, site.boundary - 1, z + 1), 0) - AMBIENT;
            double above = kelvin(level, new BlockPos(edge - 1, site.boundary, z + 1), 1) - AMBIENT;
            log("conservation", "energy_before={} energy_after={} relative_error={} across_chunk_rise={} across_section_rise={}", before, after, error, across, above);
            helper.assertTrue(across > 1.0E-6, "no heat crossed the chunk border, rise " + across);
            helper.assertTrue(above > 1.0E-6, "no heat crossed the section border, rise " + above);
            helper.assertTrue(error < 1.0E-9, "energy wasn't conserved, relative error " + error);
            helper.succeed();
        } finally {
            site.finish(min, max);
        }
    }

    private static void isotropy(GameTestHelper helper) {
        Site site = new Site(helper, 24, 20, 1);
        ServerLevel level = site.level;
        BlockPos min = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos max = min.offset(4, 4, 4);
        try {
            environment(AMBIENT, 0, AMBIENT, 2000, 0);
            site.fill(min, max, Blocks.STONE);
            long cx = min.getX() * 2L + 5;
            long cy = min.getY() * 2L + 5;
            long cz = min.getZ() * 2L + 5;
            TemperatureSystem.setKelvin(level, cx, cy, cz, HOT);
            site.step(3);
            List<Vector3i> offsets = List.of(new Vector3i(1, 0, 0), new Vector3i(-1, 0, 0), new Vector3i(0, 1, 0), new Vector3i(0, -1, 0), new Vector3i(0, 0, 1), new Vector3i(0, 0, -1));
            double worst = 0;
            double nearFirst = TemperatureSystem.kelvin(level, cx + 1, cy, cz);
            double farFirst = TemperatureSystem.kelvin(level, cx + 2, cy, cz);
            for (Vector3i offset : offsets) {
                double near = TemperatureSystem.kelvin(level, cx + offset.x, cy + offset.y, cz + offset.z);
                double far = TemperatureSystem.kelvin(level, cx + offset.x * 2L, cy + offset.y * 2L, cz + offset.z * 2L);
                worst = Math.max(worst, Math.max(Math.abs(near - nearFirst), Math.abs(far - farFirst)));
            }
            log("isotropy", "neighbour_rise={} second_rise={} worst_difference={}", nearFirst - AMBIENT, farFirst - AMBIENT, worst);
            helper.assertTrue(nearFirst - AMBIENT > 0.01 && farFirst - AMBIENT > 1.0E-5, "the heat didn't spread, " + (nearFirst - AMBIENT) + " K and " + (farFirst - AMBIENT) + " K");
            helper.assertTrue(worst < 1.0E-9, "the heat didn't spread evenly in every direction, " + worst + " K apart");
            helper.succeed();
        } finally {
            site.finish(min, max);
        }
    }

    private static void equilibrium(GameTestHelper helper) {
        Site site = new Site(helper, 28, 20, 1);
        ServerLevel level = site.level;
        BlockPos copper = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos stone = copper.east();
        try {
            environment(AMBIENT, 0, AMBIENT, 40000, 0);
            TemperatureSystem.setSettleThreshold(1.0E-6);
            site.fill(copper, copper, Blocks.COPPER_BLOCK);
            site.fill(stone, stone, Blocks.STONE);
            heat(level, copper, 500);
            heat(level, stone, 300);
            double copperCapacity = capacity(level, copper, 0);
            double stoneCapacity = capacity(level, stone, 0);
            double expected = (copperCapacity * 500 + stoneCapacity * 300) / (copperCapacity + stoneCapacity);
            double before = energy(level, copper, stone);
            site.step(2000);
            double worst = 0;
            for (int octant = 0; octant < 8; octant++) {
                worst = Math.max(worst, Math.max(Math.abs(kelvin(level, copper, octant) - expected), Math.abs(kelvin(level, stone, octant) - expected)));
            }
            double drift = Math.abs(energy(level, copper, stone) - before) / before;
            log("equilibrium", "expected={} copper={} stone={} worst_difference={} energy_drift={}", expected, kelvin(level, copper, 0), kelvin(level, stone, 7), worst, drift);
            helper.assertTrue(worst < 0.05, "the blocks didn't settle on " + expected + " K, the furthest cell was " + worst + " K away");
            helper.succeed();
        } finally {
            site.finish(copper, stone);
        }
    }

    private static void newtonCooling(GameTestHelper helper) {
        Site site = new Site(helper, 32, 20, 1);
        ServerLevel level = site.level;
        BlockPos iron = new BlockPos(site.chunkX * 16 + 6, site.boundary + 6, site.chunkZ * 16 + 6);
        try {
            double convection = 10;
            double scale = 1000;
            environment(AMBIENT, convection, AMBIENT, scale, 0);
            site.fill(iron, iron, Blocks.IRON_BLOCK);
            heat(level, iron, 500);
            double capacity = 8 * capacity(level, iron, 0);
            double time = capacity / (convection * 6.0);
            int ticks = (int) Math.round(time / (0.05 * scale));
            double worst = 0;
            for (int part = 1; part <= 4; part++) {
                site.step(ticks / 4);
                double expected = (500 - AMBIENT) * Math.exp(-convection * 6.0 * (part * (ticks / 4) * 0.05 * scale) / capacity);
                for (int octant = 0; octant < 8; octant++) {
                    double measured = kelvin(level, iron, octant) - AMBIENT;
                    worst = Math.max(worst, Math.abs(measured - expected) / expected);
                }
                log("newton_cooling", "tick={} expected_excess={} measured_excess={}", part * (ticks / 4), expected, kelvin(level, iron, 0) - AMBIENT);
            }
            helper.assertTrue(worst < 0.01, "the block didn't cool like exp(-hAt/C), the worst error was " + worst);
            helper.succeed();
        } finally {
            site.finish(iron, iron);
        }
    }

    private static void radiation(GameTestHelper helper) {
        Site site = new Site(helper, 36, 20, 1);
        ServerLevel level = site.level;
        BlockPos iron = new BlockPos(site.chunkX * 16 + 6, site.boundary + 6, site.chunkZ * 16 + 6);
        try {
            double sink = 3;
            double scale = 100;
            environment(AMBIENT, 0, sink, scale, 1);
            site.fill(iron, iron, Blocks.IRON_BLOCK);
            heat(level, iron, 1500);
            ThermalMaterial material = TemperatureSystem.material(level, new OctPos(iron, 0));
            double capacity = 8 * material.capacity();
            double factor = material.emissivity() * SIGMA * 6.0 / capacity;
            double reference = 1500;
            double worst = 0;
            int ticks = 0;
            for (int part = 1; part <= 4; part++) {
                site.step(1000);
                ticks += 1000;
                double target = ticks * 0.05 * scale;
                double fine = 0.05;
                double time = (part - 1) * 1000 * 0.05 * scale;
                while (time < target - 1.0E-9) {
                    reference = rk4(reference, factor, sink, fine);
                    time += fine;
                }
                double measured = kelvin(level, iron, 0);
                worst = Math.max(worst, Math.abs(measured - reference) / reference);
                log("radiation", "tick={} reference={} measured={}", ticks, reference, measured);
            }
            helper.assertTrue(worst < 0.01, "the block didn't radiate like the reference integration, the worst error was " + worst);
            helper.succeed();
        } finally {
            site.finish(iron, iron);
        }
    }

    private static double rk4(double temperature, double factor, double sink, double step) {
        double s4 = sink * sink * sink * sink;
        double k1 = -factor * (Math.pow(temperature, 4) - s4);
        double k2 = -factor * (Math.pow(temperature + step / 2 * k1, 4) - s4);
        double k3 = -factor * (Math.pow(temperature + step / 2 * k2, 4) - s4);
        double k4 = -factor * (Math.pow(temperature + step * k3, 4) - s4);
        return temperature + step / 6 * (k1 + 2 * k2 + 2 * k3 + k4);
    }

    private static final int BAR = 24;
    private static final double BAR_SCALE = 4000;
    private static final int BAR_TICKS = 5000;
    private static final double BAR_HEAT = 800;

    private static double[] runBar(Site site, BlockPos start, int ticks) {
        ServerLevel level = site.level;
        BlockPos end = start.offset(0, 0, BAR - 1);
        site.fill(start, end, Blocks.STONE);
        for (int tick = 0; tick < ticks; tick++) {
            heat(level, start, BAR_HEAT);
            TemperatureSystem.step(level, site.modules());
        }
        double[] values = new double[BAR * 2];
        for (int i = 0; i < values.length; i++) {
            values[i] = TemperatureSystem.kelvin(level, start.getX() * 2L, start.getY() * 2L, start.getZ() * 2L + i);
        }
        return values;
    }

    private static long checksum(double[] values) {
        long hash = 7;
        for (double value : values) {
            hash = hash * 1000003L + Double.doubleToLongBits(value);
        }
        return hash;
    }

    private static void semiInfinite(GameTestHelper helper) {
        Site site = new Site(helper, 40, 20, 1);
        BlockPos start = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos end = start.offset(0, 0, BAR - 1);
        try {
            environment(AMBIENT, 0, AMBIENT, BAR_SCALE, 0);
            double[] values = runBar(site, start, BAR_TICKS);
            ThermalMaterial stone = TemperatureSystem.material(site.level, new OctPos(start, 0));
            double alpha = stone.conductivity() / (stone.density() * stone.specificHeat());
            double time = BAR_TICKS * 0.05 * BAR_SCALE;
            double delta = BAR_HEAT - AMBIENT;
            double worst = 0;
            for (int cell : new int[]{3, 5, 7}) {
                double depth = 0.25 + 0.5 * cell - 0.75;
                double expected = delta * erfc(depth / (2 * Math.sqrt(alpha * time)));
                double measured = values[cell] - AMBIENT;
                worst = Math.max(worst, Math.abs(measured - expected) / delta);
                log("semi_infinite", "depth={} expected_rise={} measured_rise={}", depth, expected, measured);
            }
            helper.assertTrue(worst < 0.03, "the bar didn't follow the erfc profile, the worst error was " + worst + " of the step");
            helper.succeed();
        } finally {
            site.finish(start, end);
        }
    }

    private static void determinism(GameTestHelper helper) {
        Site site = new Site(helper, 44, 20, 1);
        BlockPos start = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos end = start.offset(0, 0, BAR - 1);
        try {
            environment(AMBIENT, 0, AMBIENT, BAR_SCALE, 0);
            long first = checksum(runBar(site, start, 1500));
            site.finish(start, end);
            site.level.setChunkForced(site.chunkX, site.chunkZ, true);
            site.level.getChunk(site.chunkX, site.chunkZ);
            environment(AMBIENT, 0, AMBIENT, BAR_SCALE, 0);
            long second = checksum(runBar(site, start, 1500));
            log("determinism", "first={} second={}", first, second);
            helper.assertTrue(first == second, "two identical runs gave different results, " + first + " and " + second);
            helper.succeed();
        } finally {
            site.finish(start, end);
        }
    }

    private static final int SETTLE_CELLS = 12;
    private static final int SETTLE_VOLUME = SETTLE_CELLS * SETTLE_CELLS * SETTLE_CELLS;

    private static void settle(GameTestHelper helper) {
        Site site = new Site(helper, 48, 20, 1);
        ServerLevel level = site.level;
        BlockPos min = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos max = min.offset(SETTLE_CELLS / 2 - 1, SETTLE_CELLS / 2 - 1, SETTLE_CELLS / 2 - 1);
        try {
            environment(AMBIENT, 0, AMBIENT, 20000, 0);
            site.fill(min, max, Blocks.STONE);
            site.step(5);
            helper.assertTrue(TemperatureSystem.module(level, site.chunkX, site.chunkZ, false) == null, "a region at ambient got a temperature module");
            long cx = min.getX() * 2L + SETTLE_CELLS / 2;
            long cy = min.getY() * 2L + SETTLE_CELLS / 2;
            long cz = min.getZ() * 2L + SETTLE_CELLS / 2;
            double[] sleeping = settleRun(site, min, cx, cy, cz, TemperatureSystem.SETTLE);
            int activeCells = (int) sleeping[SETTLE_VOLUME];
            int farthest = (int) sleeping[SETTLE_VOLUME + 1];
            site.finish(min, max);
            site.level.setChunkForced(site.chunkX, site.chunkZ, true);
            site.level.getChunk(site.chunkX, site.chunkZ);
            site.fill(min, max, Blocks.STONE);
            environment(AMBIENT, 0, AMBIENT, 20000, 0);
            double[] awake = settleRun(site, min, cx, cy, cz, 0);
            double worst = 0;
            for (int i = 0; i < SETTLE_VOLUME; i++) {
                worst = Math.max(worst, Math.abs(sleeping[i] - awake[i]));
            }
            log("settle", "active_cells={} of={} farthest_active={} awake_active={} worst_difference={}", activeCells, SETTLE_VOLUME, farthest, (int) awake[SETTLE_VOLUME], worst);
            helper.assertTrue(activeCells > 0 && activeCells < SETTLE_VOLUME / 4, "the number of active cells should stay small, got " + activeCells);
            helper.assertTrue(farthest <= 5, "an active cell was " + farthest + " cells from the hot one");
            helper.assertTrue((int) awake[SETTLE_VOLUME] > activeCells * 4, "without settling far more cells should stay active, got " + (int) awake[SETTLE_VOLUME]);
            helper.assertTrue(worst < 0.05, "sleeping cells changed the result by " + worst + " K");
            helper.succeed();
        } finally {
            site.finish(min, max);
        }
    }

    private static double[] settleRun(Site site, BlockPos min, long cx, long cy, long cz, double threshold) {
        ServerLevel level = site.level;
        TemperatureSystem.setSettleThreshold(threshold);
        TemperatureSystem.setKelvin(level, cx, cy, cz, 400);
        site.step(20);
        double[] result = new double[SETTLE_VOLUME + 2];
        int index = 0;
        int active = 0;
        int farthest = 0;
        for (int x = 0; x < SETTLE_CELLS; x++) {
            for (int y = 0; y < SETTLE_CELLS; y++) {
                for (int z = 0; z < SETTLE_CELLS; z++) {
                    long ox = min.getX() * 2L + x;
                    long oy = min.getY() * 2L + y;
                    long oz = min.getZ() * 2L + z;
                    result[index++] = TemperatureSystem.kelvin(level, ox, oy, oz);
                    TemperatureModule module = TemperatureSystem.module(level, (int) Math.floorDiv(ox, 32L), (int) Math.floorDiv(oz, 32L), false);
                    if (module != null) {
                        int section = level.getSectionIndex((int) Math.floorDiv(oy, 2L));
                        long[] mask = module.activeMask(section);
                        int cell = TemperatureSection.indexOf(ox, oy, oz);
                        if (mask != null && (mask[cell >> 6] & (1L << cell)) != 0) {
                            active++;
                            farthest = Math.max(farthest, (int) Math.max(Math.abs(ox - cx), Math.max(Math.abs(oy - cy), Math.abs(oz - cz))));
                        }
                    }
                }
            }
        }
        result[SETTLE_VOLUME] = active;
        result[SETTLE_VOLUME + 1] = farthest;
        return result;
    }

    private static void stability(GameTestHelper helper) {
        Site site = new Site(helper, 52, 20, 1);
        ServerLevel level = site.level;
        BlockPos min = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos max = min.offset(2, 2, 2);
        try {
            environment(AMBIENT, 10, AMBIENT, 10000, 1);
            site.fill(min, max, Blocks.COPPER_BLOCK);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                heat(level, pos, pos.getX() - min.getX() < 2 ? HOT : AMBIENT);
            }
            double highest = HOT;
            double lowest = AMBIENT;
            double high = 0;
            double low = Double.MAX_VALUE;
            for (int tick = 0; tick < 50; tick++) {
                site.step(1);
                high = 0;
                low = Double.MAX_VALUE;
                for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                    for (int octant = 0; octant < 8; octant++) {
                        double value = kelvin(level, pos, octant);
                        helper.assertTrue(!Double.isNaN(value) && !Double.isInfinite(value), "a cell became " + value);
                        high = Math.max(high, value);
                        low = Math.min(low, value);
                    }
                }
                helper.assertTrue(high <= highest + 1.0E-9, "a cell rose to " + high + " K without a source on tick " + tick);
                helper.assertTrue(low >= lowest - 1.0E-6, "a cell undershot to " + low + " K on tick " + tick);
                highest = high;
            }
            log("stability", "max={} min={} substeps_limit_hit=true", high, low);
            helper.succeed();
        } finally {
            site.finish(min, max);
        }
    }

    private static void intoModulelessChunk(GameTestHelper helper) {
        Site site = new Site(helper, 56, 20, 2);
        ServerLevel level = site.level;
        BlockPos hot = new BlockPos(site.chunkX * 16 + 15, site.boundary + 5, site.chunkZ * 16 + 5);
        BlockPos next = hot.east();
        try {
            environment(AMBIENT, 0, AMBIENT, 2000, 0);
            helper.assertTrue(TemperatureSystem.module(level, site.chunkX + 1, site.chunkZ, false) == null, "the neighbouring chunk already had a temperature module");
            site.fill(hot, hot, Blocks.STONE);
            site.fill(next, next, Blocks.STONE);
            heat(level, hot, HOT);
            site.step(30);
            helper.assertTrue(TemperatureSystem.module(level, site.chunkX + 1, site.chunkZ, false) != null, "heat reaching a chunk without a module didn't attach one");
            helper.assertTrue(kelvin(level, next, 0) > AMBIENT + 0.05, "heat didn't flow into the chunk that had no module");
            helper.succeed();
        } finally {
            site.finish(hot, next);
        }
    }

    private static void borderInvalidation(GameTestHelper helper) {
        Site site = new Site(helper, 60, 20, 2);
        ServerLevel level = site.level;
        BlockPos hot = new BlockPos(site.chunkX * 16 + 15, site.boundary + 5, site.chunkZ * 16 + 5);
        BlockPos next = hot.east();
        try {
            environment(AMBIENT, 0, AMBIENT, 2000, 0);
            site.fill(hot, hot, Blocks.STONE);
            heat(level, hot, HOT);
            site.step(20);
            helper.assertTrue(TemperatureSystem.module(level, site.chunkX + 1, site.chunkZ, false) == null, "heat crossed into air at the chunk border");
            site.fill(next, next, Blocks.STONE);
            site.step(30);
            helper.assertTrue(kelvin(level, next, 0) > AMBIENT + 0.05, "heat didn't cross after the border block became stone");
            site.fill(next, next, Blocks.AIR);
            site.step(30);
            helper.assertTrue(kelvin(level, next, 0) == AMBIENT, "heat stayed in or entered the border block after it became air, " + kelvin(level, next, 0) + " K");
            helper.succeed();
        } finally {
            site.finish(hot, next);
        }
    }

    private static void sectionView(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() >> 4) + 8;
        int chunkZ = (origin.getZ() >> 4) + 16;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunk(chunkX, chunkZ);
        int sectionIndex = level.getSectionIndex(origin.getY()) + 3;
        BlockPos block = new BlockPos(chunkX * 16 + 1, (level.getMinSection() + sectionIndex) * 16 + 2, chunkZ * 16 + 2);
        try {
            helper.assertTrue(TemperatureSystem.module(level, chunkX, chunkZ, false) == null, "the chunk already had a temperature module");
            helper.assertTrue(TemperatureSystem.section(level, chunkX, sectionIndex, chunkZ, false) == null, "a view of a chunk without a module should be null when not creating");
            helper.assertTrue(TemperatureSystem.section(level, chunkX, -1, chunkZ, true) == null, "a section below the level should have no view");
            TemperatureSection section = TemperatureSystem.section(level, chunkX, sectionIndex, chunkZ, true);
            helper.assertTrue(section != null && TemperatureSystem.module(level, chunkX, chunkZ, false) == section.module(), "creating a view didn't attach the module");
            helper.assertTrue(section.sectionIndex() == sectionIndex, "the view has the wrong section index");
            int ambient = TemperatureSystem.ambient(level);
            int index = TemperatureSection.index(3, 4, 5);
            helper.assertTrue(TemperatureSection.indexOf(-1L, -1L, -1L) == TemperatureSection.index(31, 31, 31), "indexOf should wrap negative coordinates");
            helper.assertTrue(section.get(index) == 0 && section.temperature(index) == ambient, "an untouched cell should sit at ambient");
            helper.assertTrue(!section.module().isChanged(sectionIndex), "reading marked the section changed");
            int airIndex = TemperatureSection.index(20, 20, 20);
            section.set(airIndex, ambient + 100);
            helper.assertTrue(section.material(airIndex) == null && section.heatCapacity(airIndex) == 0, "air should hold no heat");
            helper.assertTrue(section.get(airIndex) == 0 && section.module().isEmpty() && !section.module().isChanged(sectionIndex), "air shouldn't take a temperature");
            level.setBlock(block, Blocks.STONE.defaultBlockState(), 2);
            helper.assertTrue(section.material(index) != null && section.heatCapacity(index) > 0, "stone should hold heat");
            section.set(index, ambient + 100);
            helper.assertTrue(section.get(index) == 100 && section.temperature(index) == ambient + 100, "set didn't stick");
            helper.assertTrue(section.module().isChanged(sectionIndex), "writing didn't mark the section changed");
            helper.assertTrue((section.module().active(sectionIndex)[index >> 6] & (1L << index)) != 0, "writing didn't mark the cell active");
            section.add(index, 50);
            helper.assertTrue(section.get(index) == 150, "add didn't add");
            section.set(index, 100_000);
            helper.assertTrue(section.temperature(index) == TemperatureModule.MAX, "a hot write wasn't clamped to the maximum");
            helper.assertTrue(section.kelvin(index) == 100_000 / 10.0 + TemperatureModule.ZERO_CELSIUS, "the stored temperature shouldn't be clamped to the tenths range");
            section.set(index, -5000);
            helper.assertTrue(section.temperature(index) == TemperatureModule.MIN, "a cold write wasn't clamped to absolute zero");
            helper.assertTrue(section.kelvin(index) == 0, "a cold write wasn't clamped to absolute zero kelvin");
            section.setDelta(index, 7);
            long x = chunkX * 32L + 3;
            long y = (long) (level.getMinSection() + sectionIndex) * 32 + 4;
            long z = chunkZ * 32L + 5;
            helper.assertTrue(TemperatureSystem.get(level, x, y, z) == ambient + 7, "the view and TemperatureSystem disagree");
            helper.assertTrue(java.util.Objects.equals(section.material(index), TemperatureSystem.material(level, x, y, z)), "the view and TemperatureSystem disagree on the material");
            helper.assertTrue(section.heatCapacity(index) == TemperatureSystem.heatCapacity(level, x, y, z), "the view and TemperatureSystem disagree on the heat capacity");
            double before = section.kelvin(index);
            section.addHeat(index, 100_000);
            double rise = section.kelvin(index) - before;
            helper.assertTrue(Math.abs(rise - 100_000 / section.heatCapacity(index)) < 1.0E-9, "addHeat should change the temperature by joules over capacity, got " + rise + " K");
            helper.succeed();
        } finally {
            level.setBlock(block, Blocks.AIR.defaultBlockState(), 2);
            TemperatureModule module = TemperatureSystem.module(level, chunkX, chunkZ, false);
            if (module != null) {
                module.removeSection(sectionIndex);
                TemperatureSystem.TEMPERATURE.detach(module.attachment());
            }
        }
    }

    private static void spaceBounded(GameTestHelper helper) {
        Site site = new Site(helper, 90, 20, 3);
        ServerLevel level = site.level;
        BlockPos min = new BlockPos(site.chunkX * 16, site.boundary + 3, site.chunkZ * 16 + 4);
        BlockPos max = new BlockPos(site.chunkX * 16 + 47, site.boundary + 3, site.chunkZ * 16 + 7);
        try {
            environment(AMBIENT, 0, 3, 200, 1);
            double resting = TemperatureSystem.restingKelvin(level);
            double vacuum = new ThermalEnvironment(AMBIENT, 0, 3).restingKelvin(0.9);
            double balanced = new ThermalEnvironment(AMBIENT, 10, 3).restingKelvin(0.9);
            double residual = 10 * (AMBIENT - balanced) + 0.9 * SIGMA * (Math.pow(3, 4) - Math.pow(balanced, 4));
            helper.assertTrue(resting == 3 && vacuum == 3, "a vacuum should rest at the sink temperature, got " + resting + " K");
            helper.assertTrue(new ThermalEnvironment(AMBIENT, 10, AMBIENT).restingKelvin(0.9) == AMBIENT && new ThermalEnvironment(AMBIENT, 10, 3).restingKelvin(0) == AMBIENT, "the resting temperature of a matching or non radiating surface should be ambient");
            helper.assertTrue(Math.abs(residual) < 1.0E-6 && balanced > 3 && balanced < AMBIENT, "convection with a cold sink should balance in between, got " + balanced + " K, residual " + residual);
            site.fill(min, max, Blocks.STONE);
            site.step(30);
            helper.assertTrue(site.modules().isEmpty(), "stone at the resting temperature got temperature modules");
            double untouched = kelvin(level, new BlockPos(site.chunkX * 16 + 20, site.boundary + 3, site.chunkZ * 16 + 5), 0);
            helper.assertTrue(untouched == 3, "untouched stone in space should sit at 3 K, got " + untouched);
            heat(level, new BlockPos(site.chunkX * 16 + 2, site.boundary + 3, site.chunkZ * 16 + 5), 400);
            int worstSections = 0;
            int worstActive = 0;
            for (int tick = 0; tick < 400; tick += 20) {
                site.step(20);
                int sections = 0;
                int active = 0;
                for (TemperatureModule module : site.modules()) {
                    sections += module.sections.size();
                    for (int section : module.sections.keySet()) {
                        long[] mask = module.activeMask(section);
                        for (int i = 0; mask != null && i < mask.length; i++) {
                            active += Long.bitCount(mask[i]);
                        }
                    }
                }
                worstSections = Math.max(worstSections, sections);
                worstActive = Math.max(worstActive, active);
            }
            boolean farModules = TemperatureSystem.module(level, site.chunkX + 1, site.chunkZ, false) != null || TemperatureSystem.module(level, site.chunkX + 2, site.chunkZ, false) != null;
            log("space_bounded", "resting={} balanced_with_convection={} worst_sections={} worst_active_cells={} far_modules={}", resting, balanced, worstSections, worstActive, farModules);
            helper.assertTrue(worstSections <= 1, "the sections didn't stay bounded, " + worstSections + " at once");
            helper.assertTrue(worstActive < 400, "too many cells stayed active, " + worstActive);
            helper.assertTrue(!farModules, "an untouched neighbour chunk got a temperature module");
            helper.succeed();
        } finally {
            site.finish(min, max);
        }
    }

    private static List<TemperatureModule> nearby(ServerLevel level, BlockPos pos) {
        List<TemperatureModule> modules = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                TemperatureModule module = TemperatureSystem.module(level, (pos.getX() >> 4) + x, (pos.getZ() >> 4) + z, false);
                if (module != null) {
                    modules.add(module);
                }
            }
        }
        return modules;
    }

    private static void scrub(ServerLevel level, BlockPos pos) {
        reset();
        TemperatureSystem.setRoomAirExclusion(true);
        for (TemperatureModule module : nearby(level, pos)) {
            for (int section : module.sections.keySet().toIntArray()) {
                module.removeSection(section);
            }
            TemperatureSystem.TEMPERATURE.detach(module.attachment());
        }
    }

    private static void sealedRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos max = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        try {
            helper.assertTrue(AtmosphereSystem.ATMOSPHERE.isEnabled() && TemperatureSystem.TEMPERATURE.isEnabled(), "the atmosphere and temperature systems should be on");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, pos.equals(center) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
            }
            AtmosphereNetwork room = AtmosphereSystem.ATMOSPHERE.get(level).getNetwork(new OctPos(center, 0));
            helper.assertTrue(room != null && !room.isWorldAtmosphere(), "the room didn't form an atmosphere network");
            double wallEmissivity = TemperatureSystem.material(level, new OctPos(min, 0)).emissivity();
            double hot = 500;
            double seconds = 5 * 0.05 * 100;
            environment(AMBIENT, 0, 3, 100, 1);
            double[] energies = new double[2];
            for (int run = 0; run < 2; run++) {
                TemperatureSystem.setRoomAirExclusion(run == 0);
                for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                    if (!pos.equals(center)) {
                        heat(level, pos, hot);
                    }
                }
                for (int tick = 0; tick < 5; tick++) {
                    TemperatureSystem.step(level, nearby(level, min));
                }
                energies[run] = energy(level, min, max);
            }
            double saved = energies[0] - energies[1];
            double expected = 24 * ThermalMaterials.CELL_AREA * wallEmissivity * SIGMA * (Math.pow(hot, 4) - Math.pow(3, 4)) * seconds;
            log("sealed_room", "inner_face_radiation_saved_j={} expected_j={} ratio={}", saved, expected, saved / expected);
            helper.assertTrue(Math.abs(saved / expected - 1) < 0.03, "the inner wall faces should not radiate: saved " + saved + " J, expected " + expected + " J");
            TemperatureSystem.setRoomAirExclusion(true);
            double wall = 300;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (!pos.equals(center)) {
                    heat(level, pos, wall);
                }
            }
            environment(AMBIENT, 0, 3, 1000, 1);
            room.getVolume().setTemperature(600);
            double airCapacity = room.getVolume().getStateTotal(Volume.State.GAS) * AtmosphereThermalSystem.GAS_HEAT_CAPACITY;
            double wallsBefore = energy(level, min, max);
            double gapBefore = 600 - wall;
            for (int i = 0; i < 10; i++) {
                AtmosphereThermalSystem.exchange(room);
            }
            double air = room.getVolume().getTemperature();
            double wallRise = kelvin(level, helper.absolutePos(new BlockPos(2, 2, 1)), 4) - wall;
            double airLost = airCapacity * (600 - air);
            double wallsGained = energy(level, min, max) - wallsBefore;
            log("sealed_room", "air_end={} inner_wall_rise={} air_lost_j={} walls_gained_j={}", air, wallRise, airLost, wallsGained);
            helper.assertTrue(air < 600 && Math.abs(air - wall) < 0.05 * gapBefore, "the cabin air should approach the walls, " + air + " K");
            helper.assertTrue(wallRise > 0.001, "the inner wall should warm, rise " + wallRise);
            helper.assertTrue(Math.abs(airLost - wallsGained) < 1.0E-9 * airLost, "heat wasn't conserved between air and walls: " + airLost + " J against " + wallsGained + " J");
            helper.succeed();
        } finally {
            scrub(level, min);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void woolCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos max = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos notch = helper.absolutePos(new BlockPos(3, 2, 3));
        try {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(helper.absolutePos(new BlockPos(3, 2, 2)), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(helper.absolutePos(new BlockPos(2, 2, 3)), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(notch, Blocks.WHITE_WOOL.defaultBlockState(), 2);
            AtmosphereNetwork room = AtmosphereSystem.ATMOSPHERE.get(level).getNetwork(new OctPos(center, 0));
            helper.assertTrue(room != null && !room.isWorldAtmosphere(), "the room didn't form an atmosphere network");
            environment(AMBIENT, 10, AMBIENT, 5000, 1);
            room.getVolume().updateVolume(2000);
            room.getVolume().fill(AtmosphereSystem.levelAtmosphere(level));
            room.getVolume().setTemperature(600);
            double gap = 600 - TemperatureSystem.restingKelvin(level);
            double start = TemperatureSystem.restingKelvin(level);
            AtmosphereThermalSystem.exchange(room);
            double woolCapacity = capacity(level, notch, 0);
            double corner = kelvin(level, notch, 0) - start;
            double flat = kelvin(level, notch, 4) - start;
            double flatExpected = AtmosphereThermalSystem.INDOOR_CONVECTION * ThermalMaterials.CELL_AREA * 5000 / woolCapacity * gap;
            log("wool_cap", "corner_rise={} half_gap={} flat_rise={} flat_expected={}", corner, gap / 2, flat, flatExpected);
            helper.assertTrue(Math.abs(corner - gap / 2) < 1.0E-6 * gap, "a wool cell with two room faces should take half its gap, rose " + corner + " K of " + gap + " K");
            helper.assertTrue(Math.abs(flat - flatExpected) < 1.0E-6 * gap, "a wool cell with one room face should rise " + flatExpected + " K, rose " + flat + " K");
            helper.succeed();
        } finally {
            scrub(level, min);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static double[] shiftedRun(Site site, int blockX, int blockY, int blockZ, double settle) {
        ServerLevel level = site.level;
        BlockPos min = new BlockPos(blockX, blockY, blockZ);
        BlockPos max = min.offset(5, 5, 5);
        try {
            environment(AMBIENT, 0, AMBIENT, 20000, 0);
            site.fill(min, max, Blocks.IRON_BLOCK);
            heat(level, min.offset(2, 2, 2), HOT);
            TemperatureSystem.setSettleThreshold(settle);
            site.step(10);
            double[] values = new double[12 * 12 * 12];
            int i = 0;
            for (int x = 0; x < 12; x++) {
                for (int y = 0; y < 12; y++) {
                    for (int z = 0; z < 12; z++) {
                        values[i++] = TemperatureSystem.kelvin(level, blockX * 2L + x, blockY * 2L + y, blockZ * 2L + z);
                    }
                }
            }
            return values;
        } finally {
            site.finish(min, max);
        }
    }

    private static void shiftedBorders(GameTestHelper helper) {
        Site site = new Site(helper, 100, 20, 2);
        int z = site.chunkZ * 16 + 5;
        double worst = 0;
        double exact = 0;
        double spread = 0;
        for (double settle : new double[]{TemperatureSystem.SETTLE, 0}) {
            double[] across = shiftedRun(site, (site.chunkX + 1) * 16 - 3, site.boundary - 3, z, settle);
            double[] inside = shiftedRun(site, site.chunkX * 16 + 5, site.boundary + 5, z, settle);
            double difference = 0;
            for (int i = 0; i < across.length; i++) {
                difference = Math.max(difference, Math.abs(across[i] - inside[i]) / inside[i]);
                spread = Math.max(spread, inside[i] - AMBIENT);
            }
            if (settle == 0) {
                exact = difference;
            } else {
                worst = difference;
            }
        }
        log("shifted_borders", "relative_difference_default_settle={} relative_difference_settle_0={} largest_rise={}", worst, exact, spread);
        helper.assertTrue(spread > 50, "the heat should have spread, largest rise " + spread);
        helper.assertTrue(worst < 1.0E-7, "the result changed with where the section and chunk borders fall, relative difference " + worst);
        helper.assertTrue(exact < 1.0E-12, "without settling the result changed with where the borders fall, relative difference " + exact);
        helper.succeed();
    }

    private static void persistence(GameTestHelper helper) {
        Site site = new Site(helper, 110, 20, 1);
        ServerLevel level = site.level;
        BlockPos block = new BlockPos(site.chunkX * 16 + 1, site.boundary + 1, site.chunkZ * 16 + 1);
        try {
            environment(AMBIENT, 10, AMBIENT, 1, 1);
            int section = level.getSectionIndex(block.getY());
            TemperatureModule module = TemperatureSystem.module(level, site.chunkX, site.chunkZ, true);
            double resting = module.restingKelvin();
            short[] old = new short[TemperatureModule.CELLS];
            old[100] = 500;
            old[101] = -200;
            module.sections.clear();
            module.legacy.put(section, old);
            ChunkAttachment attachment = module.attachment();
            byte[] oldSave = WorldModuleSystem.encodeAll(attachment, type -> type == TemperatureSystem.TEMPERATURE);
            TemperatureSystem.TEMPERATURE.detach(attachment);
            TemperatureModule converted = load(attachment, oldSave);
            double[] values = converted.sections.get(section);
            helper.assertTrue(values != null && converted.legacy.isEmpty(), "the old save wasn't converted");
            helper.assertTrue(Math.abs(values[100] - (resting + 50)) < 1.0E-9 && Math.abs(values[101] - (resting - 20)) < 1.0E-9 && values[102] == resting, "the old save converted to " + values[100] + ", " + values[101] + " and " + values[102] + " K from " + resting + " K");
            site.fill(block, block, Blocks.STONE);
            converted.sections.clear();
            int near = TemperatureModule.index(2, 2, 2);
            int far = TemperatureModule.index(3, 3, 3);
            int blockSection = section;
            converted.setKelvin(blockSection, near, resting + 0.00123);
            converted.setKelvin(blockSection, far, 612.34567891);
            double nearSaved = converted.kelvin(blockSection, near);
            double farSaved = converted.kelvin(blockSection, far);
            byte[] save = WorldModuleSystem.encodeAll(attachment, type -> type == TemperatureSystem.TEMPERATURE);
            TemperatureSystem.TEMPERATURE.detach(attachment);
            TemperatureModule reloaded = load(attachment, save);
            double nearLoaded = reloaded.kelvin(blockSection, near);
            double farLoaded = reloaded.kelvin(blockSection, far);
            log("persistence", "near_error={} far_error={} old_save_cell={} untouched={}", Math.abs(nearLoaded - nearSaved), Math.abs(farLoaded - farSaved), values[100] - resting, reloaded.kelvin(blockSection, 5) - resting);
            helper.assertTrue(Math.abs(nearLoaded - nearSaved) < 1.0E-9, "a temperature close to resting lost precision, " + nearSaved + " became " + nearLoaded);
            helper.assertTrue(Math.abs(farLoaded - farSaved) < 1.0E-4, "a hot temperature lost too much precision, " + farSaved + " became " + farLoaded);
            helper.assertTrue(reloaded.kelvin(blockSection, 5) == resting, "an untouched cell changed");
            helper.succeed();
        } finally {
            site.finish(block, block);
        }
    }

    private static TemperatureModule load(ChunkAttachment attachment, byte[] bytes) {
        EmpiricCore.modules().load(attachment, EmpiricBuffers.buffer(bytes), EmpiricCore.modules().specs().context(Encoding.KEYED));
        return TemperatureSystem.TEMPERATURE.get(attachment);
    }

    private static void syncRange(GameTestHelper helper) {
        Site site = new Site(helper, 120, 20, 1);
        ServerLevel level = site.level;
        BlockPos block = new BlockPos(site.chunkX * 16 + 1, site.boundary + 1, site.chunkZ * 16 + 1);
        try {
            environment(AMBIENT, 10, AMBIENT, 1, 1);
            site.fill(block, block, Blocks.STONE);
            int section = level.getSectionIndex(block.getY());
            TemperatureModule module = TemperatureSystem.module(level, site.chunkX, site.chunkZ, true);
            int hotCell = TemperatureModule.index(2, 2, 2);
            int coldCell = TemperatureModule.index(3, 2, 2);
            module.setKelvin(section, hotCell, 6000.04);
            module.setKelvin(section, coldCell, 1);
            module.apply(section, module.packet(section).data());
            double hot = module.kelvin(section, hotCell);
            double cold = module.kelvin(section, coldCell);
            log("sync_range", "hot={} cold={} short_delta={}", hot, cold, module.get(section, hotCell));
            helper.assertTrue(Math.abs(hot - 6000.04) < 0.06, "a cell 5700 K above resting arrived as " + hot + " K");
            helper.assertTrue(Math.abs(cold - 1) < 0.06, "a cell 292 K below resting arrived as " + cold + " K");
            helper.succeed();
        } finally {
            site.finish(block, block);
        }
    }

    private static void config(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation overworld = Level.OVERWORLD.location();
        ResourceLocation nether = Level.NETHER.location();
        try {
            TemperatureConfig.parse("{ // comment\n \"temperature\": { \"timeScale\": 250, \"dimensions\": { \"minecraft:the_nether\": 8 } } }");
            helper.assertTrue(TemperatureConfig.timeScale(overworld) == 250, "the default should be 250");
            helper.assertTrue(TemperatureConfig.timeScale(nether) == 8, "the nether should be 8");
            TemperatureConfig.parse("{ not json");
            helper.assertTrue(TemperatureConfig.timeScale(overworld) == TemperatureConfig.DEFAULT_TIME_SCALE, "malformed json should keep the default");
            helper.assertTrue(!TemperatureConfig.hasDimension(nether), "malformed json should add no dimension");
            TemperatureConfig.parse("{\"temperature\": {\"timeScale\": -3, \"dimensions\": {\"minecraft:the_nether\": -1, \"minecraft:overworld\": 5}}}");
            helper.assertTrue(TemperatureConfig.timeScale(nether) == TemperatureConfig.DEFAULT_TIME_SCALE, "a negative dimension entry should be skipped");
            helper.assertTrue(TemperatureConfig.timeScale(overworld) == 5, "the valid dimension entry should stay");
            TemperatureSystem.setTimeScale(null);
            helper.assertTrue(TemperatureSystem.timeScale(level) == 5, "the system should use the config");
            TemperatureSystem.setTimeScale(dimension -> 7);
            helper.assertTrue(TemperatureSystem.timeScale(level) == 7, "the override should win over the config");
            helper.succeed();
        } finally {
            TemperatureSystem.setTimeScale(null);
            TemperatureConfig.setDimension(overworld, null);
            TemperatureConfig.setDimension(nether, null);
            TemperatureConfig.setDefault(1);
        }
    }

    private static void zeroTimeScale(GameTestHelper helper) {
        Site site = new Site(helper, 130, 20, 1);
        ServerLevel level = site.level;
        BlockPos block = new BlockPos(site.chunkX * 16 + 1, site.boundary + 1, site.chunkZ * 16 + 1);
        try {
            for (double scale : new double[]{0, Double.NaN, -5}) {
                environment(AMBIENT, 10, AMBIENT, scale, 1);
                TemperatureModule module = TemperatureSystem.module(level, site.chunkX, site.chunkZ, true);
                helper.assertTrue(module != null && module.sections.isEmpty(), "a new module should hold no sections");
                TemperatureSystem.step(level, List.of(module));
                helper.assertTrue(TemperatureSystem.lastSubsteps() == 0, "a time scale of " + scale + " ran substeps");
                helper.assertTrue(TemperatureSystem.module(level, site.chunkX, site.chunkZ, false) == null, "an empty module stayed attached with a time scale of " + scale);
            }
            site.fill(block, block, Blocks.STONE);
            heat(level, block, 600);
            double before = kelvin(level, block, 0);
            environment(AMBIENT, 10, AMBIENT, 0, 1);
            site.step(5);
            helper.assertTrue(kelvin(level, block, 0) == before, "heat moved with a time scale of 0");
            helper.succeed();
        } finally {
            site.finish(block, block);
        }
    }

    private static final int PARALLEL_CHUNKS = 10;
    private static final int PARALLEL_TICKS = 100;

    private static void parallelBuild(Site site) {
        Block[] blocks = {Blocks.STONE, Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK};
        int z = site.chunkZ * 16 + 8;
        for (int chunk = 0; chunk < PARALLEL_CHUNKS; chunk++) {
            Block block = blocks[chunk % blocks.length];
            int x = (site.chunkX + chunk) * 16;
            site.fill(new BlockPos(x, site.boundary + 4, z), new BlockPos(x + 15, site.boundary + 4, z), block);
            site.fill(new BlockPos(x + 8, site.boundary - 2, z), new BlockPos(x + 8, site.boundary + 17, z), block);
        }
        for (int chunk = 0; chunk < PARALLEL_CHUNKS; chunk++) {
            int x = (site.chunkX + chunk) * 16 + 8;
            for (int y : new int[]{site.boundary - 1, site.boundary + 5, site.boundary + 17}) {
                heat(site.level, new BlockPos(x, y, z), 500 + 40 * (chunk % 5));
            }
        }
    }

    private static int activeSections(Site site) {
        int count = 0;
        for (TemperatureModule module : site.modules()) {
            for (int section : module.sections.keySet().toIntArray()) {
                long[] mask = module.activeMask(section);
                if (mask != null && !TemperatureModule.isZero(mask)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long parallelHash(Site site, double[] energy) {
        ThermalMaterials.Table table = ThermalMaterials.table();
        List<TemperatureModule> modules = site.modules();
        modules.sort(Comparator.comparingLong(module -> module.chunk().getPos().toLong()));
        long hash = 7;
        double total = 0;
        for (TemperatureModule module : modules) {
            int[] sections = module.sections.keySet().toIntArray();
            Arrays.sort(sections);
            for (int section : sections) {
                double[] values = module.sections.get(section);
                byte[] materials = module.materials(section);
                hash = hash * 1000003L + section;
                for (int i = 0; i < values.length; i++) {
                    hash = hash * 1000003L + Double.doubleToLongBits(values[i]);
                    total += values[i] * table.capacity[materials[i] & 0xFF];
                }
            }
        }
        energy[0] = total;
        return hash;
    }

    private static void parallelDeterminism(GameTestHelper helper) {
        int original = TemperatureSystem.threads();
        Site site = new Site(helper, 150, 40, PARALLEL_CHUNKS);
        BlockPos min = new BlockPos(site.chunkX * 16, site.boundary - 2, site.chunkZ * 16 + 8);
        BlockPos max = new BlockPos((site.chunkX + PARALLEL_CHUNKS) * 16 - 1, site.boundary + 17, site.chunkZ * 16 + 8);
        try {
            long[] hashes = new long[3];
            double[] energies = new double[3];
            int[] counts = {1, 2, 8};
            for (int run = 0; run < counts.length; run++) {
                site = new Site(helper, 150, 40, PARALLEL_CHUNKS);
                environment(AMBIENT, 10, AMBIENT, 50, 1);
                TemperatureSystem.setThreads(counts[run]);
                parallelBuild(site);
                if (run == 0) {
                    int modules = site.modules().size();
                    int sections = activeSections(site);
                    helper.assertTrue(modules >= 9 && sections >= 30, "the scenario is too small, " + modules + " modules and " + sections + " active sections");
                }
                double[] energy = new double[1];
                long before = parallelHash(site, energy);
                site.step(PARALLEL_TICKS);
                hashes[run] = parallelHash(site, energy);
                energies[run] = energy[0];
                helper.assertTrue(hashes[run] != before, "the scenario didn't change anything");
                log("parallel_determinism", "threads={} checksum={} energy={} substeps={}", counts[run], hashes[run], energies[run], TemperatureSystem.lastSubsteps());
                site.finish(min, max);
            }
            for (int run = 1; run < counts.length; run++) {
                helper.assertTrue(hashes[run] == hashes[0], "threads " + counts[run] + " gave checksum " + hashes[run] + " and not " + hashes[0]);
                helper.assertTrue(Double.doubleToLongBits(energies[run]) == Double.doubleToLongBits(energies[0]), "threads " + counts[run] + " gave energy " + energies[run] + " and not " + energies[0]);
            }
            helper.succeed();
        } finally {
            TemperatureSystem.setThreads(original);
            site.finish(min, max);
        }
    }

    private static int expectedSubsteps(double total, Block... blocks) {
        ThermalMaterials.Table table = ThermalMaterials.table();
        long[] set = new long[4];
        for (Block block : blocks) {
            int material = ThermalMaterials.indexOf(block.defaultBlockState());
            set[material >> 6] |= 1L << material;
        }
        return (int) Math.min(64, Math.max(1, Math.ceil(total / table.stableStep(set))));
    }

    private static int substepsRun(Site site, BlockPos stone, Block extra) {
        site.fill(stone, stone, Blocks.STONE);
        if (extra != null) {
            BlockPos far = stone.offset(6, 0, 0);
            site.fill(far, far, extra);
        }
        heat(site.level, stone, 600);
        site.step(1);
        return TemperatureSystem.lastSubsteps();
    }

    private static void substepsByMaterial(GameTestHelper helper) {
        Site site = new Site(helper, 170, 40, 1);
        BlockPos stone = new BlockPos(site.chunkX * 16 + 4, site.boundary + 4, site.chunkZ * 16 + 4);
        BlockPos far = stone.offset(6, 0, 0);
        try {
            // Copper conducts well enough to need several substeps at this scale, stone doesn't
            double scale = 20000;
            environment(AMBIENT, 10, AMBIENT, scale, 1);
            double total = 0.05 * scale;
            int only = substepsRun(site, stone, null);
            site.finish(stone, far);
            site.level.setChunkForced(site.chunkX, site.chunkZ, true);
            site.level.getChunk(site.chunkX, site.chunkZ);
            environment(AMBIENT, 10, AMBIENT, scale, 1);
            int mixed = substepsRun(site, stone, Blocks.COPPER_BLOCK);
            int expectedOnly = expectedSubsteps(total, Blocks.STONE);
            int expectedMixed = expectedSubsteps(total, Blocks.STONE, Blocks.COPPER_BLOCK);
            log("substeps_by_material", "stone_only={} expected={} with_copper={} expected={}", only, expectedOnly, mixed, expectedMixed);
            helper.assertTrue(only == expectedOnly, "stone alone used " + only + " substeps, expected " + expectedOnly);
            helper.assertTrue(mixed == expectedMixed, "stone and copper used " + mixed + " substeps, expected " + expectedMixed);
            helper.assertTrue(only < mixed, "an inactive copper block in the section didn't raise the substeps, " + only + " and " + mixed);
            helper.succeed();
        } finally {
            site.finish(stone, far);
        }
    }
}
