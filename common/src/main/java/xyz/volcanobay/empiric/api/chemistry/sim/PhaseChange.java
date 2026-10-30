package xyz.volcanobay.empiric.api.chemistry.sim;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Boiling, evaporating, condensing, melting and freezing inside a volume. Changes of state take or give their latent
 * heat, so a pot can't boil past its boiling point until the liquid is gone.
 * Chemicals without enthalpies never change state here.
 */
public final class PhaseChange {

    /**
     * Rate in 1/s at which the vapour over a liquid closes in on its vapour pressure.
     */
    public static final double K_VAP = 0.5;
    private static final double R = 8.314;
    private static final double EPSILON = 1.0E-12;

    private PhaseChange() {
    }

    /**
     * Runs a step of state changes.
     *
     * @param dt Step in seconds
     * @return Whether something was boiling
     */
    public static boolean step(Volume volume, double dt) {
        if (volume.isReservoir() || volume.getTemperature() <= 0) {
            return false;
        }
        boolean boiling = false;
        for (ChemicalComponent component : new ArrayList<>(volume.getChemicalComponentsList().values())) {
            Chemical chemical = component.chemical;
            boiling |= boilOrEvaporate(volume, chemical, dt);
            meltOrFreeze(volume, chemical);
        }
        return boiling;
    }

    private static boolean boilOrEvaporate(Volume volume, Chemical chemical, double dt) {
        double latent = chemical.gethVapKJ() * 1000;
        if (latent <= 0) {
            return false;
        }
        double liquid = volume.getChemicalAmount(chemical, Volume.State.LIQUID);
        double gas = volume.getChemicalAmount(chemical, Volume.State.GAS);
        double kelvin = volume.getTemperature();
        double boil = chemical.boilingPointAt(volume.getGasPressure() / 101325) + 273.15;
        if (kelvin > boil && liquid > EPSILON) {
            double moles = Math.min(liquid, (kelvin - boil) * volume.heatCapacity() / latent);
            move(volume, chemical, moles, Volume.State.LIQUID, Volume.State.GAS, -moles * latent);
            return true;
        }
        if (kelvin > boil || liquid <= EPSILON && gas <= EPSILON) {
            return false;
        }
        double space = volume.getGasSpace();
        double wanted = chemical.vapourPressure(kelvin) * space / (R * kelvin);
        double change = (wanted - gas) * VolumeExchange.share(K_VAP, dt);
        if (change > EPSILON) {
            double moles = Math.min(change, liquid);
            move(volume, chemical, moles, Volume.State.LIQUID, Volume.State.GAS, -moles * latent);
        } else if (change < -EPSILON) {
            double moles = Math.min(-change, gas);
            move(volume, chemical, moles, Volume.State.GAS, Volume.State.LIQUID, moles * latent);
        }
        return false;
    }

    private static void meltOrFreeze(Volume volume, Chemical chemical) {
        double latent = chemical.gethFusKJ() * 1000;
        if (latent <= 0) {
            return;
        }
        double kelvin = volume.getTemperature();
        double freeze = chemical.getFreezeTemp() + 273.15;
        if (kelvin < freeze) {
            double liquid = volume.getChemicalAmount(chemical, Volume.State.LIQUID);
            if (liquid > EPSILON && chemical.getSolubility() <= 0) {
                double moles = Math.min(liquid, (freeze - kelvin) * volume.heatCapacity() / latent);
                move(volume, chemical, moles, Volume.State.LIQUID, Volume.State.SOLID, moles * latent);
            }
        } else if (kelvin > freeze) {
            double solid = volume.getChemicalAmount(chemical, Volume.State.SOLID);
            if (solid > EPSILON) {
                double moles = Math.min(solid, (kelvin - freeze) * volume.heatCapacity() / latent);
                move(volume, chemical, moles, Volume.State.SOLID, Volume.State.LIQUID, -moles * latent);
            }
        }
    }

    /**
     * Moves moles of a chemical from one state to another and adds the heat that took. The heat is added after the
     * move, so it's spread over the new heat capacity.
     */
    static void move(Volume volume, Chemical chemical, double moles, Volume.State from, Volume.State to, double joules) {
        if (moles <= EPSILON) {
            return;
        }
        volume.addChemical(chemical, -moles, from);
        volume.addChemical(chemical, moles, to);
        volume.addHeat(joules);
    }

    /**
     * Every chemical in the volume that is boiling right now, for showing which one it is.
     */
    public static List<Chemical> boiling(Volume volume) {
        List<Chemical> boiling = new ArrayList<>();
        double atm = volume.getGasPressure() / 101325;
        for (ChemicalComponent component : volume.getChemicalComponentsList().values()) {
            if (component.getAmount(Volume.State.LIQUID) > EPSILON && component.chemical.gethVapKJ() > 0
                    && volume.getTemperature() >= component.chemical.boilingPointAt(atm) + 273.15 - 0.05) {
                boiling.add(component.chemical);
            }
        }
        return boiling;
    }
}
