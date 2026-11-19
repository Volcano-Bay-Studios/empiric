package xyz.volcanobay.empiric.system;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.api.chemistry.sim.CellHeat;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.api.temperature.ThermalMaterials;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;

import java.util.*;

/**
 * Heat moving between the air of a sealed room and the blocks around it. Only runs while both the temperature and atmosphere systems are subscribed.
 * Blocks hand heat to the air and the air hands it to the other walls, and from there the temperature system carries it through the blocks and out into the world.
 */
public final class AtmosphereThermalSystem {

    /**
     * Heat capacity of one mole of gas in J/(mol K).
     */
    public static final double GAS_HEAT_CAPACITY = 29;
    /**
     * The most of a volume's temperature gap a single face may close, so the air can't overshoot the blocks.
     */
    private static final double MAX_FRACTION = 0.5;
    /**
     * The convection coefficient between a room's air and the walls in W/(m2 K), the same in every level. Open air faces use the level's environment instead.
     */
    public static final double INDOOR_CONVECTION = 5;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private AtmosphereThermalSystem() {
    }

    /**
     * Tells the temperature solver that the members of an atmosphere network changed, so it looks up which faces touch room air again.
     */
    public static void roomsChanged() {
        RoomAir.changed();
    }

    public static boolean isActive() {
        return TemperatureSystem.TEMPERATURE.isEnabled() && AtmosphereSystem.ATMOSPHERE.isEnabled();
    }

    /**
     * Moves heat between a sealed volume and every block touching it that holds heat, as convection through each touching face using {@link #INDOOR_CONVECTION}.
     * One call covers a second of real time (20 ticks) times the level's time scale. Every face is worked out against the air's temperature at the start, so the result doesn't depend on the order of the faces.
     * A cell takes at most half of its own gap per call however many faces touch the room, so light materials can't overshoot at high time scales.
     * Cells take the exact heat, with no rounding. The temperature solver leaves these faces out of its open air surface loss, so they aren't counted twice.
     *
     * @return whether the volume's temperature changed
     */
    public static boolean exchange(AtmosphereNetwork network) {
        if (!isActive() || network.isWorldAtmosphere()) {
            return false;
        }
        AtmosphereVolume volume = network.getVolume();
        double capacity = volume.getStateTotal(Volume.State.GAS) * GAS_HEAT_CAPACITY;
        if (capacity <= 0 || volume.getTemperature() <= 0) {
            return false;
        }
        Level level = network.level();
        List<SpatialPos> members = new ArrayList<>(network.members());
        members.sort(Comparator.comparingLong(SpatialPos::x).thenComparingLong(SpatialPos::y).thenComparingLong(SpatialPos::z));
        Set<SpatialPos> room = new HashSet<>(members);
        double before = volume.getTemperature();
        Views views = new Views(level);
        Map<SpatialPos, double[]> cells = new LinkedHashMap<>();
        double face = INDOOR_CONVECTION * ThermalMaterials.CELL_AREA;
        double seconds = 20 * 0.05 * TemperatureSystem.timeScale(level);
        for (SpatialPos member : members) {
            for (int[] offset : FACES) {
                SpatialPos pos = new SpatialPos(member.x() + offset[0], member.y() + offset[1], member.z() + offset[2]);
                if (room.contains(pos)) {
                    continue;
                }
                TemperatureSection section = views.at(pos.x(), pos.y(), pos.z());
                int index = TemperatureSection.indexOf(pos.x(), pos.y(), pos.z());
                double cell = section != null ? section.heatCapacity(index) : TemperatureSystem.heatCapacity(level, pos.x(), pos.y(), pos.z());
                if (cell <= 0) {
                    continue;
                }
                cells.computeIfAbsent(pos, key -> new double[]{0, cell})[0] += face;
            }
        }
        if (cells.isEmpty() || seconds <= 0) {
            return false;
        }
        double conductance = 0;
        for (double[] entry : cells.values()) {
            entry[0] = Math.min(entry[0], MAX_FRACTION * entry[1] / seconds);
            conductance += entry[0];
        }
        double scale = Math.min(1, capacity * MAX_FRACTION / (conductance * seconds));
        double heat = 0;
        for (Map.Entry<SpatialPos, double[]> entry : cells.entrySet()) {
            SpatialPos pos = entry.getKey();
            TemperatureSection section = views.at(pos.x(), pos.y(), pos.z());
            int index = TemperatureSection.indexOf(pos.x(), pos.y(), pos.z());
            double cell = section != null ? section.kelvin(index) : TemperatureSystem.kelvin(level, pos.x(), pos.y(), pos.z());
            double flow = scale * entry.getValue()[0] * seconds * (before - cell);
            heat += flow;
            if (section != null) {
                CellHeat.apply(section, index, flow, 0, 0);
            } else {
                CellHeat.apply(level, pos.x(), pos.y(), pos.z(), flow, 0, 0);
            }
        }
        double temperature = Math.max(0.01, before - heat / capacity);
        if (temperature != before) {
            volume.setTemperature(temperature);
            return true;
        }
        return false;
    }

    private static final class Views {

        private final Level level;
        private final Long2ObjectOpenHashMap<TemperatureSection> sections = new Long2ObjectOpenHashMap<>();

        Views(Level level) {
            this.level = level;
        }

        TemperatureSection at(long x, long y, long z) {
            int blockY = (int) Math.floorDiv(y, 2L);
            if (blockY < level.getMinBuildHeight() || blockY >= level.getMaxBuildHeight()) {
                return null;
            }
            int chunkX = (int) Math.floorDiv(x, 32L);
            int chunkZ = (int) Math.floorDiv(z, 32L);
            int sectionIndex = level.getSectionIndex(blockY);
            long key = ((long) chunkX & 0x3FFFFFFL) << 38 | ((long) chunkZ & 0x3FFFFFFL) << 12 | sectionIndex;
            TemperatureSection section = sections.get(key);
            if (section == null) {
                section = TemperatureSystem.section(level, chunkX, sectionIndex, chunkZ, false);
                if (section != null) {
                    sections.put(key, section);
                }
            }
            return section;
        }
    }
}
