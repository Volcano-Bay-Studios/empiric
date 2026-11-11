package xyz.volcanobay.empiric.api.chemistry.requirement;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * Chemicals the volume may hold only up to a concentration, in any state.
 */
@SelfSpec
public class AbsentReactionRequirement implements ReactionRequirement {

    @Data
    private HashMap<String, Float> chemicals = new HashMap<>();

    /**
     * @return Chemical id to the most moles per litre of it the volume may hold
     */
    public HashMap<String, Float> getChemicals() {
        return chemicals;
    }

    @Override
    public boolean met(Volume volume, double mixing) {
        double liters = Math.max(volume.getVolumeLiters(), 1.0E-9);
        for (Map.Entry<String, Float> entry : chemicals.entrySet()) {
            Chemical chemical = ChemicalRegistry.getChemical(entry.getKey());
            if (chemical == null) {
                continue;
            }
            double moles = 0;
            for (Volume.State state : Volume.State.values()) {
                moles += volume.getChemicalAmount(chemical, state);
            }
            if (moles / liters > entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
