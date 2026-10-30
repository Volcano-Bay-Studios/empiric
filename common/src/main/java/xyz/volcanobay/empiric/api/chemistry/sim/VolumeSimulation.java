package xyz.volcanobay.empiric.api.chemistry.sim;

import xyz.volcanobay.empiric.api.atmosphere.Volume;

/**
 * What happens inside a volume by itself over a step: solids dissolve, reactions run, then states change.
 * Heat and gas coming from outside are up to the caller, through {@link VolumeExchange}.
 */
public final class VolumeSimulation {

    private VolumeSimulation() {
    }

    /**
     * @param mixing How well the volume is being stirred, from 0 to 1
     * @param dt     Step in seconds
     */
    public static Result step(Volume volume, double mixing, double dt) {
        if (volume.isReservoir() || dt <= 0) {
            return Result.NONE;
        }
        Solutions.step(volume, mixing, dt);
        Reactions.step(volume, mixing, dt);
        boolean boiling = PhaseChange.step(volume, dt);
        return boiling ? Result.BOILING : Result.NONE;
    }

    public record Result(boolean boiling) {
        public static final Result NONE = new Result(false);
        public static final Result BOILING = new Result(true);
    }
}
