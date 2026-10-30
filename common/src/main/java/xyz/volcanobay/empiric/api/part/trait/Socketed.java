package xyz.volcanobay.empiric.api.part.trait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.part.Workspace;
import xyz.volcanobay.empiric.api.part.OverlapRule;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.Parts;
import xyz.volcanobay.empiric.api.part.Piece;
import xyz.volcanobay.empiric.api.part.Placement;
import xyz.volcanobay.empiric.api.part.PlacementRule;
import xyz.volcanobay.empiric.api.part.Spot;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

/**
 * The part snaps into the {@link Sockets} of a part it's aimed near, and may stand inside it there.
 *
 * @param reach Pixels from a socket's middle that still snap to it
 */
public record Socketed(int reach) implements PlacementRule, OverlapRule {

    public Socketed() {
        this(4);
    }

    @Override
    public @Nullable Placement.Result place(Level level, PartType type, BlockPos host, Vec3 aim, int rotation) {
        Spot best = null;
        double bestDistance = reach * reach;
        for (Piece rack : Workspace.of(LevelOctExtension.get(level), host).pieces()) {
            Sockets sockets = rack.type.get(PartTraits.SOCKETS);
            if (sockets == null) {
                continue;
            }
            int floor = rack.y + sockets.floor();
            if (aim.y < floor - 1 || aim.y > rack.maxY() + 2) {
                continue;
            }
            for (Sockets.Socket socket : sockets.positions()) {
                AABB box = sockets.box(rack, socket, type);
                int x = (int) Math.round(box.minX);
                int z = (int) Math.round(box.minZ);
                double dx = x + type.width() / 2.0 - aim.x;
                double dz = z + type.depth() / 2.0 - aim.z;
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance && Parts.canFit(level, host, type, x, floor, z, 0, null)) {
                    best = new Spot(host, x, floor, z, 0);
                    bestDistance = distance;
                }
            }
        }
        return best == null ? null : Placement.Result.ok(best);
    }

    @Override
    public boolean allows(Piece host, Piece guest) {
        return Sockets.holds(host, guest.type, guest.x, guest.y, guest.z);
    }
}
