package xyz.volcanobay.empiric.api.atmosphere.volume;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;

import org.jetbrains.annotations.ApiStatus;

public class AtmosphereVolume extends Volume {
    @ApiStatus.Internal
    public void updateVolume(double volume) {
        this.volume = volume;
    }
}
