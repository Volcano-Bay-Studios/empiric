package xyz.volcanobay.empiric.api.ui.tooltip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TooltipSources {

    private static final List<TooltipSource> SOURCES = new CopyOnWriteArrayList<>();

    private TooltipSources() {
    }

    public static void register(TooltipSource source) {
        SOURCES.add(source);
    }

    public static @Nullable TooltipSource.Target find(Level level, BlockHitResult hit, Player viewer) {
        for (TooltipSource source : SOURCES) {
            TooltipSource.Target target = source.find(level, hit, viewer);
            if (target != null) {
                return target;
            }
        }
        return null;
    }
}
