package xyz.volcanobay.empiric.client.ui.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipBehavior;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipContent;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipPlacement;
import xyz.volcanobay.empiric.client.ui.ClientUiHost;
import xyz.volcanobay.empiric.client.ui.GuiCanvas;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiRoot;

import java.util.ArrayList;
import java.util.List;

/**
 * The floating tooltip of whatever the player looks at. Each module on the target can add rows; together they make one
 * small screen beside the target, projected to the HUD with a line back to it. The crosshair is its pointer, so the
 * player turns to move onto it, and it stays open while the crosshair is on it even though the target is no longer
 * looked at.
 */
public final class TooltipSystem {

    private static final double OFF_PANEL = -100000;
    private static final int FADE_TICKS = 6;
    private static final double EASE = 0.3;
    private static final int GAP = 12;
    private static final int EDGE = 4;
    /**
     * How much nearer, in blocks, another corner must be before it takes over as the nearest. Stops the side flipping
     * back and forth while standing square in front of a face.
     */
    private static final double CORNER_STICK = 0.3;
    /**
     * Ticks a tooltip survives with nothing looked at, so slipping off the target for a moment doesn't close it.
     */
    private static final int GRACE_TICKS = 6;
    /**
     * Extra GUI pixels around the tooltip and the gap to its target that still count as on it.
     */
    private static final int REACH_MARGIN = 6;

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean viewKnown;

    private static Object target;
    private static List<Module<?>> modules = List.of();
    private static TooltipContent content;
    private static ClientUiHost host;
    private static UiRoot root;
    private static boolean interactive;
    private static int age;
    private static Vec3 desired;
    private static Vec3 anchor;
    private static Vec3 previousAnchor;
    private static AABB box;
    private static int nearestCorner = -1;
    private static int side = 1;
    private static double sideBlend = 1;
    private static double previousSideBlend = 1;

    private static int panelX;
    private static int panelY;
    private static boolean pointerOn;
    private static boolean nearPanel;
    private static int lostTicks;
    private static int capturedButton = -1;
    private static double lastX;
    private static double lastY;

    private record Found(Object key, List<Module<?>> modules, AABB box) {
    }

    private TooltipSystem() {
    }

    public static UiRoot root() {
        return root;
    }

    /**
     * Whether the crosshair is on the tooltip or holding a button on it, so vanilla input should leave it alone.
     */
    public static boolean active() {
        return root != null && (pointerOn || capturedButton >= 0);
    }

    public static boolean capturesKeyboard() {
        return root != null && root.focused() != null && root.focused().capturesKeyboard();
    }

