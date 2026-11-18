package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.TraitKey;
import xyz.volcanobay.empiric.core.data.Types;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class TraitKeyAdapter implements RegistrationAdapter<TraitKey> {

    @Override
    public String registry() {
        return "empiric:part_trait";
    }

    @Override
    public Class<TraitKey> valueType() {
        return TraitKey.class;
    }

    @Override
    public String idOf(TraitKey value) {
        return value.id().toString();
    }

    @Override
    public TraitKey create(RegistrationEntry entry) {
        if (entry.source() != RegistrationEntry.Kind.REF_FIELD) {
            throw new IllegalStateException(entry + " is a part trait, so it has to be a field or method that returns a TraitKey, or a Ref<TraitKey<T>> field");
        }
        Class<?> configType = Types.raw(Types.argument(entry.targetType(), 0));
        return new TraitKey<>(ResourceLocation.parse(entry.id()), configType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void register(RegistrationEntry entry, Supplier<TraitKey> value) {
        TraitKey key = value.get();
        if (!key.id().toString().equals(entry.id())) {
            throw new IllegalStateException(entry + " is registered as " + entry.id() + " but its trait key is " + key.id());
        }
        PartTraits.register(key);
    }
}
