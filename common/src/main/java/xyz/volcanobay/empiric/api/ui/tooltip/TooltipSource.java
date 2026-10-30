package xyz.volcanobay.empiric.api.ui.tooltip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the floating tooltip a target that isn't a module, such as one piece among several in a block. Asked on the
 * client every tick, before the modules of the looked-at block; the first source with a target wins.
 */
@FunctionalInterface
public interface TooltipSource {

    @Nullable Target find(Level level, BlockHitResult hit, Player viewer);

    /**
     * @param key Equal for the same target from tick to tick, so the tooltip stays open
     * @param box The target's world box, which the tooltip hangs beside
     */
    record Target(Object key, AABB box, TooltipBehavior behavior) {
    }
}
