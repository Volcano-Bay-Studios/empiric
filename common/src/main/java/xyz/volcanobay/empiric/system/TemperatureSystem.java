package xyz.volcanobay.empiric.system;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.EmpiricApi;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.data.BlockDataType;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.temperature.*;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.module.ModuleEvent;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.registry.Register;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Heat in the blocks of a level, kept per oct in kelvin and moved by a conduction, convection and radiation solver in SI units.
 * Every oct is a cube of 0.5 m. Heat flows between touching octs that hold heat, and out of the faces that touch air or the edge of the world.
 * Each server tick moves the heat by {@code 0.05 s * timeScale} of physical time, in as many smaller steps as the solver needs to stay stable.
 */
public final class TemperatureSystem {

    @Register("temperature")
    public static final ChunkModuleType<TemperatureModule> TEMPERATURE = ChunkModuleType.builder(TemperatureModule.class, TemperatureModule::new).build();

    /**
     * Fired with the section index when a section's temperatures change: on the server when it sends them, and on the client when it receives them.
     */
    public static final ModuleEvent<Integer> SECTION_CHANGED = ModuleEvent.create(Empiric.id("temperature_section_changed")::toString);

    public static final BlockDataType<TemperatureBlockData> BLOCK_DATA = new BlockDataType<>(Empiric.id("temperature"), TemperatureBlockData.class, TemperatureBlockData::new);

    /**
     * Cells whose neighbours are within this many kelvin of them, and that have nothing else to do, stop being looked at until something changes.
     */
    public static final double SETTLE = 0.01;
    /**
     * The emissivity that {@link #restingKelvin(Level)} is worked out for. Sections that hold no heat, new sections and the synced and saved deltas
     * all use that one level value, which keeps lookups cheap. Cells of materials with another emissivity settle to their own resting temperature
     * ({@link #resting(Level)}) once the solver looks at them, which only differs from the level value when the sink and ambient temperatures differ and there is convection.
     */
    public static final double REST_EMISSIVITY = 0.9;
    private static final double DEFAULT_CONVECTION = 10;

    private static final Map<Level, Set<TemperatureModule>> MODULES = new WeakHashMap<>();
    private static boolean counting;
    private static long crossExchanges;
    private static ToIntFunction<Level> ambient = level -> AtmosphereSystem.levelAtmosphere(level).ambient();
    private static Function<Level, ThermalEnvironment> environment;
    private static ToDoubleFunction<Level> timeScale;
    private static double settle = SETTLE;
    private static double emissivityScale = 1;
    private static volatile Resting restingCache;

    private TemperatureSystem() {
    }

    @VisibleForTesting
    public static void startCountingCrossExchanges() {
        crossExchanges = 0;
        counting = true;
    }

    @VisibleForTesting
    public static long stopCountingCrossExchanges() {
        counting = false;
        return crossExchanges;
    }

    static void countCrossExchange() {
        if (counting) {
            crossExchanges++;
        }
    }

    /**
     * @deprecated registration is automatic, so this does nothing.
     */
    @Deprecated
    public static void register(ModuleSystem modules) {
    }

    public static void subscribe(String modId) {
        EmpiricApi.subscribe(modId, TEMPERATURE);
    }

    public static void unsubscribe(String modId) {
        EmpiricApi.unsubscribe(modId, TEMPERATURE);
    }

    /**
     * Overrides the resting temperature of a dimension in tenths of a degree Celsius (240 is 24 C), which otherwise comes from its level atmosphere entry. Cells that touch air drift back to it.
     * Must give the same answer on the server and the client. It has no effect on a level that has its own {@link #setEnvironment environment}.
     */
    public static void setAmbient(ToIntFunction<Level> temperature) {
        ambient = temperature;
    }

    /**
     * The ambient temperature of the level in tenths of a degree Celsius.
     */
    public static int ambient(Level level) {
        if (environment != null) {
            return Mth.clamp((int) Math.round((environment.apply(level).ambientKelvin() - TemperatureModule.ZERO_CELSIUS) * 10), TemperatureModule.MIN, TemperatureModule.MAX);
        }
        return Mth.clamp(ambient.applyAsInt(level), TemperatureModule.MIN, TemperatureModule.MAX);
    }

    /**
     * The ambient temperature of the level in K, the temperature of its air.
     */
    public static double ambientKelvin(Level level) {
        if (environment != null) {
            return environment.apply(level).ambientKelvin();
        }
        return Mth.clamp(ambient.applyAsInt(level), TemperatureModule.MIN, TemperatureModule.MAX) / 10.0 + TemperatureModule.ZERO_CELSIUS;
    }

