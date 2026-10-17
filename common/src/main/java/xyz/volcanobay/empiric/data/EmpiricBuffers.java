package xyz.volcanobay.empiric.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import xyz.volcanobay.empiric.core.data.DataBuffer;

public final class EmpiricBuffers {

    private static volatile RegistryAccess registries;
    private static RegistryAccess fallback;

    private EmpiricBuffers() {
    }

    public static void registries(RegistryAccess access) {
        registries = access;
    }

    public static RegistryAccess registries() {
        RegistryAccess access = registries;
        if (access != null) {
            return access;
        }
        if (fallback == null) {
            fallback = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }
        return fallback;
    }

    public static DataBuffer of(FriendlyByteBuf buffer) {
        return (DataBuffer) buffer;
    }

    public static RegistryFriendlyByteBuf create() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
    }

    public static RegistryFriendlyByteBuf create(byte[] bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(bytes.length).writeBytes(bytes), registries());
    }

    public static DataBuffer buffer(byte[] bytes) {
        return of(create(bytes));
    }

    public static RegistryFriendlyByteBuf registryBuffer(DataBuffer buffer) {
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
            return registryBuffer;
        }
        if (buffer instanceof ByteBuf byteBuf) {
            return new RegistryFriendlyByteBuf(byteBuf, registries());
        }
        return null;
    }

    public static byte[] bytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
