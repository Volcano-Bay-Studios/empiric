package xyz.volcanobay.empiric.api.part.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctUtil;
import xyz.volcanobay.empiric.api.part.OrientedBox;
import xyz.volcanobay.empiric.api.part.PartOctBlock;
import xyz.volcanobay.empiric.api.part.Selection;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

public final class PartOutline {

    private PartOutline() {
    }

    /**
     * @return Whether the aimed-at piece's outline was drawn, so the default outline should be skipped
     */
    public static boolean render(PoseStack pose, VertexConsumer consumer, Entity entity, double camX, double camY, double camZ, BlockPos pos) {
        Selection selection = selection(entity, pos);
        if (selection == null) {
            return false;
        }
        PoseStack.Pose last = pose.last();
        Vec3 offset = new Vec3(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);
        for (Vec3[] edge : OrientedBox.outlineEdges(selection.outline())) {
            Vec3 a = edge[0].scale(1 / 16.0).add(offset);
            Vec3 b = edge[1].scale(1 / 16.0).add(offset);
            if (a.distanceToSqr(b) < 1.0E-10) {
                continue;
            }
            Vec3 normal = b.subtract(a).normalize();
            consumer.addVertex(last, (float) a.x, (float) a.y, (float) a.z).setColor(0, 0, 0, 0.4f).setNormal(last, (float) normal.x, (float) normal.y, (float) normal.z);
            consumer.addVertex(last, (float) b.x, (float) b.y, (float) b.z).setColor(0, 0, 0, 0.4f).setNormal(last, (float) normal.x, (float) normal.y, (float) normal.z);
        }
        return true;
    }

    private static @Nullable Selection selection(Entity entity, BlockPos pos) {
        if (!(entity instanceof Player player) || !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
            return null;
        }
        OctLevel oct = LevelOctExtension.get(player.level());
        if (!(oct.getOctState(pos, OctUtil.getOctant(hit)).getBlock() instanceof PartOctBlock)) {
            return null;
        }
        return Selection.of(oct, pos, player, hit);
    }
}
