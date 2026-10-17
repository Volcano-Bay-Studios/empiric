package xyz.volcanobay.empiric.api.atmosphere;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;

/**
 * The air of a dimension: what open air is made of, and the temperature everything in the dimension settles to.
 */
@SelfSpec
public class LevelAtmosphere {

    /**
     * Ambient temperature in degrees Celsius.
     */
    @Data
    public double temperature = 24.0;

    /**
     * Surface pressure in kilopascals.
     */
    @Data
    public double pressure = 101.325;

    /**
     * Mole fraction of each gas by chemical id. The fractions are normalised, so they only need to be in proportion.
     */
    @Data
    public HashMap<String, Double> composition = new HashMap<>();

    /**
     * The ambient temperature in tenths of a degree Celsius, the unit temperature cells use.
     */
    public int ambient() {
        return (int) Math.round(temperature * 10);
    }

    public double kelvin() {
        return temperature + 273.15;
    }
}
