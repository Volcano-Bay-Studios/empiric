package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import xyz.volcanobay.empiric.data.ItemModules;

import java.util.function.Supplier;

public final class EmpiricDataComponents {

    public static final Registrar<DataComponentType<?>> DATA_COMPONENTS = Platform.registrar(Registries.DATA_COMPONENT_TYPE);

    public static final Supplier<DataComponentType<ItemModules>> MODULES = DATA_COMPONENTS.register("modules", () -> DataComponentType.<ItemModules>builder()
            .persistent(ItemModules.CODEC)
            .networkSynchronized(ItemModules.STREAM_CODEC)
            .build());

    private EmpiricDataComponents() {
    }

    public static void bootstrap() {
    }
}
