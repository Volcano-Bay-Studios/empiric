package xyz.volcanobay.empiric.api.atmosphere.module;

import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.PlanetAtmosphereVolume;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.behavior.ServerTickBehavior;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.system.AtmosphereSystem;
import xyz.volcanobay.empiric.system.AtmosphereThermalSystem;

public class AtmosphereNetwork extends NetworkModule implements NetworkBehavior, ServerTickBehavior {

    private static final double PLANET_VOLUME = 1_000_000;
    private static final int INTERVAL = 20;

    @Data
    private AtmosphereVolume volume = new AtmosphereVolume();
    private int timer;

    @Override
    protected void onAttach() {
        if (id() == -2) {
            volume = new PlanetAtmosphereVolume();
        }
    }

    @Override
    public void update() {
        this.volume.updateVolume(isWorldAtmosphere() ? PLANET_VOLUME : members().size() / 8f);
        if (!level().isClientSide() && (isWorldAtmosphere() || volume.getTemperature() <= 0)) {
            volume.fill(AtmosphereSystem.levelAtmosphere(level()));
        }
        markDirty();
    }

    @Override
    public void serverTick() {
        if (++timer < INTERVAL) {
            return;
        }
        timer = 0;
        if (isWorldAtmosphere()) {
            volume.fill(AtmosphereSystem.levelAtmosphere(level()));
            markDirty();
        } else if (AtmosphereThermalSystem.exchange(this)) {
            markDirty();
        }
    }

    /**
     * The kept network takes in the absorbed one's air. Open air ignores it, so a breached room simply vents.
     */
    @Override
    public void onMerged(NetworkModule absorbed) {
        if (absorbed instanceof AtmosphereNetwork other) {
            volume.absorb(other.volume);
            markDirty();
        }
    }

    /**
     * A split takes its share of the air by size. A pocket sealed off open air keeps the level atmosphere it was filled with.
     */
    @Override
    public void onSplit(NetworkModule split) {
        if (isWorldAtmosphere() || !(split instanceof AtmosphereNetwork other)) {
            return;
        }
        int ours = members().size();
        int theirs = other.members().size();
        if (ours + theirs > 0) {
            volume.splitInto(other.volume, (double) theirs / (ours + theirs));
            markDirty();
            other.markDirty();
        }
    }

    public boolean isWorldAtmosphere() {
        return id() == -2;
    }

    public AtmosphereVolume getVolume() {
        return volume;
    }
}
