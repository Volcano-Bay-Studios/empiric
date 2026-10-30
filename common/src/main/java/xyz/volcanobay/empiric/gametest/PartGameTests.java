package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.oct.OctBlockEntityComplement;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.part.Workspace;
import xyz.volcanobay.empiric.api.part.Hitbox;
import xyz.volcanobay.empiric.api.part.OrientedBox;
import xyz.volcanobay.empiric.api.part.PartOctEntity;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.PartTypes;
import xyz.volcanobay.empiric.api.part.Parts;
import xyz.volcanobay.empiric.api.part.Piece;
import xyz.volcanobay.empiric.api.part.PieceRuntime;
import xyz.volcanobay.empiric.api.part.Placement;
import xyz.volcanobay.empiric.api.part.Selection;
import xyz.volcanobay.empiric.api.part.PlacementFailure;
import xyz.volcanobay.empiric.api.part.Spot;
import xyz.volcanobay.empiric.api.part.trait.Controls;
import xyz.volcanobay.empiric.api.part.trait.Hitboxes;
import xyz.volcanobay.empiric.api.part.trait.Shapes;
import xyz.volcanobay.empiric.api.part.trait.Socketed;
import xyz.volcanobay.empiric.api.part.trait.Sockets;
import xyz.volcanobay.empiric.api.part.trait.Support;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class PartGameTests {

    static PartType CRATE;
    static PartType SLAB;
    static PartType RACK;
    static PartType PEG;
    static PartType PANEL;
    static int FLAG;

    private PartGameTests() {
    }

    static void bootstrap() {
        CRATE = PartTypes.register(PartType.builder(id("test_crate"), 4, 6, 4).build());
        SLAB = PartTypes.register(PartType.builder(id("test_slab"), 14, 1, 14).with(PartTraits.SUPPORT, new Support(1)).with(PartTraits.SOLID).build());
        RACK = PartTypes.register(PartType.builder(id("test_rack"), 16, 9, 4)
                .with(PartTraits.SOCKETS, new Sockets(List.of(new Sockets.Socket(1, 1), new Sockets.Socket(4, 1), new Sockets.Socket(7, 1)), 2))
                .with(PartTraits.SUPPORT, new Support(9)).with(PartTraits.SOLID).with(PartTraits.ROTATABLE)
                .with(PartTraits.SHAPES, new Shapes(List.of(new AABB(0, 0, 0, 16, 2, 4), new AABB(0, 2, 0, 0.5, 9, 4),
                        new AABB(15.5, 2, 0, 16, 9, 4), new AABB(0, 8.5, 0, 16, 9, 4))))
                .build());
        PEG = PartTypes.register(PartType.builder(id("test_peg"), 2, 8, 2).with(PartTraits.SOCKETED, new Socketed()).build());
        double slope = Math.toDegrees(Math.atan2(4, 8));
        PANEL = PartTypes.register(PartType.builder(id("test_panel"), 10, 4, 8)
                .with(PartTraits.HITBOXES, Hitboxes.of(Hitbox.tilted(new AABB(0, -0.5, 0, 10, 0, Math.sqrt(80)), 0, 0, slope)))
                .with(PartTraits.CONTROLS, new Controls(List.of(new Controls.Control("knob",
                        Hitbox.tilted(new AABB(4, 0, 3, 6, 0.3, 5), 0, 0, slope), (context, control) -> InteractionResult.SUCCESS))))
                .with(PartTraits.SNEAK_PICKUP).build());
        FLAG = PieceRuntime.flag(id("test_flag"));
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("parts_many_in_one_block", floored(PartGameTests::manyInOneBlock)),
                test.apply("parts_overlap_across_blocks", floored(PartGameTests::overlapAcrossBlocks)),
                test.apply("parts_span_octants", floored(PartGameTests::spanOctants)),
                test.apply("parts_share_block_with_other_octs", floored(PartGameTests::shareBlock)),
                test.apply("parts_all_or_nothing", floored(PartGameTests::allOrNothing)),
                test.apply("parts_sockets_snap", floored(PartGameTests::socketsSnap)),
                test.apply("parts_rotated_footprint", floored(PartGameTests::rotated)),
                test.apply("parts_survive_saving", floored(PartGameTests::saveAndLoad)),
                test.apply("parts_bad_entries_skipped", floored(PartGameTests::badEntries)),
                test.apply("parts_snap_to_free_spot", floored(PartGameTests::snapToFreeSpot)),
                test.apply("parts_stand_on_supports", floored(PartGameTests::standOnSupports)),
                test.apply("parts_settle", floored(PartGameTests::settle)),
                test.apply("parts_only_solid_collide", floored(PartGameTests::collision)),
                test.apply("parts_handheld_not_placed", floored(PartGameTests::handheld)),
                test.apply("parts_tilted_hitboxes", floored(PartGameTests::tiltedHitboxes)),
                test.apply("parts_outline_union", floored(PartGameTests::outlineUnion)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Empiric.MODID, path);
    }

    private static Consumer<GameTestHelper> floored(Consumer<GameTestHelper> test) {
        return helper -> {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            test.accept(helper);
        };
    }

    private static BlockPos host(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 1, 1));
    }

    private static boolean place(ServerLevel level, Spot spot, PartType type) {
        return Parts.place(level, spot, type, ItemStack.EMPTY) != null;
    }

    private static List<Piece> pieces(ServerLevel level, BlockPos host) {
        Workspace workspace = Workspace.of(LevelOctExtension.get(level), host);
        return workspace.pieces().stream().filter(workspace::isHome).toList();
    }

    private static BlockHitResult onTop(BlockPos block, double x, double z) {
        return new BlockHitResult(Vec3.atLowerCornerOf(block).add(x / 16, 1, z / 16), Direction.UP, block, false);
    }

    private static BlockHitResult topOf(BlockPos host, Piece piece) {
        Vec3 at = Vec3.atLowerCornerOf(host).add((piece.x + piece.width() / 2.0) / 16, piece.maxY() / 16.0, (piece.z + piece.depth() / 2.0) / 16);
        return new BlockHitResult(at, Direction.UP, host, false);
    }

    private static void complement(OctLevel oct, BlockPos host, int octant) {
        oct.setOctState(host, octant, EmpiricBlocks.OCT_BLOCK_COMPLEMENT.get().defaultBlockState());
        if (oct.getOctEntity(host, octant) instanceof OctBlockEntityComplement complement) {
            complement.setSourcePos(new OctPos(host, octant == 0 ? 1 : 0));
        }
    }

    private static FullOctBlockEntity reload(ServerLevel level, BlockPos pos, Consumer<CompoundTag> edit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
        edit.accept(tag);
        return (FullOctBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), tag, level.registryAccess());
    }

    private static void manyInOneBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        for (int[] at : new int[][]{{0, 0}, {5, 0}, {0, 6}, {9, 9}}) {
            helper.assertTrue(place(level, new Spot(host, at[0], 0, at[1]), CRATE), "crate at " + at[0] + "," + at[1] + " wasn't placed");
        }
        helper.assertTrue(level.getBlockState(host).is(EmpiricBlocks.OCT_BLOCK.get()), "placing didn't make an oct block");
        helper.assertTrue(pieces(level, host).size() == 4, "expected 4 crates, found " + pieces(level, host).size());
        helper.assertTrue(LevelOctExtension.get(level).getOctState(host, 0).is(EmpiricBlocks.PART.get()), "octant 0 isn't the crate's cell");
        helper.succeed();
    }

    private static void overlapAcrossBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel oct = LevelOctExtension.get(level);
        BlockPos host = host(helper);
        helper.assertTrue(place(level, new Spot(host, 6, 0, 6), CRATE), "first crate wasn't placed");
        helper.assertTrue(!Parts.canFit(level, host, CRATE, 7, 0, 6, 0, null), "an overlapping crate was allowed");
        helper.assertTrue(Parts.canFit(level, host, CRATE, 10, 0, 6, 0, null), "a crate beside the first was refused");
        helper.assertTrue(place(level, new Spot(host, 14, 0, 6), CRATE), "a crate over the block's edge was refused");
        BlockPos east = host.east();
        helper.assertTrue(Parts.isPart(oct.getOctState(east, 0)), "the block the crate reaches into should hold its cell");
        Workspace beside = Workspace.of(oct, east);
        helper.assertTrue(beside.pieces().stream().anyMatch(piece -> piece.x == -2 && piece.z == 6), "the next block should see the crate 2 px into it");
        helper.assertTrue(!Parts.canFit(level, east, CRATE, 0, 0, 6, 0, null), "a crate overlapping one from the next block was allowed");
        UUID id = beside.pieces().stream().filter(piece -> piece.x == -2).findFirst().orElseThrow().id;
        Parts.remove(level, east, id);
        helper.assertTrue(Workspace.of(oct, host).find(id) == null && !level.getBlockState(east).is(EmpiricBlocks.OCT_BLOCK.get()), "removing it should clear both blocks");
        helper.succeed();
    }

    private static void spanOctants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel oct = LevelOctExtension.get(level);
        BlockPos host = host(helper);
        helper.assertTrue(place(level, new Spot(host, 6, 0, 6), CRATE), "crate wasn't placed");
        List<Integer> cells = Parts.cells(CRATE, 6, 0, 6);
        helper.assertTrue(cells.size() == 4, "a crate over the middle should touch 4 octants, not " + cells);
        for (int cell : cells) {
            helper.assertTrue(Parts.isPart(oct.getOctState(host, cell)), "octant " + cell + " isn't a part cell");
        }
        helper.assertTrue(Parts.entity(oct, host, 0).pieces().size() == 1 && Parts.entity(oct, host, 1).isEmpty(), "only the anchor octant stores the piece");
        Parts.remove(level, host, pieces(level, host).get(0).id);
        helper.assertTrue(level.getBlockState(host).isAir(), "removing the only piece should leave the block empty");
        helper.succeed();
    }

    private static void shareBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel oct = LevelOctExtension.get(level);
        BlockPos host = host(helper);
        complement(oct, host, 7);
        helper.assertTrue(place(level, new Spot(host, 0, 0, 0), CRATE), "a part should fit beside other oct content");
        helper.assertTrue(!Parts.canFit(level, host, CRATE, 10, 10, 10, 0, null), "a part was allowed over other oct content");
        helper.assertTrue(oct.getOctState(host, 7).is(EmpiricBlocks.OCT_BLOCK_COMPLEMENT.get()), "the other oct content was replaced");
        helper.succeed();
    }

    private static void allOrNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel oct = LevelOctExtension.get(level);
        BlockPos host = host(helper);
        complement(oct, host, 1);
        helper.assertTrue(Parts.place(level, new Spot(host, 6, 0, 0), CRATE, ItemStack.EMPTY) == null, "a crate reaching into other oct content was placed");
        helper.assertTrue(oct.getOctState(host, 0).isAir(), "a refused piece left a cell behind");
        helper.succeed();
    }

    private static void socketsSnap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        Spot rack = new Spot(host, 0, 0, 4);
        helper.assertTrue(place(level, rack, RACK), "rack wasn't placed");
        Sockets sockets = RACK.get(PartTraits.SOCKETS);
        for (int i = 0; i < sockets.positions().size(); i++) {
            Sockets.Socket socket = sockets.positions().get(i);
            Vec3 aim = new Vec3(host.getX() + (rack.x() + socket.x() + 1) / 16.0, host.getY() + RACK.height() / 16.0, host.getZ() + (rack.z() + socket.z() + 1) / 16.0);
            Placement.Result result = Placement.find(level, PEG, new BlockHitResult(aim, Direction.UP, host, false), Direction.NORTH);
            Spot snapped = result.spot();
            helper.assertTrue(result.ok() && snapped.x() == rack.x() + socket.x() && snapped.z() == rack.z() + socket.z() && snapped.y() == sockets.floor(),
                    "peg " + i + " didn't snap into its socket: " + result);
            helper.assertTrue(place(level, snapped, PEG), "peg " + i + " wasn't placed");
        }
        int expected = sockets.positions().size() + 1;
        helper.assertTrue(pieces(level, host).size() == expected, "expected the rack and its pegs, found " + pieces(level, host).size());
        Placement.settle(level, host);
        helper.assertTrue(pieces(level, host).size() == expected, "pegs in a rack should stay put when the block settles");
        helper.assertTrue(!Parts.canFit(level, host, CRATE, 1, 2, 4, 0, null), "a crate shouldn't stand in a socket");
        helper.succeed();
    }

    private static void rotated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        Placement.Result result = Placement.find(level, RACK, onTop(host.below(), 8, 8), Direction.EAST);
        helper.assertTrue(result.ok() && result.spot().rotation() == Direction.EAST.get2DDataValue(), "a rack placed facing east should turn: " + result);
        helper.assertTrue(place(level, result.spot(), RACK), "rack wasn't placed");
        Piece rack = pieces(level, host).get(0);
        helper.assertTrue(rack.width() == 4 && rack.depth() == 16, "a quarter turn should swap the footprint: " + rack.width() + "x" + rack.depth());
        Vec3 aim = Vec3.atLowerCornerOf(host).add((rack.x + 1.5) / 16, rack.maxY() / 16.0, (rack.z + 6) / 16.0);
        Placement.Result peg = Placement.find(level, PEG, new BlockHitResult(aim, Direction.UP, host, false), Direction.EAST);
        helper.assertTrue(peg.ok() && peg.spot().y() == rack.y + RACK.get(PartTraits.SOCKETS).floor(), "a peg should snap into a turned rack at " + rack.x + "," + rack.z + ": " + peg);
        helper.assertTrue(rack.box().contains(peg.spot().x() + 1, rack.y + 1, peg.spot().z() + 1), "the socket should be inside the turned rack");
        helper.succeed();
    }

    private static void saveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        Piece placed = Parts.place(level, new Spot(host, 6, 0, 6, 1), CRATE, ItemStack.EMPTY);
        helper.assertTrue(placed != null, "crate wasn't placed");
        placed.runtime.status = FLAG;
        FullOctBlockEntity loaded = reload(level, host, tag -> {
        });
        helper.assertTrue(loaded.getOctEntity(0) instanceof PartOctEntity entity && entity.pieces().size() == 1, "the piece wasn't restored");
        Piece piece = ((PartOctEntity) loaded.getOctEntity(0)).pieces().get(0);
        helper.assertTrue(piece.type == CRATE && piece.x == 6 && piece.z == 6 && piece.rotation == 1, "the piece moved while saving");
        helper.assertTrue(piece.runtime.has(FLAG), "the status flag wasn't saved");
        helper.succeed();
    }

    private static void badEntries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        helper.assertTrue(place(level, new Spot(host, 0, 0, 0), CRATE), "crate wasn't placed");
        FullOctBlockEntity loaded = reload(level, host, tag -> {
            ListTag list = tag.getCompound("0").getList("pieces", 10);
            CompoundTag unknown = list.getCompound(0).copy();
            unknown.putString("type", "empiric:no_such_part");
            list.add(unknown);
            CompoundTag broken = list.getCompound(0).copy();
            broken.putString("type", "::not a location::");
            list.add(broken);
            CompoundTag noId = list.getCompound(0).copy();
            noId.remove("id");
            list.add(noId);
        });
        PartOctEntity entity = (PartOctEntity) loaded.getOctEntity(0);
        helper.assertTrue(entity.pieces().size() == 1, "bad entries should be skipped, not " + entity.pieces().size() + " pieces");
        helper.succeed();
    }

    private static void snapToFreeSpot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        helper.assertTrue(place(level, new Spot(host, 0, 0, 0), CRATE), "first crate wasn't placed");
        Placement.Result result = Placement.find(level, CRATE, onTop(host.below(), 2, 2), Direction.NORTH);
        helper.assertTrue(result.ok(), "no spot was found next to the crate: " + result);
        Spot spot = result.spot();
        int x = (spot.host().getX() - host.getX()) * 16 + spot.x();
        int z = (spot.host().getZ() - host.getZ()) * 16 + spot.z();
        helper.assertTrue(Math.max(Math.abs(x), Math.abs(z)) == 4 && Math.min(Math.abs(x), Math.abs(z)) == 0,
                "the spot should be the closest one clear of the crate, it's at " + x + ", " + z);
        helper.succeed();
    }

    private static void standOnSupports(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        Placement.Result floor = Placement.find(level, SLAB, onTop(host.below(), 8, 8), Direction.NORTH);
        helper.assertTrue(floor.ok() && floor.spot().y() == 0 && place(level, floor.spot(), SLAB), "a slab should go on the floor at 0: " + floor);
        Piece slab = pieces(level, host).get(0);
        Placement.Result onSlab = Placement.find(level, CRATE, topOf(host, slab), Direction.NORTH);
        helper.assertTrue(onSlab.ok() && onSlab.spot().y() == 1 && place(level, onSlab.spot(), CRATE), "a crate should stand on the slab: " + onSlab);
        Piece crate = pieces(level, host).stream().filter(piece -> piece.type == CRATE).findFirst().orElseThrow();
        Placement.Result onCrate = Placement.find(level, CRATE, topOf(host, crate), Direction.NORTH);
        helper.assertTrue(!onCrate.ok() || onCrate.spot().y() == 1, "nothing should stand on a part that isn't a support: " + onCrate);
        BlockPos high = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.assertTrue(place(level, new Spot(high, 0, 0, 0), RACK), "rack wasn't placed");
        Placement.Stand hanging = Placement.support(level, high, 10, 3, 4, 4, 16, null);
        helper.assertTrue(hanging.top() == 9 && !hanging.stable(), "a crate mostly off the rack should be unstable: " + hanging);
        helper.assertTrue(Placement.support(level, high, 12, 8, 4, 4, 16, null).top() < 0, "nothing should hold up a crate in mid air");
        BlockPos slabBlock = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(slabBlock, Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        Placement.Result partial = Placement.find(level, CRATE, new BlockHitResult(Vec3.atLowerCornerOf(slabBlock).add(0.5, 0.5, 0.5), Direction.UP, slabBlock, false), Direction.NORTH);
        helper.assertTrue(partial.failure() == PlacementFailure.PARTIAL_SURFACE, "a slab top isn't somewhere parts fit: " + partial);
        helper.succeed();
    }

    private static void settle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        helper.assertTrue(place(level, new Spot(host, 1, 0, 1), SLAB) && place(level, new Spot(host, 6, 1, 6), CRATE), "slab and crate weren't placed");
        Piece slab = pieces(level, host).stream().filter(piece -> piece.type == SLAB).findFirst().orElseThrow();
        Parts.remove(level, host, slab.id);
        helper.succeedWhen(() -> {
            List<Piece> left = pieces(level, host);
            helper.assertTrue(left.size() == 1 && left.get(0).y == 0, "the crate should have settled onto the floor: " + left.stream().map(piece -> piece.y).toList());
        });
    }

    private static void collision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crates = host(helper);
        BlockPos slabs = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.assertTrue(place(level, new Spot(crates, 6, 0, 6), CRATE) && place(level, new Spot(slabs, 1, 0, 1), SLAB), "parts weren't placed");
        helper.assertTrue(level.getBlockState(crates).getCollisionShape(level, crates).isEmpty(), "a part without the solid trait shouldn't collide");
        helper.assertTrue(!level.getBlockState(crates).getShape(level, crates).isEmpty(), "it should still be selectable");
        helper.assertTrue(!level.getBlockState(slabs).getCollisionShape(level, slabs).isEmpty(), "a solid part should collide");
        helper.succeed();
    }

    private static void handheld(GameTestHelper helper) {
        PartType held = PartType.builder(id("test_held"), 2, 2, 2).with(PartTraits.HANDHELD).build();
        Placement.Result result = Placement.find(helper.getLevel(), held, onTop(host(helper).below(), 8, 8), Direction.NORTH);
        helper.assertTrue(!result.ok() && result.failure() == PlacementFailure.NONE, "a handheld part shouldn't be placed: " + result);
        helper.succeed();
    }

    private static void outlineUnion(GameTestHelper helper) {
        double single = length(OrientedBox.outlineEdges(List.of(OrientedBox.aligned(new AABB(0, 0, 0, 4, 2, 4)))));
        helper.assertTrue(Math.abs(single - 40) < 0.01, "a lone box should outline its 12 edges, got " + single);
        double flat = length(OrientedBox.outlineEdges(List.of(OrientedBox.aligned(new AABB(0, 0, 0, 2, 2, 4)), OrientedBox.aligned(new AABB(2, 0, 0, 4, 2, 4)))));
        helper.assertTrue(Math.abs(flat - 40) < 0.01, "two boxes forming one should outline as one, got " + flat);
        double crossed = length(OrientedBox.outlineEdges(List.of(OrientedBox.aligned(new AABB(0, 0, 0, 4, 2, 4)), OrientedBox.aligned(new AABB(1, 0, 1, 3, 4, 3)))));
        helper.assertTrue(Math.abs(crossed - (40 + 4 * 2 + 4 * 2)) < 0.01, "a post through a slab should hide its buried part, got " + crossed);
        Hitbox tilted = Hitbox.tilted(new AABB(0, -1, 0, 4, 0, 3), 2, 0, 20);
        for (Vec3[] edge : OrientedBox.outlineEdges(List.of(OrientedBox.aligned(new AABB(0, 0, 0, 4, 2, 4)), new OrientedBox(tilted.box(), tilted.transform())))) {
            Vec3 middle = edge[0].add(edge[1]).scale(0.5);
            helper.assertTrue(!new AABB(0, 0, 0, 4, 2, 4).deflate(0.01).contains(middle), "an edge runs inside the shape at " + middle);
        }
        helper.succeed();
    }

    private static double length(List<Vec3[]> edges) {
        return edges.stream().mapToDouble(edge -> edge[0].distanceTo(edge[1])).sum();
    }

    private static void tiltedHitboxes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos host = host(helper);
        helper.assertTrue(place(level, new Spot(host, 2, 0, 2), PANEL), "the panel wasn't placed");
        OctLevel oct = LevelOctExtension.get(level);
        Vec3 origin = Vec3.atLowerCornerOf(host);
        double angle = Math.atan2(4, 8);
        Vec3 knob = origin.add((2 + 5) / 16.0, 0, (2 + 4 * Math.cos(angle)) / 16.0);
        Selection down = Selection.ray(oct, host, knob.add(0, 15 / 16.0, 0), knob.add(0, -1 / 16.0, 0));
        helper.assertTrue(down != null && down.layer() instanceof Controls && down.index() == 0, "looking down at the knob should aim at it: " + down);
        helper.assertTrue(!down.outline().get(0).isAligned(), "the knob should be outlined turned");

        Vec3 overLowEnd = origin.add(0, 3.5 / 16.0, (2 + 1) / 16.0);
        Selection over = Selection.ray(oct, host, overLowEnd, overLowEnd.add(1, 0, 0));
        helper.assertTrue(over == null, "a ray over the low end of the slope should miss, though the panel's box is there: " + over);

        Vec3 highEnd = origin.add((2 + 2) / 16.0, 15 / 16.0, (2 + 7.5) / 16.0);
        Selection body = Selection.ray(oct, host, highEnd, highEnd.add(0, -1, 0));
        helper.assertTrue(body != null && body.isBody() && body.outline().size() == 1, "looking down at the high end should aim at the body: " + body);
        helper.succeed();
    }
}
