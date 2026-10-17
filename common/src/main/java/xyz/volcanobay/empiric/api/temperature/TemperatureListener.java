package xyz.volcanobay.empiric.api.temperature;

@FunctionalInterface
public interface TemperatureListener {

    void sectionChanged(TemperatureModule module, int section);
}
