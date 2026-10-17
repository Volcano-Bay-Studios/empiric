package xyz.volcanobay.empiric.api.atmosphere.volume;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import org.jetbrains.annotations.ApiStatus;

public class BlockVolume extends Volume {
    @ApiStatus.Internal
    public void updateVolume(double volume) {
        this.volume = volume;
    }
}
