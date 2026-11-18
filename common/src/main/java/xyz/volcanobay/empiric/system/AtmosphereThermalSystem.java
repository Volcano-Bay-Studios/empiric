package xyz.volcanobay.empiric.system;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.api.chemistry.sim.CellHeat;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
     * Heat capacity of an oct of solid in J/K, about an eighth of a cubic metre of stone.
     */
    public static final double CELL_HEAT_CAPACITY = 260_000;
    /**
     * Heat in J/K that a fully conductive face moves between a volume and a block per exchange, much faster than reality so it shows up in play.
     */
    public static final double CONDUCTANCE = 5000;
    /**
     * The most of a volume's temperature gap a single face may close, so the air can't overshoot the blocks.
     */
    private static final double MAX_FRACTION = 0.5;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private AtmosphereThermalSystem() {
    }

    public static boolean isActive() {
        return TemperatureSystem.TEMPERATURE.isEnabled() && AtmosphereSystem.ATMOSPHERE.isEnabled();
    }

    /**
     * Moves heat between a sealed volume and every conductive block touching it. Every face is worked out against the air's temperature at the start, so the result doesn't depend on the order of the faces.
     * The air takes the exact heat, while cells only take whole tenths of a degree, so each cell keeps the fraction it couldn't take yet and gets it on a later call instead of being rounded randomly.
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
        Set<SpatialPos> members = network.members();
        double before = volume.getTemperature();
        Views views = new Views(level);
        Map<SpatialPos, double[]> cells = new HashMap<>();
        double conductance = 0;
        for (SpatialPos member : members) {
            for (int[] face : FACES) {
                SpatialPos pos = new SpatialPos(member.x() + face[0], member.y() + face[1], member.z() + face[2]);
                if (members.contains(pos)) {
                    continue;
                }
                TemperatureSection section = views.at(pos.x(), pos.y(), pos.z());
                int conductivity = section != null ? section.conductivity(TemperatureSection.indexOf(pos.x(), pos.y(), pos.z())) : TemperatureSystem.conductivity(level, pos.x(), pos.y(), pos.z());
                if (conductivity <= 0) {
                    continue;
                }
                double share = conductivity / 100.0 * CONDUCTANCE;
                cells.computeIfAbsent(pos, key -> new double[1])[0] += share;
                conductance += share;
            }
        }
        if (conductance <= 0) {
            return false;
        }
        double scale = Math.min(1, capacity * MAX_FRACTION / conductance);
        Map<SpatialPos, Double> carry = network.getThermalCarry();
        double heat = 0;
        for (Map.Entry<SpatialPos, double[]> entry : cells.entrySet()) {
            SpatialPos pos = entry.getKey();
            TemperatureSection section = views.at(pos.x(), pos.y(), pos.z());
            int index = TemperatureSection.indexOf(pos.x(), pos.y(), pos.z());
            int cell = section != null ? section.temperature(index) : TemperatureSystem.get(level, pos.x(), pos.y(), pos.z());
            double flow = scale * entry.getValue()[0] * (before - (cell / 10.0 + 273.15));
            heat += flow;
            double left = section != null ? CellHeat.apply(section, index, flow, CELL_HEAT_CAPACITY, carry.getOrDefault(pos, 0.0)) : CellHeat.apply(level, pos.x(), pos.y(), pos.z(), flow, CELL_HEAT_CAPACITY, carry.getOrDefault(pos, 0.0));
            if (left != 0) {
                carry.put(pos, left);
            } else {
                carry.remove(pos);
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
