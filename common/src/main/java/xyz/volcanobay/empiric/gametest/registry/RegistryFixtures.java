package xyz.volcanobay.empiric.gametest.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.part.PartInteraction;
import xyz.volcanobay.empiric.api.part.PlacementFailure;
import xyz.volcanobay.empiric.api.registry.SilentFailure;
import xyz.volcanobay.empiric.api.part.TraitKey;
import xyz.volcanobay.empiric.api.part.trait.Flag;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.api.registry.BlockProps;
import xyz.volcanobay.empiric.api.registry.ClientRenderer;
import xyz.volcanobay.empiric.api.registry.CreativeTab;
import xyz.volcanobay.empiric.api.registry.EntityAttributes;
import xyz.volcanobay.empiric.api.registry.EntityProps;
import xyz.volcanobay.empiric.api.registry.InteractionPriority;
import xyz.volcanobay.empiric.api.registry.NoItem;
import xyz.volcanobay.empiric.api.registry.ValidBlocks;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleProperties;
import xyz.volcanobay.empiric.core.registry.CreateRegistry;
import xyz.volcanobay.empiric.core.registry.Ref;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.gametest.registry.client.FixtureGoblinRenderer;

public final class RegistryFixtures {

    private RegistryFixtures() {
    }

    @Register("test_reg_block")
    @CreativeTab("building_blocks")
    public static class RegBlock extends Block {

        private static final MapCodec<RegBlock> CODEC = simpleCodec(RegBlock::new);

        public static final Ref<RegBlock> REF = Ref.create();

        public RegBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @BlockProps
        static BlockBehaviour.Properties props() {
            return BlockBehaviour.Properties.of().strength(2.0f);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }
    }

    public static class FactoryBlock extends Block {

        private static final MapCodec<FactoryBlock> CODEC = simpleCodec(properties -> new FactoryBlock(properties, 0));

        public final int tag;

        public FactoryBlock(BlockBehaviour.Properties properties, int tag) {
            super(properties);
            this.tag = tag;
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }
    }

    public static class FactoryBlocks {

        @Register("test_reg_factory_a")
        public static final Ref<FactoryBlock> A = Ref.of(() -> new FactoryBlock(BlockBehaviour.Properties.of().strength(3.0f), 1));

        @Register("test_reg_factory_b")
        public static final Ref<FactoryBlock> B = Ref.of(() -> new FactoryBlock(BlockBehaviour.Properties.of(), 2));
    }

    @Register("test_reg_no_item")
    @NoItem
    public static class NoItemBlock extends Block {

        private static final MapCodec<NoItemBlock> CODEC = simpleCodec(NoItemBlock::new);

