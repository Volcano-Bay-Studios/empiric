package xyz.volcanobay.empiric.api.attachment;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import xyz.volcanobay.empiric.api.network.PacketTarget;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.ext.SubLevelExtension;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modules on a Sable sub-level. Only usable when Sable is installed; the type is registered by
 * {@link xyz.volcanobay.empiric.system.SubLevelModuleSystem}. Modules are saved in the sub-level's user data, keyed by its
 * UUID on the network, and synced to the players tracking the sub-level.
 */
public final class SubLevelAttachment extends PersistentAttachment {

    public static final Type TYPE = new Type();

    private final SubLevel subLevel;
    private final ModuleHolder modules = new ModuleHolder(this);
    private final Set<UUID> synced = new HashSet<>();

    public SubLevelAttachment(SubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public static SubLevelAttachment of(SubLevel subLevel) {
        return ((SubLevelExtension) subLevel).empiric$attachment();
    }

    public static SubLevelAttachment find(Level level, UUID id) {
        SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(id);
        return subLevel == null ? null : of(subLevel);
    }

    public SubLevel subLevel() {
        return subLevel;
    }

    /**
     * Players whose client has this sub-level's modules.
     */
    public Set<UUID> synced() {
        return synced;
    }

    @Override
    public Level level() {
        return subLevel.getLevel();
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return TYPE;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public PacketTarget syncTarget() {
        if (subLevel instanceof ServerSubLevel server) {
            // Sable's sink is Veil's; Sable brings Veil along regardless.
            return server.playerSink()::sendPacket;
        }
        return PacketTarget.NONE;
    }

    @Override
    public Vec3 position() {
        Vector3dc position = subLevel.logicalPose().position();
        return new Vec3(position.x(), position.y(), position.z());
    }

    public static final class Type extends AttachmentType<SubLevelAttachment, SubLevel> {

        Type() {
            super("empiric:sub_level");
        }

        @Override
        public SubLevelAttachment get(SubLevel subLevel) {
            return of(subLevel);
        }

        @Override
        public void writeKey(DataBuffer buffer, SubLevelAttachment attachment, DataContext context) {
            UUID id = attachment.subLevel.getUniqueId();
            buffer.putLong(id.getMostSignificantBits());
            buffer.putLong(id.getLeastSignificantBits());
        }

        @Override
        public SubLevelAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            UUID id = new UUID(buffer.takeLong(), buffer.takeLong());
            return SubLevelAttachment.find(WorldModuleSystem.keyLevel(side), id);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<SubLevelAttachment> action) {
            if (side == Side.CLIENT) {
                accept(ClientLookup.level(), action);
                return;
            }
            MinecraftServer server = WorldModuleSystem.server();
            if (side == Side.SERVER && server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    accept(level, action);
                }
            }
        }

        private static void accept(Level level, Consumer<SubLevelAttachment> action) {
            SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container == null) {
                return;
            }
            for (SubLevel subLevel : container.getAllSubLevels()) {
                SubLevelAttachment attachment = of(subLevel);
                if (attachment.isActive()) {
                    action.accept(attachment);
                }
            }
        }
    }
}
