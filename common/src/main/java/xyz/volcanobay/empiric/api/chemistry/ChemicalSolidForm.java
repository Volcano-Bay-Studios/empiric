package xyz.volcanobay.empiric.api.chemistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

/**
 * The item a chemical's solid is taken out of a volume as.
 */
@SelfSpec
public class ChemicalSolidForm {

    @Data
    private String chemical = "";
    @Data
    private String item = "";
    @Data
    private double moles = 1; // Moles of the chemical in one item.

    public String getChemical() {
        return chemical;
    }

    /**
     * @return Moles of the chemical in one item
     */
    public double getMoles() {
        return moles;
    }

    public ItemStack stack(int count) {
        Item found = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
        return new ItemStack(found, count);
    }

    public boolean matches(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item);
    }
}
