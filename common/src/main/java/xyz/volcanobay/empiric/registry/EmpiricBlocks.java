package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.api.part.PartOctBlock;
import xyz.volcanobay.empiric.oct.FullOctBlock;

import java.util.function.Supplier;

public final class EmpiricBlocks {

    public static final Registrar<Block> BLOCKS = Platform.registrar(Registries.BLOCK);

    public static final Supplier<FullOctBlock> OCT_BLOCK = BLOCKS.register("oct_block", () -> new FullOctBlock(BlockBehaviour.Properties.of()));
    public static final Supplier<PartOctBlock> PART = BLOCKS.register("part", () -> new PartOctBlock(BlockBehaviour.Properties.of()
            .noOcclusion()
            .strength(0.3f)
            .noLootTable()));
    public static final Supplier<OctBlockComplement> OCT_BLOCK_COMPLEMENT = BLOCKS.register("oct_block_complement", () -> new OctBlockComplement(BlockBehaviour.Properties.of()));

    private EmpiricBlocks() {
    }

    public static void bootstrap() {
    }
}
