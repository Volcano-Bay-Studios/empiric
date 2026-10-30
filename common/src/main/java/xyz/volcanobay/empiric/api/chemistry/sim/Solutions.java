package xyz.volcanobay.empiric.api.chemistry.sim;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;

import java.util.ArrayList;

/**
 * Solids dissolving into the surrounding liquids, and coming back out when there is too much.
 */
public final class Solutions {

    /**
     * Rate in 1/s at which a solid dissolves towards saturation when nothing stirs it.
     */
    public static final double K_DISS = 0.01;
    /**
     * How many times faster a fully stirred solid dissolves, on top of the unstirred rate.
     */
    public static final double MIXING_BOOST = 4;
    /**
     * Rate in 1/s at which what is over saturation comes back out as a solid.
     */
    public static final double K_PRECIPITATE = 0.05;
    private static final double EPSILON = 1.0E-12;

    private Solutions() {
    }

    /**
     * Runs a step of dissolving and precipitating.
     *
     * @param mixing How well the volume is being stirred, from 0 to 1
     * @param dt     Step in seconds
     */
    public static void step(Volume volume, double mixing, double dt) {
        if (volume.isReservoir()) {
            return;
        }
        double solvent = solventLiters(volume);
        double celsius = volume.getTemperature() - 273.15;
        for (ChemicalComponent component : new ArrayList<>(volume.getChemicalComponentsList().values())) {
            Chemical chemical = component.chemical;
            if (chemical.getSolubility() <= 0 || celsius >= chemical.getFreezeTemp()) {
                continue;
            }
            double dissolved = component.getAmount(Volume.State.LIQUID);
            double solid = component.getAmount(Volume.State.SOLID);
            double saturated = chemical.getSolubility() * solvent;
            if (dissolved < saturated && solid > EPSILON) {
                double rate = K_DISS * (1 + MIXING_BOOST * Math.clamp(mixing, 0, 1));
                double moles = Math.min(solid, (saturated - dissolved) * VolumeExchange.share(rate, dt));
                PhaseChange.move(volume, chemical, moles, Volume.State.SOLID, Volume.State.LIQUID, 0);
            } else if (dissolved > saturated) {
                double moles = (dissolved - saturated) * VolumeExchange.share(K_PRECIPITATE, dt);
                PhaseChange.move(volume, chemical, moles, Volume.State.LIQUID, Volume.State.SOLID, 0);
            }
        }
    }

    /**
     * Whether liquid moles of a chemical in the volume are dissolved rather than a liquid of their own.
     */
    public static boolean isDissolved(Volume volume, Chemical chemical) {
        return chemical.getSolubility() > 0 && volume.getTemperature() - 273.15 < chemical.getFreezeTemp();
    }

    /**
     * The liters of liquid in the volume that things can dissolve into: everything liquid that isn't itself dissolved.
     */
    public static double solventLiters(Volume volume) {
        double milliliters = 0;
        for (ChemicalComponent component : volume.getChemicalComponentsList().values()) {
            Chemical chemical = component.chemical;
            if (isDissolved(volume, chemical)) {
                continue;
            }
            double density = chemical.getDensityLiquid() > 0 ? chemical.getDensityLiquid() : 1;
            milliliters += chemical.getMass(component.getAmount(Volume.State.LIQUID)) / density;
        }
        return milliliters / 1000;
    }
}
