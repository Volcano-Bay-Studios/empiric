package xyz.volcanobay.empiric.api.chemistry;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import foundry.veil.api.resource.VeilDynamicRegistry;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.requirement.ReactionRequirement;
import xyz.volcanobay.empiric.system.DataSyncSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import static xyz.volcanobay.empiric.Empiric.LOGGER;

public class ChemicalRegistry {

    public static final ResourceKey<Registry<Chemical>> CHEMICALS_KEY = createRegistryKey("chemistry/chemicals");
    public static final ResourceKey<Registry<ChemicalReaction>> CHEMICAL_REACTIONS_KEY = createRegistryKey("chemistry/reactions");
    public static final ResourceKey<Registry<ReactionRequirementType<?>>> REQUIREMENTS_KEY = createRegistryKey("requirements");
    
    private static List<RegistryDataLoader.RegistryData<?>> registries() {
        return List.of(
                new RegistryDataLoader.RegistryData<>(CHEMICALS_KEY, Chemical.CODEC, false),
                new RegistryDataLoader.RegistryData<>(CHEMICAL_REACTIONS_KEY, ChemicalReaction.CODEC, false)
        );
    }

    private static RegistryAccess registryAccess = RegistryAccess.EMPTY;

    private static final SuggestionProvider<?> CHEMICALS_SUGGESTION_PROVIDER = (unused, builder) -> registryAccess().registry(CHEMICALS_KEY).map(registry -> SharedSuggestionProvider.suggestResource(registry.keySet(), builder)).orElseGet(Suggestions::empty);

    private ChemicalRegistry() {
    }

    public static void bootstrap() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends SharedSuggestionProvider> SuggestionProvider<T> chemicalsSuggestionProvider() {
        return (SuggestionProvider<T>) CHEMICALS_SUGGESTION_PROVIDER;
    }

    private static <T> ResourceKey<Registry<T>> createRegistryKey(String name) {
        return ResourceKey.createRegistryKey(Empiric.id(name));
    }

    public static RegistryAccess registryAccess() {
        return registryAccess;
    }

    public static @Nullable Chemical getChemical(String name) {
        return getChemical(ResourceLocation.parse(name));
    }

    public static @Nullable Chemical getChemical(ResourceLocation location) {
        Optional<Registry<Chemical>> registry = registryAccess().registry(CHEMICALS_KEY);
        return registry.map(chemicals -> chemicals.get(location)).orElse(null);
    }

    public static Set<Chemical> getAllChemicals() {
        Optional<Registry<Chemical>> registry = registryAccess().registry(CHEMICALS_KEY);
        return registry.map(chemicals -> chemicals.stream().collect(Collectors.toSet())).orElse(Set.of());
    }

    public static @Nullable ChemicalReaction getReaction(ResourceLocation location) {
        Optional<Registry<ChemicalReaction>> registry = registryAccess().registry(CHEMICAL_REACTIONS_KEY);
        return registry.map(reactions -> reactions.get(location)).orElse(null);
    }

    //TODO: maybe change all these maps to be sets instead
    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithReactants(ResourceLocation... reactants) {
        Optional<Registry<ChemicalReaction>> registry = registryAccess().registry(CHEMICAL_REACTIONS_KEY);
        return registry.map(reactions -> reactions.stream()
                .filter(reaction -> {
                    boolean ret = false;
                    for (ResourceLocation reactant : reactants) {
                        if (reaction.getReactants().containsKey(reactant.toString())) {
                            ret = true;
                            break;
                        }
                    }
                    return ret;
                })
                .collect(Collectors.toMap(
                        reactions::getKey,
                        Function.identity()
                ))
        ).orElse(Map.of());
    }

    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithProducts(ResourceLocation... products) {
        Optional<Registry<ChemicalReaction>> registry = registryAccess().registry(CHEMICAL_REACTIONS_KEY);
        return registry.map(reactions -> reactions.stream()
                .filter(reaction -> {
                    boolean ret = false;
                    for (ResourceLocation product : products) {
                        if (reaction.getProducts().containsKey(product.toString())) {
                            ret = true;
                            break;
                        }
                    }
                    return ret;
                })
                .collect(Collectors.toMap(
                        reactions::getKey,
                        Function.identity()
                ))
        ).orElse(Map.of());
    }