    /**
     * What the outside of the level does to block surfaces. Unless it is replaced with {@link #setEnvironment}, this is the ambient temperature
     * with still air (10 W/(m2 K)) and a radiation sink at ambient.
     */
    public static ThermalEnvironment environment(Level level) {
        if (environment != null) {
            return environment.apply(level);
        }
        double kelvin = ambientKelvin(level);
        return new ThermalEnvironment(kelvin, DEFAULT_CONVECTION, kelvin);
    }

    /**
     * Replaces how a level's surroundings behave, for example a vacuum with {@code new ThermalEnvironment(ambient, 0, 3)}.
     * Must give the same answer on the server and the client. Pass null to go back to the default.
     */
    public static void setEnvironment(Function<Level, ThermalEnvironment> function) {
        environment = function;
    }

    /**
     * How many seconds of physical time pass each second of real time in the level. By default it comes from the config file, {@link TemperatureConfig},
     * as the level's dimension value or else the default value. Code can replace it with {@link #setTimeScale}, which wins over the config.
     */
    public static double timeScale(Level level) {
        double scale = timeScale != null ? timeScale.applyAsDouble(level) : TemperatureConfig.timeScale(level.dimension().location());
        return scale > 0 ? scale : 0;
    }

    /**
     * Replaces the time scale of every level with the function's value. Pass null to go back to the config file.
     */
    public static void setTimeScale(ToDoubleFunction<Level> scale) {
        timeScale = scale;
    }

    public static boolean isTimeScaleOverridden() {
        return timeScale != null;
    }

    /**
     * The temperature that cells without heat sit at, where a surface of emissivity {@link #REST_EMISSIVITY} balances convection and radiation in the level's
     * {@link #environment}. It is the ambient temperature when the sink equals ambient, and the sink temperature in a vacuum.
     */
    public static double restingKelvin(Level level) {
        return resting(level)[0];
    }

    /**
     * The resting temperature of every material of the {@link ThermalMaterials} table, in K, at the material's index. Index 0, for air, is {@link #restingKelvin}.
     * The array is shared and must not be changed.
     */
    public static double[] resting(Level level) {
        ThermalEnvironment current = environment(level);
        Resting cached = restingCache;
        int generation = ThermalMaterials.generation();
        if (cached != null && cached.generation == generation && cached.scale == emissivityScale && cached.environment.equals(current)) {
            return cached.values;
        }
        ThermalMaterials.Table table = ThermalMaterials.table();
        double[] values = new double[table.count];
        values[0] = current.restingKelvin(REST_EMISSIVITY * emissivityScale);
        for (int material = 1; material < table.count; material++) {
            values[material] = current.restingKelvin(table.emissivity[material] * emissivityScale);
        }
        restingCache = new Resting(current, emissivityScale, table.generation, values);
        return values;
    }

    private record Resting(ThermalEnvironment environment, double scale, int generation, double[] values) {
    }

    public static double settleThreshold() {
        return settle;
    }

    @VisibleForTesting
    public static void setSettleThreshold(double kelvin) {
        settle = kelvin;
    }

    /**
     * Whether faces that touch the air of a sealed room are left out of the open air surface loss, true by default. Off only for tests that compare the two.
     */
    @VisibleForTesting
    public static void setRoomAirExclusion(boolean enabled) {
        RoomAir.setEnabled(enabled);
    }

    public static double emissivityScale() {
        return emissivityScale;
    }

    /**
     * Multiplies the emissivity of every material, so 0 turns radiation off. Meant for tests that check conduction or convection on their own.
     */
    @VisibleForTesting
    public static void setEmissivityScale(double scale) {
        emissivityScale = scale;
    }

    public static void registerData(DataEntrySystem data) {
        BLOCK_DATA.register(data);
        BLOCK_DATA.entries().onReload(ThermalMaterials::rebuild);
    }

    public static void register(TemperatureModule module) {
        if (module.level() instanceof ServerLevel level) {
            MODULES.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(module);
        }
    }

    public static void unregister(TemperatureModule module) {
        Set<TemperatureModule> modules = MODULES.get(module.level());
        if (modules != null) {
            modules.remove(module);
        }
    }

