package xyz.volcanobay.empiric.ext;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.oct.OctLevel;

public interface LevelOctExtension {
    OctLevel empiric$getOctLevel();

    /**
     * @deprecated Use {@link OctLevel#get(Level)} instead.
     */
    @Deprecated
    static OctLevel get(Level level) {
        return OctLevel.get(level);
    }
}
