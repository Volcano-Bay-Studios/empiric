package xyz.volcanobay.empiric.api.part;

import net.minecraft.world.phys.Vec3;

/**
 * A trait that draws its piece away from its pixel corner, for parts that sit between pixels.
 */
public interface DrawOffset {

    /**
     * @param workspace Of the block the piece is in
     * @return Block pixels to shift the piece's drawing by
     */
    Vec3 offset(Workspace workspace, Piece piece);
}
