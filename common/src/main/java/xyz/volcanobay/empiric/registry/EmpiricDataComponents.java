package xyz.volcanobay.empiric.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.data.ItemModules;

import java.util.function.Supplier;

public final class EmpiricDataComponents {

    @Deprecated
    public static final Registrar<DataComponentType<?>> DATA_COMPONENTS = Platform.registrar(Registries.DATA_COMPONENT_TYPE);

    @Register("modules")
    private static final DataComponentType<ItemModules> MODULES_TYPE = DataComponentType.<ItemModules>builder()
            .persistent(ItemModules.CODEC)
            .networkSynchronized(ItemModules.STREAM_CODEC)
            .build();

    public static final Supplier<DataComponentType<ItemModules>> MODULES = () -> MODULES_TYPE;

    private EmpiricDataComponents() {
    }

    public static void bootstrap() {
    }
}
