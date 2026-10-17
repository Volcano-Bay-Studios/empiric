package xyz.volcanobay.empiric.mixin;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin implements DataBuffer {

    @Unique
    private FriendlyByteBuf empiric$self() {
        return (FriendlyByteBuf) (Object) this;
    }

    @Override
    public void putByte(int value) {
        empiric$self().writeByte(value);
    }

    @Override
    public byte takeByte() {
        return empiric$self().readByte();
    }

    @Override
    public void putShort(int value) {
        empiric$self().writeShort(value);
    }

    @Override
    public short takeShort() {
        return empiric$self().readShort();
    }

    @Override
    public void putInt(int value) {
        empiric$self().writeInt(value);
    }

    @Override
    public int takeInt() {
        return empiric$self().readInt();
    }

    @Override
    public void putLong(long value) {
        empiric$self().writeLong(value);
    }

    @Override
    public long takeLong() {
        return empiric$self().readLong();
    }

    @Override
    public void putFloat(float value) {
        empiric$self().writeFloat(value);
    }

    @Override
    public float takeFloat() {
        return empiric$self().readFloat();
    }

    @Override
    public void putDouble(double value) {
        empiric$self().writeDouble(value);
    }

    @Override
    public double takeDouble() {
        return empiric$self().readDouble();
    }

    @Override
    public void putBytes(byte[] bytes, int offset, int length) {
        empiric$self().writeBytes(bytes, offset, length);
    }

    @Override
    public void takeBytes(byte[] target, int offset, int length) {
        empiric$self().readBytes(target, offset, length);
    }

    @Override
    public void putVarInt(int value) {
        empiric$self().writeVarInt(value);
    }

    @Override
    public int takeVarInt() {
        return empiric$self().readVarInt();
    }

    @Override
    public void putVarLong(long value) {
        empiric$self().writeVarLong(value);
    }

    @Override
    public long takeVarLong() {
        return empiric$self().readVarLong();
    }

    @Override
    public int writePosition() {
        return empiric$self().writerIndex();
    }

    @Override
    public void patchInt(int position, int value) {
        empiric$self().setInt(position, value);
    }

    @Override
    public int readable() {
        return empiric$self().readableBytes();
    }

    @Override
    public void skip(int length) {
        empiric$self().skipBytes(length);
    }

    @Override
    public DataBuffer wrap(byte[] bytes) {
        FriendlyByteBuf self = empiric$self();
        if (self instanceof RegistryFriendlyByteBuf registryBuffer) {
            return (DataBuffer) new RegistryFriendlyByteBuf(Unpooled.buffer(bytes.length).writeBytes(bytes), registryBuffer.registryAccess());
        }
        return (DataBuffer) new FriendlyByteBuf(Unpooled.buffer(bytes.length).writeBytes(bytes));
    }
}
