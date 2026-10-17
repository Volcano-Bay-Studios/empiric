package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.data.EmpiricBuffers;

import java.util.BitSet;

public record ModuleSyncC2SPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModuleSyncC2SPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("module_sync_c2s"));
    public static final StreamCodec<ByteBuf, ModuleSyncC2SPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(ModuleSyncC2SPacket::new, ModuleSyncC2SPacket::data);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ModuleSyncC2SPacket of(Module<?> module, BitSet dirty) {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        DataBuffer data = EmpiricBuffers.of(buffer);
        DataContext context = modules.specs().context(Encoding.PACKED);
        modules.writeLocator(data, module, context);
        ObjectSpec spec = module.type().spec();
        spec.writeFramed(data, module, dirty, context);
        return new ModuleSyncC2SPacket(EmpiricBuffers.bytes(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
