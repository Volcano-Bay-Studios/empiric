package xyz.volcanobay.empiric.api.temperature;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

@SelfSpec
public class TemperatureBlockData {

    /**
     * Thermal conductivity in W/(m K).
     */
    @Data
    public double conductivity = 1.0;
    /**
     * Density in kg/m3.
     */
    @Data
    public double density = 2000;
    /**
     * Specific heat in J/(kg K).
     */
    @Data
    public double specificHeat = 1000;
    /**
     * Emissivity from 0 to 1.
     */
    @Data
    public double emissivity = 0.9;
}
