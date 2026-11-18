package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import xyz.volcanobay.empiric.api.registry.ValidBlocks;
import xyz.volcanobay.empiric.core.registry.Ref;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;

import java.util.function.Supplier;

public final class EmpiricBlockEntities {

    @Deprecated
    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES = Platform.registrar(Registries.BLOCK_ENTITY_TYPE);

    @Register("oct_block")
    @ValidBlocks("oct_block")
    private static final Ref<BlockEntityType<FullOctBlockEntity>> OCT_BLOCK_REF = Ref.create();

    public static final Supplier<BlockEntityType<FullOctBlockEntity>> OCT_BLOCK = OCT_BLOCK_REF;

    private EmpiricBlockEntities() {
    }

    public static void bootstrap() {
    }
}
