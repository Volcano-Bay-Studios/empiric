package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.part.PlacementFailure;
import xyz.volcanobay.empiric.api.registry.SilentFailure;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.util.function.Supplier;

public class PlacementFailureAdapter implements RegistrationAdapter<PlacementFailure> {

    @Override
    public String registry() {
        return "empiric:placement_failure";
    }

    @Override
    public Class<PlacementFailure> valueType() {
        return PlacementFailure.class;
    }

    @Override
    public String idOf(PlacementFailure value) {
        return value.id().toString();
    }

    @Override
    public PlacementFailure create(RegistrationEntry entry) {
        if (entry.source() != RegistrationEntry.Kind.REF_FIELD) {
            throw new IllegalStateException(entry + " is a placement failure, so it has to be a Ref<PlacementFailure> field");
        }
        ResourceLocation id = ResourceLocation.parse(entry.id());
        return entry.has(SilentFailure.class) ? PlacementFailure.registerSilent(id) : PlacementFailure.register(id);
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<PlacementFailure> value) {
        value.get();
    }
}
