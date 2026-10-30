package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.part.trait.Sockets;
import xyz.volcanobay.empiric.api.part.trait.Support;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Finding where a part goes from where the player aims, and letting pieces fall when what holds them up goes.
 */
public final class Placement {

    private static final int SEARCH_RADIUS = 4;

    private Placement() {
    }

    @FunctionalInterface
    public interface PlaceListener {
        void placed(Spot spot, PartType type, ItemStack stack);
    }

    /**
     * Told on the client when the player places a part, before the server's copy arrives.
     */
    public static PlaceListener clientPlaced = (spot, type, stack) -> {
    };

    /**
     * @param failure Null when it can go at the spot
     * @param wanted  Where it was aimed, for showing a refused ghost
     */
    public record Result(@Nullable Spot spot, @Nullable PlacementFailure failure, @Nullable Spot wanted) {

        public static Result ok(Spot spot) {
            return new Result(spot, null, spot);
        }

        public static Result failed(PlacementFailure failure, @Nullable Spot wanted) {
            return new Result(null, failure, wanted);
        }

        public boolean ok() {
            return failure == null && spot != null;
        }
    }

    public static Result find(Level level, PartType type, BlockHitResult hit, Direction facing) {
        Result result = search(level, type, hit, facing);
        return new Result(result.spot() == null ? null : result.spot().normalized(), result.failure(), result.wanted());
    }

