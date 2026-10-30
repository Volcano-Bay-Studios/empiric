package xyz.volcanobay.empiric.api.model;

import foundry.veil.api.client.render.shader.program.ShaderProgram;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import xyz.volcanobay.empiric.client.model.ModelRenderer;

import java.util.function.Consumer;

/**
 * Translucent geometry of your own, drawn in the same far-to-near pass as translucent models, so it sorts against them.
 * Queue it every frame, before translucent blocks are drawn (from a block entity renderer, or an earlier level stage).
 */
public final class TranslucentDraw {

    private TranslucentDraw() {
    }

    /**
     * @param sortPoint Camera-relative point to sort by
     * @param layer     Lower layers draw first among draws at the same distance; see {@link RenderOrder}
     * @param draw      Draws the geometry. Shader state is reset afterwards, so it can set whatever it needs.
     */
    public static void queue(Vector3fc sortPoint, int layer, Consumer<Context> draw) {
        ModelRenderer.queueCustom(sortPoint, layer, draw);
    }

    /**
     * A context for drawing right away with the current projection, outside level rendering, such as in a GUI.
     */
    public static Context immediate() {
        return ModelRenderer.immediateContext();
    }

    public interface Context {
        Matrix4fc view();

        Matrix4fc projection();

        /**
         * Binds the shader with the view and projection set, and the block atlas, overlay and lightmap as
         * {@code Sampler0}, {@code Sampler1} and {@code Sampler2}, as models are drawn.
         */
        void begin(ShaderProgram shader);
    }
}
