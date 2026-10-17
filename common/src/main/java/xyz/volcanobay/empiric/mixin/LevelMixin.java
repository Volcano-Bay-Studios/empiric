package xyz.volcanobay.empiric.mixin;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.ext.LevelExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public class LevelMixin implements LevelExtension {

    @Unique
    private LevelAttachment empiric$attachment;

    @Override
    public LevelAttachment empiric$attachment() {
        if (empiric$attachment == null) {
            empiric$attachment = new LevelAttachment((Level) (Object) this);
        }
        return empiric$attachment;
    }
}
