package xyz.volcanobay.empiric.api.atmosphere;

import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;
import org.jetbrains.annotations.ApiStatus;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SelfSpec
public abstract class Volume implements Cloneable {
    private static final double R = 8.314;

    @Data
    protected double volume = 0; // The volume of the area (m^3)
    @Data
    protected double mass = 0; // The mass of everything in the volume (g)
    @Data
    protected double moles = 0; // The amount of everything in moles
    @Data
    protected double temperature = 0; // The temperature of everything in the volume (K)
    private boolean isDirty = false;

    @Data
    private HashMap<Chemical, ChemicalComponent> chemicalComponentsList = new HashMap<>();

    public double getGasPressure() {
        double gasMoles = 0;
        double occupiedVolume = 0;

        for (ChemicalComponent component : chemicalComponentsList.values()) {
            gasMoles += component.getAmount(Volume.State.GAS);

            occupiedVolume += calculateOccupiedVolume(component);
        }

        double effectiveVolume = Math.max(0.001, this.volume - occupiedVolume);

        return (gasMoles * R * this.temperature) / effectiveVolume;
    }

    private double calculateOccupiedVolume(ChemicalComponent comp) {
        double liquidVol = (comp.getAmount(Volume.State.LIQUID) * comp.chemical.getMolecularWeight()) / comp.chemical.getMolecularWeight();
        double solidVol = (comp.getAmount(Volume.State.SOLID) * comp.chemical.getMolecularWeight()) / comp.chemical.getMolecularWeight();
        return (liquidVol + solidVol) / 1000000.0;
    }

    public void tick() {

    }

    public void tickPressureFlow(Volume target) {
        double p1 = this.getGasPressure();
        double p2 = target.getGasPressure();
        double deltaP = p1 - p2;

        if (Math.abs(deltaP) < 1.0) {
            return;
        }

        double flowCoefficient = 0.05;
        double molesToMove = deltaP * flowCoefficient * 0.05;

        if (molesToMove > 0) {
            double totalGas = this.getStateTotal(State.GAS);
            double safeAmount = Math.min(molesToMove, totalGas);
            this.transferAmount(target, safeAmount, Volume.State.GAS);
        } else if (molesToMove < 0) {
            double totalTargetGas = target.getStateTotal(State.GAS);
            double safeAmount = Math.min(Math.abs(molesToMove), totalTargetGas);
            target.transferAmount(this, safeAmount, Volume.State.GAS);
        }
    }

    /**
     * Adds a chemical to the volume
     *
     * @param chemical
     * @param amount   Amount to add in moles
     * @return The new amount (moles)
     */
    public double addChemical(Chemical chemical, double amount, State state) {
        ChemicalComponent component = getOrCreateEntry(chemical);
        return addChemicalToEntry(component, amount, state);
    }

    /**
     * Set amount of a chemical in the volume
     * @param chemical
     * @param amount Amount to add in moles
     * @return
     */
    public double setChemical(Chemical chemical, double amount, State state) {
        ChemicalComponent component = getOrCreateEntry(chemical);
        double change = amount - component.getAmount(state);
        double newMass = chemical.getMolecularWeight() * change;
        mass += newMass;
        component.setAmount(amount, state);
        markDirty();
        return component.getAmount(state);
    }

    /**
     * Adds a chemical to a component that already exists in the context. This method should only ever be used to avoid unnecessary retrievals from the chemicalComponentsList.
     *
     * @param component A component of this volume.
     * @param amount    The amount to add
     * @return The new amount (moles)
     */
    protected double addChemicalToEntry(ChemicalComponent component, double amount, State state) {
        moles += amount;
        double newMass = component.chemical.getMass(amount);
        component.setAmount(amount + component.getAmount(state),state);
        mass += newMass;
        markDirty();
        return component.getAmount(state);
    }

    /**
     * Gets the volume amount from the volume
     *
     * @param chemical
     * @return The amount of the chemical (moles)
     */
    public double getChemicalAmount(Chemical chemical, State state) {
        ChemicalComponent component = getOrCreateEntry(chemical);
        return component.getAmount(state);
    }

