package xyz.volcanobay.empiric.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctMultiblock;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.ext.BlockGetterOctExtension;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.oct.FullOctBlock;

import java.util.LinkedHashSet;
import java.util.Set;

public final class OctMultiblockSystem {

    private static final double NUDGE = 1.0E-4;

    private OctMultiblockSystem() {
    }

    public static Set<OctPos> cells(BlockState state, BlockGetter level, BlockPos center) {
        Set<OctPos> cells = new LinkedHashSet<>();
        if (!(state.getBlock() instanceof OctMultiblock multiblock)) {
            return cells;
        }
        for (AABB box : multiblock.getMultiblockShape(state, level, center).toAabbs()) {
            long minX = (long) Math.floor((center.getX() + box.minX) * 2 + NUDGE);
            long minY = (long) Math.floor((center.getY() + box.minY) * 2 + NUDGE);
            long minZ = (long) Math.floor((center.getZ() + box.minZ) * 2 + NUDGE);
            long maxX = (long) Math.ceil((center.getX() + box.maxX) * 2 - NUDGE);
            long maxY = (long) Math.ceil((center.getY() + box.maxY) * 2 - NUDGE);
            long maxZ = (long) Math.ceil((center.getZ() + box.maxZ) * 2 - NUDGE);
            for (long x = minX; x < maxX; x++) {
                for (long y = minY; y < maxY; y++) {
                    for (long z = minZ; z < maxZ; z++) {
                        OctPos cell = new OctPos(x, y, z);
                        if (!cell.pos().equals(center)) {
                            cells.add(cell);
                        }
                    }
                }
            }
        }
        return cells;
    }

    public static boolean canPlace(Level level, BlockPos center, BlockState state) {
        OctLevel octLevel = LevelOctExtension.get(level);
        for (OctPos cell : cells(state, level, center)) {
            if (!octLevel.getBlockState(cell).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    public static void fill(Level level, BlockPos center, BlockState state) {
        OctLevel octLevel = LevelOctExtension.get(level);
        for (OctPos cell : cells(state, level, center)) {
            BlockState current = octLevel.getBlockState(cell);
            if (current.isAir() || (current.canBeReplaced() && !current.is(Blocks.WATER))) {
                OctBlockComplement.placeMultiblockComplement(octLevel, cell, center);
            }
        }
    }

    public static void clear(Level level, BlockPos center, BlockState state) {
        OctLevel octLevel = LevelOctExtension.get(level);
        for (OctPos cell : cells(state, level, center)) {
            if (center.equals(OctBlockComplement.getMultiblockCenter(octLevel, cell.pos(), cell.octant()))) {
                octLevel.setOctState(cell.pos(), cell.octant(), Blocks.AIR.defaultBlockState());
            }
        }
    }

    public static void blockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (level.isClientSide) {
            return;
        }
        boolean wasMultiblock = oldState.getBlock() instanceof OctMultiblock;
        boolean isMultiblock = newState.getBlock() instanceof OctMultiblock;
        if (!wasMultiblock && !isMultiblock) {
            return;
        }
        if (wasMultiblock) {
            clear(level, pos, oldState);
        }
        if (isMultiblock) {
            fill(level, pos, newState);
        }
    }

    public static BlockPos center(BlockGetter level, BlockPos pos, Player player) {
        int octant = FullOctBlock.destroyedOctant(player, pos);
        if (octant < 0) {
            return null;
        }
        return OctBlockComplement.getMultiblockCenter(BlockGetterOctExtension.get(level), pos, octant);
    }
}
