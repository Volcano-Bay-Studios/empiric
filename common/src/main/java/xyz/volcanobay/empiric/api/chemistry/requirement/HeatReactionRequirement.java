package xyz.volcanobay.empiric.api.chemistry.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.volcanobay.empiric.registry.EmpiricChemicals;

import java.util.Optional;

public class HeatReactionRequirement extends ReactionRequirement {
    public static final Codec<HeatReactionRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("min").forGetter(HeatReactionRequirement::getMinimumHeat),
            Codec.FLOAT.optionalFieldOf("max").forGetter(HeatReactionRequirement::getMaximumHeat)
    ).apply(instance, HeatReactionRequirement::new));

    private float minimumHeat = Float.NEGATIVE_INFINITY;
    private float maximumHeat = Float.POSITIVE_INFINITY;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public HeatReactionRequirement(Optional<Float> minimumHeat, Optional<Float> maximumHeat) {
        super(EmpiricChemicals.HEAT_REQUIREMENT.get());
        minimumHeat.ifPresent(aFloat -> this.minimumHeat = aFloat);
        maximumHeat.ifPresent(aFloat -> this.maximumHeat = aFloat);
    }

    protected Optional<Float> getMinimumHeat() {
        return Optional.of(minimumHeat);
    }

    protected Optional<Float> getMaximumHeat() {
        return Optional.of(maximumHeat);
    }
}
