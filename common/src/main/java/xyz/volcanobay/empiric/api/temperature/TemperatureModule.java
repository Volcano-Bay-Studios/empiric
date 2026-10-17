package xyz.volcanobay.empiric.api.temperature;

import foundry.veil.api.network.VeilPacketManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.behavior.ServerTickBehavior;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.networking.packet.TemperatureSectionS2CPacket;
import xyz.volcanobay.empiric.oct.FullOctBlock;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.util.Arrays;

public class TemperatureModule extends Module<ChunkAttachment> implements ServerTickBehavior, NetworkBehavior {

    public static final int SIZE = 32;
    public static final int CELLS = SIZE * SIZE * SIZE;
    public static final short LIMIT = 256;
    private static final int SYNC_INTERVAL = 10;

    @Data(sync = false)
    public final Int2ObjectOpenHashMap<short[]> sections = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<byte[]> conductivity = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<long[]> active = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet changed = new IntOpenHashSet();
    private int syncTimer;

    public static int index(int x, int y, int z) {
        return (x << 10) | (y << 5) | z;
    }

    public Level level() {
        return attachment().level();
    }

    public LevelChunk chunk() {
        return attachment().chunk();
    }

    public short get(int section, int index) {
        short[] values = sections.get(section);
        return values == null ? 0 : values[index];
    }

    public void set(int section, int index, int value) {
        short clamped = (short) Mth.clamp(value, 0, LIMIT);
        short[] values = sections.get(section);
        if (values == null) {
            if (clamped == 0) {
                return;
            }
            values = new short[CELLS];
            sections.put(section, values);
        }
        if (values[index] != clamped) {
            values[index] = clamped;
            changed.add(section);
            if (clamped > 0) {
                mark(section, index);
            }
        }
    }

    public long[] active(int section) {
        long[] mask = active.get(section);
        if (mask == null) {
            mask = new long[CELLS >> 6];
            short[] values = sections.get(section);
            if (values != null) {
                for (int i = 0; i < CELLS; i++) {
                    if (values[i] > 0) {
                        mask[i >> 6] |= 1L << i;
                    }
                }
            }
            active.put(section, mask);
        }
        return mask;
    }

    public void mark(int section, int index) {
        active(section)[index >> 6] |= 1L << index;
    }

    public void touch(int section) {
        changed.add(section);
    }

    public void removeSection(int section) {
        sections.remove(section);
        active.remove(section);
        changed.add(section);
    }

    public void apply(int section, byte[] data) {
        active.remove(section);
        if (data.length == 0) {
            sections.remove(section);
            return;
        }
        sections.put(section, decode(data));
    }

    public static byte[] encode(short[] values) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            int i = 0;
            while (i < values.length) {
                short value = values[i];
                int run = 1;
                while (i + run < values.length && values[i + run] == value) {
                    run++;
                }
                buffer.writeVarInt(run);
                buffer.writeVarInt(value);
                i += run;
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    public static short[] decode(byte[] data) {
        short[] values = new short[CELLS];
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        int i = 0;
        while (buffer.isReadable()) {
            int run = buffer.readVarInt();
            short value = (short) Mth.clamp(buffer.readVarInt(), 0, LIMIT);
            if (run <= 0 || i + run > CELLS) {
                throw new IllegalArgumentException("Temperature run of " + run + " at " + i + " doesn't fit in a section");
            }
            Arrays.fill(values, i, i + run, value);
            i += run;
        }
        return values;
    }

    public byte[] conductivity(int section) {
        byte[] values = conductivity.get(section);
        if (values == null) {
            values = new byte[CELLS];
            int minY = (level().getMinSection() + section) * 16;
            int baseX = chunk().getPos().getMinBlockX();
            int baseZ = chunk().getPos().getMinBlockZ();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        pos.set(baseX + x, minY + y, baseZ + z);
                        fillBlock(values, pos, x, y, z);
                    }
                }
            }
            conductivity.put(section, values);
        }
        return values;
    }

    private void fillBlock(byte[] values, BlockPos pos, int x, int y, int z) {
        BlockState state = chunk().getBlockState(pos);
        FullOctBlockEntity octs = null;
        if (state.getBlock() instanceof FullOctBlock) {
            BlockEntity blockEntity = chunk().getBlockEntity(pos);
            if (blockEntity instanceof FullOctBlockEntity octBlockEntity) {
                octs = octBlockEntity;
            }
        }
        for (int octant = 0; octant < 8; octant++) {
            BlockState octState = octs != null ? octs.getOctState(octant) : state;
            int ox = x * 2 + (octant & 1);
            int oy = y * 2 + ((octant >> 1) & 1);
            int oz = z * 2 + ((octant >> 2) & 1);
            values[index(ox, oy, oz)] = TemperatureSystem.conductivityOf(octState);
        }
    }

    public void refresh(BlockPos pos) {
        int section = level().getSectionIndex(pos.getY());
        byte[] values = conductivity.get(section);
        if (values == null) {
            return;
        }
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        fillBlock(values, pos, x, y, z);
        clearAir(section, values, x, y, z);
    }

    private void clearAir(int section, byte[] values, int x, int y, int z) {
        for (int octant = 0; octant < 8; octant++) {
            int index = index(x * 2 + (octant & 1), y * 2 + ((octant >> 1) & 1), z * 2 + ((octant >> 2) & 1));
            if (values[index] < 0) {
                set(section, index, 0);
            }
        }
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    @Override
    public void serverTick() {
        TemperatureSystem.propagate(this);
        if (!changed.isEmpty() && ++syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            VeilPacketManager.PacketSink sink = VeilPacketManager.tracking((ServerLevel) level(), chunk().getPos());
            for (int section : changed) {
                sink.sendPacket(packet(section));
                TemperatureSystem.sectionChanged(this, section);
            }
            changed.clear();
        }
    }

    private TemperatureSectionS2CPacket packet(int section) {
        short[] values = sections.get(section);
        return new TemperatureSectionS2CPacket(chunk().getPos(), section, values == null ? new byte[0] : encode(values));
    }

    @Override
    public void onTracked(VeilPacketManager.PacketSink sink) {
        for (int section : sections.keySet()) {
            sink.sendPacket(packet(section));
        }
    }

    @Override
    protected void onUnload() {
        conductivity.clear();
        active.clear();
    }
}
