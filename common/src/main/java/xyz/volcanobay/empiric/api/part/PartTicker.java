package xyz.volcanobay.empiric.api.part;

/**
 * A trait ticked on the server for each piece of its type, every tick.
 */
public interface PartTicker {

    /**
     * @param entity The octant that stores the piece
     */
    void tick(PartOctEntity entity, Piece piece);
}
