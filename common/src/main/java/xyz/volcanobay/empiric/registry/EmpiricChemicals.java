package xyz.volcanobay.empiric.registry;

import com.mojang.serialization.Codec;
import foundry.veil.platform.registry.RegistrationProvider;
import net.minecraft.core.Registry;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.chemistry.requirement.ElectrolysisRequirement;
import xyz.volcanobay.empiric.api.chemistry.requirement.HeatReactionRequirement;
import xyz.volcanobay.empiric.api.chemistry.requirement.ReactionRequirement;

import java.util.function.Supplier;

public class EmpiricChemicals {
    private static final RegistrationProvider<ChemicalRegistry.ReactionRequirementType<?>> REQUIREMENTS = RegistrationProvider.get(ChemicalRegistry.REQUIREMENTS_KEY, Empiric.MODID);
    public static final Registry<ChemicalRegistry.ReactionRequirementType<?>> REACTION_REQUIREMENTS = REQUIREMENTS.asVanillaRegistry();

    public static final Supplier<ChemicalRegistry.ReactionRequirementType<HeatReactionRequirement>> HEAT_REQUIREMENT = register("heat", HeatReactionRequirement.CODEC);
    public static final Supplier<ChemicalRegistry.ReactionRequirementType<ElectrolysisRequirement>> ELECTROLYSIS_REQUIREMENT = register("electrolysis", ElectrolysisRequirement.CODEC);

    public static <T extends ReactionRequirement> Supplier<ChemicalRegistry.ReactionRequirementType<T>> register(String name, Codec<T> codec) {
        return REQUIREMENTS.register(name, () -> new ChemicalRegistry.ReactionRequirementType<>(codec));
    }

    public static void bootstrap() {
    }
}
