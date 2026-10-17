package xyz.volcanobay.empiric.api.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class AtmosphericHelper {
    public static boolean isAirtight(Level level, BlockPos pos) {
        AABB bounds = level.getBlockState(pos).getShape(level, pos).bounds();
        return bounds.maxX - bounds.minX > 1 && bounds.maxY - bounds.minY > 1 && bounds.maxZ - bounds.minZ > 1;
    }
}
