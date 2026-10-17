package xyz.volcanobay.empiric.api.atmosphere.volume;

import xyz.volcanobay.empiric.api.atmosphere.Volume;

public class ItemVolume extends Volume {

    public ItemVolume() {
    }

    /**
     * Creates a new item value.
     * @param capacity Volume in liters
     */
    public ItemVolume(double capacity) {
        this.volume = capacity/1000;
    }
}
