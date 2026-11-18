package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.data.DataTargets;
import xyz.volcanobay.empiric.api.data.GameDataType;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class TargetedDataGameTests {

    static final DataTarget<Holder<DamageType>> DAMAGE = DataTargets.registry(Registries.DAMAGE_TYPE, "damage_types");
    static final GameDataType<Value> DATA = GameDataType.builder(Empiric.id("test_targeted"), Value.class, Value::new)
            .blocks()
            .items()
            .fluids()
            .entities()
            .blockEntities()
            .biomes()
            .dimensions()
            .players()
            .target(DAMAGE)
            .build();
    static final GameDataType<Value> BLOCKS_ONLY = GameDataType.builder(Empiric.id("test_targeted_blocks"), Value.class, Value::new)
            .blocks()
            .build();

    private TargetedDataGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("targeted_data_blocks", TargetedDataGameTests::blocks),
                test.apply("targeted_data_objects", TargetedDataGameTests::objects),
                test.apply("targeted_data_players", TargetedDataGameTests::players),
                test.apply("targeted_data_world", TargetedDataGameTests::world),
                test.apply("targeted_data_attachments", TargetedDataGameTests::attachments));
    }

    private static DataNode entry(String name, int priority, String key, String... values) {
        DataNode node = DataNode.object();
        node.put("name", DataNode.of(name));
        if (priority != 0) {
            node.put("priority", DataNode.of(priority));
        }
        DataNode list = DataNode.array();
        for (String value : values) {
            list.add(DataNode.of(value));
        }
        node.put(key, list);
        return node;
    }

    private static void load(GameDataType<Value> type, Object... idsAndNodes) {
        Map<String, DataNode> files = new LinkedHashMap<>();
        for (int i = 0; i < idsAndNodes.length; i += 2) {
            files.put((String) idsAndNodes[i], (DataNode) idsAndNodes[i + 1]);
        }
        type.load(files);
    }

    private static void blocks(GameTestHelper helper) {
        load(DATA,
                "tag", entry("tag", 0, "blocks", "#minecraft:base_stone_overworld"),
                "exact", entry("exact", 0, "blocks", "minecraft:stone"),
                "high", entry("high", 5, "blocks", "minecraft:granite"),
                "low", entry("low", 0, "blocks", "minecraft:granite", "minecraft:unknown_block"));
        helper.assertTrue(DATA.get(Blocks.STONE.defaultBlockState()).name.equals("exact"), "an exact id should beat a tag, got " + DATA.get(Blocks.STONE).name);
        helper.assertTrue(DATA.get(Blocks.DIORITE).name.equals("tag"), "diorite should come from the tag, got " + DATA.get(Blocks.DIORITE).name);
        helper.assertTrue(DATA.get(Blocks.GRANITE).name.equals("high"), "the higher priority should win, got " + DATA.get(Blocks.GRANITE).name);
        helper.assertTrue(DATA.get(Blocks.SPONGE) == DATA.defaultValue(), "an unlisted block should use the default");
        load(DATA, "tag", entry("replaced", 0, "blocks", "minecraft:sponge"));
        helper.assertTrue(DATA.get(Blocks.SPONGE).name.equals("replaced"), "a reload should pick up new entries");
        helper.assertTrue(DATA.get(Blocks.DIORITE) == DATA.defaultValue(), "a reload should drop cached lookups");
        load(DATA, "both", entryWithItems());
        helper.assertTrue(DATA.get(Blocks.DIRT).name.equals("both") && DATA.get(Items.STICK).name.equals("both"), "one entry should apply to every target it lists");
        helper.assertTrue(DATA.get(Items.APPLE) == DATA.defaultValue(), "an unlisted item should use the default");
        load(BLOCKS_ONLY, "a", entry("a", 0, "blocks", "minecraft:stone"));
        boolean rejected = false;
        try {
            BLOCKS_ONLY.get(Items.STICK);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a type without the items target should reject item lookups");
        load(BLOCKS_ONLY);
        load(DATA);
        helper.succeed();
    }

    private static DataNode entryWithItems() {
        DataNode node = entry("both", 0, "blocks", "minecraft:dirt");
        node.put("items", DataNode.of("minecraft:stick"));
        return node;
    }

    private static void objects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        load(DATA,
                "entity_id", entry("pig", 0, "entities", "minecraft:pig"),
                "entity_tag", entry("skeleton", 0, "entities", "#minecraft:skeletons"),
                "item_id", entry("stick", 0, "items", "minecraft:stick"),
                "item_tag", entry("logs", 0, "items", "#minecraft:logs"),
                "chest", entry("chest", 0, "block_entities", "minecraft:chest"),
                "fluid", entry("water", 0, "fluids", "minecraft:water"),
                "damage_id", entry("fall", 0, "damage_types", "minecraft:fall"),
                "damage_tag", entry("fire", 0, "damage_types", "#minecraft:is_fire"));
        Entity pig = EntityType.PIG.create(level);
        Entity skeleton = EntityType.SKELETON.create(level);
        helper.assertTrue(DATA.get(pig).name.equals("pig"), "a pig should match by id");
        helper.assertTrue(DATA.get(skeleton).name.equals("skeleton"), "a skeleton should match by tag");
        helper.assertTrue(DATA.get(EntityType.COW.create(level)) == DATA.defaultValue(), "a cow should use the default");
        helper.assertTrue(DATA.get(new ItemStack(Items.STICK)).name.equals("stick"), "a stick should match by id");
        helper.assertTrue(DATA.get(new ItemStack(Items.OAK_LOG)).name.equals("logs"), "an oak log should match by tag");
        helper.assertTrue(DATA.get(Items.DIAMOND) == DATA.defaultValue(), "a diamond should use the default");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        BlockEntity chest = level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(DATA.get(chest).name.equals("chest"), "a chest block entity should match by type");
        helper.assertTrue(DATA.get(Fluids.WATER.defaultFluidState()).name.equals("water"), "water should match by id");
        helper.assertTrue(DATA.get(Fluids.LAVA.defaultFluidState()) == DATA.defaultValue(), "lava should use the default");
        Registry<DamageType> damageTypes = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        helper.assertTrue(DATA.get(DAMAGE, damageTypes.getHolderOrThrow(DamageTypes.FALL)).name.equals("fall"), "fall damage should match by id");
        helper.assertTrue(DATA.get(DAMAGE, damageTypes.getHolderOrThrow(DamageTypes.ON_FIRE)).name.equals("fire"), "burning should match by tag");
        helper.assertTrue(DATA.get(DAMAGE, damageTypes.getHolderOrThrow(DamageTypes.CACTUS)) == DATA.defaultValue(), "cactus damage should use the default");
        load(DATA);
        helper.succeed();
    }

    private static void players(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        String name = player.getGameProfile().getName();
        load(DATA, "type", entry("type", 0, "entities", "minecraft:player"));
        helper.assertTrue(DATA.get(player).name.equals("type"), "a player should fall back to its entity type entry");
        load(DATA,
                "type", entry("type", 0, "entities", "minecraft:player"),
                "named", entry("named", 0, "players", name.toUpperCase()));
        helper.assertTrue(DATA.get(player).name.equals("named"), "a player name should beat the entity type, ignoring case");
        load(DATA,
                "type", entry("type", 0, "entities", "minecraft:player"),
                "uuid", entry("uuid", 0, "players", player.getUUID().toString()));
        helper.assertTrue(DATA.get(player).name.equals("uuid"), "a player uuid should beat the entity type");
        load(DATA, "other", entry("other", 0, "players", "somebody_else"));
        helper.assertTrue(DATA.get(player) == DATA.defaultValue(), "an unlisted player should use the default");
        load(DATA);
        helper.succeed();
    }

    private static void world(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Holder<Biome> biome = level.getBiome(pos);
        String biomeId = biome.unwrapKey().orElseThrow().location().toString();
        TagKey<Biome> tag = biome.tags().findFirst().orElse(null);
        load(DATA, "dimension", entry("dimension", 0, "dimensions", "minecraft:overworld"),
                "biome", entry("biome", 0, "biomes", biomeId));
        helper.assertTrue(DATA.get(level).name.equals("dimension"), "the overworld should match by id");
        helper.assertTrue(DATA.get(Level.NETHER) == DATA.defaultValue(), "the nether should use the default");
        helper.assertTrue(DATA.get(level, pos).name.equals("biome"), "the biome should match by id");
        if (tag != null) {
            load(DATA, "biome", entry("biome_tag", 0, "biomes", "#" + tag.location()));
            helper.assertTrue(DATA.get(level, pos).name.equals("biome_tag"), "the biome should match by tag " + tag.location());
        }
        load(DATA, "biome", entry("other", 0, "biomes", "minecraft:the_void"));
        helper.assertTrue(DATA.get(level, pos) == DATA.defaultValue() || biomeId.equals("minecraft:the_void"), "another biome should not match");
        load(DATA);
        helper.succeed();
    }

    private static void attachments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        load(DATA,
                "block", entry("block", 0, "blocks", "minecraft:chest"),
                "block_entity", entry("block_entity", 0, "block_entities", "minecraft:chest"),
                "entity", entry("entity", 0, "entities", "minecraft:pig"),
                "item", entry("item", 0, "items", "minecraft:stick"),
                "dimension", entry("dimension", 0, "dimensions", "minecraft:overworld"));
        helper.assertTrue(DATA.get(new BlockAttachment(level, pos)).name.equals("block"), "a block attachment should look up its state");
        helper.assertTrue(DATA.get(BlockEntityAttachment.of(level.getBlockEntity(pos))).name.equals("block_entity"), "a block entity attachment should look up its block entity");
        helper.assertTrue(DATA.get(EntityAttachment.of(EntityType.PIG.create(level))).name.equals("entity"), "an entity attachment should look up its entity");
        helper.assertTrue(DATA.get(new ItemStackAttachment(new ItemStack(Items.STICK))).name.equals("item"), "an item attachment should look up its stack");
        helper.assertTrue(DATA.get(LevelAttachment.of(level)).name.equals("dimension"), "a level attachment should look up its dimension");
        helper.assertTrue(DATA.get(ChunkAttachment.of(level.getChunkAt(pos))).name.equals("dimension"), "a chunk attachment should look up its dimension");
        load(DATA);
        helper.succeed();
    }

    public static final class Value {

        @Data
        public String name = "default";
    }
}
