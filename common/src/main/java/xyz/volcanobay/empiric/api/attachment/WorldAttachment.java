package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.module.Side;

public interface WorldAttachment extends Attachment {

    Level level();

    PacketTarget syncTarget();

    @Override
    default Side side() {
        Level level = level();
        if (level == null) {
            return Side.NONE;
        }
        return level.isClientSide ? Side.CLIENT : Side.SERVER;
    }

    default Vec3 position() {
        return null;
    }
}
