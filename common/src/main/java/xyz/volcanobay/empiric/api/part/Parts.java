package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctGetter;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Placing, moving and removing pieces, and keeping the octants they fill turned into their cell blocks.
 */
public final class Parts {

    private static final UUID PROBE = new UUID(0, 0);

    private Parts() {
    }

    public static int octant(int x, int y, int z) {
        return (x >= 8 ? 1 : 0) | (y >= 8 ? 2 : 0) | (z >= 8 ? 4 : 0);
    }

    /**
     * @return The octants of the block a box touches, from its pixel corners (maximums are exclusive)
     */
    public static List<Integer> cells(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Integer> cells = new ArrayList<>();
        if (maxX <= 0 || maxY <= 0 || maxZ <= 0 || minX >= 16 || minY >= 16 || minZ >= 16) {
            return cells;
        }
        for (int bits = 0; bits < 8; bits++) {
            boolean hiX = (bits & 1) != 0;
            boolean hiY = (bits & 2) != 0;
            boolean hiZ = (bits & 4) != 0;
            if ((hiX ? maxX > 8 : minX < 8) && (hiY ? maxY > 8 : minY < 8) && (hiZ ? maxZ > 8 : minZ < 8)) {
                cells.add(bits);
            }
        }
        return cells;
    }

