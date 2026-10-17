package xyz.volcanobay.empiric.api.temperature;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

@SelfSpec
public class TemperatureBlockData {

    @Data
    public double conductivity = 0.5;
}
