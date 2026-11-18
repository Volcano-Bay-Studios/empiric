package xyz.volcanobay.empiric.system;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.api.attachment.WorldAttachment;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.system.SystemScheduler;
import xyz.volcanobay.empiric.core.system.SystemUnit;

public final class McSystems {

    private McSystems() {
    }

    public static void configure(SystemScheduler scheduler) {
        scheduler.pin(Level.class, Entity.class, BlockEntity.class, ItemStack.class, Player.class, LevelChunk.class, MinecraftServer.class,
                WorldAttachment.class, ItemStackAttachment.class);
    }

    public static void guard(String what) {
        if (!SystemScheduler.running) {
            return;
        }
        SystemUnit unit = SystemScheduler.currentUnit();
        if (unit != null && unit.isParallel() && EmpiricCore.engine().systems().isDevMode()) {
            throw new IllegalStateException(unit.id() + " called " + what + " from a parallel system. MC access must stay on the main thread. Take an MC type as a parameter to pin it, or mark it @MainThread");
        }
    }
}
