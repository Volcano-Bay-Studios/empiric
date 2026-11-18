package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.model.EmpiricModels;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModelAdapter implements RegistrationAdapter<EmpiricModel> {

    private final List<EmpiricModel> models = new ArrayList<>();

    @Override
    public String registry() {
        return "empiric:model";
    }

    @Override
    public Class<EmpiricModel> valueType() {
        return EmpiricModel.class;
    }

    @Override
    public EmpiricModel create(RegistrationEntry entry) {
        return EmpiricModels.register(ResourceLocation.parse(entry.id()));
    }

    @Override
    public String idOf(EmpiricModel value) {
        return value.id().toString();
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<EmpiricModel> value) {
        models.add(value.get());
    }

    public List<EmpiricModel> models() {
        return models;
    }
}
