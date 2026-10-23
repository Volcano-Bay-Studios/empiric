package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.data.EmpiricBuffers;

import java.util.BitSet;

/**
 * Tells a client to reset synced fields of a module to their defaults, when the server stops tracking them.
 */
public record ModuleClearS2CPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModuleClearS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("module_clear"));
    public static final StreamCodec<ByteBuf, ModuleClearS2CPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(ModuleClearS2CPacket::new, ModuleClearS2CPacket::data);

    public static ModuleClearS2CPacket of(Module<?> module, BitSet fields) {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        DataBuffer data = EmpiricBuffers.of(buffer);
        modules.writeLocator(data, module, modules.specs().context(Encoding.PACKED));
        data.putVarInt(fields.cardinality());
        for (int i = fields.nextSetBit(0); i >= 0; i = fields.nextSetBit(i + 1)) {
            data.putVarInt(i);
        }
        return new ModuleClearS2CPacket(EmpiricBuffers.bytes(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
