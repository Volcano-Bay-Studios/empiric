package xyz.volcanobay.empiric.system;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
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
        Map<SpatialPos, double[]> cells = new HashMap<>();
        double conductance = 0;
        for (SpatialPos member : members) {
            for (int[] face : FACES) {
                SpatialPos pos = new SpatialPos(member.x() + face[0], member.y() + face[1], member.z() + face[2]);
                if (members.contains(pos)) {
                    continue;
                }
                int conductivity = TemperatureSystem.conductivity(level, pos.x(), pos.y(), pos.z());
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
            int cell = TemperatureSystem.get(level, pos.x(), pos.y(), pos.z());
            double flow = scale * entry.getValue()[0] * (before - (cell / 10.0 + 273.15));
            heat += flow;
            double wanted = flow / CELL_HEAT_CAPACITY * 10 + carry.getOrDefault(pos, 0.0);
            int applied = (int) wanted;
            if (applied != 0) {
                TemperatureSystem.set(level, pos.x(), pos.y(), pos.z(), cell + applied);
            }
            double left = wanted - applied;
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
}
