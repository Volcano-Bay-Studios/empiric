package xyz.volcanobay.empiric.registry;

import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.oct.FullOctBlock;

public final class EmpiricBlocks {

    public static final RegistrationProvider<Block> BLOCKS = RegistrationProvider.get(Registries.BLOCK, Empiric.MODID);

    public static final RegistryObject<FullOctBlock> OCT_BLOCK = BLOCKS.register("oct_block", () -> new FullOctBlock(BlockBehaviour.Properties.of()));
    public static final RegistryObject<OctBlockComplement> OCT_BLOCK_COMPLEMENT = BLOCKS.register("oct_block_complement", () -> new OctBlockComplement(BlockBehaviour.Properties.of()));

    private EmpiricBlocks() {
    }

    public static void bootstrap() {
    }
}
