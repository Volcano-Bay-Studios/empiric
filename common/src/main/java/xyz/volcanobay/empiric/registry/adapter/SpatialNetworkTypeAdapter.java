package xyz.volcanobay.empiric.registry.adapter;

import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;

import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class SpatialNetworkTypeAdapter implements RegistrationAdapter<SpatialNetworkType> {

    private final ModuleSystem modules;

    public SpatialNetworkTypeAdapter(ModuleSystem modules) {
        this.modules = modules;
    }

    @Override
    public String registry() {
        return "empiric:spatial_network_type";
    }

    @Override
    public Class<SpatialNetworkType> valueType() {
        return SpatialNetworkType.class;
    }

    @Override
    public String idOf(SpatialNetworkType value) {
        return value.id().toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void register(RegistrationEntry entry, Supplier<SpatialNetworkType> value) {
        SpatialNetworkType type = value.get();
        if (!type.id().toString().equals(entry.id())) {
            throw new IllegalStateException(entry + " is registered as " + entry.id() + " but its network type is " + type.id());
        }
        SpatialNetworkSystem.register(modules, type);
    }
}
