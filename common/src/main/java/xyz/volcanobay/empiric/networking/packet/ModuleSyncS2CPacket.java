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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public record ModuleSyncS2CPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModuleSyncS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("module_sync"));
    public static final StreamCodec<ByteBuf, ModuleSyncS2CPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(ModuleSyncS2CPacket::new, ModuleSyncS2CPacket::data);

    public static final int LIMIT = 900_000;

    public static List<ModuleSyncS2CPacket> full(Module<?> module) {
        return create(module, null);
    }

    public static List<ModuleSyncS2CPacket> dirty(Module<?> module, BitSet dirty) {
        return create(module, dirty);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ModuleSyncS2CPacket encode(Module<?> module, BitSet dirty) {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        DataBuffer data = EmpiricBuffers.of(buffer);
        DataContext context = modules.specs().context(Encoding.PACKED);
        modules.writeLocator(data, module, context);
        ObjectSpec spec = module.spec();
        data.putBoolean(dirty == null);
        if (dirty == null) {
            spec.write(data, module.data(), context);
        } else {
            spec.writeDirty(data, module.data(), dirty, context);
        }
        return new ModuleSyncS2CPacket(EmpiricBuffers.bytes(buffer));
    }

    private static List<ModuleSyncS2CPacket> create(Module<?> module, BitSet dirty) {
        ModuleSyncS2CPacket whole = encode(module, dirty);
        if (whole.data.length <= LIMIT) {
            return List.of(whole);
        }
        BitSet fields = dirty;
        if (fields == null) {
            fields = new BitSet();
            fields.set(0, module.spec().synced().length);
        }
        List<ModuleSyncS2CPacket> packets = new ArrayList<>();
        for (int i = fields.nextSetBit(0); i >= 0; i = fields.nextSetBit(i + 1)) {
            BitSet single = new BitSet();
            single.set(i);
            ModuleSyncS2CPacket packet = encode(module, single);
            if (packet.data.length > LIMIT) {
                Empiric.LOGGER.warn("Skipped syncing {}.{}, it is {} bytes which is over the {} byte packet limit", module.type().id(), module.spec().synced()[i].id(), packet.data.length, LIMIT);
                continue;
            }
            packets.add(packet);
        }
        return packets;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
