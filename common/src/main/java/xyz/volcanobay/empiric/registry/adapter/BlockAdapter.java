package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import xyz.volcanobay.empiric.api.registry.BlockItemFactory;
import xyz.volcanobay.empiric.api.registry.BlockProps;
import xyz.volcanobay.empiric.api.registry.ItemProps;
import xyz.volcanobay.empiric.api.registry.NoItem;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Supplier;

public class BlockAdapter extends VanillaAdapter<Block> {

    public BlockAdapter() {
        super(Registries.BLOCK, Block.class);
    }

    @Override
    public List<Class<? extends Annotation>> hookAnnotations() {
        return List.of(BlockProps.class, BlockItemFactory.class, ItemProps.class);
    }

    @Override
    public Block create(RegistrationEntry entry) {
        BlockBehaviour.Properties properties = entry.hook(BlockProps.class, BlockBehaviour.Properties.class);
        if (properties == null) {
            properties = BlockBehaviour.Properties.of();
        }
        return (Block) entry.construct(entry.sourceClass(), properties);
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<Block> value) {
        Supplier<Block> block = registerValue(entry, value);
        if (entry.has(NoItem.class)) {
            return;
        }
        Supplier<Item> item = Platform.registrar(Registries.ITEM, entry.namespace()).register(entry.path(), () -> {
            BlockItem factory = entry.hook(BlockItemFactory.class, BlockItem.class);
            if (factory != null) {
                return factory;
            }
            Item.Properties properties = entry.hook(ItemProps.class, Item.Properties.class);
            if (properties == null) {
                properties = new Item.Properties();
            }
            return new BlockItem(block.get(), properties);
        });
        EmpiricRegistration.addCreativeTabs(entry, item);
    }
}
