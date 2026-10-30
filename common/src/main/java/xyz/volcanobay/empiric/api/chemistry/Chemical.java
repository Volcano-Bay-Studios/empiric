package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.core.data.Data;

public class Chemical {
    /**
     * Room temperature in Celsius, the temperature {@link #defaultState()} is taken at.
     */
    public static final double ROOM_TEMPERATURE = 20;
    // Keeps the boiling point finite in a vacuum
    private static final double MIN_PRESSURE = 1.0E-6;

    @Data
    private String name = "";
    @Data
    private double boilTemp; // Standard boiling point at 1 atm in Celsius (°C).
    @Data
    private double freezeTemp; // Standard freezing point at 1 atm in Celsius (°C).
    @Data
    private double hVapKJ; //   Enthalpy of vaporization in kJ/mol .
    @Data
    private double hFusKJ; //   Enthalpy of fusion in kJ/mol.
    @Data
    private double densityLiquid = 1; //Density of the liquid in g/cm³.
    @Data
    private double densitySolid; // Density of the solid in g/cm³. Pass 0 to auto-estimate.
    @Data
    private double molecularWeight = 1; // Molecular weight in g/mol.
    @Data
    private boolean expandsOnFreezing; //Set to true for Water, Bismuth, Gallium, Silicon, or Antimony. Otherwise, set to false.
    @Data
    private int color = 0xcccccc; // The color of the chemical
    @Data
    private double heatCapacityGas = 29.1; // Molar heat capacity of the gas in J/(mol K).
    @Data
    private double heatCapacityLiquid = 30; // Molar heat capacity of the liquid in J/(mol K).
    @Data
    private double heatCapacitySolid = 25; // Molar heat capacity of the solid in J/(mol K).
    @Data
    private double solubility; // How much of the solid dissolves in a litre of liquid, in mol/L. 0 if it doesn't dissolve.
    @Data
    private double opacitySolid = 1; // How opaque the solid is, 0 (clear) to 1.
    @Data
    private double opacityLiquid = 0.7; // How opaque the liquid is, 0 (clear) to 1.
    @Data
    private double opacityGas = 0.1; // How opaque the gas is, 0 (clear) to 1.

    public Chemical() {
    }

    public Chemical(String name, double boilTemp, double freezeTemp, double hVapKJ, double hFusKJ, double densityLiquid, double densitySolid, double molecularWeight, boolean expandsOnFreezing, int color) {
        this.name = name;
        this.boilTemp = boilTemp;
        this.freezeTemp = freezeTemp;
        this.hVapKJ = hVapKJ;
        this.hFusKJ = hFusKJ;
        this.densityLiquid = densityLiquid;
        this.densitySolid = densitySolid;
        this.molecularWeight = molecularWeight;
        this.expandsOnFreezing = expandsOnFreezing;
        this.color = color;
    }

    // ---- Stored values ----
    public String getName() {
        return name;
    }

    public double getBoilTemp() {
        return boilTemp;
    }

    public double getFreezeTemp() {
        return freezeTemp;
    }

    public double gethVapKJ() {
        return hVapKJ;
    }

    public double gethFusKJ() {
        return hFusKJ;
    }

    public double getDensityLiquid() {
        return densityLiquid;
    }

    public double getDensitySolid() {
        return densitySolid;
    }

    public double getMolecularWeight() {
        return molecularWeight;
    }

    public boolean getExpandsOnFreezing() {
        return expandsOnFreezing;
    }

    public int getColor() {
        return color;
    }

    /**
     * Molar heat capacity in J/(mol K) of the chemical in a state.
     */
    public double getHeatCapacity(Volume.State state) {
        return switch (state) {
            case GAS -> heatCapacityGas;
            case LIQUID -> heatCapacityLiquid;
            case SOLID -> heatCapacitySolid;
        };
    }

    /**
     * Sets the heat capacities, for chemicals made in code rather than loaded from data.
     * @return This chemical
     */
    public Chemical withHeatCapacities(double gas, double liquid, double solid) {
        this.heatCapacityGas = gas;
        this.heatCapacityLiquid = liquid;
        this.heatCapacitySolid = solid;
        return this;
    }