    /**
     * Moves the heat of every loaded temperature module of the level by one server tick.
     */
    public static void tick(ServerLevel level) {
        Set<TemperatureModule> modules = MODULES.get(level);
        if (modules == null || modules.isEmpty()) {
            return;
        }
        ThermalSolver.step(level, new ArrayList<>(modules));
    }

    /**
     * Moves the heat of the given modules by one server tick, in as many substeps as it takes to stay stable.
     */
    @VisibleForTesting
    public static void step(ServerLevel level, Collection<TemperatureModule> modules) {
        ThermalSolver.step(level, modules);
    }

    /**
     * How many substeps the last {@link #step} used.
     */
    @VisibleForTesting
    public static int lastSubsteps() {
        return ThermalSolver.lastSubsteps();
    }

    /**
     * Sets how many threads the solver's compute phase uses, 1 for none. The default comes from the {@code empiric.temperature.threads} system property.
     */
    @VisibleForTesting
    public static void setThreads(int threads) {
        ThermalSolver.threads(threads);
    }

    @VisibleForTesting
    public static int threads() {
        return ThermalSolver.threads();
    }

    /**
     * The time the solver has spent in each phase since the server started, in nanoseconds.
     */
    @VisibleForTesting
    public static long prepareNanos() {
        return ThermalSolver.prepareNanos();
    }

    @VisibleForTesting
    public static long computeNanos() {
        return ThermalSolver.computeNanos();
    }

    @VisibleForTesting
    public static long commitNanos() {
        return ThermalSolver.commitNanos();
    }

    /**
     * Throws in development when called from the solver's compute phase, which must not touch modules, levels or user code.
     */
    public static void guardCompute(String what) {
        ThermalSolver.guard(what);
    }

    public static TemperatureModule module(Level level, int chunkX, int chunkZ, boolean create) {
        guardCompute("TemperatureSystem.module");
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        TemperatureModule module = TEMPERATURE.get(attachment);
        if (module == null && create && TEMPERATURE.isEnabled() && attachment.isActive()) {
            module = TEMPERATURE.attach(attachment);
        }
        return module;
    }

    /**
     * A view of one section of a chunk's temperatures, for reading or writing many cells at once.
     *
     * @param create attach the chunk's temperature module when it has none, under the same conditions as {@link #set}
     * @return the view, or null when the chunk isn't loaded, the section is out of the level, or the chunk has no module and {@code create} is false or can't attach one
     */
    public static TemperatureSection section(Level level, int chunkX, int sectionIndex, int chunkZ, boolean create) {
        if (sectionIndex < 0 || sectionIndex >= level.getSectionsCount()) {
            return null;
        }
        TemperatureModule module = module(level, chunkX, chunkZ, create);
        return module == null ? null : new TemperatureSection(module, sectionIndex);
    }

    private static int section(Level level, long octY) {
        return level.getSectionIndex((int) Math.floorDiv(octY, 2));
    }

    private static boolean inBuildHeight(Level level, long octY) {
        int y = (int) Math.floorDiv(octY, 2);
        return y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight();
    }

    private static int index(long x, long y, long z) {
        return TemperatureModule.index((int) Math.floorMod(x, 32L), (int) Math.floorMod(y, 32L), (int) Math.floorMod(z, 32L));
    }

