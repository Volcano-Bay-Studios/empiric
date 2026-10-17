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

    @Override
    public void onMerged(NetworkModule absorbed) {
        if (absorbed instanceof VolumeNetwork other) {
            volume.absorb(other.volume);
            markDirty();
        }
    }

    /**
     * A split takes its share of the contents by size.
     */
    @Override
    public void onSplit(NetworkModule split) {
        if (!(split instanceof VolumeNetwork other)) {
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

    public BlockVolume getVolume() {
        return volume;
    }
}
