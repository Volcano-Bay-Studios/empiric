package xyz.volcanobay.empiric.api.chemistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.HashMap;
import java.util.Locale;

/**
 * What an item is made of, as the chemicals it adds to a volume when it goes in.
 */
@SelfSpec
public class ChemicalComposition {

    @Data
    private String item = ""; // An item id, or a tag id starting with #.
    @Data
    private HashMap<String, Float> chemicals = new HashMap<>(); // Chemical id to moles per item.
    @Data
    private String state = ""; // The state the chemicals go in as, empty for the phase they have at the volume's conditions.

    /**
     * @return Chemical id to the moles of it in one item
     */
    public HashMap<String, Float> getChemicals() {
        return chemicals;
    }

    /**
     * @return The state the chemicals are added in, or null for the phase they have at the volume's conditions
     */
    public Volume.State getState() {
        return state.isEmpty() ? null : Volume.State.valueOf(state.toUpperCase(Locale.ROOT));
    }

    public boolean matches(ItemStack stack) {
        if (item.startsWith("#")) {
            return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(item.substring(1))));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item);
    }
}