    /**
     * The temperature of an oct in K, exactly. Octs without heat are at the {@link #restingKelvin resting temperature}.
     */
    public static double kelvin(Level level, long x, long y, long z) {
        if (!inBuildHeight(level, y)) {
            return restingKelvin(level);
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), false);
        return module == null ? restingKelvin(level) : module.kelvin(section(level, y), index(x, y, z));
    }

    public static double kelvin(Level level, OctPos pos) {
        return kelvin(level, pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    /**
     * Sets the temperature of an oct in K, between absolute zero and 100000 K. The cell and its neighbours are woken so the heat starts moving.
     */
    public static void setKelvin(Level level, long x, long y, long z, double kelvin) {
        if (!inBuildHeight(level, y)) {
            return;
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), kelvin != restingKelvin(level));
        if (module != null) {
            module.setKelvin(section(level, y), index(x, y, z), kelvin);
        }
    }

    public static void setKelvin(Level level, OctPos pos, double kelvin) {
        setKelvin(level, pos.getLongX(), pos.getLongY(), pos.getLongZ(), kelvin);
    }

    public static void setKelvin(Level level, BlockPos pos, double kelvin) {
        for (int octant = 0; octant < 8; octant++) {
            setKelvin(level, new OctPos(pos, octant), kelvin);
        }
    }

    /**
     * Adds heat to an oct in J, negative to remove it. The temperature changes by the heat over the oct's capacity, with no rounding.
     * Octs that hold no heat, like air, ignore it.
     */
    public static void addHeat(Level level, long x, long y, long z, double joules) {
        if (joules == 0 || !inBuildHeight(level, y)) {
            return;
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), true);
        if (module != null) {
            module.addHeat(section(level, y), index(x, y, z), joules);
        }
    }

    public static void addHeat(Level level, OctPos pos, double joules) {
        addHeat(level, pos.getLongX(), pos.getLongY(), pos.getLongZ(), joules);
    }

    private static int materialIndex(Level level, long x, long y, long z) {
        if (!inBuildHeight(level, y)) {
            return 0;
        }
        TemperatureModule module = module(level, (int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L), false);
        if (module != null) {
            return module.materials(section(level, y))[index(x, y, z)] & 0xFF;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow((int) Math.floorDiv(x, 32L), (int) Math.floorDiv(z, 32L));
        if (chunk == null) {
            return 0;
        }
        return ThermalMaterials.indexOf(OctLevel.get(level).getBlockState(new OctPos(x, y, z)));
    }

    /**
     * The heat capacity of an oct in J/K, 0 for air and anything else that holds no heat.
     */
    public static double heatCapacity(Level level, long x, long y, long z) {
        return ThermalMaterials.table().capacity[materialIndex(level, x, y, z)];
    }

    /**
     * What an oct is made of, or null if it holds no heat or its chunk isn't loaded.
     */
    public static ThermalMaterial material(Level level, long x, long y, long z) {
        return ThermalMaterials.table().materials[materialIndex(level, x, y, z)];
    }

    public static ThermalMaterial material(Level level, OctPos pos) {
        return material(level, pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    /**
     * The temperature of an oct in tenths of a degree Celsius, rounded from kelvin.
     */
    public static short get(Level level, long x, long y, long z) {
        return (short) TemperatureModule.tenths(kelvin(level, x, y, z));
    }

    /**
     * Sets the temperature of an oct in tenths of a degree Celsius.
     */
    public static void set(Level level, long x, long y, long z, int value) {
        setKelvin(level, x, y, z, value / 10.0 + TemperatureModule.ZERO_CELSIUS);
    }

    public static short get(Level level, OctPos pos) {
        return get(level, pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    public static void set(Level level, OctPos pos, int value) {
        set(level, pos.getLongX(), pos.getLongY(), pos.getLongZ(), value);
    }

    public static void add(Level level, OctPos pos, int value) {
        setKelvin(level, pos, kelvin(level, pos) + value / 10.0);
    }

    /**
     * The average temperature of the block's eight octs in tenths of a degree Celsius.
     */
    public static short get(Level level, BlockPos pos) {
        double total = 0;
        for (int octant = 0; octant < 8; octant++) {
            total += kelvin(level, new OctPos(pos, octant));
        }
        return (short) TemperatureModule.tenths(total / 8);
    }

    public static void set(Level level, BlockPos pos, int value) {
        for (int octant = 0; octant < 8; octant++) {
            set(level, new OctPos(pos, octant), value);
        }
    }

    public static void add(Level level, BlockPos pos, int value) {
        for (int octant = 0; octant < 8; octant++) {
            add(level, new OctPos(pos, octant), value);
        }
    }

    public static void blockChanged(Level level, BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        TemperatureModule module = module(level, chunkX, chunkZ, false);
        if (module != null) {
            module.refresh(pos);
        }
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (localX == 0) {
            invalidateBorder(level, chunkX - 1, chunkZ, pos, 4);
        } else if (localX == 15) {
            invalidateBorder(level, chunkX + 1, chunkZ, pos, 5);
        }
        if (localZ == 0) {
            invalidateBorder(level, chunkX, chunkZ - 1, pos, 2);
        } else if (localZ == 15) {
            invalidateBorder(level, chunkX, chunkZ + 1, pos, 3);
        }
    }

    private static void invalidateBorder(Level level, int chunkX, int chunkZ, BlockPos pos, int face) {
        TemperatureModule module = module(level, chunkX, chunkZ, false);
        if (module != null) {
            module.invalidateBorder(level.getSectionIndex(pos.getY()), face);
            module.wakeBorder(pos, face);
        }
    }
}
