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
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
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

    private SableGameTests() {
    }

    static void register(ModuleSystem modules) {
        HULL = modules.register(SubLevelModuleType.builder(Empiric.id("test_hull"), Hull.class, Hull::new).build());
        modules.subscribe(EmpiricTestModules.SUBSCRIBER, HULL);
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(test.apply("sub_level_module", SableGameTests::subLevelModule));
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

    public static class Hull extends Module<SubLevelAttachment> {

        @Data
        public int integrity;

        boolean detached;

        @Override
        protected void onDetach() {
            detached = true;
        }

        @Override
        public String toString() {
            return "Hull[" + integrity + "]";
        }
    }
}
