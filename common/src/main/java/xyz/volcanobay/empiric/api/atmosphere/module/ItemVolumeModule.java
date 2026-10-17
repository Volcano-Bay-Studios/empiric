package xyz.volcanobay.empiric.api.atmosphere.module;

import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;

public class ItemVolumeModule extends Module<ItemStackAttachment> {

    @Data
    public ItemVolume volume = new ItemVolume();
}
