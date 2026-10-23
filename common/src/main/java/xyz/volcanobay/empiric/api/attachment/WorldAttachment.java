package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.module.Side;

import java.util.Collection;

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

    /**
     * The players that get this attachment's modules, the same audience as {@link #syncTarget()} as a list so a module can
     * give each one a different share. Null if the attachment can't list them, which means its modules can't be tracked.
     */
    default Collection<ServerPlayer> viewers() {
        return null;
    }

    static Collection<ServerPlayer> trackingChunk(Level level, ChunkPos chunk) {
        return ((ServerLevel) level).getChunkSource().chunkMap.getPlayers(chunk, false);
    }

    static Collection<ServerPlayer> trackingChunk(Level level, BlockPos pos) {
        return trackingChunk(level, new ChunkPos(pos));
    }
}
