package xyz.volcanobay.empiric.gametest.registry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import xyz.volcanobay.empiric.api.part.Piece;
import xyz.volcanobay.empiric.api.part.client.PieceRenderer;
import xyz.volcanobay.empiric.api.registry.PieceRendererFor;

@PieceRendererFor("empiric:fixture_trait")
public class FixturePieceRenderer implements PieceRenderer {

    static {
        System.setProperty("empiric.test.piece_renderer_loaded", "true");
    }

    @Override
    public void render(Piece piece, PoseStack pose, int light, int overlay, boolean placed) {
    }
}
