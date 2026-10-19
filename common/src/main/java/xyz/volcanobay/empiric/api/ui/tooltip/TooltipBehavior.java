package xyz.volcanobay.empiric.api.ui.tooltip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A module that adds to the floating tooltip of whatever it's attached to. Rows from its {@code @Tooltip} fields come
 * first; {@link #tooltip} adds anything else, from text to any widget. The tooltip is a small screen: when
 * {@link #canInteract} allows it, the crosshair is its pointer, attack clicks, use right clicks and the wheel scrolls.
 * Runs on the client, so it only sees synced fields.
 */
public interface TooltipBehavior {

    /**
     * The heading above every row, or null for none.
     */
    default String tooltipTitle(Player viewer) {
        return null;
    }

    /**
     * An item drawn beside the heading.
     */
    default ItemStack tooltipIcon() {
        return ItemStack.EMPTY;
    }

    /**
     * Adds rows below the {@code @Tooltip} rows when the tooltip opens. Rows that should change bind to fields or take suppliers rather than reading
     * values here, since this runs once per opening.
     */
    default void tooltip(TooltipContent content, Player viewer) {
    }

    default boolean showsTooltip(Player viewer) {
        return true;
    }

    /**
     * Whether the viewer can point at and use the tooltip's widgets, for example only while holding a tool. Otherwise
     * it only displays.
     */
    default boolean canInteract(Player viewer) {
        return true;
    }
}
