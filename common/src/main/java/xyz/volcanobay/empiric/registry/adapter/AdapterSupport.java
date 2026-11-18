package xyz.volcanobay.empiric.registry.adapter;

import xyz.volcanobay.empiric.core.data.Types;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Helpers for the vanilla adapters. Reflection is only used during startup registration.
 */
final class AdapterSupport {

    private AdapterSupport() {
    }

    static Class<?> instanceClass(RegistrationEntry entry) {
        if (entry.source() == RegistrationEntry.Kind.CLASS) {
            return entry.sourceClass();
        }
        if (entry.source() == RegistrationEntry.Kind.REF_FIELD) {
            return Types.raw(Types.argument(entry.targetType(), 0));
        }
        return null;
    }

    static void requireNamed(Class<?> instanceClass, RegistrationEntry entry, String typeName) {
        if (instanceClass == Object.class) {
            throw new IllegalStateException(entry.id() + " is a Ref whose type argument doesn't name the class to build, write it like Ref<" + typeName + "<MyClass>>");
        }
    }

    static MethodHandle handle(Constructor<?> constructor) {
        try {
            constructor.setAccessible(true);
            return MethodHandles.lookup().unreflectConstructor(constructor);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Can't access " + constructor, e);
        }
    }

    @SuppressWarnings("unchecked")
    static <I> I factory(Class<I> type, MethodType method, Constructor<?> constructor) {
        try {
            MethodHandle target = handle(constructor);
            CallSite site = LambdaMetafactory.metafactory(MethodHandles.lookup(), "create", MethodType.methodType(type), method, target, target.type());
            return (I) site.getTarget().invoke();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static RuntimeException rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(throwable);
    }

    record Generic(Class<?> raw, Type... arguments) implements ParameterizedType {

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Generic generic && raw == generic.raw && Arrays.equals(arguments, generic.arguments);
        }

        @Override
        public int hashCode() {
            return raw.hashCode() * 31 + Arrays.hashCode(arguments);
        }

        @Override
        public String toString() {
            return raw.getName() + Arrays.toString(arguments);
        }
    }
}
