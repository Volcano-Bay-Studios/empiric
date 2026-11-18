package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

final class TemperatureGameTests {

    private static final int HEAT = 800;
    private static final int SPAN = 3;

    private TemperatureGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(test.apply("temperature_corner_spread", TemperatureGameTests::cornerSpread),
                test.apply("temperature_into_moduleless_chunk", TemperatureGameTests::intoModulelessChunk),
                test.apply("temperature_border_invalidation", TemperatureGameTests::borderInvalidation),
                test.apply("temperature_section_view", TemperatureGameTests::sectionView));
    }

    private static void cornerSpread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() >> 4) + 4;
        int chunkZ = (origin.getZ() >> 4) + 4;
        int cornerX = (chunkX + 1) * 16;
        int cornerZ = (chunkZ + 1) * 16;
        int boundary = ((origin.getY() >> 4) + 2) << 4;
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                level.setChunkForced(chunkX + x, chunkZ + z, true);
                level.getChunk(chunkX + x, chunkZ + z);
            }
        }
        BlockPos min = new BlockPos(cornerX - SPAN, boundary - SPAN, cornerZ - SPAN);
        BlockPos max = new BlockPos(cornerX + SPAN - 1, boundary + SPAN - 1, cornerZ + SPAN - 1);
        ToDoubleFunction<Level> cooling = TemperatureSystem.getCooling();
        try {
            TemperatureSystem.setCooling(dimension -> 0);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            int ambient = TemperatureSystem.ambient(level);
            for (BlockPos pos : BlockPos.betweenClosed(cornerX - 1, boundary - 1, cornerZ - 1, cornerX, boundary, cornerZ)) {
                TemperatureSystem.set(level, pos, ambient + HEAT);
            }
            for (int tick = 0; tick < 40; tick++) {
                for (int x = 0; x < 2; x++) {
                    for (int z = 0; z < 2; z++) {
                        TemperatureModule module = TemperatureSystem.module(level, chunkX + x, chunkZ + z, false);
                        if (module != null) {
                            TemperatureSystem.propagate(module);
                        }
                    }
                }
            }
            long total = 0;
            long[] quadrants = new long[8];
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                long heat = 0;
                for (int octant = 0; octant < 8; octant++) {
                    heat += TemperatureSystem.get(level, new OctPos(pos, octant)) - ambient;
                }
                total += heat;
                int quadrant = (pos.getX() >= cornerX ? 1 : 0) | (pos.getZ() >= cornerZ ? 2 : 0) | (pos.getY() >= boundary ? 4 : 0);
                quadrants[quadrant] += heat;
            }
            for (int quadrant = 0; quadrant < 8; quadrant++) {
                helper.assertTrue(quadrants[quadrant] > 0, "heat didn't reach the corner quadrant " + quadrant);
            }
            helper.assertTrue(total == 8L * 8 * HEAT, "heat wasn't conserved with cooling off, " + total + " instead of " + 8L * 8 * HEAT);
            helper.succeed();
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                TemperatureSystem.set(level, pos, TemperatureSystem.ambient(level));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            TemperatureSystem.setCooling(cooling);
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    level.setChunkForced(chunkX + x, chunkZ + z, false);
                }
            }
        }
    }

    private static void intoModulelessChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() >> 4) + 8;
        int chunkZ = (origin.getZ() >> 4) + 8;
        int y = (((origin.getY() >> 4) + 2) << 4) + 5;
        BlockPos hot = new BlockPos(chunkX * 16 + 15, y, chunkZ * 16 + 5);
        BlockPos next = hot.east();
        load(level, chunkX, chunkZ);
        ToDoubleFunction<Level> cooling = TemperatureSystem.getCooling();
        try {
            TemperatureSystem.setCooling(dimension -> 0);
            helper.assertTrue(TemperatureSystem.module(level, chunkX + 1, chunkZ, false) == null, "the neighbouring chunk already had a temperature module");
            level.setBlock(hot, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(next, Blocks.STONE.defaultBlockState(), 2);
            int ambient = TemperatureSystem.ambient(level);
            TemperatureSystem.set(level, hot, ambient + HEAT);
            propagate(level, chunkX, chunkZ, 2, 30);
            helper.assertTrue(TemperatureSystem.module(level, chunkX + 1, chunkZ, false) != null, "heat reaching a chunk without a module didn't attach one");
            helper.assertTrue(TemperatureSystem.get(level, next) > ambient, "heat didn't flow into the chunk that had no module");
            helper.succeed();
        } finally {
            restore(level, chunkX, chunkZ, 2, hot, next, cooling);
        }
    }

    private static void borderInvalidation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() >> 4) + 8;
        int chunkZ = (origin.getZ() >> 4) + 12;
        int y = (((origin.getY() >> 4) + 2) << 4) + 5;
        BlockPos hot = new BlockPos(chunkX * 16 + 15, y, chunkZ * 16 + 5);
        BlockPos next = hot.east();
        load(level, chunkX, chunkZ);
        ToDoubleFunction<Level> cooling = TemperatureSystem.getCooling();
        try {
            TemperatureSystem.setCooling(dimension -> 0);
            level.setBlock(hot, Blocks.STONE.defaultBlockState(), 2);
            int ambient = TemperatureSystem.ambient(level);
            TemperatureSystem.set(level, hot, ambient + HEAT);
            propagate(level, chunkX, chunkZ, 2, 20);
            helper.assertTrue(TemperatureSystem.module(level, chunkX + 1, chunkZ, false) == null, "heat crossed into air at the chunk border");
            level.setBlock(next, Blocks.STONE.defaultBlockState(), 2);
            propagate(level, chunkX, chunkZ, 2, 30);
            helper.assertTrue(TemperatureSystem.get(level, next) > ambient, "heat didn't cross after the border block became stone");
            level.setBlock(next, Blocks.AIR.defaultBlockState(), 2);
            propagate(level, chunkX, chunkZ, 2, 30);
            helper.assertTrue(TemperatureSystem.get(level, next) == ambient, "heat stayed in or entered the border block after it became air");
            helper.succeed();
        } finally {
            restore(level, chunkX, chunkZ, 2, hot, next, cooling);
        }
    }

    private static void load(ServerLevel level, int chunkX, int chunkZ) {
        for (int x = 0; x < 2; x++) {
            level.setChunkForced(chunkX + x, chunkZ, true);
            level.getChunk(chunkX + x, chunkZ);
        }
    }

    private static void propagate(ServerLevel level, int chunkX, int chunkZ, int chunks, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            for (int x = 0; x < chunks; x++) {
                TemperatureModule module = TemperatureSystem.module(level, chunkX + x, chunkZ, false);
                if (module != null) {
                    TemperatureSystem.propagate(module);
                }
            }
        }
    }

    private static void restore(ServerLevel level, int chunkX, int chunkZ, int chunks, BlockPos hot, BlockPos next, ToDoubleFunction<Level> cooling) {
        int ambient = TemperatureSystem.ambient(level);
        TemperatureSystem.set(level, hot, ambient);
        TemperatureSystem.set(level, next, ambient);
        level.setBlock(hot, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(next, Blocks.AIR.defaultBlockState(), 2);
        TemperatureSystem.setCooling(cooling);
        for (int x = 0; x < chunks; x++) {
            level.setChunkForced(chunkX + x, chunkZ, false);
        }
    }

    private static void sectionView(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = (origin.getX() >> 4) + 8;
        int chunkZ = (origin.getZ() >> 4) + 16;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunk(chunkX, chunkZ);
        int sectionIndex = level.getSectionIndex(origin.getY());
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
            section.set(index, ambient + 100);
            helper.assertTrue(section.get(index) == 100 && section.temperature(index) == ambient + 100, "set didn't stick");
            helper.assertTrue(section.module().isChanged(sectionIndex), "writing didn't mark the section changed");
            helper.assertTrue((section.module().active(sectionIndex)[index >> 6] & (1L << index)) != 0, "writing didn't mark the cell active");
            section.add(index, 50);
            helper.assertTrue(section.get(index) == 150, "add didn't add");
            section.set(index, 100_000);
            helper.assertTrue(section.temperature(index) == TemperatureModule.MAX, "a hot write wasn't clamped to the maximum");
            section.set(index, -5000);
            helper.assertTrue(section.temperature(index) == TemperatureModule.MIN, "a cold write wasn't clamped to absolute zero");
            section.setDelta(index, 7);
            helper.assertTrue(TemperatureSystem.get(level, chunkX * 32L + 3, (long) (level.getMinSection() + sectionIndex) * 32 + 4, chunkZ * 32L + 5) == ambient + 7, "the view and TemperatureSystem disagree");
            helper.assertTrue(section.conductivity(index) == TemperatureSystem.conductivity(level, chunkX * 32L + 3, (long) (level.getMinSection() + sectionIndex) * 32 + 4, chunkZ * 32L + 5), "the view and TemperatureSystem disagree on conductivity");
            helper.succeed();
        } finally {
            TemperatureModule module = TemperatureSystem.module(level, chunkX, chunkZ, false);
            if (module != null) {
                module.removeSection(sectionIndex);
                TemperatureSystem.TEMPERATURE.detach(module.attachment());
            }
            level.setChunkForced(chunkX, chunkZ, false);
        }
    }
}
