package xyz.volcanobay.empiric.api.atmosphere.module;

import xyz.volcanobay.empiric.api.atmosphere.volume.BlockVolume;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.core.data.Data;

public class VolumeNetwork extends NetworkModule implements NetworkBehavior {

    @Data
    private final BlockVolume volume = new BlockVolume();

    @Override
    public void update() {
        double capacity = members().size() / 8f;
        this.volume.updateVolume(capacity);
        markDirty();
    }

    public BlockVolume getVolume() {
        return volume;
    }
}
