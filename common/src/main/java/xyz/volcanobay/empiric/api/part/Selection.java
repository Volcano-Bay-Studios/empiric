package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctGetter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the player aims at among a block's pieces.
 *
 * @param layer   Null for the piece's body
 * @param index   The box's index among the layer's boxes, -1 for the body
 * @param box     In block pixels; the whole piece for its body
 * @param outline What to outline, in block pixels
 */
public record Selection(Piece piece, @Nullable HitLayer layer, int index, AABB box, List<OrientedBox> outline) {

    private static final double EPSILON = 1.0E-3;
    private static final AABB BLOCK = new AABB(0, 0, 0, 16, 16, 16);

    public Selection {
        outline = List.copyOf(outline);
    }

    public boolean isBody() {
        return layer == null;
    }

    /**
     * @return Whether the selection is a box of a layer of this class
     */
    public boolean is(Class<? extends HitLayer> type) {
        return type.isInstance(layer);
    }

    /**
     * @param hit In world coordinates, on the face hit
     */
    public static @Nullable Selection at(OctGetter oct, BlockPos pos, Vec3 hit, Direction face) {
        Vec3 px = hit.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16).subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(0.01));
        for (Candidate candidate : candidates(Workspace.of(oct, pos))) {
            if (candidate.box().contains(px, EPSILON)) {
                return candidate.selection();
            }
        }
        return null;
    }

    /**
     * @param from In world coordinates
     * @return The first thing in the block along a ray
     */
    public static @Nullable Selection ray(OctGetter oct, BlockPos pos, Vec3 from, Vec3 to) {
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 start = from.subtract(origin).scale(16);
        Vec3 end = to.subtract(origin).scale(16);
        Selection best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Candidate candidate : candidates(Workspace.of(oct, pos))) {
            double distance = distance(candidate.box(), start, end) - candidate.bias();
            if (distance < bestDistance) {
                best = candidate.selection();
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Follows the player's view, so turned boxes and layer biases count; falls back to the face hit.
     */
    public static @Nullable Selection of(OctGetter oct, BlockPos pos, Player player, @Nullable BlockHitResult hit) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange() + 1));
        Selection selection = ray(oct, pos, from, to);
        if (selection == null && hit != null && hit.getBlockPos().equals(pos)) {
            return at(oct, pos, hit.getLocation(), hit.getDirection());
        }
        return selection;
    }

    private record Candidate(Selection selection, OrientedBox box, double bias) {
    }

    /**
     * @return Every box that can be aimed at, highest bias first, bodies last
     */
    private static List<Candidate> candidates(Workspace workspace) {
        List<Candidate> layered = new ArrayList<>();
        List<Candidate> bodies = new ArrayList<>();
        for (Piece piece : workspace.pieces()) {
            for (HitLayer layer : piece.type.hooks(HitLayer.class)) {
                List<AABB> boxes = layer.boxes(piece);
                List<OrientedBox> hitBoxes = layer.hitBoxes(piece);
                for (int i = 0; i < boxes.size() && i < hitBoxes.size(); i++) {
                    layered.add(new Candidate(new Selection(piece, layer, i, boxes.get(i), List.of(hitBoxes.get(i))), hitBoxes.get(i), layer.bias()));
                }
            }
            List<OrientedBox> body = piece.hitBoxes();
            List<OrientedBox> outline = piece.type.has(PartTraits.HITBOXES) ? body : List.of(OrientedBox.aligned(piece.box()));
            Selection selection = new Selection(piece, null, -1, piece.box(), outline);
            for (OrientedBox box : body) {
                bodies.add(new Candidate(selection, box, 0));
            }
        }
        layered.sort(Comparator.comparingDouble(Candidate::bias).reversed());
        layered.addAll(bodies);
        return layered;
    }

    private static double distance(OrientedBox box, Vec3 start, Vec3 end) {
        if (!box.bounds().intersects(BLOCK)) {
            return Double.MAX_VALUE;
        }
        if (box.isAligned()) {
            box = OrientedBox.aligned(box.box().intersect(BLOCK));
        }
        return box.clip(start, end).map(hit -> hit.distanceTo(start)).orElse(Double.MAX_VALUE);
    }
}
