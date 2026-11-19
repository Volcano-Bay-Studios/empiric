package xyz.volcanobay.empiric.api.chemistry.sim;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.system.TemperatureSystem;

/**
 * Heat going into the temperature system's cells. Cells keep their temperature exactly, so nothing is left over to carry.
 */
public final class CellHeat {

    private CellHeat() {
    }

    /**
     * Adds heat to the cell at oct coordinates. The heat is added exactly, using the cell's own heat capacity.
     *
     * @param joules   Heat in J, negative to cool it
     * @param capacity Ignored, kept so existing callers still compile
     * @param carry    Ignored, kept so existing callers still compile
     * @return Always 0
     */
    public static double apply(Level level, long x, long y, long z, double joules, double capacity, double carry) {
        TemperatureSystem.addHeat(level, x, y, z, joules);
        return 0;
    }

    /**
     * Adds heat to a cell of a temperature section, which is quicker than the oct coordinate version when many cells share a section.
     *
     * @see #apply(Level, long, long, long, double, double, double)
     */
    public static double apply(TemperatureSection section, int index, double joules, double capacity, double carry) {
        section.addHeat(index, joules);
        return 0;
    }

    /**
     * @return The cell's temperature in kelvin
     */
    public static double kelvin(Level level, long x, long y, long z) {
        return TemperatureSystem.kelvin(level, x, y, z);
    }
}
