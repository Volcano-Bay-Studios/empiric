package xyz.volcanobay.empiric.api.part;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.part.trait.Flag;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Something placed off the block grid, on any pixel, several to a block. Its size is in pixels before rotation, and its
 * behaviour comes from its traits. Code asks {@link #get} for a trait rather than which type a part is. Build one with
 * {@link #builder} and register it with {@link PartTypes#register} during mod init.
 */
public final class PartType {

    private final ResourceLocation id;
    private final int width;
    private final int height;
    private final int depth;
    private final @Nullable EmpiricModel model;
    private final Supplier<? extends PartOctBlock> cell;
    private final @Nullable Supplier<? extends Item> item;
    private final Supplier<BlockState> particles;
    private final Map<TraitKey<?>, Object> traits;
    private final Map<Class<?>, List<?>> hooks = new ConcurrentHashMap<>();

    private PartType(Builder builder) {
        this.id = builder.id;
        this.width = builder.width;
        this.height = builder.height;
        this.depth = builder.depth;
        this.model = builder.model;
        this.cell = builder.cell;
        this.item = builder.item;
        this.particles = builder.particles;
        this.traits = Collections.unmodifiableMap(new LinkedHashMap<>(builder.traits));
    }

    /**
     * @param width  Pixels along x before rotation, 1 to 16
     * @param height Pixels, 1 to 16
     * @param depth  Pixels along z before rotation, 1 to 16
     */
    public static Builder builder(ResourceLocation id, int width, int height, int depth) {
        return new Builder(id, width, height, depth);
    }

    public ResourceLocation id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public @Nullable EmpiricModel model() {
        return model;
    }

    /**
     * @return The block the octants it fills become
     */
    public PartOctBlock cell() {
        return cell.get();
    }

    public Item item() {
        return item == null ? Items.AIR : item.get();
    }

    public ItemStack newStack() {
        return new ItemStack(item());
    }

    /**
     * @return What its break particles look like
     */
    public BlockState particles() {
        return particles.get();
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(TraitKey<T> key) {
        return (T) traits.get(key);
    }

    public boolean has(TraitKey<?> key) {
        return traits.containsKey(key);
    }

    public Map<TraitKey<?>, Object> traits() {
        return traits;
    }

    /**
     * @return Its trait configs that implement the hook, in the order they were added
     */
    @SuppressWarnings("unchecked")
    public <H> List<H> hooks(Class<H> hook) {
        return (List<H>) hooks.computeIfAbsent(hook, type -> {
            List<Object> found = new ArrayList<>();
            for (Object config : traits.values()) {
                if (type.isInstance(config)) {
                    found.add(config);
                }
            }
            return List.copyOf(found);
        });
    }

    @Override
    public String toString() {
        return "PartType[" + id + "]";
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final int width;
        private final int height;
        private final int depth;
        private @Nullable EmpiricModel model;
        private Supplier<? extends PartOctBlock> cell = EmpiricBlocks.PART;
        private @Nullable Supplier<? extends Item> item;
        private Supplier<BlockState> particles = Blocks.STONE::defaultBlockState;
        private final Map<TraitKey<?>, Object> traits = new LinkedHashMap<>();

        private Builder(ResourceLocation id, int width, int height, int depth) {
            if (width < 1 || width > 16 || height < 1 || height > 16 || depth < 1 || depth > 16) {
                throw new IllegalArgumentException("Part " + id + " must be 1 to 16 pixels each way, not " + width + "x" + height + "x" + depth);
            }
            this.id = id;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public Builder model(EmpiricModel model) {
            this.model = model;
            return this;
        }

        public Builder cell(Supplier<? extends PartOctBlock> cell) {
            this.cell = cell;
            return this;
        }

        public Builder item(Supplier<? extends Item> item) {
            this.item = item;
            return this;
        }

        public Builder particles(Supplier<BlockState> particles) {
            this.particles = particles;
            return this;
        }

        public <T> Builder with(TraitKey<T> key, T config) {
            traits.put(key, key.type().cast(config));
            return this;
        }

        public Builder with(TraitKey<Flag> key) {
            return with(key, Flag.ON);
        }

        public PartType build() {
            return new PartType(this);
        }
    }
}
