package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * What a reaction needs before it can happen. A requirement that isn't listed isn't needed.
 */
@SelfSpec
public class ReactionRequirements {

    @Data(id = "empiric:heat", optional = true)
    private HeatReactionRequirement heat;
    @Data(id = "empiric:electrolysis", optional = true)
    private ElectrolysisRequirement electrolysis;
    @Data(id = "empiric:pressure", optional = true)
    private PressureReactionRequirement pressure;
    @Data(id = "empiric:catalyst", optional = true)
    private CatalystReactionRequirement catalyst;
    @Data(id = "empiric:mixing", optional = true)
    private MixingReactionRequirement mixing;
    @Data(id = "empiric:absent", optional = true)
    private AbsentReactionRequirement absent;

    public HeatReactionRequirement getHeat() {
        return heat;
    }

    public ElectrolysisRequirement getElectrolysis() {
        return electrolysis;
    }

    public PressureReactionRequirement getPressure() {
        return pressure;
    }

    public CatalystReactionRequirement getCatalyst() {
        return catalyst;
    }

    public MixingReactionRequirement getMixing() {
        return mixing;
    }

    public AbsentReactionRequirement getAbsent() {
        return absent;
    }

    /**
     * @return The conditions a volume checks before running the reaction; electrolysis is handled apart from the simulation
     */
    public List<ReactionRequirement> conditions() {
        List<ReactionRequirement> conditions = new ArrayList<>();
        for (ReactionRequirement condition : new ReactionRequirement[]{heat, pressure, catalyst, mixing, absent}) {
            if (condition != null) {
                conditions.add(condition);
            }
        }
        return conditions;
    }

    public int count() {
        return conditions().size() + (electrolysis == null ? 0 : 1);
    }
}
