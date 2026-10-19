package xyz.volcanobay.empiric.api.ui;

import net.minecraft.world.item.ItemStack;

/**
 * A canvas that can also draw item stacks, flat with their count and durability bar, or as turning 3D models.
 */
public interface ItemCanvas {

    void item(ItemStack stack, int x, int y);

    /**
     * Draws an item's model in 3D, centred on {@code x, y} and {@code size} pixels across, turned by yaw and pitch in
     * degrees. Block items show as their block.
     */
    void model(ItemStack stack, float x, float y, float size, float yaw, float pitch);
}
