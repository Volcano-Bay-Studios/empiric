package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.Collection;
import java.util.function.Consumer;

public final class ServerAttachment implements WorldAttachment {

    private static ServerAttachment server = new ServerAttachment(Side.SERVER);
    private static ServerAttachment client = new ServerAttachment(Side.CLIENT);

    private final Side side;
    private final ModuleHolder modules = new ModuleHolder(this);

    private ServerAttachment(Side side) {
        this.side = side;
    }

    public static ServerAttachment of(Side side) {
        return side == Side.CLIENT ? client : server;
    }

    public static ServerAttachment of(Level level) {
        return of(level.isClientSide ? Side.CLIENT : Side.SERVER);
    }

    public static void reset(Side side) {
        if (side == Side.CLIENT) {
            client = new ServerAttachment(Side.CLIENT);
        } else {
            server = new ServerAttachment(Side.SERVER);
        }
    }

    @Override
    public Level level() {
        if (side == Side.CLIENT) {
            return ClientLookup.level();
        }
        MinecraftServer current = WorldModuleSystem.server();
        return current == null ? null : current.overworld();
    }

    @Override
    public Side side() {
        return level() == null ? Side.NONE : side;
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.SERVER;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public PacketTarget syncTarget() {
        return PacketTarget.all(WorldModuleSystem.server());
    }

    @Override
    public Collection<ServerPlayer> viewers() {
        return WorldModuleSystem.server().getPlayerList().getPlayers();
    }

    public static final class Type extends AttachmentType<ServerAttachment, Side> {

        Type() {
            super("empiric:server");
        }

        @Override
        public ServerAttachment get(Side side) {
            return of(side);
        }

        @Override
        public void writeKey(DataBuffer buffer, ServerAttachment attachment, DataContext context) {
        }

        @Override
        public ServerAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            return side == Side.NONE ? null : of(side);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<ServerAttachment> action) {
            ServerAttachment attachment = of(side);
            if (side != Side.NONE && attachment.side() == side) {
                action.accept(attachment);
            }
        }
    }
}
