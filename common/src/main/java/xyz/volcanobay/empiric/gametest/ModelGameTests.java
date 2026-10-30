package xyz.volcanobay.empiric.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.model.EmpiricModels;
import xyz.volcanobay.empiric.client.model.TranslucentEntry;
import xyz.volcanobay.empiric.model.ModelFace;
import xyz.volcanobay.empiric.model.ModelGeometry;
import xyz.volcanobay.empiric.model.ModelRenderKind;
import xyz.volcanobay.empiric.model.ObjParser;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The model system's parts that don't need a GPU. Drawing is checked by running the client.
 */
final class ModelGameTests {

    private static final String OBJ = """
            mtllib sample.mtl
            o base
            v 0 0 0
            v 1 0 0
            v 1 0 1
            v 0 0 1
            v 0.5 1 0.5
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            vn 0 2 0
            usemtl stone
            f 1/1/1 2/2/1 3/3/1 4/4/1
            g roof # a comment
            usemtl glass
            f 1/1 2/2 5
            f -5 -4 -3 -2 -1
            """;

    private static final String MTL = """
            newmtl stone
            map_Kd textures/block/stone.png
            newmtl glass
            Kd 1 1 1
            map_Kd other:block/glass
            d 0.5
            """;

    private ModelGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("translucent_sort_order", ModelGameTests::translucentSortOrder),
                test.apply("model_obj_parse", ModelGameTests::objParse),
                test.apply("model_obj_errors", ModelGameTests::objErrors),
                test.apply("model_registry", ModelGameTests::registry));
    }

    private static void objParse(GameTestHelper helper) {
        ModelGeometry geometry = ObjParser.parse(OBJ, "empiric", name -> name.equals("sample.mtl") ? MTL : null);
        helper.assertTrue(List.copyOf(geometry.parts().keySet()).equals(List.of("base", "roof")), "the parts were " + geometry.parts().keySet());

        List<ModelFace> base = geometry.parts().get("base");
        helper.assertTrue(base.size() == 1 && base.getFirst().vertices().size() == 4, "the quad didn't stay a quad");
        ModelFace quad = base.getFirst();
        helper.assertTrue(quad.texture().equals(ResourceLocation.parse("empiric:block/stone")), "a textures/ path with .png gave " + quad.texture());
        helper.assertTrue(quad.kind() == ModelRenderKind.CUTOUT && quad.shade() && !quad.atlasUv(), "the default material wasn't a shaded cutout");
        helper.assertTrue(quad.vertices().get(0).v() == 1 && quad.vertices().get(2).v() == 0, "V wasn't flipped from OBJ's bottom-up to Minecraft's top-down");
        helper.assertTrue(quad.vertices().get(0).normal().equals(0, 1, 0), "a given normal wasn't normalised: " + quad.vertices().get(0).normal());

        List<ModelFace> roof = geometry.parts().get("roof");
        helper.assertTrue(roof.size() == 4, "a triangle and a pentagon should give 1 + 3 faces, not " + roof.size());
        helper.assertTrue(roof.getFirst().vertices().size() == 3, "the triangle became " + roof.getFirst().vertices().size() + " vertices");
        for (ModelFace face : roof) {
            helper.assertTrue(face.kind() == ModelRenderKind.TRANSLUCENT && face.texture().equals(ResourceLocation.parse("other:block/glass")),
                    "the glass material wasn't translucent with its own namespace");
        }
        helper.assertTrue(roof.getFirst().vertices().get(0).normal().z() > 0.8f && roof.getFirst().vertices().get(0).normal().y() < 0, "a missing normal wasn't worked out from the face");
        helper.assertTrue(roof.get(1).vertices().get(0).position().equals(0, 0, 0) && roof.get(3).vertices().get(2).position().equals(0.5f, 1, 0.5f),
                "negative indices didn't count back from the last vertex");
        helper.assertTrue(geometry.faceCount() == 5, "the face count was " + geometry.faceCount());

        ModelGeometry noLibrary = ObjParser.parse(OBJ, "empiric", name -> null);
        helper.assertTrue(noLibrary.parts().get("base").getFirst().texture().getPath().equals("missingno"), "a missing .mtl didn't fall back to the missing texture");
        ModelGeometry exported = ObjParser.parse("mtllib plate.mtl\nv 0 0 0\nv 1 0 0\nv 1 0 1\nusemtl m\nf 1 2 3", "empiric", "block/chemistry/",
                name -> name.equals("plate.mtl") ? "newmtl m\nmap_Kd plate.png" : null);
        ResourceLocation bare = exported.parts().values().iterator().next().getFirst().texture();
        helper.assertTrue(bare.equals(ResourceLocation.parse("empiric:block/chemistry/plate")), "a bare Blockbench texture name gave " + bare);

        helper.assertTrue(ModelRenderKind.fromName("minecraft:cutout_mipped", ModelRenderKind.SOLID) == ModelRenderKind.CUTOUT
                && ModelRenderKind.fromName("translucent", ModelRenderKind.SOLID) == ModelRenderKind.TRANSLUCENT
                && ModelRenderKind.fromName("minecraft:nonsense", ModelRenderKind.SOLID) == ModelRenderKind.SOLID, "render type names were read wrongly");
        helper.succeed();
    }

    private static void objErrors(GameTestHelper helper) {
        String[][] cases = {
                {"v 0 0 0\nf 1 1", "OBJ line 2"},
                {"v 0 0 0\nf 1 1 9", "OBJ line 2"},
                {"v 0 0\n", "OBJ line 1"},
                {"v 0 0 x\n", "OBJ line 1"},
                {"\n\nf 0 0 0", "OBJ line 3"}
        };
        for (String[] entry : cases) {
            try {
                ObjParser.parse(entry[0], "empiric", name -> null);
                helper.fail("Parsed " + entry[0].replace("\n", "\\n"));
                return;
            } catch (IllegalArgumentException e) {
                helper.assertTrue(e.getMessage().startsWith(entry[1]), "The error '" + e.getMessage() + "' didn't point at " + entry[1]);
            }
        }
        helper.succeed();
    }

    private static void registry(GameTestHelper helper) {
        EmpiricModel json = EmpiricModels.register(Empiric.id("block/test_model"));
        EmpiricModel obj = EmpiricModels.register(Empiric.id("obj/test_model.obj"));
        helper.assertTrue(EmpiricModels.register(Empiric.id("block/test_model")) == json, "registering an id twice made a second model");
        helper.assertTrue(!json.isObj() && obj.isObj(), "the .obj suffix didn't make an OBJ");
        helper.assertTrue(json.file().toString().equals("empiric:models/block/test_model.json"), "the JSON file was " + json.file());
        helper.assertTrue(obj.file().toString().equals("empiric:models/obj/test_model.obj"), "the OBJ file was " + obj.file());
        helper.assertTrue(EmpiricModels.all().containsAll(List.of(json, obj)), "registered models weren't listed");
        List<ResourceLocation> ids = EmpiricModels.jsonIds();
        helper.assertTrue(ids.contains(json.id()) && !ids.contains(obj.id()), "only JSON models should go to the loaders");
        helper.assertTrue(json.part("blade").name().equals("blade") && json.part("blade").model() == json, "a part didn't remember its model and name");
        helper.succeed();
    }

    private static void translucentSortOrder(GameTestHelper helper) {
        // far first; at the same distance lower layers first; then the order queued
        int[] order = TranslucentEntry.order(new double[]{1, 9, 4, 4, 4}, new int[]{0, 0, 2, 0, 0});
        helper.assertTrue(Arrays.equals(order, new int[]{1, 3, 4, 2, 0}), "translucent draws sorted wrongly: " + Arrays.toString(order));
        helper.succeed();
    }
}
