package xyz.volcanobay.empiric.api.chemistry.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import foundry.veil.api.util.CodecUtil;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;

public abstract class ReactionRequirement {
    private final ChemicalRegistry.ReactionRequirementType<? extends ReactionRequirement> type;
    public ReactionRequirement(ChemicalRegistry.ReactionRequirementType<? extends ReactionRequirement> reactionRequirementType) {
        this.type = reactionRequirementType;
    }

    public ChemicalRegistry.ReactionRequirementType<?> getType() {
        return this.type;
    }
}
