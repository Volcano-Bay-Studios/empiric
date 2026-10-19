package xyz.volcanobay.empiric.api.behavior;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.attachment.WorldAttachment;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.SyncBehavior;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;

public interface NetworkBehavior extends SyncBehavior {

    double CLIENT_UPDATE_RANGE = 64;

    default VeilPacketManager.PacketSink syncTarget() {
        return ModuleSyncSystem.target((Module<?>) this);
    }

    default void onTracked(VeilPacketManager.PacketSink sink) {
    }

    default boolean acceptClientUpdate(ServerPlayer player, String field) {
        return inRange(player);
    }

    /**
     * Runs after a client's value for a field was applied, before it syncs to everyone else, so the module can clean it
     * up, such as running {@code NodeGraph.sanitize} on a graph.
     */
    default void onClientUpdate(ServerPlayer player, String field) {
    }

    /**
     * Whether the player is close enough to interact: always for server attachments, otherwise in the same level and
     * within {@link #CLIENT_UPDATE_RANGE} of the attachment's position, if it has one.
     */
    default boolean inRange(ServerPlayer player) {
        Attachment attachment = ((Module<?>) this).attachment();
        while (attachment instanceof ModuleAttachment child) {
            attachment = child.parent().attachment();
        }
        if (attachment instanceof ServerAttachment) {
            return true;
        }
        if (!(attachment instanceof WorldAttachment world) || world.level() != player.level()) {
            return false;
        }
        Vec3 position = world.position();
        if (position == null) {
            return true;
        }
        double dx = position.x - player.getX();
        double dz = position.z - player.getZ();
        double dy = Double.isNaN(position.y) ? 0 : position.y - player.getY();
        return dx * dx + dy * dy + dz * dz <= CLIENT_UPDATE_RANGE * CLIENT_UPDATE_RANGE;
    }
}
