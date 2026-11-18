package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.api.part.PartOctBlock;
import xyz.volcanobay.empiric.oct.FullOctBlock;

import java.util.function.Supplier;

public final class EmpiricBlocks {

    @Deprecated
    public static final Registrar<Block> BLOCKS = Platform.registrar(Registries.BLOCK);

    public static final Supplier<FullOctBlock> OCT_BLOCK = FullOctBlock.REF;
    public static final Supplier<PartOctBlock> PART = PartOctBlock.REF;
    public static final Supplier<OctBlockComplement> OCT_BLOCK_COMPLEMENT = OctBlockComplement.REF;

    private EmpiricBlocks() {
    }

    public static void bootstrap() {
    }
}
