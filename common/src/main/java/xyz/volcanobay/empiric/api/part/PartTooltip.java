package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipBehavior;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

import java.util.UUID;

/**
 * A trait hook giving a piece a floating tooltip while it's aimed at. Asked on the client every tick, so it should
 * only make the behaviour, which reads the piece through {@link Ref#get()} as it changes.
 */
@FunctionalInterface
public interface PartTooltip {

    @Nullable TooltipBehavior tooltip(Ref piece);

    /**
     * Finds a piece again each time, since a synced block replaces its pieces.
     *
     * @param pos The block the piece was aimed at in; {@link #get()} gives the piece in its pixels
     */
    record Ref(Level level, BlockPos pos, UUID id) {

        public @Nullable Piece get() {
            return Workspace.of(LevelOctExtension.get(level), pos).find(id);
        }
    }
}
