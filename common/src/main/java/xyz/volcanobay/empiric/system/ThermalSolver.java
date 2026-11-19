package xyz.volcanobay.empiric.system;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.api.temperature.ThermalEnvironment;
import xyz.volcanobay.empiric.api.temperature.ThermalMaterials;
import xyz.volcanobay.empiric.core.EmpiricCore;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * The explicit finite volume heat solver behind {@link TemperatureSystem}. Each substep prepares every active section on the main thread, computes them
 * in parallel from the prepared arrays only, then commits and exchanges on the main thread in a fixed order, so the result doesn't depend on the thread count.
 */
final class ThermalSolver {

    // plain arrays, for the hot loop
    private static final int[] DX = {0, 0, 0, 0, 1, -1};
    private static final int[] DY = {1, -1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};
    private static final int[] ORDER = {4, 5, 0, 1, 2, 3};
    private static final int WORDS = TemperatureModule.CELLS >> 6;
    private static final int PLANE = 1024;
    private static final int PLANE_WORDS = PLANE >> 6;
    private static final int MAX_SUBSTEPS = 64;
    private static final int POOL_CAP = 8;
    private static final int PARALLEL_THRESHOLD = 4;
    private static final int SET_WORDS = 4;
    private static final boolean SEQUENTIAL = Boolean.getBoolean("empiric.sequential");
    private static final double SIGMA = ThermalMaterials.STEFAN_BOLTZMANN;
    private static final ThreadLocal<Boolean> IN_COMPUTE = ThreadLocal.withInitial(() -> false);
    // The fields below are only used on the main thred
    private static final ArrayDeque<SectionWork> POOL = new ArrayDeque<>();
    private static final List<Prepared> PREPARED = new ArrayList<>();
    private static boolean capLogged;
    private static int lastSubsteps;
    private static long prepareNanos;
    private static long computeNanos;
    private static long commitNanos;
    private static int threads = defaultThreads();
    // The compute phase of the solver is running, so threads that are in it can be told apart in guard.
    private static volatile boolean computing;
    // The pool is created on first use on the main thread. Threads are daemons, so they don't hold up the server stopping, and shutdown() ends them on a clean stop.
    private static ForkJoinPool pool;

    private ThermalSolver() {
    }

