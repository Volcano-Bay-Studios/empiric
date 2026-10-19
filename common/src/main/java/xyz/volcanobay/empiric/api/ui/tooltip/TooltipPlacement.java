package xyz.volcanobay.empiric.api.ui.tooltip;

public enum TooltipPlacement {
    /**
     * Beside the target in the world, easing into place, with a line back to it. The crosshair can move onto it.
     */
    ANCHORED,
    /**
     * At a fixed spot right of the crosshair. Display only, since the crosshair can never reach it.
     */
    CROSSHAIR
}
