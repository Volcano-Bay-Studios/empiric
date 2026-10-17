package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.core.data.Data;

public class Chemical {
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
