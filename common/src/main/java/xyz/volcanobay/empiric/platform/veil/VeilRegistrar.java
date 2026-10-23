package xyz.volcanobay.empiric.platform.veil;

import foundry.veil.platform.registry.RegistrationProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import xyz.volcanobay.empiric.platform.Registrar;

import java.util.function.Supplier;

public final class VeilRegistrar<T> implements Registrar<T> {

    private final RegistrationProvider<T> provider;

    public VeilRegistrar(ResourceKey<? extends Registry<T>> registry, String modId) {
        this.provider = RegistrationProvider.get(registry, modId);
    }

    @Override
    public <R extends T> Supplier<R> register(String name, Supplier<? extends R> factory) {
        return provider.register(name, factory);
    }
}
