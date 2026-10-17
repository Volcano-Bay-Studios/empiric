package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.api.atmosphere.Volume;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

@SelfSpec
public class ChemicalComponent {
    @Data
    private double gasAmount = 0;
    @Data
    private double liquidAmount = 0;
    @Data
    private double solidAmount = 0;
    @Data
    public Chemical chemical;

    private ChemicalComponent() {
    }

    public ChemicalComponent(Chemical chemical) {
        this.chemical = chemical;
    }

    public double getAmount(@Nullable Volume.State state) {
        switch (state) {
            case GAS -> {
                return gasAmount;
            }
            case LIQUID -> {
                return  liquidAmount;
            }
            case SOLID ->  {
                return  solidAmount;
            }
            case null -> {
                return getSum();
            }
        }
    }

    @ApiStatus.Internal
    public void setAmount(double amount,Volume.State state) {
        switch (state) {
            case GAS -> gasAmount = amount;
            case LIQUID -> liquidAmount = amount;
            case SOLID -> solidAmount = amount;
        }
    }

    public double getSum() {
        return gasAmount+liquidAmount+solidAmount;
    }

    public double getDisplayAmount(@Nullable Volume.State state) {
        return getAndRoundAmount(100, state);
    }

    public double getAndRoundAmount(int multiplier,@Nullable Volume.State state) {
        return (double) Math.round(getAmount(state) * multiplier) / multiplier;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ChemicalComponent component) {
            return chemical.equals(component.chemical) && gasAmount == component.gasAmount && liquidAmount == component.liquidAmount && solidAmount == component.solidAmount;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = chemical.hashCode();
        hash = 31 * hash + Double.hashCode(gasAmount);
        hash = 31 * hash + Double.hashCode(liquidAmount);
        return 31 * hash + Double.hashCode(solidAmount);
    }
}
