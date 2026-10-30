package xyz.volcanobay.empiric.api.part.client;

import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.TraitKey;

import java.util.ArrayList;
import java.util.List;

/**
 * How each part type is drawn. A type uses the renderer of the first registered trait it has, or else just its model.
 */
public final class PartRenderers {

    private record Entry(TraitKey<?> trait, PieceRenderer renderer) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static final PieceRenderer MODEL = (piece, pose, light, overlay, placed) -> PartRendering.renderModel(piece.type, pose, light, overlay, EmpiricModel.NO_TINT);

    private PartRenderers() {
    }

    public static synchronized void register(TraitKey<?> trait, PieceRenderer renderer) {
        ENTRIES.add(new Entry(trait, renderer));
    }

    public static synchronized PieceRenderer of(PartType type) {
        for (Entry entry : ENTRIES) {
            if (type.has(entry.trait())) {
                return entry.renderer();
            }
        }
        return MODEL;
    }
}
