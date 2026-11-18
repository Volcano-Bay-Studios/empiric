package xyz.volcanobay.empiric.platform;

import foundry.veil.Veil;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.platform.veil.VeilClientHooks;
import xyz.volcanobay.empiric.platform.veil.VeilPacketRegistrar;
import xyz.volcanobay.empiric.platform.veil.VeilRegistrar;

import java.util.HashMap;
import java.util.Map;

/**
 * The loader services Empiric uses. Registries, packets, client render hooks and the environment. Everything here uses
 * Veil for now, and this class and {@code platform.veil} are the only places that know it. Moving off Veil
 * means swapping these for loader code.
 */
public final class Platform {

    private Platform() {
    }

    private static final Map<String, Registrar<?>> REGISTRARS = new HashMap<>();

    public static <T> Registrar<T> registrar(ResourceKey<? extends Registry<T>> registry) {
        return registrar(registry, Empiric.MODID);
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> Registrar<T> registrar(ResourceKey<? extends Registry<T>> registry, String namespace) {
        return (Registrar<T>) REGISTRARS.computeIfAbsent(registry.location() + "@" + namespace, name -> new VeilRegistrar<>(registry, namespace));
    }

    public static PacketRegistrar packets(String version) {
        return new VeilPacketRegistrar(Empiric.MODID, version);
    }

    /**
     * Hooks level rendering and the dev editors up to {@link xyz.volcanobay.empiric.EmpiricClient}.
     */
    public static void initClient() {
        VeilClientHooks.register();
    }

    public static boolean isDevelopment() {
        return Veil.platform().isDevelopmentEnvironment();
    }
}