    /**
     * Keeps the view of the frame being drawn, so the HUD pass can put the tooltip exactly where the target shows.
     */
    public static void captureView(Matrix4fc view, Matrix4fc projection, Vec3 cameraPosition) {
        VIEW_PROJECTION.set(projection).mul(view);
        camera = cameraPosition;
        viewKnown = true;
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            close();
            return;
        }
        Found found = find(minecraft, player);
        boolean held = pointerOn || nearPanel || capturedButton >= 0 || capturesKeyboard();
        boolean same = found != null && found.key().equals(target) && found.modules().equals(modules);
        if (same) {
            box = found.box();
            lostTicks = 0;
        } else if (root != null && found == null && !held) {
            if (++lostTicks > GRACE_TICKS) {
                close();
            }
        } else if (root == null || !held) {
            close();
            if (found != null) {
                open(found, player);
            }
        }
        if (root == null) {
            return;
        }
        for (Module<?> module : modules) {
            if (!module.isAttached()) {
                close();
                return;
            }
        }
        interactive = true;
        for (Module<?> module : modules) {
            if (module instanceof TooltipBehavior behavior && !behavior.canInteract(player)) {
                interactive = false;
            }
        }
        age++;
        desired = chooseAnchor(minecraft.gameRenderer.getMainCamera(), held);
        previousAnchor = anchor;
        anchor = anchor.lerp(desired, EASE);
        previousSideBlend = sideBlend;
        sideBlend += (side - sideBlend) * EASE;
        root.tick();
    }

    private static Found find(Minecraft minecraft, Player player) {
        HitResult hit = minecraft.hitResult;
        Level level = minecraft.level;
        List<Attachment> attachments = new ArrayList<>();
        Object key;
        AABB at;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                attachments.add(BlockEntityAttachment.of(blockEntity));
            }
            BlockAttachment block = BlockAttachment.find(level, pos);
            if (block != null) {
                attachments.add(block);
            }
            key = pos.immutable();
            at = new AABB(pos);
        } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            attachments.add(EntityAttachment.of(entity));
            key = entity;
            at = entity.getBoundingBox();
        } else {
            return null;
        }
        List<Module<?>> providers = new ArrayList<>();
        for (Attachment attachment : attachments) {
            attachment.modules().forEach(module -> {
                if (TooltipContent.provides(module, player)) {
                    providers.add(module);
                }
            });
        }
        return providers.isEmpty() ? null : new Found(key, providers, at);
    }

    /**
     * The top corner the tooltip hangs from. The tooltip goes out on the side of the target where its nearest corner
     * shows, and hangs from the outermost top corner on that side, so it never covers the target. While the player is
     * using the tooltip the side stays put, since turning towards it would otherwise move it.
     */
    private static Vec3 chooseAnchor(Camera view, boolean locked) {
        Vec3 eye = view.getPosition();
        Vector3f look = view.getLookVector();
        double rightX = -look.z();
        double rightZ = look.x();
        double length = Math.sqrt(rightX * rightX + rightZ * rightZ);
        double[][] corners = {{box.minX, box.minZ}, {box.maxX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.maxZ}};
        double centreX = (box.minX + box.maxX) / 2;
        double centreZ = (box.minZ + box.maxZ) / 2;
        if (!locked && length > 1.0e-4) {
            rightX /= length;
            rightZ /= length;
            int nearest = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < corners.length; i++) {
                double distance = Math.hypot(corners[i][0] - eye.x, corners[i][1] - eye.z) - (i == nearestCorner ? CORNER_STICK : 0);
                if (distance < best) {
                    best = distance;
                    nearest = i;
                }
            }
            nearestCorner = nearest;
            double lateral = (corners[nearest][0] - centreX) * rightX + (corners[nearest][1] - centreZ) * rightZ;
            if (lateral > 0.05) {
                side = 1;
            } else if (lateral < -0.05) {
                side = -1;
            }
        }
        int outer = 0;
        double extreme = -Double.MAX_VALUE;
        for (int i = 0; i < corners.length; i++) {
            double along = side * ((corners[i][0] - centreX) * rightX + (corners[i][1] - centreZ) * rightZ)
                    - Math.hypot(corners[i][0] - eye.x, corners[i][1] - eye.z) * 1.0e-3;
            if (along > extreme) {
                extreme = along;
                outer = i;
            }
        }
        return new Vec3(corners[outer][0], box.maxY, corners[outer][1]);
    }

    private static void open(Found found, Player player) {
        TooltipContent built = TooltipContent.of(found.modules(), player);
        if (built.isEmpty()) {
            return;
        }
        target = found.key();
        modules = List.copyOf(found.modules());
        content = built;
        host = new ClientUiHost(Minecraft.getInstance().font, TooltipSystem::close);
        root = new UiRoot(content, modules.getFirst(), host);
        root.resize(content.measuredWidth(), content.measuredHeight());
        box = found.box();
        nearestCorner = -1;
        desired = chooseAnchor(Minecraft.getInstance().gameRenderer.getMainCamera(), false);
        sideBlend = side;
        previousSideBlend = side;
        anchor = desired.add(0, -0.15, 0);
        previousAnchor = anchor;
        age = 0;
    }

    public static void close() {
        if (root != null) {
            root.dispose();
        }
        root = null;
        content = null;
        host = null;
        target = null;
        modules = List.of();
        pointerOn = false;
        nearPanel = false;
        lostTicks = 0;
        capturedButton = -1;
    }

    public static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (root == null || minecraft.options.hideGui) {
            return;
        }
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int width = Math.max(1, content.measuredWidth());
        int height = Math.max(1, content.measuredHeight());
        if (width != root.width() || height != root.height()) {
            root.resize(width, height);
        }
        float[] point = content.placement() == TooltipPlacement.ANCHORED ? project(previousAnchor.lerp(anchor, partialTick), screenWidth, screenHeight) : null;
        int x;
        int y;
        if (point == null) {
            x = screenWidth / 2 + 20;
            y = screenHeight / 2 - height / 2;
        } else {
            double blend = (previousSideBlend + (sideBlend - previousSideBlend) * partialTick + 1) / 2;
            double right = point[0] + GAP;
            double left = point[0] - GAP - width;
            if (side > 0 && right + width > screenWidth - EDGE && left >= EDGE) {
                blend = 0;
            } else if (side < 0 && left < EDGE && right + width <= screenWidth - EDGE) {
                blend = 1;
            }
            x = (int) Math.round(left + (right - left) * blend);
            y = Math.round(point[1]) - 8;
        }
        panelX = Math.clamp(x, EDGE, Math.max(EDGE, screenWidth - width - EDGE));
        panelY = Math.clamp(y, EDGE, Math.max(EDGE, screenHeight - height - EDGE));
        followCrosshair(minecraft, screenWidth, screenHeight, width, height, point != null);
        nearPanel = point != null && crosshairBetween(point, screenWidth / 2f, screenHeight / 2f, width, height);
        float fade = Math.clamp((age + partialTick) / FADE_TICKS, 0, 1);
        RenderSystem.setShaderColor(1, 1, 1, fade);
        GuiCanvas canvas = new GuiCanvas(graphics, host.font(), minecraft.font);
        if (point != null) {
            float edge = panelX + width / 2f >= point[0] ? panelX : panelX + width;
            canvas.line(point[0], point[1], edge, panelY + Math.min(8, height / 2f), 1, root.theme().color("tooltip_line"));
            canvas.nextLayer();
        }
        canvas.push();
        canvas.translate(panelX, panelY);
        root.render(canvas, partialTick);
        canvas.pop();
        canvas.finish();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /**
     * Whether the crosshair is on the tooltip, a little around it, or in the gap between it and its target, all of which
     * keep the tooltip open while the player moves from one to the other.
     */
    private static boolean crosshairBetween(float[] anchorPoint, float x, float y, int width, int height) {
        float left = Math.min(anchorPoint[0], panelX) - REACH_MARGIN;
        float right = Math.max(anchorPoint[0], panelX + width) + REACH_MARGIN;
        float top = Math.min(anchorPoint[1], panelY) - REACH_MARGIN;
        float bottom = Math.max(anchorPoint[1], panelY + height) + REACH_MARGIN;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private static float[] project(Vec3 world, int screenWidth, int screenHeight) {
        if (!viewKnown) {
            return null;
        }
        Vector4f clip = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1).mul(VIEW_PROJECTION);
        if (clip.w <= 0.05f) {
            return null;
        }
        float x = clip.x / clip.w;
        float y = clip.y / clip.w;
        return new float[]{(x + 1) / 2 * screenWidth, (1 - y) / 2 * screenHeight};
    }

    /**
     * Moves the tooltip's pointer to wherever the crosshair falls on it, and finishes drags once their button lets go.
     */
    private static void followCrosshair(Minecraft minecraft, int screenWidth, int screenHeight, int width, int height, boolean anchored) {
        double x = screenWidth / 2.0 - panelX;
        double y = screenHeight / 2.0 - panelY;
        boolean usable = interactive && anchored && minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed();
        boolean inside = usable && x >= 0 && y >= 0 && x < width && y < height;
        if (capturedButton >= 0) {
            boolean held = capturedButton == Keys.LEFT_BUTTON ? minecraft.options.keyAttack.isDown() : minecraft.options.keyUse.isDown();
            if (!held || !usable) {
                root.pointerUp(x, y, capturedButton);
                capturedButton = -1;
            } else if (x != lastX || y != lastY) {
                root.pointerDrag(x, y, x - lastX, y - lastY, capturedButton);
            }
        } else if (inside) {
            root.pointerMove(x, y);
        } else if (pointerOn) {
            root.pointerMove(OFF_PANEL, OFF_PANEL);
        }
        pointerOn = inside;
        lastX = x;
        lastY = y;
    }

    /**
     * A mouse button went down. Returns true if the tooltip took it. A click anywhere else drops its keyboard focus.
     */
    public static boolean press(int button) {
        if (root == null) {
            return false;
        }
        if (!pointerOn) {
            root.focus(null);
            return false;
        }
        if (capturedButton < 0) {
            capturedButton = button;
            root.pointerDown(lastX, lastY, button);
        }
        return true;
    }

    public static boolean scroll(double amount) {
        if (!active()) {
            return false;
        }
        root.scroll(lastX, lastY, amount);
        return true;
    }
}
