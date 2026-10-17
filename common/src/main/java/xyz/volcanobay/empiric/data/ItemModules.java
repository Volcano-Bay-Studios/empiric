package xyz.volcanobay.empiric.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.nio.ByteBuffer;
import java.util.Arrays;

public record ItemModules(byte[] data) {

    public static final Codec<ItemModules> CODEC = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new ItemModules(bytes);
    }, modules -> ByteBuffer.wrap(modules.data));
    public static final StreamCodec<ByteBuf, ItemModules> STREAM_CODEC = ByteBufCodecs.BYTE_ARRAY.map(ItemModules::new, ItemModules::data);

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemModules modules && Arrays.equals(data, modules.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "ItemModules[" + data.length + " bytes]";
    }
}
