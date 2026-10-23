package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;

import java.util.function.Supplier;

public final class EmpiricBlockEntities {

    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES = Platform.registrar(Registries.BLOCK_ENTITY_TYPE);

    public static final Supplier<BlockEntityType<FullOctBlockEntity>> OCT_BLOCK = BLOCK_ENTITIES.register("oct_block",
            () -> BlockEntityType.Builder.of((pos, state) -> new FullOctBlockEntity(EmpiricBlockEntities.OCT_BLOCK.get(), pos, state), EmpiricBlocks.OCT_BLOCK.get()).build(null));

    private EmpiricBlockEntities() {
    }

    public static void bootstrap() {
    }
}
