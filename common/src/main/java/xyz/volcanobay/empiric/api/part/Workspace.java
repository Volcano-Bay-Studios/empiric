package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctGetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The pieces in reach of one block. Includes its own and those of the 26 around it, all in its pixel space.
 */
public final class Workspace {

    private final BlockPos pos;
    private final List<Piece> pieces;
    private final PartOctEntity[] entities;
    private final Map<UUID, PartOctEntity> owners;

    private Workspace(BlockPos pos, List<Piece> pieces, PartOctEntity[] entities, Map<UUID, PartOctEntity> owners) {
        this.pos = pos;
        this.pieces = pieces;
        this.entities = entities;
        this.owners = owners;
    }

    public static Workspace of(OctGetter oct, BlockPos pos) {
        List<Piece> pieces = new ArrayList<>();
        PartOctEntity[] entities = new PartOctEntity[8];
        Map<UUID, PartOctEntity> owners = new HashMap<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    at.setWithOffset(pos, dx, dy, dz);
                    for (int octant = 0; octant < 8; octant++) {
                        if (!(oct.getOctEntity(at, octant) instanceof PartOctEntity entity)) {
                            continue;
                        }
                        if (dx == 0 && dy == 0 && dz == 0) {
                            entities[octant] = entity;
                        }
                        for (Piece piece : entity.pieces()) {
                            if (owners.putIfAbsent(piece.id, entity) == null) {
                                pieces.add(piece.shifted(dx * 16, dy * 16, dz * 16));
                            }
                        }
                    }
                }
            }
        }
        return new Workspace(pos, Collections.unmodifiableList(pieces), entities, owners);
    }

    public boolean isHome(Piece piece) {
        PartOctEntity owner = owners.get(piece.id);
        return owner != null && owner.pos().equals(pos);
    }

    /**
     * @return The piece as its own block stores it
     */
    public @Nullable Piece stored(Piece piece) {
        PartOctEntity owner = owners.get(piece.id);
        return owner == null ? null : owner.find(piece.id);
    }

    public BlockPos pos() {
        return pos;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    public @Nullable Piece find(UUID id) {
        for (Piece piece : pieces) {
            if (piece.id.equals(id)) {
                return piece;
            }
        }
        return null;
    }

    /**
     * @return The entity that stores the piece, which is the one to mark changed after editing it
     */
    public @Nullable PartOctEntity owner(Piece piece) {
        return owners.get(piece.id);
    }

    public @Nullable PartOctEntity entity(int octant) {
        return entities[octant];
    }

    public List<Piece> touching(int octant) {
        List<Piece> touching = new ArrayList<>();
        for (Piece piece : pieces) {
            if (Parts.cells(piece).contains(octant)) {
                touching.add(piece);
            }
        }
        return touching;
    }

    /**
     * @param box In block pixels
     * @return Whether anything other than the ignored piece overlaps the box
     */
    public boolean blocked(AABB box, @Nullable UUID ignore) {
        for (Piece piece : pieces) {
            if (!piece.id.equals(ignore) && piece.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return Whether anything other than the ignored piece overlaps the candidate, unless an {@link OverlapRule} of either allows it
     */
    public boolean blocked(Piece candidate, @Nullable UUID ignore) {
        for (Piece piece : pieces) {
            if (piece.id.equals(ignore) || allowed(piece, candidate)) {
                continue;
            }
            for (AABB shape : candidate.shapes()) {
                if (piece.intersects(shape)) {
                    return true;
                }
            }
            for (AABB box : candidate.occupied()) {
                if (piece.intersects(box)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allowed(Piece host, Piece guest) {
        for (OverlapRule rule : guest.type.hooks(OverlapRule.class)) {
            if (rule.allows(host, guest)) {
                return true;
            }
        }
        for (OverlapRule rule : host.type.hooks(OverlapRule.class)) {
            if (rule.allows(host, guest)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return Block pixels the piece is drawn shifted by, from its {@link DrawOffset} traits
     */
    public Vec3 drawOffset(Piece piece) {
        Vec3 total = Vec3.ZERO;
        for (DrawOffset offset : piece.type.hooks(DrawOffset.class)) {
            total = total.add(offset.offset(this, piece));
        }
        return total;
    }

    public void changed(Piece piece) {
        PartOctEntity owner = owner(piece);
        if (owner != null) {
            owner.changed();
        }
    }
}
