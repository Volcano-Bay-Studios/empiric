package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

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

    public int count() {
        return (heat == null ? 0 : 1) + (electrolysis == null ? 0 : 1) + (pressure == null ? 0 : 1) + (catalyst == null ? 0 : 1);
    }
}
