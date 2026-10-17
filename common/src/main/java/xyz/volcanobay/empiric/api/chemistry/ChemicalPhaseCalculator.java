package xyz.volcanobay.empiric.api.chemistry;

/**
 * A utility class to calculate chemical phase transitions at non-standard pressures.
 */
public class ChemicalPhaseCalculator {

    private static final double R = 8.314; // Ideal Gas Constant J/(mol*K)
    private static final double KELVIN_OFFSET = 273.15;
    private static final double ATM_TO_PA = 101325.0;

    /**
     * Record to store the calculated temperature results.
     */
    public record PhaseTransitions(double boilingPointC, double freezingPointC) {}

    /**
     * Calculates phase transitions using standard data from sources like PubChem.
     *
     * @param targetP            The target pressure in atmospheres (atm).
     * @param tBp1AtmC           Standard boiling point at 1 atm in Celsius (°C).
     * @param tFp1AtmC           Standard freezing point at 1 atm in Celsius (°C).
     * @param hVapKJ             Enthalpy of vaporization in kJ/mol.
     * @param hFusKJ             Enthalpy of fusion in kJ/mol.
     * @param densityLiq         Density of the liquid in g/cm³.
     * @param densitySol         Density of the solid in g/cm³. Pass 0 to auto-estimate.
     * @param molecularWeight    Molecular weight in g/mol.
     * @param expandsOnFreezing  Set to true for Water, Bismuth, Gallium, Silicon, or Antimony.
     *                           Otherwise, set to false.
     * @return                   A PhaseTransitions record containing calculated values in °C.
     */
    public static PhaseTransitions calculate(
            double targetP,
            double tBp1AtmC,
            double tFp1AtmC,
            double hVapKJ,
            double hFusKJ,
            double densityLiq,
            double densitySol,
            double molecularWeight,
            boolean expandsOnFreezing) {

        // 1. Convert Enthalpies to Joules/mol
        double hVapJ = hVapKJ * 1000.0;
        double hFusJ = hFusKJ * 1000.0;

        // 2. Determine/Estimate Solid Density
        double effectiveDensitySol = densitySol;
        if (effectiveDensitySol <= 0) {
            if (expandsOnFreezing) {
                // Solid is less dense than liquid (e.g., Ice)
                effectiveDensitySol = densityLiq * 0.917;
            } else {
                // Solid is more dense than liquid
                effectiveDensitySol = densityLiq * 1.10;
            }
        }

        double volLiq = (molecularWeight / densityLiq) / 1_000_000.0;
        double volSol = (molecularWeight / effectiveDensitySol) / 1_000_000.0;

        double t1BpK = tBp1AtmC + KELVIN_OFFSET;
        double invT2 = (1.0 / t1BpK) - (R * Math.log(targetP / 1.0) / hVapJ);
        double boilingC = (1.0 / invT2) - KELVIN_OFFSET;

        double t1FpK = tFp1AtmC + KELVIN_OFFSET;
        double deltaV = volLiq - volSol;
        double deltaP = (targetP - 1.0) * ATM_TO_PA;
        double freezingC = (t1FpK + (t1FpK * deltaV * deltaP / hFusJ)) - KELVIN_OFFSET;

        return new PhaseTransitions(boilingC, freezingC);
    }
}
