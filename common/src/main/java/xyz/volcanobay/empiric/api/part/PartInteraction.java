package xyz.volcanobay.empiric.api.part;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import org.jetbrains.annotations.Nullable;

/**
 * Something a player can do to a piece. Register with {@link PartInteractions#register}; the first one, by priority,
 * that returns a result wins. Picking a piece up with an empty hand is the fallback after all of them.
 */
public interface PartInteraction {

    /**
     * @return Null when it doesn't apply
     */
    default @Nullable ItemInteractionResult useItemOn(PartContext context) {
        return null;
    }

    /**
     * Using it with an empty main hand.
     *
     * @return Null when it doesn't apply
     */
    default @Nullable InteractionResult useWithoutItem(PartContext context) {
        return null;
    }
}
