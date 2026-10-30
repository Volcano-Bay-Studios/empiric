package xyz.volcanobay.empiric.api.chemistry.sim;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.system.TemperatureSystem;

/**
 * Heat going into the temperature system's cells. Cells only hold whole tenths of a degree, so the caller keeps the
 * fraction a cell couldn't take yet and hands it back next time, instead of it being rounded away.
 */
public final class CellHeat {

    private CellHeat() {
    }

    /**
     * Adds heat to the cell at oct coordinates.
     *
     * @param joules   Heat in J, negative to cool it
     * @param capacity The cell's heat capacity in J/K
     * @param carry    Tenths of a degree left over from the last call
     * @return Tenths of a degree left over this time, to pass in next time
     */
    public static double apply(Level level, long x, long y, long z, double joules, double capacity, double carry) {
        double wanted = joules / capacity * 10 + carry;
        int applied = (int) wanted;
        if (applied != 0) {
            TemperatureSystem.set(level, x, y, z, TemperatureSystem.get(level, x, y, z) + applied);
        }
        return wanted - applied;
    }

    /**
     * @return The cell's temperature in kelvin
     */
    public static double kelvin(Level level, long x, long y, long z) {
        return TemperatureSystem.get(level, x, y, z) / 10.0 + 273.15;
    }
}
