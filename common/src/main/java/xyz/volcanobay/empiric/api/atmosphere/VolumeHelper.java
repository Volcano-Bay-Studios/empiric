package xyz.volcanobay.empiric.api.atmosphere;

import xyz.volcanobay.empiric.system.AtmosphereSystem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.module.BloodstreamModule;
import xyz.volcanobay.empiric.api.atmosphere.module.ItemVolumeModule;
import xyz.volcanobay.empiric.api.atmosphere.module.VolumeNetwork;
import xyz.volcanobay.empiric.api.atmosphere.volume.BlockVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.BloodstreamVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import xyz.volcanobay.empiric.api.oct.OctPos;

public class VolumeHelper {
    public static @Nullable BloodstreamVolume getBloodstreamVolume(LivingEntity entity) {
        BloodstreamModule module = AtmosphereSystem.BLOODSTREAM.get(entity);
        return module == null ? null : module.getVolume();
    }

    public static @Nullable ItemVolume getItemVolume(ItemStack stack) {
        if (stack.isEmpty() || !AtmosphereSystem.ITEM_VOLUME.isEnabled()) {
            return null;
        }
        ItemVolumeModule module = AtmosphereSystem.ITEM_VOLUME.get(stack);
        return module == null ? null : module.volume;
    }

    public static void setItemVolume(ItemStack stack, ItemVolume volume) {
        AtmosphereSystem.ITEM_VOLUME.modify(stack, module -> module.volume = volume);
    }

    public static @Nullable BlockVolume getBlockVolume(Level level, OctPos pos) {
        VolumeNetwork network = AtmosphereSystem.VOLUME.get(level).getNetwork(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        if (network != null) {
            return network.getVolume();
        }
        return null;
    }

    public static @Nullable Volume getVolumeSoft(Level level, OctPos pos) {
        VolumeNetwork volumeNetwork = AtmosphereSystem.VOLUME.get(level).getNetworkSoft(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        if (volumeNetwork != null) {
            return volumeNetwork.getVolume();
        }
        AtmosphereNetwork atmosphereNetwork = AtmosphereSystem.ATMOSPHERE.get(level).getNetworkSoft(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        if (atmosphereNetwork != null) {
            return atmosphereNetwork.getVolume();
        }
        return null;
    }


    public static @Nullable Volume getBreathableVolume(LivingEntity entity) {
        ItemStack itemBySlot = entity.getItemBySlot(EquipmentSlot.HEAD);
        ItemVolume itemVolume = getItemVolume(itemBySlot);
        if (itemVolume != null) {
            return itemVolume;
        }

        return getVolumeSoft(entity.level(), new OctPos(entity.getEyePosition()));
    }
}
