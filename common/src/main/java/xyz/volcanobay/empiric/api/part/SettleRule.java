package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A trait that keeps or drops its piece when what's under it changes, instead of standing it on a surface.
 */
public interface SettleRule {

    /**
     * @param workspace Of {@code pos}, which is the piece's own block
     * @return Whether it dealt with the piece; if not, the piece falls to the surface below or drops
     */
    boolean settle(Level level, BlockPos pos, Workspace workspace, Piece piece);
}
