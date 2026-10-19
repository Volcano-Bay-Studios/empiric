package xyz.volcanobay.empiric.client.ui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import xyz.volcanobay.empiric.api.ui.surface.WorldSurface;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceRenderer;
import xyz.volcanobay.empiric.client.ui.tooltip.TooltipSystem;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;

/**
 * Sends typing to a widget tree in the world, a tooltip or a surface, while one of its text fields has focus and no
 * screen is open. Keys stop reaching the game meanwhile, so typing doesn't walk the player around; Escape ends it.
 */
public final class UiKeyboard {

    private static final ClientUiHost.Characters CHARACTERS = new ClientUiHost.Characters();
    private static boolean capturing;

    private UiKeyboard() {
    }

    public static UiRoot capturing() {
        if (Minecraft.getInstance().screen != null) {
            return null;
        }
        if (TooltipSystem.capturesKeyboard()) {
            return TooltipSystem.root();
        }
        for (WorldSurface surface : SurfaceRenderer.surfaces()) {
            Widget focused = surface.root().focused();
            if (focused != null && focused.capturesKeyboard()) {
                return surface.root();
            }
        }
        return null;
    }

    /**
     * Lets go of movement keys held when typing started, since their release won't reach the game.
     */
    public static void tick() {
        boolean now = capturing() != null;
        if (now && !capturing) {
            KeyMapping.releaseAll();
        }
        capturing = now;
    }

    public static boolean key(int key, int action, int modifiers) {
        UiRoot root = capturing();
        if (root == null) {
            return false;
        }
        if (action != GLFW.GLFW_RELEASE) {
            root.key(key, modifiers);
        }
        return true;
    }

    public static boolean character(int codepoint, int modifiers) {
        UiRoot root = capturing();
        if (root == null) {
            return false;
        }
        if (codepoint <= Character.MAX_VALUE) {
            int joined = CHARACTERS.accept((char) codepoint);
            if (joined >= 0) {
                root.character(joined, modifiers);
            }
        } else {
            root.character(codepoint, modifiers);
        }
        return true;
    }
}