    /**
     * @return How opaque the chemical is in a state, 0 (clear) to 1
     */
    public double getOpacity(Volume.State state) {
        return switch (state) {
            case GAS -> opacityGas;
            case LIQUID -> opacityLiquid;
            case SOLID -> opacitySolid;
        };
    }

    /**
     * Sets the opacities, 0 (clear) to 1, for chemicals made in code rather than loaded from data.
     * @return This chemical
     */
    public Chemical withOpacities(double gas, double liquid, double solid) {
        this.opacityGas = gas;
        this.opacityLiquid = liquid;
        this.opacitySolid = solid;
        return this;
    }

    /**
     * Sets the solubility in mol/L, for chemicals made in code rather than loaded from data.
     * @return This chemical
     */
    public Chemical withSolubility(double solubility) {
        this.solubility = solubility;
        return this;
    }

    /**
     * @return How much of the solid dissolves in a litre of liquid, in mol/L, 0 if it doesn't
     */
    public double getSolubility() {
        return solubility;
    }

    /**
     * The boiling point in Celsius at a pressure, from {@link ChemicalPhaseCalculator}. Chemicals without enthalpies keep their 1 atm point.
     * @param atm Pressure in atmospheres
     */
    public double boilingPointAt(double atm) {
        if (atm == 1 || hVapKJ <= 0 || hFusKJ <= 0) {
            return boilTemp;
        }
        return ChemicalPhaseCalculator.calculate(Math.max(atm, MIN_PRESSURE), boilTemp, freezeTemp, hVapKJ, hFusKJ,
                densityLiquid, densitySolid, molecularWeight, expandsOnFreezing).boilingPointC();
    }

    /**
     * The pressure of this chemical's vapour over its liquid at a temperature, from the Clausius–Clapeyron relation
     * around the standard boiling point. 0 for chemicals without an enthalpy of vaporization.
     * @param kelvin Temperature in kelvin
     * @return Pressure in Pa
     */
    public double vapourPressure(double kelvin) {
        if (hVapKJ <= 0 || kelvin <= 0) {
            return 0;
        }
        double boil = boilTemp + 273.15;
        return 101325 * Math.exp(-hVapKJ * 1000 / 8.314 * (1 / kelvin - 1 / boil));
    }


    /**
     * The phase of this chemical at 1 atm.
     * @param celsius Temperature in Celsius
     */
    public Volume.State phaseAt(double celsius) {
        return phaseAt(celsius, 1);
    }

    /**
     * The phase of this chemical at a pressure, with the transition points from {@link ChemicalPhaseCalculator}.
     * Chemicals without enthalpies keep their 1 atm points.
     * @param celsius Temperature in Celsius
     * @param atm     Pressure in atmospheres
     */
    public Volume.State phaseAt(double celsius, double atm) {
        double boil = boilTemp;
        double freeze = freezeTemp;
        if (atm != 1 && hVapKJ > 0 && hFusKJ > 0) {
            ChemicalPhaseCalculator.PhaseTransitions transitions = ChemicalPhaseCalculator.calculate(Math.max(atm, MIN_PRESSURE),
                    boilTemp, freezeTemp, hVapKJ, hFusKJ, densityLiquid, densitySolid, molecularWeight, expandsOnFreezing);
            boil = transitions.boilingPointC();
            freeze = transitions.freezingPointC();
        }
        if (celsius < freeze) {
            return Volume.State.SOLID;
        }
        return celsius < boil ? Volume.State.LIQUID : Volume.State.GAS;
    }

    /**
     * The phase of this chemical at room temperature, used where no state is given.
     */
    public Volume.State defaultState() {
        return phaseAt(ROOM_TEMPERATURE);
    }

    // ---- Calculate(d) values ----
    public double getMass(double moles) {
        return moles * molecularWeight;
    }
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public static Chemical unknown(String name) {
        return new Chemical(name, 0, 0, 0, 0, 1, 0, 1, false, 0xcccccc);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Chemical chemical) {
            return name.equals(chemical.name);
        }

        return false;
    }
}
