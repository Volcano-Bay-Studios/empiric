package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class UiScreenTypeAdapter implements RegistrationAdapter<UiScreenType> {

    @Override
    public String registry() {
        return "empiric:ui_screen";
    }

    @Override
    public Class<UiScreenType> valueType() {
        return UiScreenType.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void register(RegistrationEntry entry, Supplier<UiScreenType> value) {
        UiScreens.register(ResourceLocation.parse(entry.id()), value.get());
    }
}
