package xyz.volcanobay.empiric.system;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;

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

    private static final RandomSource RANDOM = RandomSource.create();
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private AtmosphereThermalSystem() {
    }

    public static boolean isActive() {
        return TemperatureSystem.TEMPERATURE.isEnabled() && AtmosphereSystem.ATMOSPHERE.isEnabled();
    }

    /**
     * Moves heat between a sealed volume and every conductive block touching it.
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
        double temperature = before;
        for (SpatialPos member : members) {
            for (int[] face : FACES) {
                long x = member.x() + face[0];
                long y = member.y() + face[1];
                long z = member.z() + face[2];
                if (members.contains(new SpatialPos(x, y, z))) {
                    continue;
                }
                int conductivity = TemperatureSystem.conductivity(level, x, y, z);
                if (conductivity <= 0) {
                    continue;
                }
                int cell = TemperatureSystem.get(level, x, y, z);
                double gap = temperature - (cell / 10.0 + 273.15);
                double flow = Math.min(conductivity / 100.0 * CONDUCTANCE, capacity * MAX_FRACTION);
                double tenths = flow * gap / CELL_HEAT_CAPACITY * 10;
                int applied = (int) Math.floor(tenths);
                if (RANDOM.nextDouble() < tenths - applied) {
                    applied++;
                }
                if (applied != 0) {
                    TemperatureSystem.set(level, x, y, z, cell + applied);
                    temperature = Math.max(0.01, temperature - applied / 10.0 * CELL_HEAT_CAPACITY / capacity);
                }
            }
        }
        if (temperature != before) {
            volume.setTemperature(temperature);
            return true;
        }
        return false;
    }
}
