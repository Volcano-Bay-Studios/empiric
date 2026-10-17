package xyz.volcanobay.empiric.ext;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.oct.OctLevel;

public interface LevelOctExtension {
    OctLevel empiric$getOctLevel();

    static OctLevel get(Level level) {
        return ((LevelOctExtension) level).empiric$getOctLevel();
    }
}
