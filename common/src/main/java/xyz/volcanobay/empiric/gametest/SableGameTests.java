package xyz.volcanobay.empiric.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.api.attachment.SubLevelSplit;
import xyz.volcanobay.empiric.api.behavior.SplitBehavior;
import xyz.volcanobay.empiric.api.module.SubLevelModuleType;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Sub-level attachment tests. Registered only when Sable is installed.
 */
final class SableGameTests {

    static SubLevelModuleType<Hull> HULL;
    static SubLevelModuleType<Cargo> CARGO;
    static SubLevelModuleType<Flag> FLAG;

    private SableGameTests() {
    }

    static void register(ModuleSystem modules) {
        HULL = modules.register(SubLevelModuleType.builder(Empiric.id("test_hull"), Hull.class, Hull::new).build());
        modules.subscribe(EmpiricTestModules.SUBSCRIBER, HULL);
        CARGO = modules.register(SubLevelModuleType.builder(Empiric.id("test_cargo"), Cargo.class, Cargo::new).build());
        modules.subscribe(EmpiricTestModules.SUBSCRIBER, CARGO);
        FLAG = modules.register(SubLevelModuleType.builder(Empiric.id("test_flag"), Flag.class, Flag::new).build());
        modules.subscribe(EmpiricTestModules.SUBSCRIBER, FLAG);
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(test.apply("sub_level_module", SableGameTests::subLevelModule),
                test.apply("sub_level_split", SableGameTests::subLevelSplit));
    }

    private static void subLevelModule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        helper.assertTrue(container != null, "level has no sub-level container");
        ServerSubLevel subLevel = spawn(container, helper.absoluteVec(new Vec3(1.5, 4.5, 1.5)));
        SubLevelAttachment attachment = SubLevelAttachment.of(subLevel);
        helper.assertTrue(attachment.isActive(), "new sub-level isn't active");
        helper.assertTrue(attachment.side() == Side.SERVER, "sub-level attachment isn't on the server");

        HULL.attach(subLevel).integrity = 42;
        boolean[] seen = {false};
        SubLevelAttachment.TYPE.forEachLoaded(Side.SERVER, loaded -> seen[0] |= loaded == attachment);
        helper.assertTrue(seen[0], "sub-level isn't listed as loaded");
        helper.assertTrue(SubLevelAttachment.find(level, subLevel.getUniqueId()) == attachment, "sub-level isn't found by its id");

        SubLevelData data = SubLevelSerializer.toData(subLevel, List.of());
        CompoundTag userData = subLevel.getUserDataTag();
        helper.assertTrue(userData != null && userData.contains(WorldModuleSystem.TAG), "serializing didn't write the modules to the user data");

        Hull before = HULL.get(subLevel);
        container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        helper.assertTrue(!attachment.isActive(), "removed sub-level is still active");
        helper.assertTrue(HULL.get(attachment) == null && before.detached, "removing the sub-level didn't detach its modules");

        ServerSubLevel loaded = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(data.fullTag()));
        helper.assertTrue(loaded != null && loaded != subLevel, "sub-level didn't load back");
        Hull restored = HULL.get(loaded);
        helper.assertTrue(restored != null && restored.integrity == 42, "loaded sub-level lost its module: " + restored);

        container.removeSubLevel(loaded, SubLevelRemovalReason.REMOVED);
        helper.succeed();
    }

    private static void subLevelSplit(GameTestHelper helper) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        helper.assertTrue(container != null, "level has no sub-level container");
        ServerSubLevel subLevel = spawn(container, helper.absoluteVec(new Vec3(1.5, 4.5, 1.5)));
        LevelPlot plot = subLevel.getPlot();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = 1; x <= 4; x++) {
            plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(x, 0, 0), stone, 3);
        }
        Hull hull = HULL.attach(subLevel);
        hull.integrity = 40;
        CARGO.attach(subLevel).load = 7;
        FLAG.attach(subLevel).color = 3;

        helper.runAfterDelay(2, () -> plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(2, 0, 0), Blocks.AIR.defaultBlockState(), 3));
        helper.succeedWhen(() -> {
            SubLevelSplit split = hull.split;
            helper.assertTrue(split != null, "the sub-level didn't split");
            helper.assertTrue(split.from().subLevel() == subLevel, "split came from the wrong sub-level");
            BlockPos center = plot.getCenterBlock();
            helper.assertTrue(split.blocks().size() == 2 && split.moved(center.offset(3, 0, 0)) && split.moved(center.offset(4, 0, 0)), "wrong blocks moved: " + split.blocks());
            ServerSubLevel into = (ServerSubLevel) split.into().subLevel();
            helper.assertTrue(into.getLevel().getBlockState(split.newPos(center.offset(4, 0, 0))).is(Blocks.STONE), "newPos doesn't point at the moved block");

            Hull copy = HULL.get(into);
            helper.assertTrue(copy != null && copy != hull, "hull wasn't copied");
            helper.assertTrue(copy.integrity + hull.integrity == 40 && copy.integrity > 0 && hull.integrity > 0, "hull integrity wasn't divided: " + hull + " / " + copy);
            Cargo cargo = CARGO.get(into);
            helper.assertTrue(cargo != null && cargo.load == 7 && CARGO.get(subLevel) == null, "cargo didn't move");
            helper.assertTrue(FLAG.get(subLevel) != null && FLAG.get(into) == null, "module without SplitBehavior didn't stay");

            container.removeSubLevel(into, SubLevelRemovalReason.REMOVED);
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        });
    }

    private static ServerSubLevel spawn(ServerSubLevelContainer container, Vec3 position) {
        Pose3d pose = new Pose3d();
        pose.position().set(position.x, position.y, position.z);
        ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
        subLevel.updateLastPose();
        return subLevel;
    }

    public static class Hull extends Module<SubLevelAttachment> implements SplitBehavior {

        @Data
        public int integrity;

        boolean detached;
        SubLevelSplit split;

        @Override
        public void onSplit(SubLevelSplit split) {
            this.split = split;
            Hull copy = split.copy(this);
            copy.integrity = Math.max(1, (int) Math.round(integrity * split.massFraction()));
            integrity -= copy.integrity;
        }

        @Override
        protected void onDetach() {
            detached = true;
        }

        @Override
        public String toString() {
            return "Hull[" + integrity + "]";
        }
    }

    public static class Flag extends Module<SubLevelAttachment> {

        @Data
        public int color;
    }

    public static class Cargo extends Module<SubLevelAttachment> implements SplitBehavior {

        @Data
        public int load;

        @Override
        public void onSplit(SubLevelSplit split) {
            split.move(this);
        }
    }
}
