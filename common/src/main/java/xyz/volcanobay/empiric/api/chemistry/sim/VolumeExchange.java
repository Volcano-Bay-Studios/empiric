package xyz.volcanobay.empiric.api.chemistry.sim;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Gas and heat moving between two volumes.
 */
public final class VolumeExchange {

    private static final double R = 8.314;
    private static final double EPSILON = 1.0E-12;

    private VolumeExchange() {
    }

    /**
     * The share of a gap that a rate closes over a step.
     *
     * @param rate Rate in 1/s
     * @param dt   Step in seconds
     */
    public static double share(double rate, double dt) {
        return rate <= 0 || dt <= 0 ? 0 : 1 - Math.exp(-rate * dt);
    }

    /**
     * Moves gas between two volumes joined by an opening. Gas flows from the higher pressure to the lower one, and the
     * two mixtures diffuse into each other without changing either pressure.
     *
     * @param flow      Rate in 1/s at which the pressure difference closes
     * @param diffusion Rate in 1/s at which the inner mixture takes on the outer one's
     * @param dt        Step in seconds
     * @return Moles of gas that left the inner volume, negative if gas came in
     */
    public static double equalize(Volume inner, Volume outer, double flow, double diffusion, double dt) {
        double moved = flow(inner, outer, share(flow, dt));
        diffuse(inner, outer, share(diffusion, dt));
        return moved;
    }

    private static double flow(Volume inner, Volume outer, double share) {
        double innerGas = inner.getStateTotal(Volume.State.GAS);
        double innerSpace = inner.getGasSpace();
        double innerT = inner.getTemperature();
        if (share <= 0 || innerSpace <= 0 || innerT <= 0) {
            return 0;
        }
        double pressure;
        if (outer.isReservoir()) {
            pressure = outer.getGasPressure();
        } else {
            double outerSpace = outer.getGasSpace();
            double outerT = outer.getTemperature();
            if (outerSpace <= 0 || outerT <= 0) {
                return 0;
            }
            pressure = (innerGas + outer.getStateTotal(Volume.State.GAS)) * R / (innerSpace / innerT + outerSpace / outerT);
        }
        double wanted = pressure * innerSpace / (R * innerT);
        double move = (innerGas - wanted) * share;
        if (move > EPSILON) {
            inner.transferAmount(outer, Math.min(move, innerGas), Volume.State.GAS);
            return move;
        }
        if (move < -EPSILON) {
            double amount = Math.min(-move, outer.getStateTotal(Volume.State.GAS));
            take(outer, inner, amount);
            return -amount;
        }
        return 0;
    }

    private static void diffuse(Volume inner, Volume outer, double share) {
        double innerGas = inner.getStateTotal(Volume.State.GAS);
        double outerGas = outer.getStateTotal(Volume.State.GAS);
        if (share <= 0 || innerGas <= EPSILON || outerGas <= EPSILON) {
            return;
        }
        // the same number of moles goes each way, so neither pressure changes
        double swap = share * (outer.isReservoir() ? innerGas : innerGas * outerGas / (innerGas + outerGas));
        List<Portion> incoming = portions(outer, swap);
        inner.transferAmount(outer, swap, Volume.State.GAS);
        for (Portion portion : incoming) {
            outer.transferChemical(portion.chemical(), inner, portion.moles(), Volume.State.GAS);
        }
    }

    /**
     * Moves an amount of gas out of a volume in the proportions of its mixture.
     */
    private static void take(Volume from, Volume to, double amount) {
        for (Portion portion : portions(from, amount)) {
            from.transferChemical(portion.chemical(), to, portion.moles(), Volume.State.GAS);
        }
    }

    private static List<Portion> portions(Volume volume, double amount) {
        double total = volume.getStateTotal(Volume.State.GAS);
        List<Portion> portions = new ArrayList<>();
        if (total <= 0) {
            return portions;
        }
        for (ChemicalComponent component : volume.getChemicalComponentsList().values()) {
            double gas = component.getAmount(Volume.State.GAS);
            if (gas > 0) {
                portions.add(new Portion(component.chemical, amount * gas / total));
            }
        }
        return portions;
    }

    /**
     * Moves heat between a volume and something at a fixed temperature, or with a heat capacity of its own.
     *
     * @param kelvin      The other side's temperature
     * @param capacity    The other side's heat capacity in J/K, {@link Double#POSITIVE_INFINITY} if it doesn't warm up
     * @param conductance Heat in W/K the two exchange per kelvin of difference
     * @param dt          Step in seconds
     * @return Heat in J that went into the volume, negative if it came out; the other side gets the opposite
     */
    public static double heat(Volume volume, double kelvin, double capacity, double conductance, double dt) {
        double own = volume.heatCapacity();
        if (own <= 0 || capacity <= 0 || conductance <= 0 || volume.isReservoir()) {
            return 0;
        }
        double combined = Double.isInfinite(capacity) ? own : own * capacity / (own + capacity);
        double joules = combined * (kelvin - volume.getTemperature()) * share(conductance / combined, dt);
        volume.addHeat(joules);
        return joules;
    }

    private record Portion(Chemical chemical, double moles) {
    }
}
