package xyz.volcanobay.empiric.system;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.api.attachment.SubLevelSplit;
import xyz.volcanobay.empiric.api.behavior.SplitBehavior;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs {@link SubLevelAttachment}s. Only touched when Sable is installed.
 * Sublevel lifecycle track sublevel load state. Writes on read.
 */
public final class SubLevelModuleSystem {

    private static int loading;
    /**
     * Blocks Sable is about to assemble into a split-off sub-level, claimed by the next sub-level added.
     */
    private static PendingSplit pending;
    /**
     * Splits waiting for the container tick, so the blocks have moved before {@link SplitBehavior}s hear about them.
     */
    private static final List<SubLevelSplit> SPLITS = new ArrayList<>();

    private SubLevelModuleSystem() {
    }

    public static void bootstrap() {
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> container.addObserver(new Observer()));
        SubLevelHeatMapManager.addSplitListener(SubLevelModuleSystem::splitting);
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
        if (pending != null && subLevel instanceof ServerSubLevel && subLevel.getLevel() == pending.from().getLevel()) {
            SPLITS.add(new SubLevelSplit(SubLevelAttachment.of(pending.from()), SubLevelAttachment.of(subLevel), pending.blocks()));
            pending = null;
        }
    }

    private static void splitting(Level level, BoundingBox3ic bounds, Collection<BlockPos> blocks) {
        pending = null;
        if (level.isClientSide || blocks.isEmpty()) {
            return;
        }
        BlockPos anchor = blocks.iterator().next();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        LevelPlot plot = container == null ? null : container.getPlot(SectionPos.blockToSectionCoord(anchor.getX()), SectionPos.blockToSectionCoord(anchor.getZ()));
        if (plot != null) {
            pending = new PendingSplit(plot.getSubLevel(), List.copyOf(blocks));
        }
    }

    private static void fireSplits(SubLevelContainer container) {
        Iterator<SubLevelSplit> iterator = SPLITS.iterator();
        while (iterator.hasNext()) {
            SubLevelSplit split = iterator.next();
            if (split.from().subLevel().getLevel() != container.getLevel()) {
                continue;
            }
            iterator.remove();
            if (!split.from().isActive() || !split.into().isActive()) {
                continue;
            }
            split.from().modules().forEach(module -> {
                if (module instanceof SplitBehavior behavior && module.isAttached()) {
                    behavior.onSplit(split);
                }
            });
        }
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
        if (!SPLITS.isEmpty()) {
            fireSplits(container);
        }
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

    private record PendingSplit(SubLevel from, List<BlockPos> blocks) {
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
