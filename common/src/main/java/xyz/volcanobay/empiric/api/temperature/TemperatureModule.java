package xyz.volcanobay.empiric.api.temperature;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.module.SaveHook;
import xyz.volcanobay.empiric.api.network.PacketTarget;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.networking.packet.TemperatureSectionS2CPacket;
import xyz.volcanobay.empiric.oct.FullOctBlock;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public class TemperatureModule extends Module<ChunkAttachment> implements NetworkBehavior, SaveHook {

    public static final int SIZE = 32;
    public static final int CELLS = SIZE * SIZE * SIZE;
    /**
     * The whole number temperature getters work in tenths of a degree Celsius, so 245 is 24.5 C.
     */
    public static final int MIN = -2731;
    public static final int MAX = Short.MAX_VALUE;
    public static final double ZERO_CELSIUS = 273.15;
    public static final double MAX_KELVIN = 100_000;
    private static final int SYNC_INTERVAL = 10;
    private static final int WORDS = CELLS >> 6;
    // plain arrays, for the hot loops
    private static final int[] FACE_X = {0, 0, 0, 0, 1, -1};
    private static final int[] FACE_Y = {1, -1, 0, 0, 0, 0};
    private static final int[] FACE_Z = {0, 0, 1, -1, 0, 0};

    /**
     * The temperature of every cell in K, by section. A section that isn't here is entirely at the level's resting temperature.
     */
    public final Int2ObjectOpenHashMap<double[]> sections = new Int2ObjectOpenHashMap<>();
    /**
     * The saved form of {@link #sections}: the difference from the resting temperature in K, by section, rounded to a float. It only holds data while the module is being saved or loaded.
     */
    @Data(sync = false, id = "deltas")
    private final Int2ObjectOpenHashMap<float[]> saved = new Int2ObjectOpenHashMap<>();
    /**
     * Saves from before temperatures were stored in kelvin. They are tenths of a degree from ambient, and are converted when the module loads.
     */
    @Data(sync = false, id = "sections")
    public final Int2ObjectOpenHashMap<short[]> legacy = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<byte[]> materials = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<long[]> materialSets = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<long[]> active = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Border> borders = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet changed = new IntOpenHashSet();
    private final IntOpenHashSet idle = new IntOpenHashSet();
    private int syncTimer;
    private int materialGeneration = -1;

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
     * The dimension's ambient temperature in tenths of a degree Celsius.
     */
    public int ambient() {
        return TemperatureSystem.ambient(level());
    }

    public double ambientKelvin() {
        return TemperatureSystem.ambientKelvin(level());
    }

    /**
     * The temperature that cells without heat sit at, see {@link TemperatureSystem#restingKelvin}.
     */
    public double restingKelvin() {
        return TemperatureSystem.restingKelvin(level());
    }

    public static String format(int temperature) {
        return String.format("%.1f C", temperature / 10f);
    }

    /**
     * Rounds kelvin to whole tenths of a degree Celsius, within {@link #MIN} and {@link #MAX}.
     */
    public static int tenths(double kelvin) {
        return (int) Mth.clamp(Math.round((kelvin - ZERO_CELSIUS) * 10), MIN, MAX);
    }

    public double kelvin(int section, int index) {
        double[] values = sections.get(section);
        return values == null ? restingKelvin() : values[index];
    }

    /**
     * The cell's difference from the level's resting temperature in tenths of a degree, 0 if the section holds no heat yet. It saturates at the range of a short.
     */
    public short get(int section, int index) {
        double[] values = sections.get(section);
        return values == null ? 0 : delta(values[index], restingKelvin());
    }

    public static short delta(double kelvin, double restingKelvin) {
        return (short) Mth.clamp(deltaTenths(kelvin, restingKelvin), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    /**
     * The difference between two temperatures in whole tenths of a degree, with no range limit short of the temperature limits.
     */
    public static int deltaTenths(double kelvin, double restingKelvin) {
        return (int) Math.round((kelvin - restingKelvin) * 10);
    }

    /**
     * Sets the difference from the resting temperature in tenths of a degree. Cells that hold no heat, like air, ignore it.
     */
    public void set(int section, int index, int delta) {
        setKelvin(section, index, restingKelvin() + delta / 10.0);
    }

    /**
     * Sets a cell's temperature, between absolute zero and {@link #MAX_KELVIN}. The section is marked changed and the cell and the cells around it are woken.
     * A cell that holds no heat, like air, ignores it.
     */
    public void setKelvin(int section, int index, double kelvin) {
        if (Double.isNaN(kelvin) || materials(section)[index] == 0) {
            return;
        }
        double clamped = Mth.clamp(kelvin, 0, MAX_KELVIN);
        double[] values = sections.get(section);
        if (values == null) {
            if (clamped == restingKelvin()) {
                return;
            }
            values = values(section, true);
        }
        if (values[index] != clamped) {
            values[index] = clamped;
            changed.add(section);
            wake(section, index);
        }
    }

    /**
     * Adds heat to a cell in J. A cell that holds no heat, like air, ignores it.
     */
    public void addHeat(int section, int index, double joules) {
        int material = materials(section)[index] & 0xFF;
        if (material == 0 || joules == 0) {
            return;
        }
        setKelvin(section, index, kelvin(section, index) + joules / ThermalMaterials.table().capacity[material]);
    }

    public double[] values(int section, boolean create) {
        double[] values = sections.get(section);
        if (values == null && create) {
            values = new double[CELLS];
            Arrays.fill(values, restingKelvin());
            sections.put(section, values);
            idle.remove(section);
        }
        return values;
    }

    /**
     * The cells of a section that the solver still has to look at, as a bit mask over the cell index. Cells of a section that has no mask yet start active when they differ from their material's resting temperature.
     */
    public long[] active(int section) {
        TemperatureSystem.guardCompute("TemperatureModule.active");
        long[] mask = active.get(section);
        if (mask == null) {
            mask = new long[WORDS];
            double[] values = sections.get(section);
            if (values != null) {
                double[] resting = TemperatureSystem.resting(level());
                byte[] materials = materials(section);
                double settle = TemperatureSystem.settleThreshold();
                for (int i = 0; i < CELLS; i++) {
                    if (materials[i] != 0 && Math.abs(values[i] - resting[materials[i] & 0xFF]) > settle) {
                        mask[i >> 6] |= 1L << i;
                    }
                }
            }
            active.put(section, mask);
        }
        return mask;
    }

    public long[] activeMask(int section) {
        return active.get(section);
    }

    public long[] swapActive(int section, long[] mask) {
        return active.put(section, mask);
    }

    public void mark(int section, int index) {
        active(section)[index >> 6] |= 1L << index;
        idle.remove(section);
    }

    /**
     * Marks the cell and its six neighbours as active, including the ones in other sections and chunks that already hold heat.
     */
    public void wake(int section, int index) {
        if (sections.containsKey(section)) {
            mark(section, index);
        }
        int x = (index >> 10) & 31;
        int y = (index >> 5) & 31;
        int z = index & 31;
        int count = level().getSectionsCount();
        for (int face = 0; face < 6; face++) {
            int nx = x + FACE_X[face];
            int ny = y + FACE_Y[face];
            int nz = z + FACE_Z[face];
            if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE && nz >= 0 && nz < SIZE) {
                if (sections.containsKey(section)) {
                    mark(section, index(nx, ny, nz));
                }
                continue;
            }
            int neighbour = index(Math.floorMod(nx, SIZE), Math.floorMod(ny, SIZE), Math.floorMod(nz, SIZE));
            if (face < 2) {
                int other = section + (face == 0 ? 1 : -1);
                if (other >= 0 && other < count) {
                    wakeCell(other, neighbour);
                }
                continue;
            }
            ChunkPos pos = chunk().getPos();
            TemperatureModule module = TemperatureSystem.module(level(), pos.x + FACE_X[face], pos.z + FACE_Z[face], false);
            if (module != null) {
                module.wakeCell(section, neighbour);
            }
        }
    }

    public void wakeCell(int section, int index) {
        if (sections.containsKey(section)) {
            mark(section, index);
        }
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
        idle.remove(section);
        changed.add(section);
    }

    /**
     * Removes the sections that have nothing left to do and sit within {@code settle} kelvin of the resting temperature of their materials everywhere.
     */
    public void retire(double settle) {
        double[] resting = TemperatureSystem.resting(level());
        for (int section : sections.keySet().toIntArray()) {
            long[] mask = active.get(section);
            if (mask != null && !isZero(mask)) {
                idle.remove(section);
                continue;
            }
            if (idle.contains(section)) {
                continue;
            }
            if (settled(sections.get(section), materials(section), resting, settle)) {
                removeSection(section);
            } else {
                idle.add(section);
            }
        }
    }

    public static boolean isZero(long[] mask) {
        for (long word : mask) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean settled(double[] values, byte[] materials, double[] resting, double settle) {
        for (int i = 0; i < values.length; i++) {
            if (materials[i] != 0 && Math.abs(values[i] - resting[materials[i] & 0xFF]) > settle) {
                return false;
            }
        }
        return true;
    }

    public void apply(int section, byte[] data) {
        active.remove(section);
        idle.remove(section);
        if (data.length == 0) {
            sections.remove(section);
            return;
        }
        int[] deltas = decode(data);
        double resting = restingKelvin();
        double[] values = new double[CELLS];
        for (int i = 0; i < CELLS; i++) {
            values[i] = resting + deltas[i] / 10.0;
        }
        sections.put(section, values);
    }

    /**
     * Run-length codes a section's differences from the resting temperature in tenths of a degree, each run as a count and a zigzag varint.
     */
    public static byte[] encode(int[] values) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            int i = 0;
            while (i < values.length) {
                int value = values[i];
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

    public static int[] decode(byte[] data) {
        int[] values = new int[CELLS];
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        int i = 0;
        while (buffer.isReadable()) {
            int run = buffer.readVarInt();
            int zigzag = buffer.readVarInt();
            int value = (zigzag >>> 1) ^ -(zigzag & 1);
            if (run <= 0 || i + run > CELLS) {
                throw new IllegalArgumentException("Temperature run of " + run + " at " + i + " doesn't fit in a section");
            }
            Arrays.fill(values, i, i + run, value);
            i += run;
        }
        return values;
    }

    private void checkGeneration() {
        int current = ThermalMaterials.generation();
        if (current != materialGeneration) {
            materialGeneration = current;
            materials.clear();
            materialSets.clear();
            borders.clear();
        }
    }

    /**
     * The material index of every cell of a section, see {@link ThermalMaterials}.
     */
    public byte[] materials(int section) {
        TemperatureSystem.guardCompute("TemperatureModule.materials");
        checkGeneration();
        byte[] values = materials.get(section);
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
            materials.put(section, values);
        }
        return values;
    }

    /**
     * Which materials occur in a section, as a bit set where bit {@code m} of word {@code m >> 6} is material {@code m}. Material 0 is never set.
     * It is kept until the section's materials change, and the array must not be changed.
     */
    public long[] materialSet(int section) {
        byte[] values = materials(section);
        long[] set = materialSets.get(section);
        if (set == null) {
            set = new long[4];
            for (byte value : values) {
                int material = value & 0xFF;
                set[material >> 6] |= 1L << material;
            }
            set[0] &= ~1L;
            materialSets.put(section, set);
        }
        return set;
    }

    private void fillBlock(byte[] values, BlockPos pos, int x, int y, int z) {
        BlockState state = chunk().getBlockState(pos);
        FullOctBlockEntity octs = octs(chunk(), pos, state);
        for (int octant = 0; octant < 8; octant++) {
            int ox = x * 2 + (octant & 1);
            int oy = y * 2 + ((octant >> 1) & 1);
            int oz = z * 2 + ((octant >> 2) & 1);
            values[index(ox, oy, oz)] = octMaterial(state, octs, octant);
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

    private static byte octMaterial(BlockState state, FullOctBlockEntity octs, int octant) {
        return (byte) ThermalMaterials.indexOf(octs != null ? octs.getOctState(octant) : state);
    }

    /**
     * The materials of the neighbouring chunk's cells that touch this chunk's section on a horizontal face (2 is +Z, 3 is -Z, 4 is +X, 5 is -X).
     * X faces are indexed by {@code y * 32 + z} and Z faces by {@code x * 32 + y}, with the coordinates local to this section.
     */
    public byte[] border(int section, int face, LevelChunk neighbour) {
        TemperatureSystem.guardCompute("TemperatureModule.border");
        checkGeneration();
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
                        values[cell] = octMaterial(state, octs, octant);
                    }
                }
            }
        }
        return values;
    }

    private record Border(WeakReference<LevelChunk> chunk, byte[] values) {

            Border(LevelChunk chunk, byte[] values) {
                this(new WeakReference<>(chunk), values);
            }
        }

    public void refresh(BlockPos pos) {
        int section = level().getSectionIndex(pos.getY());
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        checkGeneration();
        byte[] values = materials.get(section);
        if (values != null) {
            materialSets.remove(section);
            fillBlock(values, pos, x, y, z);
            clearAir(section, values, x, y, z);
        }
        for (int octant = 0; octant < 8; octant++) {
            wake(section, index(x * 2 + (octant & 1), y * 2 + ((octant >> 1) & 1), z * 2 + ((octant >> 2) & 1)));
        }
    }

    /**
     * Wakes the cells of this chunk that touch a block of the neighbouring chunk on the given face (2 is +Z, 3 is -Z, 4 is +X, 5 is -X).
     */
    public void wakeBorder(BlockPos pos, int face) {
        int section = level().getSectionIndex(pos.getY());
        if (!sections.containsKey(section)) {
            return;
        }
        int baseY = (pos.getY() & 15) * 2;
        for (int along = 0; along < 2; along++) {
            for (int up = 0; up < 2; up++) {
                int index = switch (face) {
                    case 4 -> index(31, baseY + up, (pos.getZ() & 15) * 2 + along);
                    case 5 -> index(0, baseY + up, (pos.getZ() & 15) * 2 + along);
                    case 2 -> index((pos.getX() & 15) * 2 + along, baseY + up, 31);
                    default -> index((pos.getX() & 15) * 2 + along, baseY + up, 0);
                };
                mark(section, index);
            }
        }
    }

    private void clearAir(int section, byte[] values, int x, int y, int z) {
        for (int octant = 0; octant < 8; octant++) {
            int index = index(x * 2 + (octant & 1), y * 2 + ((octant >> 1) & 1), z * 2 + ((octant >> 2) & 1));
            if (values[index] == 0) {
                clearCell(section, index);
            }
        }
    }

    private void clearCell(int section, int index) {
        double[] cells = sections.get(section);
        double resting = restingKelvin();
        if (cells != null && cells[index] != resting) {
            cells[index] = resting;
            changed.add(section);
        }
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    @VisibleForTesting
    public void flushSync() {
        if (changed.isEmpty() || ++syncTimer < SYNC_INTERVAL) {
            return;
        }
        syncTimer = 0;
        ServerLevel level = (ServerLevel) level();
        ChunkPos pos = chunk().getPos();
        boolean watched = !level.getChunkSource().chunkMap.getPlayers(pos, false).isEmpty();
        PacketTarget sink = watched ? PacketTarget.tracking(level, pos) : null;
        for (int section : changed) {
            if (sink != null) {
                sink.send(packet(section));
            }
            TemperatureSystem.TEMPERATURE.fire(this, TemperatureSystem.SECTION_CHANGED, section);
        }
        changed.clear();
    }

    @VisibleForTesting
    public TemperatureSectionS2CPacket packet(int section) {
        double[] values = sections.get(section);
        if (values == null) {
            return new TemperatureSectionS2CPacket(chunk().getPos(), section, new byte[0]);
        }
        double resting = restingKelvin();
        int[] deltas = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            deltas[i] = deltaTenths(values[i], resting);
        }
        return new TemperatureSectionS2CPacket(chunk().getPos(), section, encode(deltas));
    }

    @Override
    public void onTracked(PacketTarget sink) {
        for (int section : sections.keySet()) {
            sink.send(packet(section));
        }
    }

    @Override
    protected void onAttach() {
        TemperatureSystem.register(this);
    }

    /**
     * Fills the saved form of the sections, just before the module is written.
     */
    @Override
    public void beforeSave() {
        double resting = restingKelvin();
        saved.clear();
        for (Int2ObjectMap.Entry<double[]> entry : sections.int2ObjectEntrySet()) {
            double[] values = entry.getValue();
            float[] deltas = new float[CELLS];
            for (int i = 0; i < CELLS; i++) {
                deltas[i] = (float) (values[i] - resting);
            }
            saved.put(entry.getIntKey(), deltas);
        }
    }

    @Override
    public void afterSave() {
        saved.clear();
    }

    @Override
    protected void onLoad() {
        TemperatureSystem.register(this);
        double resting = restingKelvin();
        for (Int2ObjectMap.Entry<float[]> entry : saved.int2ObjectEntrySet()) {
            float[] deltas = entry.getValue();
            double[] values = new double[CELLS];
            for (int i = 0; i < CELLS; i++) {
                values[i] = Mth.clamp(resting + deltas[i], 0, MAX_KELVIN);
            }
            sections.put(entry.getIntKey(), values);
        }
        saved.clear();
        for (Int2ObjectMap.Entry<short[]> entry : legacy.int2ObjectEntrySet()) {
            short[] deltas = entry.getValue();
            double[] values = new double[CELLS];
            for (int i = 0; i < CELLS; i++) {
                values[i] = resting + deltas[i] / 10.0;
            }
            sections.put(entry.getIntKey(), values);
        }
        legacy.clear();
    }

    @Override
    protected void onDetach() {
        TemperatureSystem.unregister(this);
    }

    @Override
    protected void onUnload() {
        TemperatureSystem.unregister(this);
        materials.clear();
        active.clear();
        borders.clear();
        idle.clear();
    }
}
