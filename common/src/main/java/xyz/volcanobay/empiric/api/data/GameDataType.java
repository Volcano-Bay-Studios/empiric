package xyz.volcanobay.empiric.api.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.core.entry.DataTarget;
import xyz.volcanobay.empiric.core.entry.TargetedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A data entry type that attaches to game objects. Pick the targets with the {@link #builder}, then look values up with
 * {@code get} for the object you have, without naming a target. Each lookup throws {@link IllegalArgumentException}
 * when the type doesn't declare the target it needs.
 *
 * <p>Entries live in {@code data/<namespace>/<path of the type id>/} and list what they cover under the target keys
 * ({@code "blocks"}, {@code "items"}, {@code "entities"}, {@code "block_entities"}, {@code "fluids"},
 * {@code "biomes"}, {@code "dimensions"}, {@code "players"} or the key of a registry target), plus an optional
 * {@code "priority"}. Register it with {@code @Register} on a static field, like any data entry type.
 */
public class GameDataType<T> extends TargetedDataType<T> {

    public GameDataType(ResourceLocation id, Class<T> type, Supplier<T> defaults, DataTarget<?>... targets) {
        super(id.toString(), type, defaults, targets);
    }

    public GameDataType(ResourceLocation id, Class<T> type, DataTarget<?>... targets) {
        this(id, type, (Supplier<T>) null, targets);
    }

    public static <T> Builder<T> builder(ResourceLocation id, Class<T> type, Supplier<T> defaults) {
        return new Builder<>(id, type, defaults);
    }

    /**
     * A builder whose type has no defaults supplier. See
     * {@link xyz.volcanobay.empiric.core.entry.DataEntryType#DataEntryType(String, Class, String...)}.
     */
    public static <T> Builder<T> builder(ResourceLocation id, Class<T> type) {
        return new Builder<>(id, type, null);
    }

    @Override
    protected void warn(String message) {
        Empiric.LOGGER.warn(message);
    }

    public T get(BlockState state) {
        return get(DataTargets.BLOCKS, state.getBlock());
    }

    public T get(Block block) {
        return get(DataTargets.BLOCKS, block);
    }

    public T get(Item item) {
        return get(DataTargets.ITEMS, item);
    }

    public T get(ItemStack stack) {
        return get(DataTargets.ITEMS, stack.getItem());
    }

    public T get(FluidState state) {
        return get(DataTargets.FLUIDS, state.getType());
    }

    public T get(Fluid fluid) {
        return get(DataTargets.FLUIDS, fluid);
    }

    public T get(BlockEntity blockEntity) {
        return get(DataTargets.BLOCK_ENTITY_TYPES, blockEntity.getType());
    }

    /**
     * The entry for the entity. A player is looked up by UUID and name first, then like any other entity by its type.
     */
    public T get(Entity entity) {
        boolean players = hasTarget(DataTargets.PLAYERS);
        boolean types = hasTarget(DataTargets.ENTITY_TYPES);
        if (!players && !types) {
            throw new IllegalArgumentException("Data entry type " + id() + " declares neither players nor entities");
        }
        if (players && entity instanceof Player player) {
            T value = find(DataTargets.PLAYERS, player.getGameProfile());
            if (value != null) {
                return value;
            }
        }
        if (types) {
            return get(DataTargets.ENTITY_TYPES, entity.getType());
        }
        return defaultValue();
    }

    /**
     * The entry for the level's dimension. Unlike {@link DimensionDataType}, there is no overworld fallback.
     */
    public T get(Level level) {
        return get(DataTargets.DIMENSIONS, level.dimension());
    }

    public T get(ResourceKey<Level> dimension) {
        return get(DataTargets.DIMENSIONS, dimension);
    }

    /**
     * The entry for the biome at a position.
     */
    public T get(Level level, BlockPos pos) {
        return get(DataTargets.BIOMES, level.getBiome(pos));
    }

    public T get(Holder<Biome> biome) {
        return get(DataTargets.BIOMES, biome);
    }

    public T get(BlockAttachment attachment) {
        return get(attachment.state());
    }

    public T get(BlockEntityAttachment attachment) {
        return get(attachment.blockEntity());
    }

    public T get(EntityAttachment attachment) {
        return get(attachment.entity());
    }

    public T get(ItemStackAttachment attachment) {
        return get(attachment.stack());
    }

    public T get(LevelAttachment attachment) {
        return get(attachment.level());
    }

    public T get(ChunkAttachment attachment) {
        return get(attachment.level());
    }

    public static final class Builder<T> {

        private final ResourceLocation id;
        private final Class<T> type;
        private final Supplier<T> defaults;
        private final List<DataTarget<?>> targets = new ArrayList<>();

        private Builder(ResourceLocation id, Class<T> type, Supplier<T> defaults) {
            this.id = id;
            this.type = type;
            this.defaults = defaults;
        }

        public Builder<T> blocks() {
            return target(DataTargets.BLOCKS);
        }

        public Builder<T> items() {
            return target(DataTargets.ITEMS);
        }

        public Builder<T> fluids() {
            return target(DataTargets.FLUIDS);
        }

        public Builder<T> entities() {
            return target(DataTargets.ENTITY_TYPES);
        }

        public Builder<T> blockEntities() {
            return target(DataTargets.BLOCK_ENTITY_TYPES);
        }

        public Builder<T> biomes() {
            return target(DataTargets.BIOMES);
        }

        public Builder<T> dimensions() {
            return target(DataTargets.DIMENSIONS);
        }

        public Builder<T> players() {
            return target(DataTargets.PLAYERS);
        }

        public <V> Builder<T> registry(ResourceKey<? extends Registry<V>> registry, String key) {
            return target(DataTargets.registry(registry, key));
        }

        public Builder<T> target(DataTarget<?> target) {
            targets.add(target);
            return this;
        }

        public GameDataType<T> build() {
            return new GameDataType<>(id, type, defaults, targets.toArray(new DataTarget<?>[0]));
        }
    }
}
