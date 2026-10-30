package xyz.volcanobay.empiric.api.part.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.part.Workspace;
import xyz.volcanobay.empiric.api.part.DrawOffset;
import xyz.volcanobay.empiric.api.part.PartItem;
import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.Piece;
import xyz.volcanobay.empiric.api.part.Placement;
import xyz.volcanobay.empiric.api.part.PlacementFailure;
import xyz.volcanobay.empiric.api.part.Spot;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

import java.util.UUID;

/**
 * Draws a faded ghost where the held part would go.
 */
public final class PlacementPreview {

    private static final int FADED = 0x80ffffff;
    private static final int REFUSED = 0x70ff3030;
    private static final long PENDING_NANOS = 1_000_000_000L;
    private static final UUID GHOST = new UUID(0, 1);

    private static @Nullable Pending pending;
    private static boolean pendingDrawn;

    private PlacementPreview() {
    }

    public static void placed(Spot spot, PartType type, ItemStack stack) {
        pending = new Pending(spot, type, stack, System.nanoTime());
        pendingDrawn = false;
    }

    /**
     * @param camera World position of the camera
     */
    public static void render(Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.level == null || player == null) {
            pending = null;
            return;
        }
        if (renderPending(minecraft.level, camera)) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof PartItem item) || !item.showsPlacement(minecraft.level, player, hit)) {
            return;
        }
        PartType type = item.type();
        Placement.Result result = Placement.find(minecraft.level, type, hit, player.getDirection());
        Spot spot = result.ok() ? result.spot() : result.wanted();
        if (spot == null || result.failure() == PlacementFailure.NONE || type.model() == null) {
            return;
        }
        PoseStack pose = at(minecraft.level, camera, new Piece(GHOST, type, spot.x(), spot.y(), spot.z(), spot.rotation(), ItemStack.EMPTY), spot.host());
        PartRendering.toModel(pose, type);
        type.model().render(pose, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, result.ok() ? FADED : REFUSED);
    }

    private static boolean renderPending(Level level, Vec3 camera) {
        Pending placed = pending;
        if (placed == null) {
            return false;
        }
        if (pendingDrawn || System.nanoTime() - placed.since > PENDING_NANOS) {
            pending = null;
            return false;
        }
        Spot spot = placed.spot;
        Piece piece = new Piece(GHOST, placed.type, spot.x(), spot.y(), spot.z(), spot.rotation(), placed.stack);
        PoseStack pose = at(level, camera, piece, spot.host());
        PartRenderers.of(placed.type).render(piece, pose, LevelRenderer.getLightColor(level, spot.host()), OverlayTexture.NO_OVERLAY, false);
        return true;
    }

    private static PoseStack at(Level level, Vec3 camera, Piece piece, BlockPos host) {
        PoseStack pose = new PoseStack();
        pose.translate(host.getX() - camera.x, host.getY() + PartRendering.LIFT - camera.y, host.getZ() - camera.z);
        if (!piece.type.hooks(DrawOffset.class).isEmpty()) {
            Vec3 shift = Workspace.of(LevelOctExtension.get(level), host).drawOffset(piece);
            pose.translate(shift.x / 16, shift.y / 16, shift.z / 16);
        }
        PartRendering.toPiece(pose, piece.type, piece.x, piece.y, piece.z, piece.rotation);
        return pose;
    }

    static void drawn(BlockPos host, Piece piece) {
        Pending placed = pending;
        if (placed != null && placed.type == piece.type && placed.spot.host().equals(host)
                && placed.spot.x() == piece.x && placed.spot.y() == piece.y && placed.spot.z() == piece.z) {
            pendingDrawn = true;
        }
    }

    private record Pending(Spot spot, PartType type, ItemStack stack, long since) {
    }
}
