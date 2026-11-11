package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

@SelfSpec
public class HeatReactionRequirement implements ReactionRequirement {

    @Data(optional = true)
    private Float min;
    @Data(optional = true)
    private Float max;

    public float getMinimumHeat() {
        return min == null ? Float.NEGATIVE_INFINITY : min;
    }

    public float getMaximumHeat() {
        return max == null ? Float.POSITIVE_INFINITY : max;
    }

    @Override
    public boolean met(Volume volume, double mixing) {
        double celsius = volume.getTemperature() - 273.15;
        return celsius >= getMinimumHeat() && celsius <= getMaximumHeat();
    }
}
