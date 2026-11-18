package xyz.volcanobay.empiric.gametest.systems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.core.system.CommandBuffer;
import xyz.volcanobay.empiric.core.system.DeltaTime;
import xyz.volcanobay.empiric.core.system.EmpiricSystem;
import xyz.volcanobay.empiric.core.system.Execute;
import xyz.volcanobay.empiric.core.system.ParallelSafe;
import xyz.volcanobay.empiric.core.system.Read;
import xyz.volcanobay.empiric.core.system.Resource;
import xyz.volcanobay.empiric.core.system.ThreadSafe;

public final class SystemFixtures {

    public static final String PARALLEL = "test_parallel";
    public static final String GUARD = "test_guard";
    public static final String PINNED = "test_pinned";
    public static final String BUFFER = "test_buffer";

    private SystemFixtures() {
    }

    public static class Mass extends Module<ChunkAttachment> {

        @Data
        public double value;
    }

    public static class Speed extends Module<ChunkAttachment> {

        @Data
        public double value;
    }

    public static final class Modules {

        @Register("test_sys_mass")
        public static final ChunkModuleType<Mass> MASS = ChunkModuleType.builder(Mass.class, Mass::new).build();

        @Register("test_sys_speed")
        public static final ChunkModuleType<Speed> SPEED = ChunkModuleType.builder(Speed.class, Speed::new).build();

        private Modules() {
        }
    }

    @ThreadSafe
    public record ServerLevelRef(ServerLevel level) {
    }

    @EmpiricSystem(phase = PARALLEL)
    public static class ParallelStep {

        @Execute
        public void step(Mass mass, @Read Speed speed, @DeltaTime double dt) {
            mass.value += speed.value * dt;
        }
    }

    @EmpiricSystem(phase = GUARD)
    @ParallelSafe
    public static class GuardProbe {

        @Execute
        public void probe(Mass mass, @Resource ServerLevelRef ref) {
            ref.level().getBlockState(BlockPos.ZERO);
        }
    }

    @EmpiricSystem(phase = PINNED)
    public static class PinnedProbe {

        public static volatile int calls;

        @Execute
        public void probe(Mass mass, ChunkAttachment attachment) {
            attachment.level().getBlockState(BlockPos.ZERO);
            calls++;
        }
    }

    @EmpiricSystem(phase = BUFFER)
    public static class BufferDetach {

        @Execute
        public void detach(Speed speed, ChunkAttachment attachment, CommandBuffer buffer) {
            buffer.detach(attachment, Modules.SPEED);
        }
    }
}
