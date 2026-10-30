package xyz.volcanobay.empiric.api.chemistry.sim;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.chemistry.requirement.CatalystReactionRequirement;
import xyz.volcanobay.empiric.api.chemistry.requirement.HeatReactionRequirement;
import xyz.volcanobay.empiric.api.chemistry.requirement.PressureReactionRequirement;

import java.util.Map;

/**
 * Data-driven reactions ({@link ChemicalRegistry#REACTIONS}) inside a volume.
 */
public final class Reactions {

    /**
     * How much faster a fully stirred volume reacts.
     */
    public static final double MIXING_BOOST = 2;
    private static final double EPSILON = 1.0E-12;

    private Reactions() {
    }

    /**
     * @param mixing How well the volume is being stirred, from 0 to 1
     * @param dt     Step in seconds
     * @return Whether any reaction ran
     */
    public static boolean step(Volume volume, double mixing, double dt) {
        if (volume.isReservoir() || volume.getTemperature() <= 0 || dt <= 0) {
            return false;
        }
        boolean ran = false;
        for (ChemicalReaction reaction : ChemicalRegistry.getAllReactions()) {
            if (reaction.getRequirements().getElectrolysis() == null) {
                ran |= run(volume, reaction, mixing, dt);
            }
        }
        return ran;
    }

    /**
     * @return Whether the volume meets the reaction's heat, pressure and catalyst requirements
     */
    public static boolean canRun(Volume volume, ChemicalReaction reaction) {
        HeatReactionRequirement heat = reaction.getRequirements().getHeat();
        double celsius = volume.getTemperature() - 273.15;
        if (heat != null && (celsius < heat.getMinimumHeat() || celsius > heat.getMaximumHeat())) {
            return false;
        }
        PressureReactionRequirement pressure = reaction.getRequirements().getPressure();
        double atm = volume.getGasPressure() / 101325;
        if (pressure != null && (atm < pressure.getMinimumAtm() || atm > pressure.getMaximumAtm())) {
            return false;
        }
        CatalystReactionRequirement catalyst = reaction.getRequirements().getCatalyst();
        if (catalyst != null) {
            for (Map.Entry<String, Float> entry : catalyst.getChemicals().entrySet()) {
                Chemical chemical = ChemicalRegistry.getChemical(entry.getKey());
                if (chemical == null || total(volume, chemical) < Math.max(entry.getValue(), EPSILON)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean run(Volume volume, ChemicalReaction reaction, double mixing, double dt) {
        if (reaction.getReactants().isEmpty() || !canRun(volume, reaction)) {
            return false;
        }
        double possible = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Float> reactant : reaction.getReactants().entrySet()) {
            Chemical chemical = ChemicalRegistry.getChemical(reactant.getKey());
            if (chemical == null || reactant.getValue() <= 0) {
                return false;
            }
            possible = Math.min(possible, total(volume, chemical) / reactant.getValue());
        }
        for (String product : reaction.getProducts().keySet()) {
            if (ChemicalRegistry.getChemical(product) == null) {
                return false;
            }
        }
        double extent = possible * VolumeExchange.share(reaction.getRate() * (1 + MIXING_BOOST * Math.clamp(mixing, 0, 1)), dt);
        if (extent <= EPSILON) {
            return false;
        }
        for (Map.Entry<String, Float> reactant : reaction.getReactants().entrySet()) {
            take(volume, ChemicalRegistry.getChemical(reactant.getKey()), extent * reactant.getValue());
        }
        double celsius = volume.getTemperature() - 273.15;
        double atm = volume.getGasPressure() / 101325;
        for (Map.Entry<String, Float> product : reaction.getProducts().entrySet()) {
            Chemical chemical = ChemicalRegistry.getChemical(product.getKey());
            volume.addChemical(chemical, extent * product.getValue(), chemical.phaseAt(celsius, atm));
        }
        volume.addHeat(-reaction.getEnthalpyKJ() * 1000 * extent);
        return true;
    }

    private static double total(Volume volume, Chemical chemical) {
        double total = 0;
        for (Volume.State state : Volume.State.values()) {
            total += volume.getChemicalAmount(chemical, state);
        }
        return total;
    }

    private static void take(Volume volume, Chemical chemical, double moles) {
        double total = total(volume, chemical);
        if (total <= 0) {
            return;
        }
        double share = Math.min(1, moles / total);
        for (Volume.State state : Volume.State.values()) {
            double amount = volume.getChemicalAmount(chemical, state);
            if (amount > 0) {
                volume.addChemical(chemical, -amount * share, state);
            }
        }
    }
}
