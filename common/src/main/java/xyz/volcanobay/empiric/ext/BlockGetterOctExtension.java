package xyz.volcanobay.empiric.ext;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.oct.OctGetter;
import xyz.volcanobay.empiric.oct.OctGetterImpl;

public interface BlockGetterOctExtension {

    static OctGetter get(BlockGetter getter) {
        if (getter instanceof Level level) {
            return LevelOctExtension.get(level);
        }
        return new OctGetterImpl(getter);
    }
}
