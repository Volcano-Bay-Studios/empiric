package xyz.volcanobay.empiric.api.module;

import net.minecraft.server.level.ServerPlayer;

/**
 * Implemented by a {@code @SelfSpec} block entity that wants to vet or clean up edits to its {@code clientMutable} fields.
 * {@link SelfModule} calls these after its own range check.
 */
public interface SelfNetworkBehavior {

    default boolean acceptClientUpdate(ServerPlayer player, String field) {
        return true;
    }

    /**
     * Runs after a client's value for a field was applied, before it syncs on, so the block entity can clamp it or react.
     */
    default void onClientUpdate(ServerPlayer player, String field) {
    }
}
