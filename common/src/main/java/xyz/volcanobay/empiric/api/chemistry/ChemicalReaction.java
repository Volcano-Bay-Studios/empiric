package xyz.volcanobay.empiric.api.chemistry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.volcanobay.empiric.api.chemistry.requirement.ReactionRequirement;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.registry.EmpiricChemicals;

import java.util.*;

public class ChemicalReaction {
    public static final Codec<ChemicalReaction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(
                    Codec.STRING,
                    Codec.FLOAT
            ).fieldOf("reactants").forGetter(ChemicalReaction::getReactants),
            Codec.unboundedMap(
                    Codec.STRING,
                    Codec.FLOAT
            ).fieldOf("products").forGetter(ChemicalReaction::getProducts),
            Codec.<ChemicalRegistry.ReactionRequirementType<?>, ReactionRequirement>dispatchedMap(
                            EmpiricChemicals.REACTION_REQUIREMENTS.byNameCodec(),
                            ChemicalRegistry.ReactionRequirementType::codec
                    ).fieldOf("requirements").forGetter(ChemicalReaction::getRequirements)
    ).apply(instance, ChemicalReaction::new));

    private final Map<String, Float> reactants;
    private final Map<String, Float> products;
    private final Map<ChemicalRegistry.ReactionRequirementType<?>, ReactionRequirement> requirements = new HashMap<>();
    private final float energyOutput;

    public ChemicalReaction(Map<String, Float> reactants, Map<String, Float> products, Map<ChemicalRegistry.ReactionRequirementType<?>, ReactionRequirement> requirements) {
        this.reactants = reactants;
        this.products = products;
        this.requirements.putAll(requirements);
        float energyIn = 0;
        float energyOut = 0;

        for (Float value : reactants.values()) {
            energyIn += value;
        }
        for (Float value : products.values()) {
            energyOut += value;
        }

        energyOutput = energyIn - energyOut;
    }

    public Map<String, Float> getReactants() {
        return reactants;
    }

    public Map<String, Float> getProducts() {
        return products;
    }

    public Map<ChemicalRegistry.ReactionRequirementType<?>, ReactionRequirement> getRequirements() {
        return requirements;
    }

    public float getEnergyOutput() {
        return energyOutput;
    }
}
