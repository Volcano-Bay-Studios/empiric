package xyz.volcanobay.empiric.client.ui.surface;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.api.ui.surface.WorldSurface;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.surface.SurfacePlane;

/**
 * Turns where the player is looking into a mouse for the surface in front of them: attack is the left button, use is the
 * right button, and the wheel scrolls it. While a surface has a button held it keeps the pointer even when the look ray
 * leaves it, so drags don't get dropped at the edge. Blocks and entities closer than the surface hide it.
 */
public final class SurfaceInput {

    private static final double OFF_SURFACE = -100000;
    /**
     * Surfaces hang on block faces, so a block hit at the same distance doesn't count as in front.
     */
    private static final double FACE_TOLERANCE = 0.02;

    private static WorldSurface hovered;
    private static WorldSurface captured;
    private static int capturedButton;
    private static double lastX;
    private static double lastY;

    private SurfaceInput() {
    }

    public static void reset() {
        hovered = null;
        captured = null;
    }

    public static boolean isTarget(WorldSurface surface) {
        return surface == hovered || surface == captured;
    }

    public static WorldSurface hovered() {
        return hovered;
    }

    /**
     * The surface the pointer is on: the one holding a drag, or else the hovered one.
     */
    public static WorldSurface pointed() {
        return captured != null ? captured : hovered;
    }

    /**
     * Whether the player's look ray is on a surface, so vanilla attacking, using and scrolling should leave it alone.
     */
    public static boolean active() {
        return hovered != null || captured != null;
    }

    /**
     * Follows the look ray. Runs every frame and tick, since drags and hover hints should move with the view.
     */
    public static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !minecraft.mouseHandler.isMouseGrabbed()) {
            release(minecraft);
            leave();
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        Vec3 look = new Vec3(camera.getLookVector());
        if (captured != null) {
            drag(minecraft, eye, look);
            return;
        }
        if (TooltipSystem.active()) {
            leave();
            return;
        }
        WorldSurface best = null;
        SurfacePlane.Hit bestHit = null;
        double blocked = blockedAt(minecraft, eye);
        for (WorldSurface surface : SurfaceRenderer.surfaces()) {
            if (!surface.isInteractive() || surface.isRemoved()) {
                continue;
            }
            SurfacePlane.Hit hit = surface.plane().intersect(eye.x, eye.y, eye.z, look.x, look.y, look.z);
            if (hit == null || !hit.inside() || hit.distance() > surface.reach() || hit.distance() > blocked + FACE_TOLERANCE) {
                continue;
            }
            if (bestHit == null || hit.distance() < bestHit.distance()) {
                best = surface;
                bestHit = hit;
            }
        }
        if (best != hovered) {
            leave();
            hovered = best;
        }
        if (hovered != null) {
            lastX = bestHit.x();
            lastY = bestHit.y();
            hovered.root().pointerMove(lastX, lastY);
        }
    }

    private static double blockedAt(Minecraft minecraft, Vec3 eye) {
        HitResult result = minecraft.hitResult;
        if (result == null || result.getType() == HitResult.Type.MISS) {
            return Double.MAX_VALUE;
        }
        return result.getLocation().distanceTo(eye);
    }

    private static void leave() {
        if (hovered != null) {
            hovered.root().pointerMove(OFF_SURFACE, OFF_SURFACE);
            hovered = null;
        }
    }

    private static void drag(Minecraft minecraft, Vec3 eye, Vec3 look) {
        boolean held = capturedButton == Keys.LEFT_BUTTON ? minecraft.options.keyAttack.isDown() : minecraft.options.keyUse.isDown();
        if (captured.isRemoved()) {
            captured = null;
            return;
        }
        SurfacePlane.Hit hit = captured.plane().intersect(eye.x, eye.y, eye.z, look.x, look.y, look.z);
        double x = hit == null ? lastX : hit.x();
        double y = hit == null ? lastY : hit.y();
        if (!held) {
            captured.root().pointerUp(x, y, capturedButton);
            captured = null;
            return;
        }
        if (x != lastX || y != lastY) {
            captured.root().pointerDrag(x, y, x - lastX, y - lastY, capturedButton);
            lastX = x;
            lastY = y;
        }
    }

    private static void release(Minecraft minecraft) {
        if (captured != null) {
            captured.root().pointerUp(lastX, lastY, capturedButton);
            captured = null;
        }
    }

    /**
     * A mouse button went down. Returns true if a surface took it, so vanilla must not also act on it.
     */
    public static boolean press(int button) {
        if (captured != null) {
            return true;
        }
        update();
        for (WorldSurface surface : SurfaceRenderer.surfaces()) {
            if (surface != hovered) {
                surface.root().focus(null);
            }
        }
        if (hovered == null) {
            return false;
        }
        captured = hovered;
        capturedButton = button;
        captured.root().pointerDown(lastX, lastY, button);
        return true;
    }

    /**
     * The wheel turned. Returns true if a surface took it, so the hotbar must not also scroll.
     */
    public static boolean scroll(double amount) {
        WorldSurface target = captured != null ? captured : hovered;
        if (target == null) {
            return false;
        }
        target.root().scroll(lastX, lastY, amount);
        return true;
    }
}
