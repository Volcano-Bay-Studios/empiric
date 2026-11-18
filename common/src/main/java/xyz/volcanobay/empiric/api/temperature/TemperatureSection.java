package xyz.volcanobay.empiric.api.temperature;

import net.minecraft.util.Mth;
import xyz.volcanobay.empiric.system.TemperatureSystem;

/**
 * Direct access to the cells of one 32x32x32 oct section of a chunk's temperatures, for code that reads or writes many
 * cells of the same section. Get one from {@link TemperatureSystem#section}, and use {@link #indexOf} to find a cell.
 * Reads and writes behave like the matching {@link TemperatureSystem} calls, without the lookups for each cell.
 * <p>
 * A section view is valid for the current tick only. Don't keep it, because the section can be removed or its chunk can
 * unload afterwards. Values are absolute temperatures in tenths of a degree Celsius unless a method says delta.
 */
public final class TemperatureSection {

    private final TemperatureModule module;
    private final int sectionIndex;
    private final int ambient;
    private final int low;
    private final int high;
    private short[] values;
    private byte[] conductivity;
    private long[] active;

    public TemperatureSection(TemperatureModule module, int sectionIndex) {
        this.module = module;
        this.sectionIndex = sectionIndex;
        this.ambient = module.ambient();
        this.low = Math.max(TemperatureModule.MIN - ambient, Short.MIN_VALUE);
        this.high = Math.min(TemperatureModule.MAX - ambient, Short.MAX_VALUE);
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
     * The cell's difference from the dimension's ambient temperature, 0 if the section holds no heat yet.
     */
    public short get(int index) {
        if (values == null) {
            values = module.values(sectionIndex, false);
            if (values == null) {
                return 0;
            }
        }
        return values[index];
    }

    public short temperature(int index) {
        return (short) (ambient + get(index));
    }

    public void set(int index, int temperature) {
        setDelta(index, temperature - ambient);
    }

    /**
     * Sets the difference from ambient, clamped so the temperature stays between absolute zero and {@link TemperatureModule#MAX}.
     * The section is marked changed and the cell active, as in {@link TemperatureModule#set}.
     */
    public void setDelta(int index, int delta) {
        short clamped = (short) Mth.clamp(delta, low, high);
        if (values == null) {
            if (clamped == 0) {
                return;
            }
            values = module.values(sectionIndex, true);
        }
        if (values[index] != clamped) {
            values[index] = clamped;
            module.touch(sectionIndex);
            if (clamped != 0) {
                if (active == null) {
                    active = module.active(sectionIndex);
                }
                active[index >> 6] |= 1L << index;
            }
        }
    }

    public void add(int index, int delta) {
        setDelta(index, get(index) + delta);
    }

    /**
     * The cell's conductivity from 0 to 100, or -1 for a cell that doesn't conduct and holds no heat.
     */
    public byte conductivity(int index) {
        if (conductivity == null) {
            conductivity = module.conductivity(sectionIndex);
        }
        return conductivity[index];
    }
}
