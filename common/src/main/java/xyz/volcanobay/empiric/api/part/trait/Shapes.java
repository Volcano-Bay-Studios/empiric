package xyz.volcanobay.empiric.api.part.trait;

import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The boxes a part is really made of, for one that isn't a plain box, such as an open frame.
 *
 * @param boxes In the part's own pixels, before rotation
 */
public record Shapes(List<AABB> boxes) {

    public Shapes {
        boxes = List.copyOf(boxes);
    }
}
