package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.api.atmosphere.Volume;

/**
 * One condition a volume has to meet for a reaction to run.
 */
public interface ReactionRequirement {

    /**
     * @param mixing How well the volume is being stirred, from 0 to 1
     */
    boolean met(Volume volume, double mixing);
}
