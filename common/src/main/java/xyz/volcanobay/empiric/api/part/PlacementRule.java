package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A trait that decides where its part goes instead of, or before, standing it on a surface.
 */
public interface PlacementRule {

    /**
     * @param aim      The point aimed at, in the host's pixels
     * @param rotation Quarter turns the part would have
     * @return Null to leave it to the next rule and then to a surface
     */
    @Nullable Placement.Result place(Level level, PartType type, BlockPos host, Vec3 aim, int rotation);
}