    public static Map<ResourceLocation, ChemicalReaction> getReactionsWithRequirements(ChemicalRegistry.ReactionRequirementType<?>... requirements) {
        Optional<Registry<ChemicalReaction>> registry = registryAccess().registry(CHEMICAL_REACTIONS_KEY);
        return registry.map(reactions -> reactions.stream()
                .filter(reaction -> {
                    boolean ret = false;
                    for (var requirement : requirements) {
                        if (reaction.getRequirements().containsKey(requirement)) {
                            ret = true;
                            break;
                        }
                    }
                    return ret;
                })
                .collect(Collectors.toMap(
                        reactions::getKey,
                        Function.identity()
                ))
        ).orElse(Map.of());
    }
    //

    public static Set<ChemicalReaction> getAllReactions() {
        Optional<Registry<ChemicalReaction>> registry = registryAccess().registry(CHEMICAL_REACTIONS_KEY);
        return registry.map(reactions -> reactions.stream().collect(Collectors.toSet())).orElse(Set.of());
    }

    public static Map<String, Map<String, String>> encode() {
        Map<String, Map<String, String>> encoded = new LinkedHashMap<>();
        encoded.put(CHEMICALS_KEY.location().toString(), encode(CHEMICALS_KEY, Chemical.CODEC));
        encoded.put(CHEMICAL_REACTIONS_KEY.location().toString(), encode(CHEMICAL_REACTIONS_KEY, ChemicalReaction.CODEC));
        return encoded;
    }

    private static <T> Map<String, String> encode(ResourceKey<Registry<T>> key, Codec<T> codec) {
        Map<String, String> encoded = new LinkedHashMap<>();
        registryAccess.registry(key).ifPresent(registry -> {
            for (Map.Entry<ResourceKey<T>, T> entry : registry.entrySet()) {
                codec.encodeStart(JsonOps.INSTANCE, entry.getValue()).resultOrPartial(error -> LOGGER.error("Couldn't encode {}: {}", entry.getKey(), error))
                        .ifPresent(json -> encoded.put(entry.getKey().location().toString(), json.toString()));
            }
        });
        return encoded;
    }

    public static void applySynced(String registry, Map<String, String> entries) {
        if (registry.equals(CHEMICALS_KEY.location().toString())) {
            replace(CHEMICALS_KEY, Chemical.CODEC, entries);
        } else if (registry.equals(CHEMICAL_REACTIONS_KEY.location().toString())) {
            replace(CHEMICAL_REACTIONS_KEY, ChemicalReaction.CODEC, entries);
        } else {
            LOGGER.warn("The server sent unknown chemistry registry {}", registry);
        }
    }

    private static <T> void replace(ResourceKey<Registry<T>> key, Codec<T> codec, Map<String, String> entries) {
        MappedRegistry<T> registry = new MappedRegistry<>(key, Lifecycle.stable());
        entries.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .resultOrPartial(error -> LOGGER.error("Couldn't read synced {} {}: {}", key.location(), id, error))
                .ifPresent(value -> Registry.register(registry, ResourceLocation.parse(id), value)));
        registry.freeze();
        List<Registry<?>> registries = new ArrayList<>();
        for (ResourceKey<? extends Registry<?>> other : List.of(CHEMICALS_KEY, CHEMICAL_REACTIONS_KEY)) {
            if (other != key) {
                registryAccess.registry(other).ifPresent(registries::add);
            }
        }
        registries.add(registry);
        registryAccess = new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
    }

    public static class Reloader implements PreparableReloadListener {

        public static final Reloader INSTANCE = new Reloader();

        public Reloader() {
        }

        @Override
        public @NotNull CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller preparationsProfiler, @NotNull ProfilerFiller reloadProfiler, @NotNull Executor backgroundExecutor, @NotNull Executor gameExecutor) {
            return VeilDynamicRegistry.loadRegistries(resourceManager, registries(), backgroundExecutor)
                    .thenCompose(preparationBarrier::wait)
                    .thenAcceptAsync(data -> {
                        registryAccess = data.registryAccess();
                        data.errors().values().forEach(Exception::printStackTrace);
                        String msg = VeilDynamicRegistry.printErrors(data.errors());
                        if (msg != null) {
                            LOGGER.error("Chemical registry loading errors: {}", msg);
                        }
                        LOGGER.info("Loaded {} chemicals", registryAccess.registryOrThrow(CHEMICALS_KEY).size());
                        LOGGER.info("Loaded {} chemical reactions", registryAccess.registryOrThrow(CHEMICAL_REACTIONS_KEY).size());
                        DataSyncSystem.broadcast();
                    }, gameExecutor);
        }

        @Override
        public @NotNull String getName() {
            return ChemicalRegistry.class.getSimpleName();
        }
    }

    public record ReactionRequirementType<T extends ReactionRequirement>(Codec<T> codec) {}
}
