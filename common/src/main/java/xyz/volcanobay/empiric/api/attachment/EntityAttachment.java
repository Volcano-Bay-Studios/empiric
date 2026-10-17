package xyz.volcanobay.empiric.api.attachment;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.ext.EntityExtension;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.function.Consumer;

public final class EntityAttachment extends PersistentAttachment {

    private final Entity entity;
    private final ModuleHolder modules = new ModuleHolder(this);

    public EntityAttachment(Entity entity) {
        this.entity = entity;
    }

    public static EntityAttachment of(Entity entity) {
        return ((EntityExtension) entity).empiric$attachment();
    }

    public Entity entity() {
        return entity;
    }

    @Override
    public Level level() {
        return entity.level();
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.ENTITY;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public VeilPacketManager.PacketSink syncTarget() {
        return entity instanceof ServerPlayer ? VeilPacketManager.trackingAndSelf(entity) : VeilPacketManager.tracking(entity);
    }

    @Override
    public Vec3 position() {
        return entity.position();
    }

    public static final class Type extends AttachmentType<EntityAttachment, Entity> {

        Type() {
            super("empiric:entity");
        }

        @Override
        public EntityAttachment get(Entity entity) {
            return of(entity);
        }

        @Override
        public void writeKey(DataBuffer buffer, EntityAttachment attachment, DataContext context) {
            buffer.putVarInt(attachment.entity.getId());
        }

        @Override
        public EntityAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            int id = buffer.takeVarInt();
            Level level = WorldModuleSystem.keyLevel(side);
            Entity entity = level == null ? null : level.getEntity(id);
            return entity == null ? null : of(entity);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<EntityAttachment> action) {
            if (side == Side.CLIENT) {
                ClientLookup.forEachEntity(entity -> accept(entity, action));
                return;
            }
            MinecraftServer server = WorldModuleSystem.server();
            if (server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    level.getAllEntities().forEach(entity -> accept(entity, action));
                }
            }
        }

        private static void accept(Entity entity, Consumer<EntityAttachment> action) {
            EntityAttachment attachment = of(entity);
            if (attachment.isActive()) {
                action.accept(attachment);
            }
        }
    }
}
