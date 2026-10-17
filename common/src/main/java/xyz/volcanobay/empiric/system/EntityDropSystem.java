package xyz.volcanobay.empiric.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.module.EmpiricDropPolicies;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

public final class EntityDropSystem {

    private static final Set<EntityAttachment> HELD = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<EntityAttachment> TRANSFERRING = new ThreadLocal<>();

    private EntityDropSystem() {
    }

    public static Predicate<ModuleType<?, ?>> saveFilter(EntityAttachment attachment) {
        if (TRANSFERRING.get() == attachment) {
            return type -> !type.dropsOn(EmpiricDropPolicies.LEVEL_CHANGE);
        }
        return ModuleType::persists;
    }

    public static boolean held(EntityAttachment attachment) {
        return HELD.contains(attachment);
    }

    public static void transferring(Entity entity) {
        TRANSFERRING.set(entity == null ? null : EntityAttachment.of(entity));
    }

    public static void respawning(ServerPlayer player) {
        HELD.add(EntityAttachment.of(player));
    }

    public static void restored(ServerPlayer player, ServerPlayer previous, boolean keepEverything) {
        EntityAttachment source = EntityAttachment.of(previous);
        if (!HELD.remove(source)) {
            return;
        }
        boolean died = !keepEverything;
        boolean changedLevel = previous.level() != player.level();
        EntityAttachment.of(player).pending(WorldModuleSystem.encodeAll(source, type ->
                !(died && type.dropsOn(EmpiricDropPolicies.DEATH))
                        && !(changedLevel && type.dropsOn(EmpiricDropPolicies.LEVEL_CHANGE))));
        EmpiricCore.modules().detachAll(source);
    }

    public static void changingLevel(ServerPlayer player) {
        EntityAttachment attachment = EntityAttachment.of(player);
        EmpiricCore.modules().drop(attachment, EmpiricDropPolicies.LEVEL_CHANGE);
        attachment.pending(WorldModuleSystem.encodeAll(attachment, type -> true));
    }

    public static void inventoryCleared(Player player) {
        if (!player.level().isClientSide()) {
            EmpiricCore.modules().drop(EntityAttachment.of(player), EmpiricDropPolicies.INVENTORY_CLEAR);
        }
    }
}
