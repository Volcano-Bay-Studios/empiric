package xyz.volcanobay.empiric.registry.adapter;

import xyz.volcanobay.empiric.api.part.PartInteraction;
import xyz.volcanobay.empiric.api.part.PartInteractions;
import xyz.volcanobay.empiric.api.registry.InteractionPriority;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.util.function.Supplier;

public class PartInteractionAdapter implements RegistrationAdapter<PartInteraction> {

    @Override
    public String registry() {
        return "empiric:part_interaction";
    }

    @Override
    public Class<PartInteraction> valueType() {
        return PartInteraction.class;
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<PartInteraction> value) {
        InteractionPriority priority = entry.annotation(InteractionPriority.class);
        PartInteractions.register(priority == null ? 0 : priority.value(), value.get());
    }
}
