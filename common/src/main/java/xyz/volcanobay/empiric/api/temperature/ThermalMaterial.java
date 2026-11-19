package xyz.volcanobay.empiric.api.temperature;

/**
 * What a block is made of as far as heat goes, in SI units.
 *
 * @param conductivity thermal conductivity in W/(m K)
 * @param density      density in kg/m3
 * @param specificHeat specific heat in J/(kg K)
 * @param emissivity   how well the surface radiates, from 0 to 1
 * @param capacity     heat capacity of one oct (0.125 m3) of it in J/K
 */
public record ThermalMaterial(double conductivity, double density, double specificHeat, double emissivity,
                              double capacity) {

    public static ThermalMaterial of(double conductivity, double density, double specificHeat, double emissivity) {
        double safeDensity = Math.max(density, 1.0E-3);
        double safeHeat = Math.max(specificHeat, 1.0E-3);
        return new ThermalMaterial(Math.max(conductivity, 0), safeDensity, safeHeat, Math.max(0, Math.min(1, emissivity)), safeDensity * safeHeat * ThermalMaterials.CELL_VOLUME);
    }
}