    /**
     * Transfers a specific chemical into another volume.
     *
     * @param chemical
     * @param target
     * @param amount
     */
    public void transferChemical(Chemical chemical, Volume target, double amount, State state) {
        ChemicalComponent ourEntry = getOrCreateEntry(chemical);
        double newAmount = Math.min(amount, ourEntry.getAmount(state));

        transferTemperature(chemical,target,newAmount, state);

        addChemicalToEntry(ourEntry, -newAmount, state);
        target.addChemical(chemical, newAmount, state);
    }

    public void transferTemperature(Chemical chemical, Volume target, double amount, State state) {
        double sourceMass = chemical.getMass(amount);
        double targetMass = chemical.getMass(target.getChemicalAmount(chemical, state));

        double finalTemperature = ((sourceMass * getTemperature()) + (targetMass * target.getTemperature())) / (sourceMass + targetMass);

        target.setTemperature(finalTemperature);
    }

    /**
     * Transfers a certain amount of every chemical into another volume.
     *
     * @param target The target volume that everything is moved into.
     * @param amount The amount to move (moles)
     */
    public void transferAmount(Volume target, double amount, Volume.State state) {
        double remainingToTransfer = amount;

        List<ChemicalComponent> components = new ArrayList<>(chemicalComponentsList.values());

        while (remainingToTransfer > 1e-9) {
            double currentTotalStateMoles = 0;
            List<ChemicalComponent> available = new ArrayList<>();

            for (ChemicalComponent component : components) {
                double stateAmount = component.getAmount(state);
                if (stateAmount > 0) {
                    currentTotalStateMoles += stateAmount;
                    available.add(component);
                }
            }

            if (available.isEmpty() || currentTotalStateMoles == 0) {
                break;
            }

            double totalPulledThisPass = 0;
            for (ChemicalComponent component : available) {
                double share = (component.getAmount(state) / currentTotalStateMoles) * remainingToTransfer;
                double actualMove = Math.min(share, component.getAmount(state));

                transferTemperature(component.chemical, target, actualMove, state);

                this.addChemical(component.chemical, -actualMove, state);
                target.addChemical(component.chemical, actualMove, state);

                totalPulledThisPass += actualMove;
            }

            remainingToTransfer -= totalPulledThisPass;
            if (totalPulledThisPass == 0) {
                break;
            }
        }
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getStateTotal(State state) {
        double total = 0;
        for (ChemicalComponent value : chemicalComponentsList.values()) {
            total += value.getAmount(state);
        }
        return total;
    }

    private ChemicalComponent getOrCreateEntry(Chemical chemical) {
        return chemicalComponentsList.computeIfAbsent(chemical, (name) -> new ChemicalComponent(chemical));
    }

    /**
     * Read only! Do not ever modify {@link ChemicalComponent}!
     */
    @ApiStatus.Internal
    public Map<Chemical, ChemicalComponent> getChemicalComponentsList() {
        return chemicalComponentsList;
    }

    /**
     * Get the volume in liters
     *
     * @return The volume in liters
     */
    public double getVolumeLiters() {
        return volume * 1000;
    }

    /**
     * This returns how full the volume is.
     * @return A double from 0 to 1, where 0 is empty, and 1 is full
     */
    public double deltaFill() {
        return 1d;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    @Override
    public int hashCode() {
        int hash = chemicalComponentsList.hashCode();
        hash = 31 * hash + Double.hashCode(volume);
        hash = 31 * hash + Double.hashCode(mass);
        hash = 31 * hash + Double.hashCode(temperature);
        return hash * 31 + Double.hashCode(moles);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Volume volume) {
            return volume.chemicalComponentsList.equals(chemicalComponentsList) && volume.volume == this.volume && volume.temperature == this.temperature && volume.mass == this.mass && volume.moles == this.moles;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Volume";
    }

    @Override
    public Volume clone() {
        try {
            Volume clone = (Volume) super.clone();
            clone.chemicalComponentsList = (HashMap<Chemical, ChemicalComponent>) chemicalComponentsList.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public enum State {
        GAS,
        LIQUID,
        SOLID
    }
}