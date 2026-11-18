package xyz.volcanobay.empiric.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3i;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.data.GameDataType;
import xyz.volcanobay.empiric.api.data.IdOrTag;
import xyz.volcanobay.empiric.api.data.IdSet;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.ByteArrayDataBuffer;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.data.tree.TreeDataBuffer;
import xyz.volcanobay.empiric.core.data.value.Chance;
import xyz.volcanobay.empiric.core.data.value.IntRange;
import xyz.volcanobay.empiric.data.MinecraftSpecs;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class LenientDataGameTests {

    static final GameDataType<Tuning> TUNING = GameDataType.builder(Empiric.id("test_record_targeted"), Tuning.class)
            .blocks()
            .build();

    private LenientDataGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("lenient_id_sets", LenientDataGameTests::idSets),
                test.apply("lenient_id_set_forms", LenientDataGameTests::idSetForms),
                test.apply("lenient_codec_specs", LenientDataGameTests::codecSpecs),
                test.apply("lenient_vanilla_enums", LenientDataGameTests::vanillaEnums),
                test.apply("lenient_components", LenientDataGameTests::components),
                test.apply("lenient_positions", LenientDataGameTests::positions),
                test.apply("lenient_record_game_data", LenientDataGameTests::recordGameData));
    }

    private static DataNode json(Object... values) {
        DataNode array = DataNode.array();
        for (Object value : values) {
            array.add(node(value));
        }
        return array;
    }

    private static DataNode object(Object... keysAndValues) {
        DataNode object = DataNode.object();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            object.put((String) keysAndValues[i], node(keysAndValues[i + 1]));
        }
        return object;
    }

    private static DataNode node(Object value) {
        if (value instanceof DataNode node) {
            return node;
        }
        if (value instanceof String text) {
            return DataNode.of(text);
        }
        if (value instanceof Integer number) {
            return DataNode.of((long) number);
        }
        if (value instanceof Double number) {
            return DataNode.of(number);
        }
        return DataNode.of((Boolean) value);
    }

    private static <T> T read(Class<T> type, DataNode node) {
        SpecRegistry specs = EmpiricCore.specs();
        return specs.spec(type).read(TreeDataBuffer.reader(node), specs.context(Encoding.KEYED));
    }

    private static <T> DataNode write(Class<T> type, T value) {
        SpecRegistry specs = EmpiricCore.specs();
        TreeDataBuffer buffer = TreeDataBuffer.writer();
        specs.spec(type).write(buffer, value, specs.context(Encoding.KEYED));
        return buffer.result();
    }

    private static <T> T packed(Class<T> type, T value) {
        SpecRegistry specs = EmpiricCore.specs();
        DataBuffer buffer = new ByteArrayDataBuffer();
        specs.spec(type).write(buffer, value, specs.context(Encoding.PACKED));
        return specs.spec(type).read(new ByteArrayDataBuffer(((ByteArrayDataBuffer) buffer).toByteArray()), specs.context(Encoding.PACKED));
    }

    @SuppressWarnings("unchecked")
    private static IdSet<Block> idSet(DataNode node) {
        return (IdSet<Block>) read(IdSet.class, node);
    }

    private static void idSets(GameTestHelper helper) {
        IdSet<Block> stone = idSet(DataNode.of("minecraft:stone"));
        helper.assertTrue(stone.matches(Blocks.STONE) && stone.matches(Blocks.STONE.defaultBlockState()), "an id should match its block");
        helper.assertTrue(!stone.matches(Blocks.DIRT), "an id should not match another block");
        helper.assertTrue(stone.ids().size() == 1 && stone.tags().isEmpty(), "a lone id is one id, got " + stone);

        IdSet<Block> tag = idSet(DataNode.of("#minecraft:base_stone_overworld"));
        helper.assertTrue(tag.matches(Blocks.GRANITE) && tag.matches(Blocks.STONE), "a tag should match its blocks");
        helper.assertTrue(!tag.matches(Blocks.DIRT), "a tag should not match other blocks");
        helper.assertTrue(tag.tags().size() == 1 && tag.ids().isEmpty(), "a tag is listed as a tag, got " + tag);

        IdSet<Block> both = idSet(json("minecraft:dirt", "#minecraft:base_stone_overworld"));
        helper.assertTrue(both.matches(Blocks.DIRT) && both.matches(Blocks.DIORITE) && !both.matches(Blocks.SAND), "a list should match any element");

        IdSet<Block> any = idSet(DataNode.of("*"));
        helper.assertTrue(any.isAny() && any.matches(Blocks.SAND) && any.matches(Blocks.AIR), "* should match everything");
        helper.assertTrue(idSet(json("minecraft:stone", "*")).isAny(), "* anywhere in a list should match everything");
        helper.assertTrue(idSet(json()).isEmpty() && !idSet(json()).matches(Blocks.STONE), "an empty list should match nothing");

        IdSet<?> items = read(IdSet.class, DataNode.of("minecraft:apple"));
        helper.assertTrue(items.matches(Items.APPLE) && items.matches(new ItemStack(Items.APPLE)) && !items.matches(Items.STICK), "an id should match its item");
        IdSet<?> logs = read(IdSet.class, DataNode.of("#minecraft:logs"));
        helper.assertTrue(logs.matches(Items.OAK_LOG) && !logs.matches(Items.STICK), "an item tag should match its items");
        IdSet<?> fluids = read(IdSet.class, DataNode.of("minecraft:water"));
        helper.assertTrue(fluids.matches(Fluids.WATER) && !fluids.matches(Fluids.LAVA), "an id should match its fluid");
        IdSet<?> entities = read(IdSet.class, DataNode.of("minecraft:pig"));
        helper.assertTrue(entities.matches(EntityType.PIG) && !entities.matches(EntityType.COW), "an id should match its entity type");

        IdOrTag<Block> single = read(IdOrTag.class, DataNode.of("#minecraft:logs"));
        helper.assertTrue(single.tag() && single.location().toString().equals("minecraft:logs"), "an IdOrTag should read a tag, got " + single);
        helper.succeed();
    }

    private static void idSetForms(GameTestHelper helper) {
        helper.assertTrue(write(IdSet.class, idSet(DataNode.of("minecraft:stone"))).equals(DataNode.of("minecraft:stone")), "one element should be written as a string");
        DataNode two = write(IdSet.class, idSet(json("minecraft:stone", "#minecraft:logs")));
        helper.assertTrue(two.equals(json("minecraft:stone", "#minecraft:logs")), "two elements should be written as a list, got " + two);
        helper.assertTrue(write(IdSet.class, IdSet.any()).equals(DataNode.of("*")), "any should be written as *");
        for (IdSet<Block> set : List.of(IdSet.<Block>any(), IdSet.<Block>empty(), idSet(json("minecraft:stone", "#minecraft:logs")))) {
            helper.assertTrue(set.equals(packed(IdSet.class, set)), "a packed round trip should keep " + set);
        }
        boolean rejected = false;
        try {
            idSet(DataNode.of("not an id"));
        } catch (RuntimeException e) {
            rejected = true;
        }
        helper.assertTrue(rejected, "an invalid id should fail to read");
        helper.succeed();
    }

    public record Sample(int count, String label) {

        static final Codec<Sample> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("count").forGetter(Sample::count),
                Codec.STRING.fieldOf("label").forGetter(Sample::label)).apply(instance, Sample::new));
    }

    private static void codecSpecs(GameTestHelper helper) {
        Spec<Sample> spec = MinecraftSpecs.codec(Sample.CODEC);
        SpecRegistry registry = new SpecRegistry();
        MinecraftSpecs.register(registry, Sample.class, Sample.CODEC);
        helper.assertTrue(registry.spec(Sample.class) != null, "register should add the spec");

        Sample sample = new Sample(4, "four");
        TreeDataBuffer writer = TreeDataBuffer.writer();
        spec.write(writer, sample, registry.context(Encoding.KEYED));
        DataNode written = writer.result();
        helper.assertTrue(written.equals(object("count", 4, "label", "four")), "structured data should be the codec's JSON, got " + written);
        Sample structured = spec.read(TreeDataBuffer.reader(written), registry.context(Encoding.KEYED));
        helper.assertTrue(sample.equals(structured), "a structured round trip should keep the value, got " + structured);

        for (Encoding encoding : Encoding.values()) {
            ByteArrayDataBuffer buffer = new ByteArrayDataBuffer();
            spec.write(buffer, sample, registry.context(encoding));
            Sample bytes = spec.read(new ByteArrayDataBuffer(buffer.toByteArray()), registry.context(encoding));
            helper.assertTrue(sample.equals(bytes), "a " + encoding + " round trip should keep the value, got " + bytes);
        }
        helper.assertTrue(spec.copy(sample) == sample && spec.same(sample, new Sample(4, "four")) && !spec.same(sample, new Sample(5, "four")), "a codec spec compares with equals and doesn't copy");

        helper.succeed();
    }

    private static void vanillaEnums(GameTestHelper helper) {
        helper.assertTrue(read(Direction.class, DataNode.of("north")) == Direction.NORTH, "a serialized name should read");
        helper.assertTrue(read(Direction.class, DataNode.of("NORTH")) == Direction.NORTH, "the constant name should read");
        helper.assertTrue(read(Direction.class, DataNode.of("North")) == Direction.NORTH, "any case should read");
        helper.assertTrue(read(DyeColor.class, DataNode.of("light_blue")) == DyeColor.LIGHT_BLUE, "a snake case name should read");
        helper.assertTrue(read(DyeColor.class, DataNode.of("LIGHT_BLUE")) == DyeColor.LIGHT_BLUE, "the constant name should read");
        helper.assertTrue(read(DyeColor.class, DataNode.of("light-blue")) == DyeColor.LIGHT_BLUE, "dashes should read");
        helper.assertTrue(write(DyeColor.class, DyeColor.LIGHT_BLUE).equals(DataNode.of("LIGHT_BLUE")), "enums should still be written by constant name");
        boolean rejected = false;
        try {
            read(Direction.class, DataNode.of("sideways"));
        } catch (RuntimeException e) {
            rejected = e.getMessage().contains("north");
        }
        helper.assertTrue(rejected, "an unknown name should fail and list the valid ones");
        helper.succeed();
    }

    private static void components(GameTestHelper helper) {
        Component literal = read(Component.class, DataNode.of("hello"));
        helper.assertTrue(literal.getString().equals("hello"), "a plain string should read as literal text, got " + literal.getString());
        Component styled = read(Component.class, object("text", "hi", "color", "red"));
        helper.assertTrue(styled.getString().equals("hi") && styled.getStyle().getColor() != null, "an object should read as a full component, got " + styled);
        helper.succeed();
    }

    private static void positions(GameTestHelper helper) {
        BlockPos expected = new BlockPos(1, 2, 3);
        helper.assertTrue(read(BlockPos.class, json(1, 2, 3)).equals(expected), "an array should read");
        helper.assertTrue(read(BlockPos.class, object("x", 1, "y", 2, "z", 3)).equals(expected), "an object should read");
        helper.assertTrue(read(BlockPos.class, object("X", 1, "Y", 2, "Z", 3)).equals(expected), "an object with other case should read");
        helper.assertTrue(read(BlockPos.class, DataNode.of("1 2 3")).equals(expected), "spaces should read");
        helper.assertTrue(read(BlockPos.class, DataNode.of("1,2,3")).equals(expected), "commas should read");
        helper.assertTrue(read(BlockPos.class, DataNode.of(" 1, 2 ,3 ")).equals(expected), "loose text should read");
        helper.assertTrue(write(BlockPos.class, expected).equals(json(1, 2, 3)), "a position should still be written as an array");
        boolean rejected = false;
        try {
            read(BlockPos.class, object("x", 1, "y", 2));
        } catch (RuntimeException e) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a missing key should fail");

        helper.assertTrue(read(Vec3.class, json(1.5, 2.0, 3.0)).equals(new Vec3(1.5, 2, 3)), "a vec3 array should read");
        helper.assertTrue(read(Vec3.class, object("x", 1.5, "y", 2.0, "z", 3.0)).equals(new Vec3(1.5, 2, 3)), "a vec3 object should read");
        helper.assertTrue(read(Vector3d.class, object("x", 1.5, "y", 2.0, "z", 3.0)).equals(new Vector3d(1.5, 2, 3)), "a vector3d object should read");
        helper.assertTrue(read(Vector3i.class, DataNode.of("4 5 6")).equals(new Vector3i(4, 5, 6)), "a vector3i string should read");
        helper.assertTrue(packed(BlockPos.class, expected).equals(expected), "a packed round trip should keep the position");
        helper.succeed();
    }

    public record Tuning(String name, IntRange range, Chance chance, IdSet<Block> allowed) {

        static final Tuning DEFAULT = new Tuning("default", IntRange.exactly(1), Chance.ALWAYS, IdSet.empty());
    }

    private static void recordGameData(GameTestHelper helper) {
        DataNode entry = object("blocks", "minecraft:stone", "range", "2..4", "chance", "25%");
        Map<String, DataNode> files = Map.of("stone", entry, "bad", object("blocks", "minecraft:dirt", "range", "wrong"));
        Map<String, Exception> errors = TUNING.load(files);
        helper.assertTrue(errors.isEmpty(), "a record entry with a bad field should still load, got " + errors);
        Tuning stone = TUNING.get(Blocks.STONE);
        helper.assertTrue(stone.range().equals(new IntRange(2, 4)) && stone.chance().equals(new Chance(0.25)), "the entry should be read, got " + stone);
        helper.assertTrue(stone.name().equals("default"), "a missing key should take the default instance's value, got " + stone.name());
        Tuning dirt = TUNING.get(Blocks.DIRT);
        helper.assertTrue(dirt.range().equals(IntRange.exactly(1)), "a bad field should keep its default, got " + dirt.range());
        helper.assertTrue(TUNING.problems().containsKey("bad"), "the bad field should be reported, got " + TUNING.problems());
        helper.assertTrue(TUNING.get(Blocks.SAND) == TUNING.defaultValue() && TUNING.defaultValue().equals(Tuning.DEFAULT), "an unlisted block should use the DEFAULT constant");
        TUNING.load(Map.of());
        helper.succeed();
    }
}