    private static int defaultThreads() {
        if (SEQUENTIAL) {
            return 1;
        }
        return Math.max(1, Integer.getInteger("empiric.temperature.threads", Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
    }

    static int lastSubsteps() {
        return lastSubsteps;
    }

    static long prepareNanos() {
        return prepareNanos;
    }

    static long computeNanos() {
        return computeNanos;
    }

    static long commitNanos() {
        return commitNanos;
    }

    static int threads() {
        return threads;
    }

    @VisibleForTesting
    static void threads(int count) {
        threads = SEQUENTIAL ? 1 : Math.max(1, count);
        shutdown();
    }

    static void shutdown() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }

    static void guard(String what) {
        if (computing && IN_COMPUTE.get() && EmpiricCore.engine().systems().isDevMode()) {
            throw new IllegalStateException(what + " was called from the heat solver's compute phase. It must only read the prepared arrays");
        }
    }

    private static ForkJoinPool pool() {
        if (pool == null) {
            AtomicInteger counter = new AtomicInteger();
            pool = new ForkJoinPool(threads, forkJoinPool -> {
                ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(forkJoinPool);
                thread.setName("empiric-heat-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, null, false);
        }
        return pool;
    }

    static void step(ServerLevel level, Collection<TemperatureModule> modules) {
        Step step = Step.begin(level, modules);
        if (step == null) {
            return;
        }
        lastSubsteps = 0;
        for (TemperatureModule module : step.modules) {
            for (int section : module.sections.keySet().toIntArray()) {
                module.active(section);
            }
        }
        double remaining = step.total;
        double dt = 0;
        int left = 0;
        int done = 0;
        long[] set = new long[SET_WORDS];
        long[] plan = null;
        while (remaining > 0) {
            long start = System.nanoTime();
            int count = prepare(step, set);
            prepareNanos += System.nanoTime() - start;
            if (count == 0) {
                break;
            }
            if (plan == null || !Arrays.equals(set, plan)) {
                double stable = step.table.stableStep(set);
                double wanted = Math.ceil(remaining / stable);
                if (wanted > MAX_SUBSTEPS && !capLogged) {
                    capLogged = true;
                    Empiric.LOGGER.warn("The temperature time scale needs {} substeps per tick, more than the limit of {}, so the steps are longer than is safe for the lightest materials", (int) wanted, MAX_SUBSTEPS);
                }
                left = (int) Math.max(1, Math.min(Math.max(1, MAX_SUBSTEPS - done), wanted));
                dt = remaining / left;
                plan = set.clone();
            }
            Context context = new Context(step, dt);
            start = System.nanoTime();
            parallel(count, i -> process(context, PREPARED.get(i)));
            long mid = System.nanoTime();
            computeNanos += mid - start;
            // Each task writes only its own section's values, so the cell updates run in parallel. The module maps are updated after, in order.
            parallel(count, i -> apply(context, PREPARED.get(i)));
            for (int i = 0; i < count; i++) {
                commit(PREPARED.get(i));
            }
            Neighbour slot = new Neighbour();
            for (int i = 0; i < count; i++) {
                exchange(step, context, PREPARED.get(i), slot);
            }
            commitNanos += System.nanoTime() - mid;
            remaining -= dt;
            done++;
            left--;
            if (left <= 0) {
                break;
            }
        }
        lastSubsteps = done;
        for (Prepared prepared : PREPARED) {
            prepared.clear();
        }
        for (TemperatureModule module : step.modules) {
            if (!module.isAttached()) {
                continue;
            }
            module.retire(step.settle);
            module.flushSync();
            if (module.isEmpty()) {
                TemperatureSystem.TEMPERATURE.detach(module.attachment());
            }
        }
        trimPool();
    }

    private static void trimPool() {
        while (POOL.size() > POOL_CAP) {
            POOL.pop();
        }
        while (PREPARED.size() > 4096) {
            PREPARED.remove(PREPARED.size() - 1);
        }
    }

    private static Prepared prepared(int index) {
        while (PREPARED.size() <= index) {
            PREPARED.add(new Prepared());
        }
        return PREPARED.get(index);
    }

    private static int prepare(Step step, long[] set) {
        Arrays.fill(set, 0);
        int used = 0;
        for (int m = 0; m < step.modules.size(); m++) {
            TemperatureModule module = step.modules.get(m);
            if (!module.isAttached() || module.sections.isEmpty()) {
                continue;
            }
            int[] keys = module.sections.keySet().toIntArray();
            Arrays.sort(keys);
            ChunkPos pos = module.chunk().getPos();
            for (int section : keys) {
                long[] active = module.active(section);
                if (TemperatureModule.isZero(active)) {
                    continue;
                }
                Prepared prepared = prepared(used++);
                prepared.module = module;
                prepared.section = section;
                prepared.values = module.sections.get(section);
                prepared.active = active;
                prepared.materials = module.materials(section);
                prepared.room = RoomAir.mask(step.level, pos.x, pos.z, section);
                merge(set, module.materialSet(section));
                int needed = boundaryFaces(active);
                for (int face = 0; face < 6; face++) {
                    Neighbour slot = prepared.slots[face];
                    if ((needed & (1 << face)) == 0) {
                        slot.reset();
                        continue;
                    }
                    slot.resolve(step, module, section, face, false);
                    if (slot.kind == Neighbour.SECTION && slot.module != null) {
                        merge(set, slot.module.materialSet(slot.section));
                    }
                }
                prepared.work = acquire();
                prepared.work.section = section;
            }
        }
        return used;
    }

    private static void merge(long[] set, long[] other) {
        for (int word = 0; word < SET_WORDS; word++) {
            set[word] |= other[word];
        }
    }

    /**
     * The faces that have an active cell on the section's boundary, as a bit mask.
     */
    private static int boundaryFaces(long[] active) {
        int faces = 0;
        for (int word = 0; word < active.length; word++) {
            long bits = active[word];
            if (bits == 0) {
                continue;
            }
            int x = word >> 4;
            int y = (word & 15) << 1;
            if (x == 31) {
                faces |= 1 << 4;
            } else if (x == 0) {
                faces |= 1 << 5;
            }
            if (y == 0 && (bits & 0xFFFFFFFFL) != 0) {
                faces |= 1 << 1;
            }
            if (y == 30 && (bits >>> 32) != 0) {
                faces |= 1;
            }
            if ((bits & 0x0000000100000001L) != 0) {
                faces |= 1 << 3;
            }
            if ((bits & 0x8000000080000000L) != 0) {
                faces |= 1 << 2;
            }
        }
        return faces;
    }

    /**
     * Runs {@code task} for every index below {@code count}, on the heat workers when there are enough. Tasks may only touch prepared arrays.
     */
    private static void parallel(int count, IntConsumer task) {
        computing = true;
        try {
            if (threads <= 1 || count < PARALLEL_THRESHOLD) {
                IN_COMPUTE.set(true);
                try {
                    for (int i = 0; i < count; i++) {
                        task.accept(i);
                    }
                } finally {
                    IN_COMPUTE.set(false);
                }
                return;
            }
            AtomicInteger next = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int worker = Math.min(threads, count); worker > 0; worker--) {
                tasks.add(() -> {
                    IN_COMPUTE.set(true);
                    try {
                        int i;
                        while (failure.get() == null && (i = next.getAndIncrement()) < count) {
                            task.accept(i);
                        }
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    } finally {
                        IN_COMPUTE.set(false);
                    }
                    return null;
                });
            }
            pool().invokeAll(tasks);
            Throwable thrown = failure.get();
            if (thrown != null) {
                throw new IllegalStateException("The heat solver failed on a worker thread", thrown);
            }
        } finally {
            computing = false;
        }
    }

    static final class Step {

        final ServerLevel level;
        final List<TemperatureModule> modules = new ArrayList<>();
        private final Set<TemperatureModule> known = Collections.newSetFromMap(new IdentityHashMap<>());
        final ThermalMaterials.Table table;
        final double ambient;
        final double[] rest;
        final double levelRest;
        final double convection;
        final double sink;
        final double radiation;
        final double settle;
        final double wakeChange;
        final double total;
        final int sectionCount;

        private Step(ServerLevel level, ThermalEnvironment environment, double total) {
            this.level = level;
            this.table = ThermalMaterials.table();
            this.ambient = environment.ambientKelvin();
            this.rest = TemperatureSystem.resting(level);
            this.levelRest = rest[0];
            this.convection = Math.max(0, environment.convection());
            this.sink = environment.sinkKelvin();
            this.radiation = TemperatureSystem.emissivityScale();
            this.settle = TemperatureSystem.settleThreshold();
            this.wakeChange = settle * 0.01;
            this.sectionCount = level.getSectionsCount();
            this.total = total > 0 ? total : 0;
        }

        static Step begin(ServerLevel level, Collection<TemperatureModule> modules) {
            double dt = 0.05 * TemperatureSystem.timeScale(level);
            if (modules.isEmpty()) {
                return null;
            }
            Step step = new Step(level, TemperatureSystem.environment(level), dt);
            List<TemperatureModule> sorted = new ArrayList<>(modules);
            sorted.sort(Comparator.comparingLong(module -> module.chunk().getPos().toLong()));
            for (TemperatureModule module : sorted) {
                step.add(module);
            }
            return step;
        }

        void add(TemperatureModule module) {
            if (module.isAttached() && known.add(module)) {
                modules.add(module);
            }
        }
    }

    /**
     * The constants of one substep, everything the compute phase may read besides the prepared sections.
     */
    static final class Context {

        final int count;
        final double[] capacity;
        final double[] emissivity;
        final double[] conductance;
        final double[] rest;
        final double levelRest;
        final double convection;
        final double ambient;
        final double sink;
        final double sinkPower;
        final double radiation;
        final double settle;
        final double wakeChange;
        final double dt;

        Context(Step step, double dt) {
            this.count = step.table.count;
            this.capacity = step.table.capacity;
            this.emissivity = step.table.emissivity;
            this.conductance = step.table.conductance;
            this.rest = step.rest;
            this.levelRest = step.levelRest;
            this.convection = step.convection;
            this.ambient = step.ambient;
            this.sink = step.sink;
            this.sinkPower = pow4(step.sink);
            this.radiation = step.radiation;
            this.settle = step.settle;
            this.wakeChange = step.wakeChange;
            this.dt = dt;
        }
    }

    /**
     * A section ready to compute. Every field is set on the main thread before the compute phase and only read during it.
     */
    static final class Prepared {

        TemperatureModule module;
        int section;
        double[] values;
        long[] active;
        byte[] materials;
        long[] room;
        SectionWork work;
        final Neighbour[] slots = new Neighbour[6];

        Prepared() {
            for (int face = 0; face < 6; face++) {
                slots[face] = new Neighbour();
            }
        }

        void clear() {
            module = null;
            values = null;
            active = null;
            materials = null;
            room = null;
            work = null;
            for (Neighbour slot : slots) {
                slot.reset();
            }
        }
    }

    static final class SectionWork {

        int section;
        final double[] flux = new double[TemperatureModule.CELLS];
        final long[] touched = new long[WORDS];
        long[] next = new long[WORDS];
        final double[] out = new double[6 * PLANE];
        final long[] wake = new long[6 * PLANE_WORDS];
        final boolean[] used = new boolean[6];
        boolean crosses;
        boolean changed;

        void give(int face, int plane, double heat) {
            use(face);
            out[face * PLANE + plane] -= heat;
        }

        void wake(int face, int plane) {
            use(face);
            wake[face * PLANE_WORDS + (plane >> 6)] |= 1L << plane;
        }

        private void use(int face) {
            used[face] = true;
            crosses = true;
        }

        void clear() {
            for (int face = 0; face < 6; face++) {
                if (used[face]) {
                    Arrays.fill(out, face * PLANE, (face + 1) * PLANE, 0);
                    Arrays.fill(wake, face * PLANE_WORDS, (face + 1) * PLANE_WORDS, 0);
                    used[face] = false;
                }
            }
            crosses = false;
        }
    }

    private static SectionWork acquire() {
        SectionWork work = POOL.poll();
        return work == null ? new SectionWork() : work;
    }

    private static void release(SectionWork work) {
        work.clear();
        POOL.push(work);
    }

    static final class Neighbour {

        static final int BLOCKED = 0;
        static final int OPEN = 1;
        static final int SECTION = 2;

        boolean resolved;
        int kind;
        TemperatureModule module;
        int section;
        double[] values;
        byte[] materials;
        long[] active;
        long[] room;

        void reset() {
            room = null;
            resolved = false;
            kind = BLOCKED;
            module = null;
            values = null;
            materials = null;
            active = null;
        }

        /**
         * Finds what lies across a face, BLOCKED for an unloaded chunk, OPEN past the build height and SECTION otherwise. A module is only created when {@code create} is set.
         */
        void resolve(Step step, TemperatureModule own, int ownSection, int face, boolean create) {
            reset();
            resolved = true;
            section = ownSection;
            if (face < 2) {
                section += face == 0 ? 1 : -1;
                if (section < 0 || section >= step.sectionCount) {
                    kind = OPEN;
                    return;
                }
                module = own;
                room = RoomAir.mask(step.level, own.chunk().getPos().x, own.chunk().getPos().z, section);
            } else {
                ChunkPos pos = own.chunk().getPos();
                int chunkX = pos.x + DX[face];
                int chunkZ = pos.z + DZ[face];
                LevelChunk chunk = step.level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    return;
                }
                module = TemperatureSystem.TEMPERATURE.get(ChunkAttachment.of(chunk));
                if (module == null && create) {
                    module = TemperatureSystem.module(step.level, chunkX, chunkZ, true);
                    if (module == null) {
                        return;
                    }
                    step.add(module);
                }
                room = RoomAir.mask(step.level, chunkX, chunkZ, section);
                if (module == null) {
                    kind = SECTION;
                    materials = own.border(ownSection, face, chunk);
                    return;
                }
            }
            kind = SECTION;
            values = module.sections.get(section);
            materials = module.materials(section);
            active = module.activeMask(section);
        }

        int cell(int face, int plane) {
            return module == null ? plane : real(face, plane);
        }

        /**
         * The index of the cell across the face in its own section.
         */
        int real(int face, int plane) {
            return switch (face) {
                case 0 -> TemperatureModule.index(plane >> 5, 0, plane & 31);
                case 1 -> TemperatureModule.index(plane >> 5, 31, plane & 31);
                case 2 -> TemperatureModule.index(plane >> 5, plane & 31, 0);
                case 3 -> TemperatureModule.index(plane >> 5, plane & 31, 31);
                case 4 -> TemperatureModule.index(0, plane >> 5, plane & 31);
                default -> TemperatureModule.index(31, plane >> 5, plane & 31);
            };
        }
    }

    private static int plane(int face, int x, int y, int z) {
        if (face < 2) {
            return (x << 5) | z;
        }
        if (face < 4) {
            return (x << 5) | y;
        }
        return (y << 5) | z;
    }

    private static int source(int face, int plane) {
        return switch (face) {
            case 0 -> TemperatureModule.index(plane >> 5, 31, plane & 31);
            case 1 -> TemperatureModule.index(plane >> 5, 0, plane & 31);
            case 2 -> TemperatureModule.index(plane >> 5, plane & 31, 31);
            case 3 -> TemperatureModule.index(plane >> 5, plane & 31, 0);
            case 4 -> TemperatureModule.index(31, plane >> 5, plane & 31);
            default -> TemperatureModule.index(0, plane >> 5, plane & 31);
        };
    }

    private static void process(Context ctx, Prepared prepared) {
        double[] values = prepared.values;
        long[] active = prepared.active;
        SectionWork work = prepared.work;
        Neighbour[] slots = prepared.slots;
        byte[] materials = prepared.materials;
        double[] capacity = ctx.capacity;
        double[] emissivity = ctx.emissivity;
        double[] conductance = ctx.conductance;
        int count = ctx.count;
        double[] flux = work.flux;
        long[] touched = work.touched;
        long[] next = work.next;
        double settle = ctx.settle;
        double convection = ctx.convection;
        double environment = ctx.ambient;
        double sink = ctx.sink;
        double sinkPower = ctx.sinkPower;
        double dt = ctx.dt;
        double[] rest = ctx.rest;
        long[] room = prepared.room;
        for (int word = 0; word < active.length; word++) {
            long bits = active[word];
            while (bits != 0) {
                int i = (word << 6) | Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int own = materials[i] & 0xFF;
                if (own == 0) {
                    continue;
                }
                double heat = values[i];
                int x = (i >> 10) & 31;
                int y = (i >> 5) & 31;
                int z = i & 31;
                boolean keep = false;
                int exposed = 0;
                for (int k = 0; k < 6; k++) {
                    int face = ORDER[k];
                    int nx = x + DX[face];
                    int ny = y + DY[face];
                    int nz = z + DZ[face];
                    if (nx >= 0 && nx < 32 && ny >= 0 && ny < 32 && nz >= 0 && nz < 32) {
                        int j = TemperatureModule.index(nx, ny, nz);
                        int other = materials[j] & 0xFF;
                        if (other == 0) {
                            if (room == null || (room[j >> 6] & (1L << j)) == 0) {
                                exposed++;
                            }
                            continue;
                        }
                        double gap = values[j] - heat;
                        if (Math.abs(gap) > settle) {
                            keep = true;
                            next[j >> 6] |= 1L << j;
                        }
                        if ((face & 1) == 1 && (active[j >> 6] & (1L << j)) != 0) {
                            continue;
                        }
                        double q = conductance[own * count + other] * gap;
                        flux[i] += q;
                        flux[j] -= q;
                        touched[j >> 6] |= 1L << j;
                        if (Math.abs(dt * q / capacity[other]) > ctx.wakeChange) {
                            next[j >> 6] |= 1L << j;
                        }
                        continue;
                    }
                    Neighbour slot = slots[face];
                    if (slot.kind == Neighbour.BLOCKED) {
                        continue;
                    }
                    if (slot.kind == Neighbour.OPEN) {
                        exposed++;
                        continue;
                    }
                    int plane = plane(face, x, y, z);
                    int j = slot.cell(face, plane);
                    int other = slot.materials[j] & 0xFF;
                    if (other == 0) {
                        if (slot.room == null || !roomAt(slot, face, plane)) {
                            exposed++;
                        }
                        continue;
                    }
                    double gap = (slot.values == null ? ctx.levelRest : slot.values[j]) - heat;
                    if (Math.abs(gap) > settle) {
                        keep = true;
                        work.wake(face, plane);
                    }
                    if ((face & 1) == 1 && slot.active != null && (slot.active[j >> 6] & (1L << j)) != 0) {
                        continue;
                    }
                    double q = conductance[own * count + other] * gap;
                    flux[i] += q;
                    work.give(face, plane, q);
                }
                if (exposed > 0) {
                    double reach = emissivity[own] * ctx.radiation;
                    double area = exposed * ThermalMaterials.CELL_AREA;
                    double surface = area * (convection * (environment - heat) + reach * SIGMA * (sinkPower - pow4(heat)));
                    // Linearised backward Euler. The loss is divided by 1 + dt * dLoss/dT / C, so surfaces stay stable at any step length and only conduction limits the substeps.
                    double slope = area * (convection + 4 * reach * SIGMA * heat * heat * heat);
                    surface /= 1 + dt * slope / capacity[own];
                    double low = Double.POSITIVE_INFINITY;
                    double high = Double.NEGATIVE_INFINITY;
                    if (convection > 0) {
                        low = Math.min(low, environment);
                        high = Math.max(high, environment);
                    }
                    if (reach > 0) {
                        low = Math.min(low, sink);
                        high = Math.max(high, sink);
                    }
                    if (high >= low) {
                        keep |= Math.abs(heat - rest[own]) > settle;
                    }
                    if (high >= low) {
                        double target = heat + dt * surface / capacity[own];
                        double limited = Math.max(Math.min(heat, low), Math.min(Math.max(heat, high), target));
                        surface = (limited - heat) * capacity[own] / dt;
                    }
                    flux[i] += surface;
                }
                touched[word] |= 1L << i;
                if (keep) {
                    next[word] |= 1L << i;
                }
            }
        }
        wakeChanged(ctx, active, materials, work, slots);
    }

    private static boolean roomAt(Neighbour slot, int face, int plane) {
        int j = slot.real(face, plane);
        return (slot.room[j >> 6] & (1L << j)) != 0;
    }

    private static void wakeChanged(Context ctx, long[] active, byte[] materials, SectionWork work, Neighbour[] slots) {
        double[] capacity = ctx.capacity;
        double[] flux = work.flux;
        long[] next = work.next;
        for (int word = 0; word < active.length; word++) {
            long bits = active[word];
            while (bits != 0) {
                int i = (word << 6) | Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int own = materials[i] & 0xFF;
                if (own == 0 || Math.abs(ctx.dt * flux[i] / capacity[own]) <= ctx.wakeChange) {
                    continue;
                }
                next[i >> 6] |= 1L << i;
                int x = (i >> 10) & 31;
                int y = (i >> 5) & 31;
                int z = i & 31;
                for (int face = 0; face < 6; face++) {
                    int nx = x + DX[face];
                    int ny = y + DY[face];
                    int nz = z + DZ[face];
                    if (nx >= 0 && nx < 32 && ny >= 0 && ny < 32 && nz >= 0 && nz < 32) {
                        int j = TemperatureModule.index(nx, ny, nz);
                        if (materials[j] != 0) {
                            next[j >> 6] |= 1L << j;
                        }
                        continue;
                    }
                    Neighbour slot = slots[face];
                    if (!slot.resolved || slot.kind != Neighbour.SECTION) {
                        continue;
                    }
                    int plane = plane(face, x, y, z);
                    if (slot.materials[slot.cell(face, plane)] != 0) {
                        work.wake(face, plane);
                    }
                }
            }
        }
    }

    private static double pow4(double value) {
        double square = value * value;
        return square * square;
    }

    /**
     * Adds the substep's flux to the section's own cells. Safe on a heat worker, as it touches only the prepared arrays and its own work.
     */
    static void apply(Context ctx, Prepared prepared) {
        double[] capacity = ctx.capacity;
        SectionWork work = prepared.work;
        double[] values = prepared.values;
        byte[] materials = prepared.materials;
        double[] flux = work.flux;
        long[] touched = work.touched;
        boolean changed = false;
        for (int word = 0; word < touched.length; word++) {
            long bits = touched[word];
            touched[word] = 0;
            while (bits != 0) {
                int i = (word << 6) | Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int material = materials[i] & 0xFF;
                if (material != 0) {
                    double previous = values[i];
                    double updated = Mth.clamp(previous + ctx.dt * flux[i] / capacity[material], 0, TemperatureModule.MAX_KELVIN);
                    if (updated != previous) {
                        values[i] = updated;
                        changed = true;
                    }
                }
                flux[i] = 0;
            }
        }
        work.changed = changed;
    }

    /**
     * Publishes the next active mask and the change, on the main thread after {@link #apply}.
     */
    static void commit(Prepared prepared) {
        SectionWork work = prepared.work;
        long[] previous = prepared.module.swapActive(prepared.section, work.next);
        work.next = previous == null ? new long[WORDS] : previous;
        Arrays.fill(work.next, 0);
        if (work.changed) {
            prepared.module.touch(prepared.section);
        }
    }

    static void exchange(Step step, Context ctx, Prepared prepared, Neighbour slot) {
        SectionWork work = prepared.work;
        if (work.crosses) {
            for (int face = 0; face < 6; face++) {
                if (work.used[face]) {
                    transfer(step, ctx, prepared, face, slot);
                }
            }
        }
        release(work);
        prepared.work = null;
    }

    private static void transfer(Step step, Context ctx, Prepared prepared, int face, Neighbour slot) {
        SectionWork work = prepared.work;
        double[] out = work.out;
        long[] wake = work.wake;
        int base = face * PLANE;
        int wakeBase = face * PLANE_WORDS;
        boolean energy = false;
        for (int plane = 0; plane < PLANE; plane++) {
            if (out[base + plane] != 0) {
                energy = true;
                break;
            }
        }
        slot.resolve(step, prepared.module, work.section, face, energy);
        if (slot.kind != Neighbour.SECTION || slot.module == null) {
            if (energy) {
                refund(ctx, prepared, face, 0, PLANE);
            }
            return;
        }
        TemperatureModule target = slot.module;
        double[] values = slot.values;
        if (values == null && energy) {
            values = target.values(slot.section, true);
        }
        if (values == null) {
            return;
        }
        double[] capacity = ctx.capacity;
        boolean changed = false;
        for (int plane = 0; plane < PLANE; plane++) {
            double heat = out[base + plane];
            boolean woken = (wake[wakeBase + (plane >> 6)] & (1L << plane)) != 0;
            if (heat == 0 && !woken) {
                continue;
            }
            int j = slot.cell(face, plane);
            if (heat != 0) {
                int material = slot.materials[j] & 0xFF;
                if (material == 0) {
                    refund(ctx, prepared, face, plane, plane + 1);
                    continue;
                }
                double rise = ctx.dt * heat / capacity[material];
                values[j] = Mth.clamp(values[j] + rise, 0, TemperatureModule.MAX_KELVIN);
                changed = true;
                TemperatureSystem.countCrossExchange();
                woken |= Math.abs(rise) > ctx.wakeChange;
            }
            if (woken) {
                target.mark(slot.section, j);
            }
        }
        if (changed) {
            target.touch(slot.section);
        }
    }

    private static void refund(Context ctx, Prepared prepared, int face, int from, int to) {
        double[] values = prepared.values;
        byte[] materials = prepared.materials;
        double[] out = prepared.work.out;
        int base = face * PLANE;
        for (int plane = from; plane < to; plane++) {
            if (out[base + plane] != 0) {
                int i = source(face, plane);
                values[i] = Mth.clamp(values[i] + ctx.dt * out[base + plane] / ctx.capacity[materials[i] & 0xFF], 0, TemperatureModule.MAX_KELVIN);
            }
        }
    }
}
