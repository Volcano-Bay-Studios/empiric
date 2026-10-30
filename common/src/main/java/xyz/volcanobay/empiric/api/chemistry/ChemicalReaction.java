package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.api.chemistry.requirement.ReactionRequirements;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;

@SelfSpec
public class ChemicalReaction {

    public static final double DEFAULT_RATE = 0.5;

    @Data
    private HashMap<String, Float> reactants = new HashMap<>();
    @Data
    private HashMap<String, Float> products = new HashMap<>();
    @Data
    private ReactionRequirements requirements = new ReactionRequirements();
    @Data(optional = true)
    private Double rate;
    @Data(optional = true)
    private Double enthalpy;

    public ChemicalReaction() {
    }

    public HashMap<String, Float> getReactants() {
        return reactants;
    }

    public HashMap<String, Float> getProducts() {
        return products;
    }

    public ReactionRequirements getRequirements() {
        return requirements;
    }

    /**
     * @return Rate in 1/s at which the limiting reactant runs out while the requirements are met
     */
    public double getRate() {
        return rate == null ? DEFAULT_RATE : rate;
    }

    /**
     * @return kJ taken in per run of the equation as written (negative gives heat off)
     */
    public double getEnthalpyKJ() {
        return enthalpy == null ? 0 : enthalpy;
    }

    public float getEnergyOutput() {
        float energyIn = 0;
        float energyOut = 0;
        for (Float value : reactants.values()) {
            energyIn += value;
        }
        for (Float value : products.values()) {
            energyOut += value;
        }
        return energyIn - energyOut;
    }
}
