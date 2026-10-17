package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.api.chemistry.requirement.ReactionRequirements;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;

@SelfSpec
public class ChemicalReaction {

    @Data
    private HashMap<String, Float> reactants = new HashMap<>();
    @Data
    private HashMap<String, Float> products = new HashMap<>();
    @Data
    private ReactionRequirements requirements = new ReactionRequirements();

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
