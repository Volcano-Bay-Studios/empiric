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
    protected static final double R = 8.314;

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

        return (gasMoles * R * this.temperature) / gasSpace(occupiedVolume);
    }

    /**
     * The room the gas has in m³: the volume less what liquids and solids take up. Gas is never squeezed below 1% of the
     * volume, so a brim-full container still has a finite pressure.
     */
    public double getGasSpace() {
        return gasSpace(getOccupiedVolume());
    }

    private double gasSpace(double occupiedVolume) {
        double floor = this.volume > 0 ? this.volume * 0.01 : 0.001;
        return Math.max(floor, this.volume - occupiedVolume);
    }

    /**
     * The room liquids and solids take up, in m³.
     */
    public double getOccupiedVolume() {
        double occupied = 0;
        for (ChemicalComponent component : chemicalComponentsList.values()) {
            occupied += calculateOccupiedVolume(component);
        }
        return occupied;
    }

    private double calculateOccupiedVolume(ChemicalComponent comp) {
        Chemical chemical = comp.chemical;
        double liquidDensity = chemical.getDensityLiquid() > 0 ? chemical.getDensityLiquid() : 1;
        double solidDensity = chemical.getDensitySolid() > 0 ? chemical.getDensitySolid() : liquidDensity;
        double liquidVol = chemical.getMass(comp.getAmount(Volume.State.LIQUID)) / liquidDensity;
        double solidVol = chemical.getMass(comp.getAmount(Volume.State.SOLID)) / solidDensity;
        return (liquidVol + solidVol) / 1000000.0;
    }

    /**
     * Heat needed to warm everything in the volume by one kelvin, in J/K.
     */
    public double heatCapacity() {
        double capacity = 0;
        for (ChemicalComponent component : chemicalComponentsList.values()) {
            capacity += heatCapacity(component.chemical, component.getAmount(State.GAS), State.GAS)
                    + heatCapacity(component.chemical, component.getAmount(State.LIQUID), State.LIQUID)
                    + heatCapacity(component.chemical, component.getAmount(State.SOLID), State.SOLID);
        }
        return capacity;
    }

    private static double heatCapacity(Chemical chemical, double moles, State state) {
        return moles > 0 ? moles * chemical.getHeatCapacity(state) : 0;
    }

    /**
     * Adds heat to the volume, or takes it away when negative, changing its temperature by its heat capacity.
     * Nothing happens to an empty volume. The temperature never drops below 0.01 K.
     *
     * @param joules Heat in J
     */
    public void addHeat(double joules) {
        double capacity = heatCapacity();
        if (capacity <= 0 || joules == 0) {
            return;
        }
        setTemperature(Math.max(0.01, temperature + joules / capacity));
    }

    /**
     * Whether this volume is too big to be changed by what is moved in and out of it, like the open air.
     */
    public boolean isReservoir() {
        return false;
    }

    public void tick() {

    }

    /**
     * @deprecated Its fixed step is tuned for room sized volumes and overshoots on small ones. Use
     * {@link xyz.volcanobay.empiric.api.chemistry.sim.VolumeExchange#equalize}, which is stable at any size.
     */
    @Deprecated
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
        amount = Math.max(0, amount);
        double change = amount - component.getAmount(state);
        moles += change;
        mass += chemical.getMass(change);
        component.setAmount(amount, state);
        prune(component);
        markDirty();
        return amount;
    }

    /**
     * Adds a chemical to a component that already exists in the context. This method should only ever be used to avoid unnecessary retrievals from the chemicalComponentsList.
     *
     * @param component A component of this volume.
     * @param amount    The amount to add
     * @return The new amount (moles)
     */
    protected double addChemicalToEntry(ChemicalComponent component, double amount, State state) {
        // never take more than there is, so rounding can't leave a negative amount behind
        amount = Math.max(amount, -component.getAmount(state));
        moles += amount;
        double newMass = component.chemical.getMass(amount);
        double result = Math.max(0, amount + component.getAmount(state));
        component.setAmount(result, state);
        mass += newMass;
        prune(component);
        markDirty();
        return result;
    }

    // drops a chemical's entry once every state of it has run out
    private void prune(ChemicalComponent component) {
        if (component.getSum() <= 0 && chemicalComponentsList.get(component.chemical) == component) {
            chemicalComponentsList.remove(component.chemical);
        }
    }

    /**
     * Gets the volume amount from the volume
     *
     * @param chemical
     * @return The amount of the chemical (moles)
     */
    public double getChemicalAmount(Chemical chemical, State state) {
        ChemicalComponent component = chemicalComponentsList.get(chemical);
        return component == null ? 0 : component.getAmount(state);
    }

    /**
     * Transfers a specific chemical into another volume.
     *
     * @param chemical
     * @param target
     * @param amount
     */
    public void transferChemical(Chemical chemical, Volume target, double amount, State state) {
        ChemicalComponent ourEntry = chemicalComponentsList.get(chemical);
        if (ourEntry == null || amount <= 0) {
            return;
        }
        double newAmount = Math.min(amount, ourEntry.getAmount(state));

        transferTemperature(chemical,target,newAmount, state);

        addChemicalToEntry(ourEntry, -newAmount, state);
        target.addChemical(chemical, newAmount, state);
    }

    /**
     * Sets the target's temperature to what it would be once the given amount of a chemical arrived at this volume's
     * temperature: the two mix weighted by heat capacity, the target's whole contents against the incoming amount.
     */
    public void transferTemperature(Chemical chemical, Volume target, double amount, State state) {
        double incoming = heatCapacity(chemical, amount, state);
        double existing = target.heatCapacity();
        if (incoming <= 0) {
            return;
        }
        if (existing <= 0 || target.getTemperature() <= 0) {
            target.setTemperature(getTemperature());
            return;
        }
        double finalTemperature = (incoming * getTemperature() + existing * target.getTemperature()) / (incoming + existing);

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

    /**
     * Moves everything from another volume into this one, in every state. The temperatures mix weighted by moles.
     */
    public void absorb(Volume other) {
        double ours = getStateTotal(null);
        double theirs = other.getStateTotal(null);
        if (ours + theirs > 0) {
            setTemperature((ours * temperature + theirs * other.temperature) / (ours + theirs));
        }
        for (ChemicalComponent component : other.chemicalComponentsList.values()) {
            for (State state : State.values()) {
                double amount = component.getAmount(state);
                if (amount != 0) {
                    addChemical(component.chemical, amount, state);
                }
            }
        }
    }

    /**
     * Replaces the target's contents with a fraction of everything in this volume, taken out of this one, at this volume's temperature.
     *
     * @param fraction Share of every chemical to move, from 0 to 1
     */
    public void splitInto(Volume target, double fraction) {
        target.clear();
        target.setTemperature(temperature);
        for (ChemicalComponent component : new ArrayList<>(chemicalComponentsList.values())) {
            for (State state : State.values()) {
                double amount = component.getAmount(state) * fraction;
                if (amount != 0) {
                    addChemical(component.chemical, -amount, state);
                    target.addChemical(component.chemical, amount, state);
                }
            }
        }
    }

    /**
     * Empties the volume of every chemical.
     */
    public void clear() {
        chemicalComponentsList.clear();
        mass = 0;
        moles = 0;
        markDirty();
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
            // components are mutable, so the clone gets its own copies rather than sharing them
            clone.chemicalComponentsList = new HashMap<>();
            for (Map.Entry<Chemical, ChemicalComponent> entry : chemicalComponentsList.entrySet()) {
                clone.chemicalComponentsList.put(entry.getKey(), entry.getValue().copy());
            }
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