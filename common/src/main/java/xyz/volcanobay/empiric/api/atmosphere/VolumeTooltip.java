package xyz.volcanobay.empiric.api.atmosphere;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;
import xyz.volcanobay.empiric.api.chemistry.sim.Solutions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The lines an item's tooltip shows for the volume it holds.
 */
public final class VolumeTooltip {

    private static final int MAX_LINES = 6;

    private VolumeTooltip() {
    }

    /**
     * Adds the volume's lines after the first line of a tooltip, the item's name.
     */
    public static void append(Volume itemVolume, List<Component> tooltipComponents) {
        Style detailStyle = Style.EMPTY.withColor(0xAAAAAA);
        List<Line> lines = new ArrayList<>();
        for (ChemicalComponent value : itemVolume.getChemicalComponentsList().values()) {
            Chemical chemical = value.chemical;
            String name = name(chemical);
            double liquid = value.getAmount(Volume.State.LIQUID);
            double solid = value.getAmount(Volume.State.SOLID);
            double gas = value.getAmount(Volume.State.GAS);
            if (liquid > 1.0E-6) {
                double grams = chemical.getMass(liquid);
                if (Solutions.isDissolved(itemVolume, chemical)) {
                    lines.add(new Line(String.format("%.2f g of %s (dissolved)", grams, name), chemical.getColor(), grams));
                } else {
                    double density = chemical.getDensityLiquid() > 0 ? chemical.getDensityLiquid() : 1;
                    lines.add(new Line(String.format("%.1f mL of %s", grams / density, name), chemical.getColor(), grams));
                }
            }
            if (solid > 1.0E-6) {
                double grams = chemical.getMass(solid);
                lines.add(new Line(String.format("%.2f g of solid %s", grams, name), chemical.getColor(), grams));
            }
            if (gas > 1.0E-6) {
                lines.add(new Line(String.format("%.2f mmol of %s gas", gas * 1000, name), chemical.getColor(), chemical.getMass(gas)));
            }
        }
        if (lines.isEmpty()) {
            tooltipComponents.add(Component.literal("It is empty.").withStyle(detailStyle));
            return;
        }
        int at = Math.min(1, tooltipComponents.size());
        List<Component> added = new ArrayList<>();
        added.add(Component.literal("It is filled with").withStyle(detailStyle));
        lines.sort(Comparator.comparingDouble(Line::grams).reversed());
        for (int i = 0; i < Math.min(MAX_LINES, lines.size()); i++) {
            Line line = lines.get(i);
            added.add(Component.literal(line.text()).withStyle(Style.EMPTY.withColor(line.color())));
        }
        if (lines.size() > MAX_LINES) {
            added.add(Component.literal("and " + (lines.size() - MAX_LINES) + " more").withStyle(detailStyle));
        }
        String state = String.format("%.1f °C", itemVolume.getTemperature() - 273.15);
        double atm = itemVolume.getGasPressure() / 101325;
        if (itemVolume.getStateTotal(Volume.State.GAS) > 0 && Math.abs(atm - 1) > 0.05) {
            state += String.format(", %.2f atm", atm);
        }
        added.add(Component.literal(state).withStyle(detailStyle));
        tooltipComponents.addAll(at, added);
    }

    private static String name(Chemical chemical) {
        ResourceLocation location = ResourceLocation.tryParse(chemical.getName());
        if (location == null) {
            return chemical.getName();
        }
        return Component.translatable("chemical." + location.getNamespace() + "." + location.getPath()).getString();
    }

    private record Line(String text, int color, double grams) {
    }
}
