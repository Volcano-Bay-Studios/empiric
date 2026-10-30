package xyz.volcanobay.empiric.api.part.trait;

/**
 * The config of a trait that is only there or not.
 */
public record Flag() {

    public static final Flag ON = new Flag();
}
