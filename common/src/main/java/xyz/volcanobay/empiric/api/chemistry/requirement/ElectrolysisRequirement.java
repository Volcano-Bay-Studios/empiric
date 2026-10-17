package xyz.volcanobay.empiric.api.chemistry.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.volcanobay.empiric.registry.EmpiricChemicals;

public class ElectrolysisRequirement extends ReactionRequirement {
    public static final Codec<ElectrolysisRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("min_amps").forGetter(ElectrolysisRequirement::getMinAmps)
    ).apply(instance, ElectrolysisRequirement::new));

    private final double minAmps;

    public ElectrolysisRequirement(double minAmps) {
        super(EmpiricChemicals.ELECTROLYSIS_REQUIREMENT.get());
        this.minAmps = minAmps;
    }

    public double getMinAmps() {
        return this.minAmps;
    }
}
