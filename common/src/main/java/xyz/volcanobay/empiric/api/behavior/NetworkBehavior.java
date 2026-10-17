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
