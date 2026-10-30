package xyz.volcanobay.empiric.api.part;

/**
 * A trait that lets two pieces overlap. Both pieces' rules are asked, with the same host and guest.
 */
public interface OverlapRule {

    /**
     * @return Whether the guest may stand inside the host
     */
    boolean allows(Piece host, Piece guest);
}
