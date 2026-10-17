package xyz.volcanobay.empiric.registry;

import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;

public final class EmpiricBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES = RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, Empiric.MODID);

    public static final RegistryObject<BlockEntityType<FullOctBlockEntity>> OCT_BLOCK = BLOCK_ENTITIES.register("oct_block",
            () -> BlockEntityType.Builder.of((pos, state) -> new FullOctBlockEntity(EmpiricBlockEntities.OCT_BLOCK.get(), pos, state), EmpiricBlocks.OCT_BLOCK.get()).build(null));

    private EmpiricBlockEntities() {
    }

    public static void bootstrap() {
    }
}
