package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.data.EmpiricBuffers;

public record ModuleRemoveS2CPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModuleRemoveS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("module_remove"));
    public static final StreamCodec<ByteBuf, ModuleRemoveS2CPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(ModuleRemoveS2CPacket::new, ModuleRemoveS2CPacket::data);

    public static ModuleRemoveS2CPacket of(Module<?> module) {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        modules.writeLocator(EmpiricBuffers.of(buffer), module, modules.specs().context(Encoding.PACKED));
        return new ModuleRemoveS2CPacket(EmpiricBuffers.bytes(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
