package xyz.volcanobay.empiric.api.temperature;

import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.util.*;

/**
 * The distinct materials of the temperature block data, numbered from 1 so a cell can store its material in a byte.
 * Index 0 means the cell holds no heat, as in air. The table is rebuilt whenever the block data reloads.
 */
public final class ThermalMaterials {

    /**
     * Side of an oct in m.
     */
    public static final double CELL_SIZE = 0.5;
    /**
     * Face area of an oct in m2.
     */
    public static final double CELL_AREA = 0.25;
    /**
     * Volume of an oct in m3.
     */
    public static final double CELL_VOLUME = 0.125;
    public static final double STEFAN_BOLTZMANN = 5.670374419e-8;
    public static final int MAX = 255;
    private static final double STABILITY = 0.45;

    private static volatile Table table;
    private static int generation;

    private ThermalMaterials() {
    }

    /**
     * The current table. It is built from the loaded block data on first use.
     */
    public static Table table() {
        Table current = table;
        if (current == null) {
            current = rebuild();
        }
        return current;
    }

    public static int generation() {
        return table().generation;
    }

    public static int indexOf(BlockState state) {
        return table().indexOf(state);
    }

    public static synchronized Table rebuild() {
        List<TemperatureBlockData> sources = new ArrayList<>();
        sources.add(TemperatureSystem.BLOCK_DATA.defaultValue());
        for (DataEntry<TemperatureBlockData> entry : TemperatureSystem.BLOCK_DATA.entries().entries()) {
            sources.add(entry.value());
        }
        Map<ThermalMaterial, Integer> byValue = new LinkedHashMap<>();
        Map<TemperatureBlockData, Integer> byData = Collections.synchronizedMap(new IdentityHashMap<>());
        List<ThermalMaterial> folded = new ArrayList<>();
        List<TemperatureBlockData> foldedSources = new ArrayList<>();
        for (TemperatureBlockData source : sources) {
            ThermalMaterial material = materialOf(source);
            Integer index = byValue.get(material);
            if (index == null && byValue.size() < MAX) {
                index = byValue.size() + 1;
                byValue.put(material, index);
            }
            if (index != null) {
                byData.put(source, index);
            } else {
                folded.add(material);
                foldedSources.add(source);
            }
        }
        if (!folded.isEmpty()) {
            Empiric.LOGGER.warn("Temperature block data has more than {} distinct materials, {} of them were folded into the nearest one", MAX, folded.size());
        }
        int count = byValue.size() + 1;
        ThermalMaterial[] materials = new ThermalMaterial[count];
        for (Map.Entry<ThermalMaterial, Integer> entry : byValue.entrySet()) {
            materials[entry.getValue()] = entry.getKey();
        }
        for (int i = 0; i < folded.size(); i++) {
            byData.put(foldedSources.get(i), nearest(materials, folded.get(i)));
        }
        double[] capacity = new double[count];
        double[] emissivity = new double[count];
        double[] conductance = new double[count * count];
        for (int a = 1; a < count; a++) {
            capacity[a] = materials[a].capacity();
            emissivity[a] = materials[a].emissivity();
            for (int b = 1; b < count; b++) {
                double ka = materials[a].conductivity();
                double kb = materials[b].conductivity();
                conductance[a * count + b] = ka + kb <= 0 ? 0 : CELL_AREA * 2 * ka * kb / (CELL_SIZE * (ka + kb));
            }
        }
        Table built = new Table(++generation, count, materials, capacity, emissivity, conductance, byData, byValue);
        table = built;
        return built;
    }

    private static ThermalMaterial materialOf(TemperatureBlockData data) {
        return ThermalMaterial.of(data.conductivity, data.density, data.specificHeat, data.emissivity);
    }

    private static int nearest(ThermalMaterial[] materials, ThermalMaterial material) {
        int best = 1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 1; i < materials.length; i++) {
            double conductivity = Math.log((materials[i].conductivity() + 1.0E-6) / (material.conductivity() + 1.0E-6));
            double capacity = Math.log(materials[i].capacity() / material.capacity());
            double distance = conductivity * conductivity + capacity * capacity;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * A snapshot of the materials, with the numbers the solver needs for each. The arrays are indexed by material and must not be changed.
     */
    public static final class Table {

        public final int generation;
        /**
         * The number of materials, counting 0 for no heat.
         */
        public final int count;
        public final ThermalMaterial[] materials;
        /**
         * Heat capacity of one oct in J/K.
         */
        public final double[] capacity;
        public final double[] emissivity;
        /**
         * Conductance in W/K between the centres of two touching octs, at {@code a * count + b}.
         */
        public final double[] conductance;
        private final Map<TemperatureBlockData, Integer> byData;
        private final Map<ThermalMaterial, Integer> byValue;

        Table(int generation, int count, ThermalMaterial[] materials, double[] capacity, double[] emissivity, double[] conductance, Map<TemperatureBlockData, Integer> byData, Map<ThermalMaterial, Integer> byValue) {
            this.generation = generation;
            this.count = count;
            this.materials = materials;
            this.capacity = capacity;
            this.emissivity = emissivity;
            this.conductance = conductance;
            this.byData = byData;
            this.byValue = byValue;
        }

        /**
         * The material of a block state, 0 for air.
         */
        public int indexOf(BlockState state) {
            if (state.isAir()) {
                return 0;
            }
            TemperatureBlockData data = TemperatureSystem.BLOCK_DATA.get(state);
            Integer index = byData.get(data);
            if (index == null) {
                ThermalMaterial material = materialOf(data);
                index = byValue.get(material);
                if (index == null) {
                    index = nearest(materials, material);
                }
                byData.put(data, index);
            }
            return index;
        }

        public double conductance(int first, int second) {
            return conductance[first * count + second];
        }

        /**
         * The longest step in seconds that keeps conduction stable for every material. Surface losses (convection and radiation) are
         * applied implicitly by the solver, so they don't limit the step.
         */
        public double stableStep() {
            return stableStep(null);
        }

        /**
         * Like {@link #stableStep()}, for only the materials whose bit is set in {@code materialSet} (bit {@code m} of word {@code m >> 6}). Null means every material.
         */
        public double stableStep(long[] materialSet) {
            double step = Double.MAX_VALUE;
            for (int m = 1; m < count; m++) {
                if (materialSet != null && (materialSet[m >> 6] & (1L << m)) == 0) {
                    continue;
                }
                double rate = 6 * conductance[m * count + m];
                if (rate > 0) {
                    step = Math.min(step, STABILITY * capacity[m] / rate);
                }
            }
            return step;
        }
    }
}
