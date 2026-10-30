package xyz.volcanobay.empiric.api.part.trait;

import net.minecraft.world.phys.AABB;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.PartType;
import xyz.volcanobay.empiric.api.part.Piece;

import java.util.List;

/**
 * Places in a part, such as a rack, that {@link Socketed} parts snap into and stand inside.
 *
 * @param positions Corners of the sockets, in the part's own pixels before rotation
 * @param floor     Height socketed parts stand at, in pixels from the part's bottom
 */
public record Sockets(List<Socket> positions, int floor) {

    public record Socket(int x, int z) {
    }

    public Sockets {
        positions = List.copyOf(positions);
    }

    /**
     * @return Whether a part of this type with its corner here (block pixels) stands in one of the rack's sockets
     */
    public static boolean holds(Piece rack, PartType socketed, double x, double y, double z) {
        Sockets sockets = rack.type.get(PartTraits.SOCKETS);
        if (sockets == null || !socketed.has(PartTraits.SOCKETED) || Math.abs(y - (rack.y + sockets.floor)) > 1.0E-3) {
            return false;
        }
        for (Socket socket : sockets.positions) {
            AABB box = sockets.box(rack, socket, socketed);
            if (Math.abs(box.minX - x) < 1.0E-3 && Math.abs(box.minZ - z) < 1.0E-3) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The footprint a socketed part takes in the socket, in block pixels, flat at the rack's bottom
     */
    public AABB box(Piece rack, Socket socket, PartType socketed) {
        return rack.toBlock(new AABB(socket.x, 0, socket.z, socket.x + socketed.width(), 0, socket.z + socketed.depth()));
    }
}
