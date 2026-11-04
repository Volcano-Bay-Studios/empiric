package xyz.volcanobay.empiric.ext;

import net.minecraft.world.level.BlockGetter;
import xyz.volcanobay.empiric.api.oct.OctGetter;

public interface BlockGetterOctExtension {

    /**
     * @deprecated Use {@link OctGetter#get(BlockGetter)} instead.
     */
    @Deprecated
    static OctGetter get(BlockGetter getter) {
        return OctGetter.get(getter);
    }
}
