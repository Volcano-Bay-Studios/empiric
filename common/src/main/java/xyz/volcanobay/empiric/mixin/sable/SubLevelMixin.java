package xyz.volcanobay.empiric.mixin.sable;

import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.ext.SubLevelExtension;

@Mixin(value = SubLevel.class, remap = false)
public class SubLevelMixin implements SubLevelExtension {

    @Unique
    private SubLevelAttachment empiric$attachment;

    @Override
    public SubLevelAttachment empiric$attachment() {
        if (empiric$attachment == null) {
            empiric$attachment = new SubLevelAttachment((SubLevel) (Object) this);
        }
        return empiric$attachment;
    }
}
