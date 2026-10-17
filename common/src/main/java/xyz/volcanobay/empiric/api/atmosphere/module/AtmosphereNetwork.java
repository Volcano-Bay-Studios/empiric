package xyz.volcanobay.empiric.api.atmosphere.module;

import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.PlanetAtmosphereVolume;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.core.data.Data;

public class AtmosphereNetwork extends NetworkModule implements NetworkBehavior {

    @Data
    private AtmosphereVolume volume = new AtmosphereVolume();

    @Override
    protected void onAttach() {
        if (id() == -2) {
            volume = new PlanetAtmosphereVolume();
        }
    }

    @Override
    public void update() {
        double capacity = members().size() / 8f;
        this.volume.updateVolume(capacity);
        markDirty();
    }

    public AtmosphereVolume getVolume() {
        return volume;
    }
}
