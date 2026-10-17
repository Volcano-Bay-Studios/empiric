package xyz.volcanobay.empiric.api.atmosphere.module;

import net.minecraft.world.entity.LivingEntity;
import xyz.volcanobay.empiric.api.atmosphere.volume.BloodstreamVolume;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;

public class BloodstreamModule extends Module<EntityAttachment> {

    @Data(optional = true)
    private BloodstreamVolume volume;

    @Override
    protected void onAttach() {
        if (volume == null && attachment().entity() instanceof LivingEntity living) {
            volume = new BloodstreamVolume(living);
        }
    }

    @Override
    protected void onLoad() {
        onAttach();
    }

    public BloodstreamVolume getVolume() {
        return volume;
    }
}
