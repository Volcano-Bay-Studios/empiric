package xyz.volcanobay.empiric.gametest.registry.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import xyz.volcanobay.empiric.api.registry.ClientTooltipSource;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipSource;

@ClientTooltipSource
public class FixtureTooltipSource implements TooltipSource {

    static {
        System.setProperty("empiric.test.tooltip_source_loaded", "true");
    }

    @Override
    public Target find(Level level, BlockHitResult hit, Player viewer) {
        return null;
    }
}
