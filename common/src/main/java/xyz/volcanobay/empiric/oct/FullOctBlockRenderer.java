package xyz.volcanobay.empiric.oct;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.oct.OctBlockRenderer;
import xyz.volcanobay.empiric.api.oct.OctBlockRendererRegistry;

public class FullOctBlockRenderer implements BlockEntityRenderer<FullOctBlockEntity> {
    public static final FullOctBlockRenderer INSTANCE = new FullOctBlockRenderer();

    /**
     * NeoForge's culling box (it overrides the loader's default when present); oct content may reach into neighbouring blocks.
     */
    public AABB getRenderBoundingBox(FullOctBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1);
    }
    
    @Override
    public void render(FullOctBlockEntity be,
                       float partialTick,
                       PoseStack pose,
                       MultiBufferSource buffers,
                       int light,
                       int overlay) {
        for (int i = 0; i < 8; i++) {
            OctBlockEntity obe = be.getOctEntity(i);
            if (obe == null) {
                continue;
            }
            OctBlockRenderer renderer = OctBlockRendererRegistry.get(obe.getClass());
            if (renderer == null) {
                continue;
            }
            float x = (i & 1) != 0 ? 0.5f : 0;
            float y = (i & 2) != 0 ? 0.5f : 0;
            float z = (i & 4) != 0 ? 0.5f : 0;
            pose.translate(x, y, z);
            //noinspection unchecked
            renderer.render(obe, partialTick, pose, buffers, light, overlay);
            pose.translate(-x, -y, -z);
        }
    }
}
