package xyz.volcanobay.empiric.api.temperature;

/**
 * What the outside of a level does to the surface of its blocks.
 *
 * @param ambientKelvin the temperature of the surrounding air in K, which air cells sit at
 * @param convection    heat transfer to the surrounding air in W/(m2 K), 0 for vacuum
 * @param sinkKelvin    the temperature surfaces radiate to in K
 */
public record ThermalEnvironment(double ambientKelvin, double convection, double sinkKelvin) {

    /**
     * The temperature at which an exposed surface with this emissivity neither gains nor loses heat, the solution of
     * {@code h (T_env - T) + e sigma (T_sink^4 - T^4) = 0}. It is the ambient temperature when the sink matches it, the sink for a vacuum,
     * and in between otherwise. A surface that exchanges nothing (no convection and no emissivity) rests at ambient.
     *
     * @param emissivity the surface emissivity, 0 to 1
     */
    public double restingKelvin(double emissivity) {
        double h = Math.max(0, convection);
        double e = Math.max(0, emissivity);
        if (e <= 0) {
            return ambientKelvin;
        }
        if (h <= 0) {
            return sinkKelvin;
        }
        if (ambientKelvin == sinkKelvin) {
            return ambientKelvin;
        }
        double low = Math.min(ambientKelvin, sinkKelvin);
        double high = Math.max(ambientKelvin, sinkKelvin);
        double sigma = ThermalMaterials.STEFAN_BOLTZMANN;
        double sinkPower = Math.pow(sinkKelvin, 4);
        for (int i = 0; i < 80; i++) {
            double middle = 0.5 * (low + high);
            double net = h * (ambientKelvin - middle) + e * sigma * (sinkPower - middle * middle * middle * middle);
            if (net > 0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return 0.5 * (low + high);
    }
}
