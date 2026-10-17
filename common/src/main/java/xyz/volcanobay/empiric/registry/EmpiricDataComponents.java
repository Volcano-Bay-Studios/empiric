package xyz.volcanobay.empiric.registry;

import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.data.ItemModules;

public final class EmpiricDataComponents {

    public static final RegistrationProvider<DataComponentType<?>> DATA_COMPONENTS = RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, Empiric.MODID);

    public static final RegistryObject<DataComponentType<ItemModules>> MODULES = DATA_COMPONENTS.register("modules", () -> DataComponentType.<ItemModules>builder()
            .persistent(ItemModules.CODEC)
            .networkSynchronized(ItemModules.STREAM_CODEC)
            .build());

    private EmpiricDataComponents() {
    }

    public static void bootstrap() {
    }
}
