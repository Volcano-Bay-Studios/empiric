package xyz.volcanobay.empiric.api.atmosphere.volume;

import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;

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
}