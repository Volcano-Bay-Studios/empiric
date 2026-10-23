package xyz.volcanobay.empiric.gametest;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.oct.OctMultiblock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.api.network.SpatialNetworkable;

import java.util.List;
import java.util.function.Supplier;

public final class EmpiricTestBlocks {

    public static Supplier<TestOctBlock> TEST_OCT;
    public static Supplier<OctBlock> PLAIN_OCT;
    public static Supplier<TestPipeOct> PIPE;
    public static Supplier<TestMultiblock> MULTIBLOCK;

    private EmpiricTestBlocks() {
    }

    public static void bootstrap() {
        Registrar<Block> blocks = Platform.registrar(Registries.BLOCK);
        TEST_OCT = blocks.register("test_oct", () -> new TestOctBlock(BlockBehaviour.Properties.of()));
        PLAIN_OCT = blocks.register("test_plain_oct", () -> new OctBlock(BlockBehaviour.Properties.of()));
        PIPE = blocks.register("test_pipe", () -> new TestPipeOct(BlockBehaviour.Properties.of()));
        MULTIBLOCK = blocks.register("test_multiblock", () -> new TestMultiblock(BlockBehaviour.Properties.of()));
    }

    public static class TestOctBlock extends OctBlock {

        public TestOctBlock(Properties properties) {
            super(properties);
        }

        @Override
        public @Nullable Class<? extends OctBlockEntity> getOctEntityType() {
            return TestOctEntity.class;
        }

        @Override
        public @Nullable OctBlockEntity createOctEntity(BlockPos pos, int octant, BlockState state) {
            return new TestOctEntity(pos, octant, state);
        }
    }

    public static class TestPipeOct extends OctBlock implements SpatialNetworkable {

        public TestPipeOct(Properties properties) {
            super(properties);
        }

        @Override
        public List<SpatialNetworkType<?>> spatialNetworks() {
            return List.of(EmpiricTestModules.PIPES);
        }
    }

    public static class TestMultiblock extends Block implements OctMultiblock {

        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 24, 16);

        public TestMultiblock(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getMultiblockShape(BlockState state, BlockGetter level, BlockPos pos) {
            return SHAPE;
        }
    }

    public static class TestOctEntity extends OctBlockEntity {

        public int ticks;

        public TestOctEntity(BlockPos worldPosition, int octantPosition, BlockState blockState) {
            super(worldPosition, octantPosition, blockState);
        }

        @Override
        public boolean isTicked() {
            return true;
        }

        @Override
        public void tick() {
            ticks++;
        }
    }

    @SelfSpec
    public static class TestSelfEntity extends BlockEntity {

        @Data
        public int energy;
        @Data(watch = true)
        public String label = "";

        public TestSelfEntity(BlockPos pos, BlockState state) {
            super(BlockEntityType.CHEST, pos, state);
        }
    }
}
