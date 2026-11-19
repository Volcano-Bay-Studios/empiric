package xyz.volcanobay.empiric.api.temperature;

import xyz.volcanobay.empiric.system.TemperatureSystem;

/**
 * Direct access to the cells of one 32x32x32 oct section of a chunk's temperatures, for code that reads or writes many
 * cells of the same section. Get one from {@link TemperatureSystem#section}, and use {@link #indexOf} to find a cell.
 * Reads and writes behave like the matching {@link TemperatureSystem} calls, without the lookups for each cell.
 * <p>
 * A section view is valid for the current tick only. Don't keep it, because the section can be removed or its chunk can
 * unload afterwards. Values are absolute temperatures in tenths of a degree Celsius unless a method says delta or kelvin.
 */
public final class TemperatureSection {

    private final TemperatureModule module;
    private final int sectionIndex;
    private final double restingKelvin;
    private double[] values;
    private byte[] materials;

    public TemperatureSection(TemperatureModule module, int sectionIndex) {
        this.module = module;
        this.sectionIndex = sectionIndex;
        this.restingKelvin = module.restingKelvin();
        this.values = module.values(sectionIndex, false);
    }

    public static int index(int x, int y, int z) {
        return TemperatureModule.index(x, y, z);
    }

    /**
     * The cell index for oct coordinates, which can be anywhere. Only the position inside the section counts, so check
     * that the position belongs to this section yourself.
     */
    public static int indexOf(long x, long y, long z) {
        return TemperatureModule.index((int) Math.floorMod(x, 32L), (int) Math.floorMod(y, 32L), (int) Math.floorMod(z, 32L));
    }

    public TemperatureModule module() {
        return module;
    }

    public int sectionIndex() {
        return sectionIndex;
    }

    /**
     * The cell's temperature in K, which is the level's resting temperature if the section holds no heat yet.
     */
    public double kelvin(int index) {
        if (values == null) {
            values = module.values(sectionIndex, false);
            if (values == null) {
                return restingKelvin;
            }
        }
        return values[index];
    }

    /**
     * The cell's difference from the level's resting temperature in tenths of a degree, 0 if the section holds no heat yet.
     */
    public short get(int index) {
        return TemperatureModule.delta(kelvin(index), restingKelvin);
    }

    public short temperature(int index) {
        return (short) TemperatureModule.tenths(kelvin(index));
    }

    public void set(int index, int temperature) {
        setKelvin(index, temperature / 10.0 + TemperatureModule.ZERO_CELSIUS);
    }

    /**
     * Sets the difference from the resting temperature in tenths of a degree.
     */
    public void setDelta(int index, int delta) {
        setKelvin(index, restingKelvin + delta / 10.0);
    }

    public void add(int index, int delta) {
        setKelvin(index, kelvin(index) + delta / 10.0);
    }

    /**
     * Sets the temperature of a cell in K, clamped between absolute zero and {@link TemperatureModule#MAX_KELVIN}.
     * The section is marked changed and the cell and its neighbours are woken, as in {@link TemperatureModule#setKelvin}.
     */
    public void setKelvin(int index, double kelvin) {
        module.setKelvin(sectionIndex, index, kelvin);
        values = module.values(sectionIndex, false);
    }

    /**
     * Adds heat to a cell in J. A cell that holds no heat, like air, ignores it.
     */
    public void addHeat(int index, double joules) {
        module.addHeat(sectionIndex, index, joules);
        values = module.values(sectionIndex, false);
    }

    /**
     * The cell's material, or null if it holds no heat.
     */
    public ThermalMaterial material(int index) {
        return ThermalMaterials.table().materials[materialIndex(index)];
    }

    /**
     * The cell's heat capacity in J/K, 0 for a cell that holds no heat.
     */
    public double heatCapacity(int index) {
        return ThermalMaterials.table().capacity[materialIndex(index)];
    }

    private int materialIndex(int index) {
        if (materials == null) {
            materials = module.materials(sectionIndex);
        }
        return materials[index] & 0xFF;
    }
}
