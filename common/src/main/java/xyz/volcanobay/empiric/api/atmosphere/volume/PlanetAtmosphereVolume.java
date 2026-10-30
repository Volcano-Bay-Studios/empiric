package xyz.volcanobay.empiric.api.atmosphere.volume;

import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;

/**
 * Open air. Gases can be moved in and out without changing it, and its temperature only follows the dimension's {@link #fill}.
 */
public class PlanetAtmosphereVolume extends AtmosphereVolume {
    @Override
    public double addChemical(Chemical chemical, double amount, State state) {
        return getChemicalAmount(chemical,state);
    }

    @Override
    protected double addChemicalToEntry(ChemicalComponent component, double amount, State state) {
        return getChemicalAmount(component.chemical,state);
    }

    @Override
    public double setChemical(Chemical chemical, double amount, State state) {
        return getChemicalAmount(chemical,state);
    }

    @Override
    public void setTemperature(double temperature) {
    }

    @Override
    public void addHeat(double joules) {
    }

    @Override
    public boolean isReservoir() {
        return true;
    }

    @Override
    public void clear() {
    }

    @Override
    protected void put(Chemical chemical, double amount) {
        super.setChemical(chemical, amount, State.GAS);
    }
}