    public static List<Integer> cells(AABB box) {
        return cells(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static List<Integer> cells(PartType type, int x, int y, int z) {
        return cells(x, y, z, x + type.width(), y + type.height(), z + type.depth());
    }

    /**
     * @return The octants a piece fills, counting what takes up room with it
     */
    public static List<Integer> cells(Piece piece) {
        return cells(piece.bodyBox());
    }

    public static @Nullable PartOctEntity entity(OctGetter oct, BlockPos pos, int octant) {
        return oct.getOctEntity(pos, octant) instanceof PartOctEntity entity ? entity : null;
    }

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof PartOctBlock;
    }

    /**
     * @return The blocks a box touches; the box is in the host's pixels
     */
    public static List<BlockPos> blocksOf(BlockPos host, AABB box) {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = Math.floorDiv((int) Math.floor(box.minX + 1.0E-6), 16);
        int minY = Math.floorDiv((int) Math.floor(box.minY + 1.0E-6), 16);
        int minZ = Math.floorDiv((int) Math.floor(box.minZ + 1.0E-6), 16);
        int maxX = Math.floorDiv((int) Math.ceil(box.maxX - 1.0E-6) - 1, 16);
        int maxY = Math.floorDiv((int) Math.ceil(box.maxY - 1.0E-6) - 1, 16);
        int maxZ = Math.floorDiv((int) Math.ceil(box.maxZ - 1.0E-6) - 1, 16);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    blocks.add(host.offset(bx, by, bz));
                }
            }
        }
        return blocks;
    }

    /**
     * @return A box in the host's pixels as the block's own pixels
     */
    public static AABB local(BlockPos host, BlockPos block, AABB box) {
        return box.move(-16 * (block.getX() - host.getX()), -16 * (block.getY() - host.getY()), -16 * (block.getZ() - host.getZ()));
    }

    /**
     * @param box In the host's pixels
     * @return Whether every block the box touches is free there: replaceable, or oct content that is air or a part
     */
    public static boolean roomFor(Level level, BlockPos host, AABB box) {
        OctLevel oct = LevelOctExtension.get(level);
        for (BlockPos block : blocksOf(host, box)) {
            if (!level.getBlockState(block).is(EmpiricBlocks.OCT_BLOCK.get())) {
                if (!isFreeHost(level, block)) {
                    return false;
                }
                continue;
            }
            for (int cell : cells(local(host, block, box))) {
                BlockState state = oct.getOctState(block, cell);
                if (!state.isAir() && !isPart(state)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return Whether there is room at the spot, ignoring support; the spot may reach into neighbouring blocks
     */
    public static boolean canFit(Level level, BlockPos host, PartType type, int x, int y, int z, int rotation, @Nullable UUID ignore) {
        Spot spot = new Spot(host, x, y, z, rotation).normalized();
        BlockPos home = spot.host();
        int width = Piece.width(type, rotation);
        int depth = Piece.depth(type, rotation);
        AABB box = new AABB(spot.x(), spot.y(), spot.z(), spot.x() + width, spot.y() + type.height(), spot.z() + depth);
        if (!roomFor(level, home, box)) {
            return false;
        }
        return !Workspace.of(LevelOctExtension.get(level), home).blocked(new Piece(PROBE, type, spot.x(), spot.y(), spot.z(), rotation, ItemStack.EMPTY), ignore);
    }

    public static boolean isFreeHost(Level level, BlockPos host) {
        BlockState state = level.getBlockState(host);
        return level.getFluidState(host).isEmpty() && (state.isAir() || state.canBeReplaced());
    }

    /**
     * @param stack The item being placed, which becomes the piece and keeps its components
     * @return The piece, or null if it didn't fit
     */
    public static @Nullable Piece place(Level level, Spot spot, PartType type, ItemStack stack) {
        Spot normal = spot.normalized();
        BlockPos host = normal.host();
        if (!canFit(level, host, type, normal.x(), normal.y(), normal.z(), normal.rotation(), null)) {
            return null;
        }
        Piece piece = new Piece(UUID.randomUUID(), type, normal.x(), normal.y(), normal.z(), normal.rotation(), stack);
        for (BlockPos block : blocksOf(host, piece.box())) {
            BlockState state = level.getBlockState(block);
            if (!state.isAir() && !state.is(EmpiricBlocks.OCT_BLOCK.get())) {
                level.destroyBlock(block, true);
            }
        }
        if (!adopt(level, host, piece)) {
            reconcile(level, host, piece.box());
            return null;
        }
        reconcile(level, host, piece.box());
        PartOctEntity owner = entity(LevelOctExtension.get(level), host, piece.anchor());
        if (owner != null) {
            owner.changed();
        }
        return piece;
    }

    private static boolean adopt(Level level, BlockPos host, Piece piece) {
        OctLevel oct = LevelOctExtension.get(level);
        int anchor = piece.anchor();
        BlockState state = oct.getOctState(host, anchor);
        if (!isPart(state) && !oct.setOctState(host, anchor, piece.type.cell().defaultBlockState())) {
            return false;
        }
        PartOctEntity entity = entity(oct, host, anchor);
        if (entity == null) {
            return false;
        }
        entity.add(piece);
        return true;
    }

    /**
     * @return The piece's stack, empty if it wasn't found
     */
    public static ItemStack remove(Level level, BlockPos host, UUID id) {
        Workspace workspace = Workspace.of(LevelOctExtension.get(level), host);
        Piece piece = workspace.find(id);
        PartOctEntity owner = piece == null ? null : workspace.owner(piece);
        if (owner == null) {
            return ItemStack.EMPTY;
        }
        owner.remove(id);
        owner.changed();
        AABB body = piece.bodyBox();
        reconcile(level, host, body);
        for (BlockPos block : blocksOf(host, new AABB(body.minX - 16, body.minY, body.minZ - 16, body.maxX, body.maxY + 16, body.maxZ))) {
            requestSettle(level, block);
        }
        return piece.stack;
    }

    /**
     * Removes the piece and pops its stack into the world.
     */
    public static void drop(Level level, BlockPos host, Piece piece) {
        ItemStack stack = remove(level, host, piece.id);
        if (!stack.isEmpty()) {
            Block.popResource(level, host, stack);
        }
        level.playSound(null, host, piece.type.cell().defaultBlockState().getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /**
     * @param piece As the host's workspace sees it
     * @param y     In the host's pixels
     */
    public static boolean move(Level level, BlockPos host, Piece piece, int y) {
        OctLevel oct = LevelOctExtension.get(level);
        Workspace workspace = Workspace.of(oct, host);
        PartOctEntity owner = workspace.owner(piece);
        Piece stored = workspace.stored(piece);
        if (owner == null || stored == null || !canFit(level, host, piece.type, piece.x, y, piece.z, piece.rotation, piece.id)) {
            return false;
        }
        Spot normal = new Spot(host, piece.x, y, piece.z, piece.rotation).normalized();
        owner.remove(piece.id);
        Piece moved = piece.at(normal.x(), normal.y(), normal.z());
        if (!adopt(level, normal.host(), moved)) {
            owner.add(stored);
            return false;
        }
        reconcile(level, host, piece.bodyBox().minmax(piece.at(piece.x, y, piece.z).bodyBox()));
        owner.changed();
        PartOctEntity newOwner = entity(oct, normal.host(), moved.anchor());
        if (newOwner != null) {
            newOwner.changed();
        }
        return true;
    }

    /**
     * @param box In the host's pixels; every block it touches is reconciled
     */
    public static void reconcile(Level level, BlockPos host, AABB box) {
        for (BlockPos block : blocksOf(host, box)) {
            reconcile(level, block);
        }
    }

    /**
     * Turns the octants pieces fill into their cell blocks and empties the ones nothing fills any more. Where pieces
     * of different cells share an octant, the hardest cell block wins.
     */
    public static void reconcile(Level level, BlockPos host) {
        OctLevel oct = LevelOctExtension.get(level);
        boolean isOct = level.getBlockState(host).is(EmpiricBlocks.OCT_BLOCK.get());
        Workspace workspace = Workspace.of(oct, host);
        PartOctBlock[] wanted = new PartOctBlock[8];
        boolean any = false;
        for (Piece piece : workspace.pieces()) {
            PartOctBlock cellBlock = piece.type.cell();
            for (int cell : cells(piece)) {
                if (wanted[cell] == null || harder(cellBlock, wanted[cell])) {
                    wanted[cell] = cellBlock;
                }
                any = true;
            }
        }
        if (!isOct && (!any || !isFreeHost(level, host))) {
            return;
        }
        for (int cell = 0; cell < 8; cell++) {
            BlockState state = oct.getOctState(host, cell);
            if (wanted[cell] != null && state.getBlock() != wanted[cell] && (state.isAir() || isPart(state))) {
                oct.setOctState(host, cell, wanted[cell].defaultBlockState());
            }
        }
        for (int cell = 0; cell < 8; cell++) {
            if (wanted[cell] == null && isPart(oct.getOctState(host, cell))) {
                PartOctEntity entity = entity(oct, host, cell);
                if (entity == null || entity.isEmpty()) {
                    oct.setOctState(host, cell, Blocks.AIR.defaultBlockState());
                    if (!level.getBlockState(host).is(EmpiricBlocks.OCT_BLOCK.get())) {
                        return;
                    }
                }
            }
        }
    }

    private static boolean harder(PartOctBlock a, PartOctBlock b) {
        float hardA = a.defaultDestroyTime();
        float hardB = b.defaultDestroyTime();
        return hardA != hardB ? hardA > hardB : a.builtInRegistryHolder().key().location().compareTo(b.builtInRegistryHolder().key().location()) < 0;
    }

    public static void requestSettle(Level level, BlockPos pos) {
        OctLevel oct = LevelOctExtension.get(level);
        for (int octant = 0; octant < 8; octant++) {
            PartOctEntity entity = entity(oct, pos, octant);
            if (entity != null && !entity.isEmpty()) {
                entity.requestSettle();
                return;
            }
        }
    }
}
