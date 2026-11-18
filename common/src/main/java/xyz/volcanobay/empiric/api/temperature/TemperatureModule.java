package xyz.volcanobay.empiric.api.temperature;

import java.lang.ref.WeakReference;
import xyz.volcanobay.empiric.api.network.PacketTarget;
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
    /** Temperatures are whole tenths of a degree Celsius, so 245 is 24.5 C. */
    public static final int MIN = -2731;
    public static final int MAX = Short.MAX_VALUE;
    private static final int SYNC_INTERVAL = 10;

    @Data(sync = false)
    public final Int2ObjectOpenHashMap<short[]> sections = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<byte[]> conductivity = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<long[]> active = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Border> borders = new Int2ObjectOpenHashMap<>();
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

    /**
     * Cells hold the difference from the dimension's ambient temperature, so an untouched cell (0) is always at ambient.
     */
    public int ambient() {
        return TemperatureSystem.ambient(level());
    }

    public static String format(int temperature) {
        return String.format("%.1f C", temperature / 10f);
    }

    public short get(int section, int index) {
        short[] values = sections.get(section);
        return values == null ? 0 : values[index];
    }

    public void set(int section, int index, int value) {
        int ambient = ambient();
        short clamped = (short) Mth.clamp(value, Math.max(MIN - ambient, Short.MIN_VALUE), Math.min(MAX - ambient, Short.MAX_VALUE));
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
            if (clamped != 0) {
                mark(section, index);
            }
        }
    }

    public short[] values(int section, boolean create) {
        short[] values = sections.get(section);
        if (values == null && create) {
            values = new short[CELLS];
            sections.put(section, values);
        }
        return values;
    }

    public long[] active(int section) {
        long[] mask = active.get(section);
        if (mask == null) {
            mask = new long[CELLS >> 6];
            short[] values = sections.get(section);
            if (values != null) {
                for (int i = 0; i < CELLS; i++) {
                    if (values[i] != 0) {
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

    /**
     * Whether the section changed since the server last sent it to players.
     */
    public boolean isChanged(int section) {
        return changed.contains(section);
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
                buffer.writeVarInt((value << 1) ^ (value >> 31));
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
            int zigzag = buffer.readVarInt();
            short value = (short) Mth.clamp((zigzag >>> 1) ^ -(zigzag & 1), Short.MIN_VALUE, Short.MAX_VALUE);
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
        FullOctBlockEntity octs = octs(chunk(), pos, state);
        for (int octant = 0; octant < 8; octant++) {
            int ox = x * 2 + (octant & 1);
            int oy = y * 2 + ((octant >> 1) & 1);
            int oz = z * 2 + ((octant >> 2) & 1);
            values[index(ox, oy, oz)] = octConductivity(state, octs, octant);
        }
    }

    private static FullOctBlockEntity octs(LevelChunk chunk, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FullOctBlock) {
            BlockEntity blockEntity = chunk.getBlockEntity(pos);
            if (blockEntity instanceof FullOctBlockEntity octBlockEntity) {
                return octBlockEntity;
            }
        }
        return null;
    }

    private static byte octConductivity(BlockState state, FullOctBlockEntity octs, int octant) {
        return TemperatureSystem.conductivityOf(octs != null ? octs.getOctState(octant) : state);
    }

    /**
     * The conductivity of the neighbouring chunk's cells that touch this chunk's section on a horizontal face (2 is +Z, 3 is -Z, 4 is +X, 5 is -X).
     * X faces are indexed by {@code y * 32 + z} and Z faces by {@code x * 32 + y}, with the coordinates local to this section.
     */
    public byte[] border(int section, int face, LevelChunk neighbour) {
        int key = section * 4 + face - 2;
        Border border = borders.get(key);
        if (border == null || border.chunk.get() != neighbour) {
            border = new Border(neighbour, buildBorder(section, face, neighbour));
            borders.put(key, border);
        }
        return border.values;
    }

    public void invalidateBorder(int section, int face) {
        borders.remove(section * 4 + face - 2);
    }

    private byte[] buildBorder(int section, int face, LevelChunk neighbour) {
        byte[] values = new byte[SIZE * SIZE];
        int minY = (level().getMinSection() + section) * 16;
        boolean alongX = face >= 4;
        int fixed = alongX ? (face == 4 ? neighbour.getPos().getMinBlockX() : neighbour.getPos().getMaxBlockX()) : (face == 2 ? neighbour.getPos().getMinBlockZ() : neighbour.getPos().getMaxBlockZ());
        int touching = face == 4 || face == 2 ? 0 : 1;
        int base = alongX ? neighbour.getPos().getMinBlockZ() : neighbour.getPos().getMinBlockX();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int along = 0; along < 16; along++) {
            for (int y = 0; y < 16; y++) {
                if (alongX) {
                    pos.set(fixed, minY + y, base + along);
                } else {
                    pos.set(base + along, minY + y, fixed);
                }
                BlockState state = neighbour.getBlockState(pos);
                FullOctBlockEntity octs = octs(neighbour, pos, state);
                for (int other = 0; other < 2; other++) {
                    for (int oy = 0; oy < 2; oy++) {
                        int octant = alongX ? touching | (oy << 1) | (other << 2) : other | (oy << 1) | (touching << 2);
                        int cell = alongX ? (y * 2 + oy) * SIZE + along * 2 + other : (along * 2 + other) * SIZE + y * 2 + oy;
                        values[cell] = octConductivity(state, octs, octant);
                    }
                }
            }
        }
        return values;
    }

    private static final class Border {

        final WeakReference<LevelChunk> chunk;
        final byte[] values;

        Border(LevelChunk chunk, byte[] values) {
            this.chunk = new WeakReference<>(chunk);
            this.values = values;
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
            PacketTarget sink = PacketTarget.tracking((ServerLevel) level(), chunk().getPos());
            for (int section : changed) {
                sink.send(packet(section));
                TemperatureSystem.TEMPERATURE.fire(this, TemperatureSystem.SECTION_CHANGED, section);
            }
            changed.clear();
        }
    }

    private TemperatureSectionS2CPacket packet(int section) {
        short[] values = sections.get(section);
        return new TemperatureSectionS2CPacket(chunk().getPos(), section, values == null ? new byte[0] : encode(values));
    }

    @Override
    public void onTracked(PacketTarget sink) {
        for (int section : sections.keySet()) {
            sink.send(packet(section));
        }
    }

    @Override
    protected void onUnload() {
        conductivity.clear();
        active.clear();
        borders.clear();
    }
}
