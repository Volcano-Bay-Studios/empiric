package xyz.volcanobay.empiric.api.part.client;

import com.mojang.blaze3d.vertex.PoseStack;
import xyz.volcanobay.empiric.api.part.Piece;

@FunctionalInterface
public interface PieceRenderer {

    /**
     * @param pose   At the piece's minimum corner in its own unrotated frame, one unit a block
     * @param placed False for a piece the client just placed that the server hasn't sent yet
     */
    void render(Piece piece, PoseStack pose, int light, int overlay, boolean placed);
}
