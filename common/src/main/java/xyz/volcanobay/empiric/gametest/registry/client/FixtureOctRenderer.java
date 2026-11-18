package xyz.volcanobay.empiric.gametest.registry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import xyz.volcanobay.empiric.api.oct.OctBlockRenderer;
import xyz.volcanobay.empiric.api.registry.OctRendererFor;
import xyz.volcanobay.empiric.gametest.registry.RegistryFixtures;

@OctRendererFor(RegistryFixtures.FixtureOct.class)
public class FixtureOctRenderer implements OctBlockRenderer<RegistryFixtures.FixtureOct> {

    static {
        System.setProperty("empiric.test.oct_renderer_loaded", "true");
    }

    @Override
    public void render(RegistryFixtures.FixtureOct be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    }
}
