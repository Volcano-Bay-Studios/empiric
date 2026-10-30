package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;

/**
 * Where a part's minimum corner goes, in the host's pixels; it may be outside 0 to 15 until normalized.
 *
 * @param rotation Quarter turns, 0 to 3
 */
public record Spot(BlockPos host, int x, int y, int z, int rotation) {

    public Spot(BlockPos host, int x, int y, int z) {
        this(host, x, y, z, 0);
    }

    /**
     * @return The same spot from the block holding its corner, so its pixels are 0 to 15
     */
    public Spot normalized() {
        return new Spot(host.offset(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16)),
                Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16), rotation);
    }
}
