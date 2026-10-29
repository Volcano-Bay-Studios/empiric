package xyz.volcanobay.empiric.model;

/**
 * How a face is blended. Each kind of a model is baked into its own mesh, and translucent meshes are drawn last.
 */
public enum ModelRenderKind {
    SOLID,
    CUTOUT,
    TRANSLUCENT;

    /**
     * Reads a vanilla render type name such as {@code minecraft:cutout_mipped}.
     */
    public static ModelRenderKind fromName(String name, ModelRenderKind fallback) {
        String path = name.substring(name.indexOf(':') + 1);
        return switch (path) {
            case "solid" -> SOLID;
            case "cutout", "cutout_mipped" -> CUTOUT;
            case "translucent", "tripwire" -> TRANSLUCENT;
            default -> fallback;
        };
    }
}
