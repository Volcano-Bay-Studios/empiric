package xyz.volcanobay.empiric.system;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Runs {@link SubLevelAttachment}s. Only touched when Sable is installed.
 * Sublevel lifecycle track sublevel load state. Writes on read.
 */
public final class SubLevelModuleSystem {

    private static int loading;

    private SubLevelModuleSystem() {
    }

    public static void bootstrap() {
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> container.addObserver(new Observer()));
    }

    public static void registerAttachment(ModuleSystem modules) {
        modules.registerAttachment(SubLevelAttachment.TYPE);
    }

    public static void beginLoad() {
        loading++;
    }

    public static void endLoad(@Nullable ServerSubLevel subLevel) {
        loading--;
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        SubLevelAttachment attachment = SubLevelAttachment.of(subLevel);
        CompoundTag data = subLevel.getUserDataTag();
        if (data != null && data.contains(WorldModuleSystem.TAG)) {
            attachment.pending(data.getByteArray(WorldModuleSystem.TAG));
        }
        WorldModuleSystem.activate(attachment);
    }

    public static void save(ServerSubLevel subLevel) {
        SubLevelAttachment attachment = SubLevelAttachment.of(subLevel);
        byte[] bytes;
        if (attachment.isActive()) {
            bytes = attachment.modules().isEmpty() ? null : WorldModuleSystem.encode(attachment);
        } else {
            bytes = attachment.pending();
        }
        CompoundTag data = subLevel.getUserDataTag();
        if (bytes == null) {
            if (data != null) {
                data.remove(WorldModuleSystem.TAG);
            }
            return;
        }
        if (data == null) {
            data = new CompoundTag();
            subLevel.setUserDataTag(data);
        }
        data.putByteArray(WorldModuleSystem.TAG, bytes);
    }

    private static void added(SubLevel subLevel) {
        if (loading > 0 && subLevel instanceof ServerSubLevel) {
            return;
        }
        WorldModuleSystem.activate(SubLevelAttachment.of(subLevel));
    }

    private static void removed(SubLevel subLevel, SubLevelRemovalReason reason) {
        SubLevelAttachment attachment = SubLevelAttachment.of(subLevel);
        if (!attachment.isActive()) {
            return;
        }
        if (reason == SubLevelRemovalReason.REMOVED && subLevel instanceof ServerSubLevel) {
            EmpiricCore.modules().detachAll(attachment);
        } else {
            EmpiricCore.modules().unload(attachment);
        }
        attachment.synced().clear();
        attachment.active(false);
    }

    private static void tick(SubLevelContainer container) {
        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (!(subLevel instanceof ServerSubLevel server) || subLevel.isRemoved()) {
                continue;
            }
            SubLevelAttachment attachment = SubLevelAttachment.of(subLevel);
            if (!attachment.isActive()) {
                continue;
            }
            Collection<UUID> tracking = server.getTrackingPlayers();
            Set<UUID> synced = attachment.synced();
            synced.retainAll(tracking);
            for (UUID id : tracking) {
                if (synced.add(id) && server.getLevel().getPlayerByUUID(id) instanceof ServerPlayer player) {
                    ModuleSyncSystem.syncTo(player, attachment);
                }
            }
        }
    }

    private static final class Observer implements SubLevelObserver {

        @Override
        public void onSubLevelAdded(SubLevel subLevel) {
            added(subLevel);
        }

        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            removed(subLevel, reason);
        }

        @Override
        public void tick(SubLevelContainer container) {
            SubLevelModuleSystem.tick(container);
        }
    }
}
