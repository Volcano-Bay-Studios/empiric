package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * Chemicals that must be in the volume, in any state, without being used up.
 */
@SelfSpec
public class CatalystReactionRequirement implements ReactionRequirement {

    @Data
    private HashMap<String, Float> chemicals = new HashMap<>();

    /**
     * @return Chemical id to the moles of it that must be present
     */
    public HashMap<String, Float> getChemicals() {
        return chemicals;
    }

    @Override
    public boolean met(Volume volume, double mixing) {
        for (Map.Entry<String, Float> entry : chemicals.entrySet()) {
            Chemical chemical = ChemicalRegistry.getChemical(entry.getKey());
            if (chemical == null) {
                return false;
            }
            double moles = 0;
            for (Volume.State state : Volume.State.values()) {
                moles += volume.getChemicalAmount(chemical, state);
            }
            if (moles < Math.max(entry.getValue(), 1.0E-12)) {
                return false;
            }
        }
        return true;
    }
}
