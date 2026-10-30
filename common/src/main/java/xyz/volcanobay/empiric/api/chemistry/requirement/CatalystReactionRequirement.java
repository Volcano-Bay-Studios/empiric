package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;

/**
 * Chemicals that must be in the volume, in any state, without being used up.
 */
@SelfSpec
public class CatalystReactionRequirement {

    @Data
    private HashMap<String, Float> chemicals = new HashMap<>();

    /**
     * @return Chemical id to the moles of it that must be present
     */
    public HashMap<String, Float> getChemicals() {
        return chemicals;
    }
}
