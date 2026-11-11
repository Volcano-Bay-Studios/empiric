package xyz.volcanobay.empiric.api.chemistry;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class ChemicalRegistry {

    public static final DataEntryType<Chemical> CHEMICALS = new DataEntryType<>("empiric:chemistry/chemicals", Chemical.class, Chemical::new);
    public static final DataEntryType<ChemicalReaction> REACTIONS = new DataEntryType<>("empiric:chemistry/reactions", ChemicalReaction.class, ChemicalReaction::new);

    public static final DataEntryType<ChemicalComposition> COMPOSITIONS = new DataEntryType<>("empiric:chemistry/compositions", ChemicalComposition.class, ChemicalComposition::new);
    public static final DataEntryType<ChemicalSolidForm> SOLID_FORMS = new DataEntryType<>("empiric:chemistry/solid_forms", ChemicalSolidForm.class, ChemicalSolidForm::new);

    private static final SuggestionProvider<?> CHEMICALS_SUGGESTION_PROVIDER = (unused, builder) -> SharedSuggestionProvider.suggest(CHEMICALS.entries().stream().map(DataEntry::id), builder);

    private ChemicalRegistry() {
    }

    public static void registerData(DataEntrySystem data) {
        data.register(CHEMICALS);
        data.register(REACTIONS);
        data.register(COMPOSITIONS);
        data.register(SOLID_FORMS);
    }

    @SuppressWarnings("unchecked")
    public static <T extends SharedSuggestionProvider> SuggestionProvider<T> chemicalsSuggestionProvider() {
        return (SuggestionProvider<T>) CHEMICALS_SUGGESTION_PROVIDER;
    }

    public static @Nullable Chemical getChemical(String name) {
        return CHEMICALS.get(name);
    }

    public static @Nullable Chemical getChemical(ResourceLocation location) {
        return CHEMICALS.get(location.toString());
    }

    public static Set<Chemical> getAllChemicals() {
        Set<Chemical> chemicals = new HashSet<>();
        for (DataEntry<Chemical> entry : CHEMICALS.entries()) {
            chemicals.add(entry.value());
        }
        return chemicals;
    }

    public static @Nullable ChemicalReaction getReaction(ResourceLocation location) {
        return REACTIONS.get(location.toString());
    }

    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithReactants(ResourceLocation... reactants) {
        return reactionsMatching(reaction -> {
            for (ResourceLocation reactant : reactants) {
                if (reaction.getReactants().containsKey(reactant.toString())) {
                    return true;
                }
            }
            return false;
        });
    }

    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithProducts(ResourceLocation... products) {
        return reactionsMatching(reaction -> {
            for (ResourceLocation product : products) {
                if (reaction.getProducts().containsKey(product.toString())) {
                    return true;
                }
            }
            return false;
        });
    }

    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithHeat() {
        return reactionsMatching(reaction -> reaction.getRequirements().getHeat() != null);
    }

    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithElectrolysis() {
        return reactionsMatching(reaction -> reaction.getRequirements().getElectrolysis() != null);
    }

    public static Set<ChemicalReaction> getAllReactions() {
        Set<ChemicalReaction> reactions = new HashSet<>();
        for (DataEntry<ChemicalReaction> entry : REACTIONS.entries()) {
            reactions.add(entry.value());
        }
        return reactions;
    }

    /**
     * @return What one of the item is made of, or null if it isn't a chemical source
     */
    public static @Nullable ChemicalComposition compositionOf(ItemStack stack) {
        for (DataEntry<ChemicalComposition> entry : COMPOSITIONS.entries()) {
            if (entry.value().matches(stack)) {
                return entry.value();
            }
        }
        return null;
    }

    /**
     * @return How the chemical's solid is taken out as an item, or null if it has no item of its own
     */
    public static @Nullable ChemicalSolidForm solidFormOf(Chemical chemical) {
        for (DataEntry<ChemicalSolidForm> entry : SOLID_FORMS.entries()) {
            if (chemical == getChemical(entry.value().getChemical())) {
                return entry.value();
            }
        }
        return null;
    }

    /**
     * @return The chemical an item is the solid form of, or null if it isn't one
     */
    public static @Nullable ChemicalSolidForm solidFormOf(ItemStack stack) {
        for (DataEntry<ChemicalSolidForm> entry : SOLID_FORMS.entries()) {
            if (entry.value().matches(stack)) {
                return entry.value();
            }
        }
        return null;
    }

    private static Map<ResourceLocation, ChemicalReaction> reactionsMatching(Predicate<ChemicalReaction> filter) {
        Map<ResourceLocation, ChemicalReaction> matching = new LinkedHashMap<>();
        for (DataEntry<ChemicalReaction> entry : REACTIONS.entries()) {
            if (filter.test(entry.value())) {
                matching.put(ResourceLocation.parse(entry.id()), entry.value());
            }
        }
        return matching;
    }
}
