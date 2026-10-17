package xyz.volcanobay.empiric.client;

import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.api.behavior.ItemRenderBehavior;
import xyz.volcanobay.empiric.data.ItemModules;
import xyz.volcanobay.empiric.registry.EmpiricDataComponents;
import xyz.volcanobay.empiric.system.ItemModuleSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ItemModuleCache {

    private static final Map<ItemModules, List<ItemRenderBehavior>> RENDERERS = Collections.synchronizedMap(new WeakHashMap<>());

    private ItemModuleCache() {
    }

    public static List<ItemRenderBehavior> renderers(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        ItemModules modules = stack.get(EmpiricDataComponents.MODULES.get());
        if (modules == null) {
            return List.of();
        }
        return RENDERERS.computeIfAbsent(modules, key -> {
            List<ItemRenderBehavior> found = new ArrayList<>();
            ItemStackAttachment attachment = ItemModuleSystem.open(stack);
            attachment.modules().forEach(module -> {
                if (module instanceof ItemRenderBehavior behavior) {
                    found.add(behavior);
                }
            });
            return found.isEmpty() ? List.of() : List.copyOf(found);
        });
    }

    public static void clear() {
        RENDERERS.clear();
    }
}