    private static Result search(Level level, PartType type, BlockHitResult hit, Direction facing) {
        if (type.has(PartTraits.HANDHELD)) {
            return Result.failed(PlacementFailure.NONE, null);
        }
        Direction face = hit.getDirection();
        boolean onOct = level.getBlockState(hit.getBlockPos()).is(EmpiricBlocks.OCT_BLOCK.get());
        if (face != Direction.UP && !onOct) {
            return Result.failed(PlacementFailure.NONE, null);
        }
        Vec3 point = hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(1.0E-3));
        BlockPos host = BlockPos.containing(point);
        int rotation = type.has(PartTraits.ROTATABLE) ? Piece.rotationFacing(facing) : 0;
        int width = Piece.width(type, rotation);
        int depth = Piece.depth(type, rotation);
        Vec3 aim = point.subtract(Vec3.atLowerCornerOf(host)).scale(16);
        int x = (int) Math.round(aim.x - width / 2.0);
        int z = (int) Math.round(aim.z - depth / 2.0);
        int startY = Math.max(0, Math.min(16, (int) Math.round(aim.y)));
        Spot wanted = new Spot(host, x, startY, z, rotation);
        if (!level.getBlockState(host).is(EmpiricBlocks.OCT_BLOCK.get()) && !Parts.isFreeHost(level, host)) {
            return Result.failed(PlacementFailure.PARTIAL_SURFACE, wanted);
        }
        for (PlacementRule rule : type.hooks(PlacementRule.class)) {
            Result ruled = rule.place(level, type, host, aim, rotation);
            if (ruled != null) {
                return ruled.wanted() == null && !ruled.ok() ? Result.failed(ruled.failure(), wanted) : ruled;
            }
        }
        Result aimed = evaluate(level, host, type, x, z, rotation, startY, null);
        if (aimed.ok()) {
            return aimed;
        }
        Result best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                double distance = dx * dx + dz * dz;
                if ((dx == 0 && dz == 0) || distance >= bestDistance) {
                    continue;
                }
                Result candidate = evaluate(level, host, type, x + dx, z + dz, rotation, startY, null);
                if (candidate.ok()) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best != null ? best : Result.failed(aimed.failure(), aimed.wanted() != null ? aimed.wanted() : wanted);
    }

    private static Result evaluate(Level level, BlockPos host, PartType type, int x, int z, int rotation, int startY, @Nullable UUID ignore) {
        int width = Piece.width(type, rotation);
        int depth = Piece.depth(type, rotation);
        Stand stand = support(level, host, x, z, width, depth, startY, ignore);
        Spot spot = new Spot(host, x, Math.max(0, stand.top()), z, rotation);
        if (stand.top() < 0) {
            return Result.failed(PlacementFailure.NO_SURFACE, new Spot(host, x, startY, z, rotation));
        }
        if (!stand.stable()) {
            return Result.failed(PlacementFailure.UNSTABLE, spot);
        }
        if (!Parts.canFit(level, host, type, x, stand.top(), z, rotation, ignore)) {
            return Result.failed(PlacementFailure.BLOCKED, spot);
        }
        return Result.ok(spot);
    }

    /**
     * @param top    Height of the highest surface under the footprint, in the host's pixels, -1 for none
     * @param stable Whether that surface holds the footprint up under its middle or over half of it
     */
    public record Stand(int top, boolean stable) {
    }

    /**
     * @param x     Footprint corner, in the host's pixels
     * @param fromY Highest surface to consider, in the host's pixels
     */
    public static Stand support(Level level, BlockPos host, int x, int z, int width, int depth, int fromY, @Nullable UUID ignore) {
        OctLevel oct = LevelOctExtension.get(level);
        Workspace workspace = Workspace.of(oct, host);
        List<AABB> solids = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (AABB box : otherOctContent(level, oct, host.offset(dx, 0, dz))) {
                    solids.add(box.move(dx * 16, 0, dz * 16));
                }
            }
        }
        int[] tops = new int[width * depth];
        int highest = -1;
        for (int i = 0; i < width; i++) {
            for (int k = 0; k < depth; k++) {
                int top = columnTop(level, host, workspace, solids, x + i, z + k, fromY, ignore);
                tops[i * depth + k] = top;
                highest = Math.max(highest, top);
            }
        }
        if (highest < 0) {
            return new Stand(-1, false);
        }
        int supported = 0;
        for (int top : tops) {
            if (top == highest) {
                supported++;
            }
        }
        boolean middle = true;
        for (int i : middle(width)) {
            for (int k : middle(depth)) {
                middle &= tops[i * depth + k] == highest;
            }
        }
        return new Stand(highest, middle || supported * 2 >= tops.length);
    }

    private static int[] middle(int size) {
        return size % 2 == 0 ? new int[]{size / 2 - 1, size / 2} : new int[]{size / 2};
    }

    /**
     * @return The height of the highest surface at or below a height in one pixel column, -1 for none
     */
    private static int columnTop(Level level, BlockPos host, Workspace workspace, List<AABB> solids, int column, int row, int fromY, @Nullable UUID ignore) {
        int top = -1;
        double cx = column + 0.5;
        double cz = row + 0.5;
        for (Piece piece : workspace.pieces()) {
            Support support = piece.type.get(PartTraits.SUPPORT);
            if (piece.id.equals(ignore) || support == null) {
                continue;
            }
            if (cx > piece.x && cx < piece.maxX() && cz > piece.z && cz < piece.maxZ()) {
                int surface = piece.y + support.top();
                Sockets sockets = piece.type.get(PartTraits.SOCKETS);
                if (surface <= fromY) {
                    top = Math.max(top, surface);
                } else if (sockets != null && piece.y + sockets.floor() <= fromY) {
                    top = Math.max(top, piece.y + sockets.floor());
                }
            }
        }
        for (AABB box : solids) {
            if (cx > box.minX && cx < box.maxX && cz > box.minZ && cz < box.maxZ) {
                int surface = (int) Math.ceil(box.maxY - 1.0E-4);
                if (surface <= fromY) {
                    top = Math.max(top, surface);
                }
            }
        }
        BlockPos block = host.offset(Math.floorDiv(column, 16), 0, Math.floorDiv(row, 16));
        if (top < 0 && floorAt(level, block, Math.floorMod(column, 16) / 16.0 + 0.5 / 16, Math.floorMod(row, 16) / 16.0 + 0.5 / 16)) {
            top = 0;
        }
        return top;
    }

    private static boolean floorAt(Level level, BlockPos host, double x, double z) {
        BlockPos below = host.below();
        BlockState state = level.getBlockState(below);
        for (AABB box : state.getCollisionShape(level, below, CollisionContext.empty()).toAabbs()) {
            if (box.maxY >= 1 - 1.0E-4 && x > box.minX && x < box.maxX && z > box.minZ && z < box.maxZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The boxes of oct content in the block that isn't a part, in block pixels
     */
    private static List<AABB> otherOctContent(Level level, OctLevel oct, BlockPos host) {
        List<AABB> boxes = new ArrayList<>();
        if (!level.getBlockState(host).is(EmpiricBlocks.OCT_BLOCK.get())) {
            return boxes;
        }
        for (int octant = 0; octant < 8; octant++) {
            BlockState state = oct.getOctState(host, octant);
            if (state.isAir() || Parts.isPart(state) || !(state.getBlock() instanceof OctBlock octBlock)) {
                continue;
            }
            double ox = (octant & 1) != 0 ? 8 : 0;
            double oy = (octant & 2) != 0 ? 8 : 0;
            double oz = (octant & 4) != 0 ? 8 : 0;
            for (AABB box : octBlock.getShape(state, oct, host, octant, CollisionContext.empty()).toAabbs()) {
                boxes.add(new AABB(box.minX * 16 + ox, box.minY * 16 + oy, box.minZ * 16 + oz, box.maxX * 16 + ox, box.maxY * 16 + oy, box.maxZ * 16 + oz));
            }
        }
        return boxes;
    }

    /**
     * Lets the block's own pieces fall onto what's under them now, lowest first, and drops those with nothing to stand on.
     */
    public static void settle(Level level, BlockPos pos) {
        OctLevel oct = LevelOctExtension.get(level);
        Workspace start = Workspace.of(oct, pos);
        List<Piece> pieces = new ArrayList<>(start.pieces().stream().filter(start::isHome).toList());
        pieces.sort(Comparator.comparingInt(piece -> piece.y));
        boolean topChanged = false;
        for (Piece piece : pieces) {
            Workspace workspace = Workspace.of(oct, pos);
            Piece current = workspace.find(piece.id);
            if (current == null || ruled(level, pos, workspace, current)) {
                continue;
            }
            Stand stand = support(level, pos, current.x, current.z, current.width(), current.depth(), current.y, current.id);
            if (stand.top() == current.y && stand.stable()) {
                continue;
            }
            topChanged |= current.bodyBox().maxY >= 16;
            if (stand.top() < 0 || !stand.stable() || !Parts.move(level, pos, current, stand.top())) {
                Parts.drop(level, pos, current);
            }
        }
        if (topChanged) {
            Parts.requestSettle(level, pos.above());
        }
    }

    private static boolean ruled(Level level, BlockPos pos, Workspace workspace, Piece piece) {
        for (SettleRule rule : piece.type.hooks(SettleRule.class)) {
            if (rule.settle(level, pos, workspace, piece)) {
                return true;
            }
        }
        return false;
    }
}
