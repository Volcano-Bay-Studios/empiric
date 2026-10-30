package xyz.volcanobay.empiric.api.part.trait;

/**
 * Other parts can stand on it.
 *
 * @param top Height of the surface they stand on, in pixels from the part's bottom
 */
public record Support(int top) {
}
