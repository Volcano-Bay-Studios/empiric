package xyz.volcanobay.empiric.api.chemistry;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
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

    private static final SuggestionProvider<?> CHEMICALS_SUGGESTION_PROVIDER = (unused, builder) -> SharedSuggestionProvider.suggest(CHEMICALS.entries().stream().map(DataEntry::id), builder);

    private ChemicalRegistry() {
    }

    public static void registerData(DataEntrySystem data) {
        data.register(CHEMICALS);
        data.register(REACTIONS);
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