        public NoItemBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }
    }

    public static class RegEntity extends BlockEntity {

        public RegEntity(BlockPos pos, BlockState state) {
            super(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("empiric:test_reg_be")), pos, state);
        }
    }

    public static class RegEntity2 extends BlockEntity {

        public RegEntity2(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }

    public static final class BlockEntities {

        @Register("test_reg_be2")
        @ValidBlocks("test_reg_block")
        public static final Ref<BlockEntityType<RegEntity2>> BLOCK_ENTITY_2 = Ref.create();

        private BlockEntities() {
        }

        @Register("test_reg_be")
        static BlockEntityType<RegEntity> blockEntity(RegBlock block) {
            return BlockEntityType.Builder.of(RegEntity::new, block).build(null);
        }
    }

    @Register("test_reg_goblin")
    @ClientRenderer(FixtureGoblinRenderer.class)
    public static class Goblin extends Pig {

        public static final Ref<EntityType<Goblin>> TYPE = Ref.create();

        public Goblin(EntityType<? extends Pig> type, Level level) {
            super(type, level);
        }

        @EntityAttributes
        static AttributeSupplier.Builder attributes() {
            return Pig.createAttributes();
        }

        @EntityProps
        static EntityType.Builder<Goblin> props(EntityType.Builder<Goblin> builder) {
            return builder.sized(0.5f, 0.5f);
        }
    }

    public static class RegModule extends Module<ChunkAttachment> {
    }

    @Register("fixture_heat")
    public static class FixtureHeat extends Module<ChunkAttachment> {

        public static final Ref<ChunkModuleType<FixtureHeat>> TYPE = Ref.create();

        @ModuleProperties
        static void configure(ChunkModuleType.Builder<FixtureHeat> builder) {
            builder.diffEvery(4);
        }
    }

    public static final class Modules {

        @Register("test_reg_module")
        public static final ChunkModuleType<RegModule> MODULE = ChunkModuleType.builder(RegModule.class, RegModule::new).build();

        private Modules() {
        }
    }

    public static class Spell {
    }

    public static final class Spells {

        @CreateRegistry("empiric:test_reg_spells")
        public static final Ref<Registry<Spell>> SPELLS = Ref.create();

        @Register("test_reg_fire")
        public static final Spell FIRE = new Spell();

        private Spells() {
        }
    }

    public static final class Vanilla {

        @Register("fixture_boom")
        public static final SoundEvent BOOM = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("empiric", "fixture_boom"));

        @Register(value = "fixture_sparkle", registry = "minecraft:particle_type")
        public static final SimpleParticleType SPARKLE = new SimpleParticleType(false) {
        };

        @Register(value = "fixture_stat", registry = "minecraft:custom_stat")
        public static final ResourceLocation STAT = ResourceLocation.fromNamespaceAndPath("empiric", "fixture_stat");

        private Vanilla() {
        }
    }

    public static class ForeignThing {
    }

    public static final class Foreign {

        public static final ResourceKey<Registry<ForeignThing>> KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("empiric", "test_foreign"));

        @Register(value = "test_foreign_entry", registry = "empiric:test_foreign")
        public static final ForeignThing ENTRY = new ForeignThing();

        private Foreign() {
        }
    }

    public static class FixtureNetwork extends NetworkModule {
    }

    public static final class Networks {

        @Register("fixture_network")
        public static final SpatialNetworkType<FixtureNetwork> NETWORK = new SpatialNetworkType<>(ResourceLocation.fromNamespaceAndPath("empiric", "fixture_network"), FixtureNetwork.class, FixtureNetwork::new);

        private Networks() {
        }
    }

    public static class FixtureScreenModule extends Module<ChunkAttachment> implements NetworkBehavior {
    }

    public static final class Screens {

        @Register("fixture_screen_module")
        public static final ChunkModuleType<FixtureScreenModule> MODULE = ChunkModuleType.builder(FixtureScreenModule.class, FixtureScreenModule::new).build();

        private Screens() {
        }

        @Register("fixture_screen")
        static UiScreenType<FixtureScreenModule> screen(ChunkModuleType<FixtureScreenModule> type) {
            return UiScreens.defineLayout(type, "empiric:fixture_screen");
        }
    }

    public static final class Part {

        @Register("fixture_trait")
        public static final Ref<TraitKey<Flag>> TRAIT = Ref.create();

        @Register("fixture_trait_field")
        public static final TraitKey<Flag> TRAIT_FIELD = new TraitKey<>(ResourceLocation.fromNamespaceAndPath("empiric", "fixture_trait_field"), Flag.class);

        @Register("fixture_failure")
        public static final Ref<PlacementFailure> FAILURE = Ref.create();

        @Register("fixture_silent_failure")
        @SilentFailure
        public static final Ref<PlacementFailure> SILENT_FAILURE = Ref.create();

        @Register("fixture_interaction_field")
        @InteractionPriority(999999)
        public static final PartInteraction SECOND = new PartInteraction() {
        };

        private Part() {
        }
    }

    @Register("fixture_interaction")
    @InteractionPriority(1000000)
    public static class FixtureInteraction implements PartInteraction {
    }

    public static class FixtureOct extends OctBlockEntity {

        public FixtureOct(BlockPos pos, int octant, BlockState state) {
            super(pos, octant, state);
        }
    }

    public static final String PIECE_RENDERER = "xyz.volcanobay.empiric.gametest.registry.client.FixturePieceRenderer";
    public static final String OCT_RENDERER = "xyz.volcanobay.empiric.gametest.registry.client.FixtureOctRenderer";
    public static final String TOOLTIP_SOURCE = "xyz.volcanobay.empiric.gametest.registry.client.FixtureTooltipSource";
}
