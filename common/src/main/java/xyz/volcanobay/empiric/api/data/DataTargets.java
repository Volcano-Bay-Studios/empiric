package xyz.volcanobay.empiric.api.data;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The built-in targets for {@link GameDataType}. Each one matches an id or a {@code #tag} against the candidate's
 * registry holder, except {@link #DIMENSIONS} (ids only) and {@link #PLAYERS} (UUIDs and names).
 */
public final class DataTargets {

    public static final DataTarget<Block> BLOCKS = new RegistryDataTarget<Block, Block>(Registries.BLOCK, "blocks", Block::builtInRegistryHolder);
    public static final DataTarget<Item> ITEMS = new RegistryDataTarget<Item, Item>(Registries.ITEM, "items", Item::builtInRegistryHolder);
    public static final DataTarget<Fluid> FLUIDS = new RegistryDataTarget<Fluid, Fluid>(Registries.FLUID, "fluids", Fluid::builtInRegistryHolder);
    public static final DataTarget<EntityType<?>> ENTITY_TYPES = new RegistryDataTarget<EntityType<?>, EntityType<?>>(Registries.ENTITY_TYPE, "entities", type -> BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type));
    public static final DataTarget<BlockEntityType<?>> BLOCK_ENTITY_TYPES = new RegistryDataTarget<BlockEntityType<?>, BlockEntityType<?>>(Registries.BLOCK_ENTITY_TYPE, "block_entities", type -> BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(type));
    public static final DataTarget<Holder<Biome>> BIOMES = registry(Registries.BIOME, "biomes");
    public static final DataTarget<ResourceKey<Level>> DIMENSIONS = new DimensionTarget();
    public static final DataTarget<GameProfile> PLAYERS = new PlayerTarget();

    private DataTargets() {
    }

    /**
     * A target for any registry, builtin, custom or dynamic (damage types, enchantments and so on). Lookups pass the
     * value's {@link Holder}.
     */
    public static <V> DataTarget<Holder<V>> registry(ResourceKey<? extends Registry<V>> registry, String key) {
        return new RegistryDataTarget<Holder<V>, V>(registry, key, holder -> holder);
    }

    private static final class DimensionTarget implements DataTarget<ResourceKey<Level>> {

        @Override
        public String key() {
            return "dimensions";
        }

        @Override
        public boolean cacheable() {
            return true;
        }

        @Override
        public Object keyOf(ResourceKey<Level> candidate) {
            return candidate.location();
        }

        @Override
        public List<Selector<ResourceKey<Level>>> compile(String typeId, String entryId, DataNode selectors, Consumer<String> warnings) {
            List<Selector<ResourceKey<Level>>> compiled = new ArrayList<>();
            for (DataNode node : selectors.elementsOrSelf()) {
                String value = node.asString();
                ResourceLocation id = value.startsWith("#") ? null : ResourceLocation.tryParse(value);
                if (id == null) {
                    warnings.accept(typeId + " entry " + entryId + " lists the invalid dimension " + value + " (tags aren't supported)");
                    continue;
                }
                compiled.add(new Selector<>() {
                    @Override
                    public boolean matches(ResourceKey<Level> candidate) {
                        return candidate.location().equals(id);
                    }

                    @Override
                    public int specificity() {
                        return EXACT;
                    }

                    @Override
                    public Set<Object> exactKeys() {
                        return Set.of(id);
                    }
                });
            }
            return compiled;
        }
    }

    private static final class PlayerTarget implements DataTarget<GameProfile> {

        @Override
        public String key() {
            return "players";
        }

        @Override
        public List<Selector<GameProfile>> compile(String typeId, String entryId, DataNode selectors, Consumer<String> warnings) {
            List<Selector<GameProfile>> compiled = new ArrayList<>();
            for (DataNode node : selectors.elementsOrSelf()) {
                String value = node.asString();
                UUID uuid = parseUuid(value);
                compiled.add(new Selector<>() {
                    @Override
                    public boolean matches(GameProfile candidate) {
                        if (uuid != null) {
                            return uuid.equals(candidate.getId());
                        }
                        return value.equalsIgnoreCase(candidate.getName());
                    }

                    @Override
                    public int specificity() {
                        return EXACT;
                    }
                });
            }
            return compiled;
        }

        private static UUID parseUuid(String value) {
            if (value.length() != 36) {
                return null;
            }
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
