package xyz.volcanobay.empiric.api.atmosphere.volume;

import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.atmosphere.LevelAtmosphere;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AtmosphereVolume extends Volume {

    private static final Set<String> UNKNOWN = new HashSet<>();

    @ApiStatus.Internal
    public void updateVolume(double volume) {
        this.volume = volume;
    }

    /**
     * Sets this volume to a dimension's air: its temperature, and as much of each gas as gives its pressure in the current volume.
     */
    public void fill(LevelAtmosphere atmosphere) {
        temperature = atmosphere.kelvin();
        double total = 0;
        for (double fraction : atmosphere.composition.values()) {
            total += Math.max(0, fraction);
        }
        if (total <= 0) {
            return;
        }
        double moles = atmosphere.pressure * 1000 * volume / (R * temperature);
        for (Map.Entry<String, Double> entry : atmosphere.composition.entrySet()) {
            Chemical chemical = ChemicalRegistry.getChemical(entry.getKey());
            if (chemical == null) {
                if (UNKNOWN.add(entry.getKey())) {
                    Empiric.LOGGER.warn("Level atmosphere lists the unknown chemical {}", entry.getKey());
                }
                continue;
            }
            put(chemical, moles * Math.max(0, entry.getValue()) / total);
        }
    }

    protected void put(Chemical chemical, double amount) {
        addChemical(chemical, amount - getChemicalAmount(chemical, State.GAS), State.GAS);
    }
}
