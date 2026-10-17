package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

@SelfSpec
public class ElectrolysisRequirement {

    @Data(id = "min_amps")
    private double minAmps;

    public double getMinAmps() {
        return this.minAmps;
    }
}
