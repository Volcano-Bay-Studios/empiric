package xyz.volcanobay.empiric.api.part.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.api.oct.OctBlockRenderer;
import xyz.volcanobay.empiric.api.part.Workspace;
import xyz.volcanobay.empiric.api.part.DrawOffset;
import xyz.volcanobay.empiric.api.part.PartOctEntity;
import xyz.volcanobay.empiric.api.part.Piece;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

public class PartRenderer implements OctBlockRenderer<PartOctEntity> {

    @Override
    public void render(PartOctEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int offsetX = (entity.octant() & 1) != 0 ? 8 : 0;
        int offsetY = (entity.octant() & 2) != 0 ? 8 : 0;
        int offsetZ = (entity.octant() & 4) != 0 ? 8 : 0;
        for (Piece piece : entity.pieces()) {
            PlacementPreview.drawn(entity.pos(), piece);
            pose.pushPose();
            pose.translate(-offsetX / 16f, -offsetY / 16f + PartRendering.LIFT, -offsetZ / 16f);
            if (!piece.type.hooks(DrawOffset.class).isEmpty() && entity.level() != null) {
                Vec3 shift = Workspace.of(LevelOctExtension.get(entity.level()), entity.pos()).drawOffset(piece);
                pose.translate(shift.x / 16, shift.y / 16, shift.z / 16);
            }
            PartRendering.toPiece(pose, piece.type, piece.x, piece.y, piece.z, piece.rotation);
            PartRenderers.of(piece.type).render(piece, pose, light, overlay, true);
            pose.popPose();
        }
    }
}
