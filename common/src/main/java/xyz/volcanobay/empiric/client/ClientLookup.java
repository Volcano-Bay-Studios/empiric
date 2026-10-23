package xyz.volcanobay.empiric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

public final class ClientLookup {

    private ClientLookup() {
    }

    public static Level level() {
        return Minecraft.getInstance().level;
    }

    public static HitResult hitResult() {
        return Minecraft.getInstance().hitResult;
    }

    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static void blockChanged(BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void forEachEntity(Consumer<Entity> action) {
        if (Minecraft.getInstance().level != null) {
            Minecraft.getInstance().level.entitiesForRendering().forEach(action);
        }
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("Can't send packets to the server while not connected");
        }
        connection.send(new ServerboundCustomPayloadPacket(payload));
    }

    /**
     * Runs the task on the client thread: right away when already on it, otherwise queued.
     */
    public static void execute(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            task.run();
        } else {
            minecraft.execute(task);
        }
    }
}
