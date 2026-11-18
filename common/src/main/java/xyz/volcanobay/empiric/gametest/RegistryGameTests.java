package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.api.part.PartInteraction;
import xyz.volcanobay.empiric.api.part.PartInteractions;
import xyz.volcanobay.empiric.api.part.trait.Flag;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.PlacementFailure;
import xyz.volcanobay.empiric.api.registry.ClientTooltipSource;
import xyz.volcanobay.empiric.api.registry.OctRendererFor;
import xyz.volcanobay.empiric.api.registry.PieceRendererFor;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.gametest.registry.RegistryFixtures;
import xyz.volcanobay.empiric.registry.EmpiricDataComponents;
import xyz.volcanobay.empiric.registry.EmpiricMenus;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;
import xyz.volcanobay.empiric.registry.adapter.VanillaAdapter;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Tests the registration annotations against the fixtures in {@code gametest.registry}.
 */
final class RegistryGameTests {

    private RegistryGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("register_block_and_item", RegistryGameTests::blockAndItem),
                test.apply("register_no_item", RegistryGameTests::noItem),
                test.apply("register_injected_block_entity", RegistryGameTests::injectedBlockEntity),
                test.apply("register_module_field", RegistryGameTests::moduleField),
                test.apply("register_module_class", RegistryGameTests::moduleClass),
                test.apply("register_entity_attributes", RegistryGameTests::entityAttributes),
                test.apply("register_client_renderer_not_initialized", RegistryGameTests::clientRendererNotInitialized),
                test.apply("register_creative_tab", RegistryGameTests::creativeTab),
                test.apply("register_created_registry", RegistryGameTests::createdRegistry),
                test.apply("register_sound_by_type", RegistryGameTests::soundByType),
                test.apply("register_particle_by_registry", RegistryGameTests::particleByRegistry),
                test.apply("register_custom_stat_by_registry", RegistryGameTests::customStatByRegistry),
                test.apply("register_foreign_registry", RegistryGameTests::foreignRegistry),
                test.apply("register_wrong_value_type", RegistryGameTests::wrongValueType),
                test.apply("register_own_menu_and_component", RegistryGameTests::ownMenuAndComponent),
                test.apply("register_network_type", RegistryGameTests::networkType),
                test.apply("register_screen_type", RegistryGameTests::screenType),
                test.apply("register_trait_keys", RegistryGameTests::traitKeys),
                test.apply("register_placement_failure", RegistryGameTests::placementFailure),
                test.apply("register_part_interactions", RegistryGameTests::partInteractions),
                test.apply("register_ref_factory", RegistryGameTests::refFactory),
                test.apply("register_client_only_not_initialized", RegistryGameTests::clientOnlyNotInitialized));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("empiric", path);
    }

    private static void blockAndItem(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.get(id("test_reg_block"));
        helper.assertTrue(block instanceof RegistryFixtures.RegBlock, "the block wasn't registered, found " + block);
        helper.assertTrue(RegistryFixtures.RegBlock.REF.get() == block, "the ref doesn't hold the registered block");
        helper.assertTrue(block.defaultDestroyTime() == 2.0f, "the block props hook wasn't used, destroy time " + block.defaultDestroyTime());
        Item item = BuiltInRegistries.ITEM.get(id("test_reg_block"));
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == block, "the block item wasn't registered, found " + item);
        helper.succeed();
    }

    private static void refFactory(GameTestHelper helper) {
        RegistryFixtures.FactoryBlock first = RegistryFixtures.FactoryBlocks.A.get();
        RegistryFixtures.FactoryBlock second = RegistryFixtures.FactoryBlocks.B.get();
        helper.assertTrue(first != second && first.tag == 1 && second.tag == 2, "the factory fields didn't get their own blocks");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(id("test_reg_factory_a")) == first, "the first block wasn't registered");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(id("test_reg_factory_b")) == second, "the second block wasn't registered");
        helper.assertTrue(first.defaultDestroyTime() == 3.0f, "the factory's properties weren't used");
        Item item = BuiltInRegistries.ITEM.get(id("test_reg_factory_a"));
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == first, "the factory block's item wasn't registered, found " + item);
        helper.succeed();
    }

    private static void noItem(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id("test_reg_no_item")), "the block wasn't registered");
        helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(id("test_reg_no_item")), "@NoItem still got an item");
        helper.succeed();
    }

    private static void injectedBlockEntity(GameTestHelper helper) {
        Block block = RegistryFixtures.RegBlock.REF.get();
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("test_reg_be"));
        helper.assertTrue(type != null && type.isValid(block.defaultBlockState()), "the factory method's block entity type isn't valid for the block");
        BlockEntityType<?> type2 = RegistryFixtures.BlockEntities.BLOCK_ENTITY_2.get();
        helper.assertTrue(type2.isValid(block.defaultBlockState()), "the Ref field's block entity type isn't valid for the block");
        helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("test_reg_be2")) == type2, "the Ref field's type isn't registered");
        helper.assertTrue(type2.create(BlockPos.ZERO, block.defaultBlockState()) instanceof RegistryFixtures.RegEntity2 entity && entity.getType() == type2, "the block entity didn't get its own type");
        helper.succeed();
    }

    private static void moduleField(GameTestHelper helper) {
        helper.assertTrue(EmpiricCore.modules().type("empiric:test_reg_module") == RegistryFixtures.Modules.MODULE, "the module type field wasn't registered");
        helper.succeed();
    }

    private static void moduleClass(GameTestHelper helper) {
        ChunkModuleType<RegistryFixtures.FixtureHeat> type = RegistryFixtures.FixtureHeat.TYPE.get();
        helper.assertTrue("empiric:fixture_heat".equals(type.id()), "the module class got the id " + type.id());
        helper.assertTrue(EmpiricCore.modules().typeOf(RegistryFixtures.FixtureHeat.class) == type, "typeOf doesn't return the ref's type");
        helper.assertTrue(type.diffEvery() == 4, "the @ModuleProperties hook wasn't applied, diffEvery is " + type.diffEvery());
        EmpiricCore.modules().subscribe("empiric", type);
        ChunkAttachment chunk = ChunkAttachment.of(helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO)));
        try {
            helper.assertTrue(type.attach(chunk) != null, "the module class wasn't attached");
            helper.assertTrue(type.get(chunk) != null, "the attached module can't be found");
        } finally {
            type.detach(chunk);
            EmpiricCore.modules().unsubscribe("empiric", type);
        }
        helper.succeed();
    }

    private static void entityAttributes(GameTestHelper helper) {
        helper.assertTrue(DefaultAttributes.hasSupplier(RegistryFixtures.Goblin.TYPE.get()), "the goblin has no default attributes");
        Entity goblin = helper.spawn(RegistryFixtures.Goblin.TYPE.get(), 1, 1, 1);
        helper.assertTrue(goblin.isAlive(), "the goblin didn't spawn");
        helper.succeed();
    }

    private static void clientRendererNotInitialized(GameTestHelper helper) {
        helper.assertTrue(System.getProperty("empiric.test.renderer_loaded") == null, "the client renderer was initialized on the server");
        helper.succeed();
    }

    private static void creativeTab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("building_blocks")));
        Item item = BuiltInRegistries.ITEM.get(id("test_reg_block"));
        boolean found = false;
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.is(item)) {
                found = true;
            }
        }
        helper.assertTrue(found, "the block item isn't in the building blocks tab");
        helper.succeed();
    }

    private static void createdRegistry(GameTestHelper helper) {
        helper.assertTrue(RegistryFixtures.Spells.SPELLS.get().get(id("test_reg_fire")) == RegistryFixtures.Spells.FIRE, "the spell isn't in the created registry");
        helper.succeed();
    }

    private static void soundByType(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.SOUND_EVENT.get(id("fixture_boom")) == RegistryFixtures.Vanilla.BOOM, "the sound event isn't in the sound registry");
        helper.succeed();
    }

    private static void particleByRegistry(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.get(id("fixture_sparkle")) == RegistryFixtures.Vanilla.SPARKLE, "the particle type isn't in the particle registry");
        helper.succeed();
    }

    private static void customStatByRegistry(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.CUSTOM_STAT.containsKey(id("fixture_stat")), "the custom stat wasn't registered through its registry id");
        helper.succeed();
    }

    private static void foreignRegistry(GameTestHelper helper) {
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(RegistryFixtures.Foreign.KEY.location());
        helper.assertTrue(registry != null, "the foreign registry doesn't exist");
        helper.assertTrue(registry.get(id("test_foreign_entry")) == RegistryFixtures.Foreign.ENTRY, "the entry isn't in the foreign registry");
        helper.succeed();
    }

    private static void wrongValueType(GameTestHelper helper) {
        VanillaAdapter.checkValueType("fixture", Item.class, BuiltInRegistries.ITEM);
        try {
            VanillaAdapter.checkValueType("fixture", SoundEvent.class, BuiltInRegistries.ITEM);
        } catch (IllegalStateException e) {
            helper.assertTrue(e.getMessage().contains("fixture") && e.getMessage().contains(SoundEvent.class.getName()) && e.getMessage().contains("minecraft:item"), "the error doesn't name the entry, the value and the registry: " + e.getMessage());
            helper.succeed();
            return;
        }
        helper.fail("a sound event was accepted by the item registry");
    }

    private static void ownMenuAndComponent(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.MENU.get(id("ui")) == EmpiricMenus.UI.get(), "the ui menu type isn't registered");
        helper.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.get(id("modules")) == EmpiricDataComponents.MODULES.get(), "the modules component isn't registered");
        helper.succeed();
    }

    private static void networkType(GameTestHelper helper) {
        helper.assertTrue(SpatialNetworkSystem.type("empiric:fixture_network") == RegistryFixtures.Networks.NETWORK, "the network type isn't known to the system");
        helper.assertTrue(EmpiricCore.modules().type("empiric:fixture_network") == RegistryFixtures.Networks.NETWORK.moduleType(), "the network module type wasn't registered");
        helper.assertTrue(RegistryFixtures.Networks.NETWORK.index() >= 0, "the network type got no index");
        helper.succeed();
    }

    private static void screenType(GameTestHelper helper) {
        UiScreenType<?> screen = UiScreens.get(id("fixture_screen"));
        helper.assertTrue(screen != null && screen.moduleType() == RegistryFixtures.Screens.MODULE, "the screen type wasn't registered, found " + screen);
        helper.assertTrue(screen.id().equals(id("fixture_screen")), "the screen type has the id " + screen.id());
        helper.succeed();
    }

    private static void traitKeys(GameTestHelper helper) {
        helper.assertTrue(PartTraits.get(id("fixture_trait")) == RegistryFixtures.Part.TRAIT.get(), "the trait key ref doesn't match the registered key");
        helper.assertTrue(RegistryFixtures.Part.TRAIT.get().type() == Flag.class, "the trait key got the wrong config type");
        helper.assertTrue(PartTraits.get(id("fixture_trait_field")) == RegistryFixtures.Part.TRAIT_FIELD, "the trait key field wasn't registered");
        helper.succeed();
    }

    private static void placementFailure(GameTestHelper helper) {
        helper.assertTrue(PlacementFailure.get(id("fixture_failure")) == RegistryFixtures.Part.FAILURE.get(), "the placement failure wasn't registered");
        helper.assertTrue(RegistryFixtures.Part.FAILURE.get().key() != null, "a normal placement failure should have a message");
        helper.assertTrue(RegistryFixtures.Part.SILENT_FAILURE.get().key() == null, "@SilentFailure should show no message");
        helper.succeed();
    }

    private static void partInteractions(GameTestHelper helper) {
        List<PartInteraction> all = PartInteractions.all();
        helper.assertTrue(all.size() >= 2 && all.get(0) instanceof RegistryFixtures.FixtureInteraction, "the class interaction isn't first, found " + all);
        helper.assertTrue(all.get(1) == RegistryFixtures.Part.SECOND, "the field interaction isn't second");
        helper.succeed();
    }

    private static void clientOnlyNotInitialized(GameTestHelper helper) {
        helper.assertTrue(has(PieceRendererFor.class, RegistryFixtures.PIECE_RENDERER), "the piece renderer wasn't read from the scan");
        helper.assertTrue(has(OctRendererFor.class, RegistryFixtures.OCT_RENDERER), "the oct renderer wasn't read from the scan");
        helper.assertTrue(has(ClientTooltipSource.class, RegistryFixtures.TOOLTIP_SOURCE), "the tooltip source wasn't read from the scan");
        helper.assertTrue(System.getProperty("empiric.test.piece_renderer_loaded") == null, "the piece renderer was initialized on the server");
        helper.assertTrue(System.getProperty("empiric.test.oct_renderer_loaded") == null, "the oct renderer was initialized on the server");
        helper.assertTrue(System.getProperty("empiric.test.tooltip_source_loaded") == null, "the tooltip source was initialized on the server");
        helper.succeed();
    }

    private static boolean has(Class<? extends Annotation> annotation, String className) {
        for (EmpiricRegistration.ClientEntry entry : EmpiricRegistration.clientEntries(annotation)) {
            if (entry.className().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
