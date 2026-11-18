package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import xyz.volcanobay.empiric.api.registry.ItemProps;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Supplier;

public class ItemAdapter extends VanillaAdapter<Item> {

    public ItemAdapter() {
        super(Registries.ITEM, Item.class);
    }

    @Override
    public List<Class<? extends Annotation>> hookAnnotations() {
        return List.of(ItemProps.class);
    }

    @Override
    public Item create(RegistrationEntry entry) {
        Item.Properties properties = entry.hook(ItemProps.class, Item.Properties.class);
        if (properties == null) {
            properties = new Item.Properties();
        }
        return (Item) entry.construct(entry.sourceClass(), properties);
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<Item> value) {
        Supplier<Item> item = registerValue(entry, value);
        EmpiricRegistration.addCreativeTabs(entry, item);
    }
}
