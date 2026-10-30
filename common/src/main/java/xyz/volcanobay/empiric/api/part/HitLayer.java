package xyz.volcanobay.empiric.api.part;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Extra boxes a trait adds to a piece, such as controls or items sitting on it, that can be aimed at on their own. A
 * selection of one carries the layer and the box's index in {@link #boxes}.
 */
public interface HitLayer {

    /**
     * @return Boxes in the piece's block pixels
     */
    List<AABB> boxes(Piece piece);

    /**
     * @return The boxes as aimed at and outlined, in the piece's block pixels; one per box of {@link #boxes}, in order
     */
    default List<OrientedBox> hitBoxes(Piece piece) {
        List<OrientedBox> boxes = new ArrayList<>();
        for (AABB box : boxes(piece)) {
            boxes.add(OrientedBox.aligned(box));
        }
        return boxes;
    }

    /**
     * @return Pixels of ray distance this layer wins by over the body and lower layers
     */
    default double bias() {
        return 0;
    }

    /**
     * @return Whether its boxes take up room, so nothing else may overlap them and they count for the octants filled
     */
    default boolean occupies() {
        return false;
    }

    /**
     * @return Whether using the piece with an item in hand still goes to the interactions when this layer is aimed at
     */
    default boolean takesItems() {
        return true;
    }

    /**
     * Using it with an empty hand.
     */
    default InteractionResult use(PartContext context) {
        return InteractionResult.PASS;
    }
}
