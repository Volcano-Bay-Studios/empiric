package xyz.volcanobay.empiric.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.system.SystemScheduler;
import xyz.volcanobay.empiric.core.system.UnitInfo;
import xyz.volcanobay.empiric.gametest.systems.SystemFixtures;
import xyz.volcanobay.empiric.gametest.systems.SystemFixtures.Mass;
import xyz.volcanobay.empiric.gametest.systems.SystemFixtures.Modules;
import xyz.volcanobay.empiric.gametest.systems.SystemFixtures.ServerLevelRef;
import xyz.volcanobay.empiric.gametest.systems.SystemFixtures.Speed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class SystemGameTests {

    private static final String SUBSCRIBER = "empiric_gametest_systems";
    private static final int DEFAULT_BATCH = 256;

    private SystemGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("systems_parallel_step", SystemGameTests::parallelStep),
                test.apply("systems_guard_throws", SystemGameTests::guardThrows),
                test.apply("systems_pinned_by_attachment", SystemGameTests::pinnedByAttachment),
                test.apply("systems_inspector", SystemGameTests::inspector),
                test.apply("systems_command_buffer", SystemGameTests::commandBuffer));
    }

    private static SystemScheduler systems() {
        return EmpiricCore.engine().systems();
    }

    private static void subscribe() {
        EmpiricCore.modules().subscribe(SUBSCRIBER, Modules.MASS);
        EmpiricCore.modules().subscribe(SUBSCRIBER, Modules.SPEED);
    }

    private static void cleanup(List<ChunkAttachment> attachments) {
        for (ChunkAttachment attachment : attachments) {
            Modules.MASS.detach(attachment);
            Modules.SPEED.detach(attachment);
        }
        EmpiricCore.modules().unsubscribe(SUBSCRIBER, Modules.MASS);
        EmpiricCore.modules().unsubscribe(SUBSCRIBER, Modules.SPEED);
        systems().batchSize(DEFAULT_BATCH);
    }

    private static List<ChunkAttachment> attach(GameTestHelper helper, boolean speed) {
        subscribe();
        List<ChunkAttachment> attachments = new ArrayList<>();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = helper.getLevel().getChunkAt(origin.offset(dx * 16, 0, dz * 16));
                ChunkAttachment attachment = ChunkAttachment.of(chunk);
                Modules.MASS.attach(attachment);
                if (speed) {
                    Modules.SPEED.attach(attachment).value = attachments.size() + 1;
                }
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    private static UnitInfo unit(String phase, String method) {
        for (UnitInfo info : systems().describe(Side.SERVER)) {
            if (info.phase().equals(phase) && info.id().endsWith("#" + method)) {
                return info;
            }
        }
        return null;
    }

    private static void parallelStep(GameTestHelper helper) {
        List<ChunkAttachment> attachments = attach(helper, true);
        try {
            helper.assertTrue(attachments.size() >= 9, "expected 9 chunks, got " + attachments.size());
            UnitInfo info = unit(SystemFixtures.PARALLEL, "step");
            helper.assertTrue(info != null, "the parallel step system isn't scheduled");
            helper.assertTrue(info.parallel(), "the parallel step was pinned: " + info.pinReasons());
            helper.assertTrue(info.querySize() == attachments.size(), "the query has " + info.querySize() + " tuples, expected " + attachments.size());
            systems().batchSize(2);
            for (int i = 0; i < 10; i++) {
                systems().run(Side.SERVER, SystemFixtures.PARALLEL, 1);
            }
            for (int i = 0; i < attachments.size(); i++) {
                double mass = Modules.MASS.get(attachments.get(i)).value;
                helper.assertTrue(mass == 10.0 * (i + 1), "mass " + i + " is " + mass + ", expected " + 10.0 * (i + 1));
            }
        } finally {
            cleanup(attachments);
        }
        helper.succeed();
    }

    private static void guardThrows(GameTestHelper helper) {
        List<ChunkAttachment> attachments = attach(helper, false);
        systems().resources().put(Side.SERVER, ServerLevelRef.class, new ServerLevelRef(helper.getLevel()));
        try {
            UnitInfo info = unit(SystemFixtures.GUARD, "probe");
            helper.assertTrue(info != null && info.parallel(), "the guard probe should be parallel, found " + info);
            boolean threw = false;
            try {
                systems().run(Side.SERVER, SystemFixtures.GUARD, 1);
            } catch (IllegalStateException e) {
                threw = e.getMessage() != null && e.getMessage().contains("getBlockState");
                helper.assertTrue(threw, "the guard threw the wrong message: " + e.getMessage());
            }
            helper.assertTrue(threw, "calling getBlockState from a parallel system didn't throw");
            helper.assertTrue(!SystemScheduler.running, "the scheduler is still marked running");
        } finally {
            systems().resources().remove(Side.SERVER, ServerLevelRef.class);
            cleanup(attachments);
        }
        helper.succeed();
    }

    private static void pinnedByAttachment(GameTestHelper helper) {
        List<ChunkAttachment> attachments = attach(helper, false);
        try {
            UnitInfo info = unit(SystemFixtures.PINNED, "probe");
            helper.assertTrue(info != null, "the pinned probe isn't scheduled");
            helper.assertTrue(!info.parallel(), "taking an attachment should pin the system");
            helper.assertTrue(info.pinReasons().stream().anyMatch(reason -> reason.contains("ChunkAttachment")), "no pin reason names the attachment: " + info.pinReasons());
            SystemFixtures.PinnedProbe.calls = 0;
            systems().run(Side.SERVER, SystemFixtures.PINNED, 1);
            helper.assertTrue(SystemFixtures.PinnedProbe.calls == attachments.size(), "the pinned probe ran " + SystemFixtures.PinnedProbe.calls + " times, expected " + attachments.size());
        } finally {
            cleanup(attachments);
        }
        helper.succeed();
    }

    private static void inspector(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))))
                .withPermission(4)
                .withSuppressedOutput();
        int count;
        try {
            count = level.getServer().getCommands().getDispatcher().execute("empiric systems", source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("/empiric systems failed: " + e.getMessage(), e);
        }
        helper.assertTrue(count >= 1, "/empiric systems listed " + count + " units");
        helper.succeed();
    }

    private static void commandBuffer(GameTestHelper helper) {
        List<ChunkAttachment> attachments = attach(helper, true);
        try {
            UnitInfo before = unit(SystemFixtures.BUFFER, "detach");
            helper.assertTrue(before != null && before.querySize() == attachments.size(), "the buffer system should query " + attachments.size() + " tuples, found " + before);
            systems().run(Side.SERVER, SystemFixtures.BUFFER, 1);
            for (ChunkAttachment attachment : attachments) {
                helper.assertTrue(Modules.SPEED.get(attachment) == null, "Speed wasn't detached through the command buffer");
            }
            UnitInfo after = unit(SystemFixtures.BUFFER, "detach");
            helper.assertTrue(after.querySize() == 0, "the query still has " + after.querySize() + " tuples");
        } finally {
            cleanup(attachments);
        }
        helper.succeed();
    }
}
