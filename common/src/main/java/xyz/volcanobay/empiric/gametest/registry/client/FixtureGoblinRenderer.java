package xyz.volcanobay.empiric.gametest.registry.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PigRenderer;

public class FixtureGoblinRenderer extends PigRenderer {

    static {
        System.setProperty("empiric.test.renderer_loaded", "true");
    }

    public FixtureGoblinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
