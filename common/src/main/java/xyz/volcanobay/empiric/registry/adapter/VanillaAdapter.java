package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.platform.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class VanillaAdapter<T> implements RegistrationAdapter<T> {

    private static final Map<String, Class<?>> TYPED = new HashMap<>();

    private final ResourceKey<? extends Registry<T>> key;
    private final Class<T> valueType;
    private final Registry<T> direct;
    private final boolean byType;

    public VanillaAdapter(ResourceKey<? extends Registry<T>> key, Class<T> valueType) {
        this(key, valueType, null);
    }

    /**
     * Registers straight into a registry that was created for it, so its entries don't wait for a registry event.
     */
    public VanillaAdapter(ResourceKey<? extends Registry<T>> key, Class<T> valueType, Registry<T> direct) {
        this(key, valueType, direct, true);
    }

    private VanillaAdapter(ResourceKey<? extends Registry<T>> key, Class<T> valueType, Registry<T> direct, boolean byType) {
        this.key = key;
        this.valueType = valueType;
        this.direct = direct;
        this.byType = byType;
    }

    /**
     * An adapter that routes values of this type to the registry on its own, so entries don't have to name it.
     */
    public static <T> VanillaAdapter<T> typed(ResourceKey<? extends Registry<T>> key, Class<?> valueType) {
        TYPED.put(key.location().toString(), valueType);
        return new VanillaAdapter<>(key, generic(valueType));
    }

    /**
     * An adapter for any registry, which only takes entries that name it with {@code registry = "..."}. It holds the
     * value type of the typed adapter for that registry if there is one.
     */
    public static VanillaAdapter<Object> named(ResourceKey<? extends Registry<?>> key) {
        return named(key, null);
    }

    /**
     * Like {@link #named(ResourceKey)}, and registers straight into {@code direct}, which is the registry the key names.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static VanillaAdapter<Object> named(ResourceKey<? extends Registry<?>> key, Registry<?> direct) {
        Class<?> type = TYPED.getOrDefault(key.location().toString(), Object.class);
        return new VanillaAdapter<>((ResourceKey) key, (Class) type, (Registry) direct, false);
    }

    /**
     * Checks that a value fits a registry that has entries. The base type is the topmost class above the first entry's
     * class that isn't {@link Object}. Registries that are empty, or whose first entry is an anonymous, hidden or
     * synthetic class, can't be checked and always pass. Only clear mismatches fail, since entries can be different
     * classes behind one interface and a field's declared type can be wider than its value.
     *
     * @throws IllegalStateException if {@code valueClass} isn't that base type or a subclass of it
     */
    public static void checkValueType(String what, Class<?> valueClass, Registry<?> registry) {
        if (registry == null) {
            return;
        }
        Object first = null;
        for (Object value : registry) {
            first = value;
            break;
        }
        if (first == null) {
            return;
        }
        Class<?> sample = first.getClass();
        if (sample.isAnonymousClass() || sample.isHidden() || sample.isSynthetic()) {
            return;
        }
        Class<?> base = sample;
        while (base.getSuperclass() != null && base.getSuperclass() != Object.class) {
            base = base.getSuperclass();
        }
        if (!base.isAssignableFrom(valueClass) && !valueClass.isAssignableFrom(sample) && !sharesInterface(sample, valueClass)) {
            throw new IllegalStateException(what + " is a " + valueClass.getName() + ", but the registry " + registry.key().location() + " holds " + base.getName() + " values");
        }
    }

    private static boolean sharesInterface(Class<?> sample, Class<?> valueClass) {
        for (Class<?> type = sample; type != null; type = type.getSuperclass()) {
            for (Class<?> face : type.getInterfaces()) {
                if (!face.getName().startsWith("java.") && face.isAssignableFrom(valueClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> generic(Class<?> type) {
        return (Class<T>) type;
    }

    @Override
    public String registry() {
        return key.location().toString();
    }

    @Override
    public Class<T> valueType() {
        return valueType;
    }

    @Override
    public boolean routesByType() {
        return byType;
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<T> value) {
        registerValue(entry, value);
    }

    protected Supplier<T> registerValue(RegistrationEntry entry, Supplier<T> value) {
        if (!byType) {
            checkValueType(entry.id(), entry.sourceClass(), direct != null ? direct : BuiltInRegistries.REGISTRY.get(key.location()));
        }
        if (direct != null) {
            T created = Registry.register(direct, ResourceLocation.parse(entry.id()), value.get());
            return () -> created;
        }
        return Platform.registrar(key, entry.namespace()).register(entry.path(), value);
    }
}
