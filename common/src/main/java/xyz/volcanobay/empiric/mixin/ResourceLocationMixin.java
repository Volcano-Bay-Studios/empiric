package xyz.volcanobay.empiric.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import xyz.volcanobay.empiric.core.module.ModuleId;

@Mixin(ResourceLocation.class)
public abstract class ResourceLocationMixin implements ModuleId {

    @Override
    public String id() {
        return toString();
    }
}
