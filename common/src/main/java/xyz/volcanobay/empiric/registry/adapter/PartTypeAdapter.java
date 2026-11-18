package xyz.volcanobay.empiric.registry.adapter;

import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.PartTypes;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.util.function.Supplier;

public class PartTypeAdapter implements RegistrationAdapter<PartType> {

    @Override
    public String registry() {
        return "empiric:part_type";
    }

    @Override
    public Class<PartType> valueType() {
        return PartType.class;
    }

    @Override
    public String idOf(PartType value) {
        return value.id().toString();
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<PartType> value) {
        PartType type = value.get();
        if (!type.id().toString().equals(entry.id())) {
            throw new IllegalStateException(entry + " is registered as " + entry.id() + " but its part type is " + type.id());
        }
        PartTypes.register(type);
    }
}
