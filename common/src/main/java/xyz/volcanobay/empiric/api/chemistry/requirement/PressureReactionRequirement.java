package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

/**
 * Gas pressure in atmospheres the volume must be within.
 */
@SelfSpec
public class PressureReactionRequirement implements ReactionRequirement {

    @Data(optional = true)
    private Float min;
    @Data(optional = true)
    private Float max;

    public float getMinimumAtm() {
        return min == null ? Float.NEGATIVE_INFINITY : min;
    }

    public float getMaximumAtm() {
        return max == null ? Float.POSITIVE_INFINITY : max;
    }

    @Override
    public boolean met(Volume volume, double mixing) {
        double atm = volume.getGasPressure() / 101325;
        return atm >= getMinimumAtm() && atm <= getMaximumAtm();
    }
}
