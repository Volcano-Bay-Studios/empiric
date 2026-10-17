package xyz.volcanobay.empiric.api.atmosphere.volume;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import xyz.volcanobay.empiric.api.atmosphere.Volume;

public class BloodstreamVolume extends Volume {

    public BloodstreamVolume() {
    }

    public BloodstreamVolume(LivingEntity entity) {
        AABB boundingBox = entity.getBoundingBox();
        volume = boundingBox.getXsize()*boundingBox.getYsize()* boundingBox.getZsize()/10f;
    }

    @Override
    public String toString() {
        return "Bloodstream";
    }
}
