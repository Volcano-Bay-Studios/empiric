package xyz.volcanobay.empiric.api.ui;

import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;

/**
 * Server-side hooks for a module that screens are opened on. Without it, any player in range can open them.
 */
public interface UiBehavior extends NetworkBehavior {

    default boolean canOpen(ServerPlayer player, UiScreenType<?> screen) {
        return inRange(player);
    }

    default void onOpened(ServerPlayer player, UiScreenType<?> screen) {
    }

    default void onClosed(ServerPlayer player, UiScreenType<?> screen) {
    }
}
