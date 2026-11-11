package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

/**
 * How well the volume has to be stirred, from 0 to 1.
 */
@SelfSpec
public class MixingReactionRequirement implements ReactionRequirement {

    @Data
    private float min;

    public float getMinimumMixing() {
        return min;
    }

    @Override
    public boolean met(Volume volume, double mixing) {
        return mixing >= min;
    }
}
