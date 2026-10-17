package xyz.volcanobay.empiric.mixin.oct;

import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.oct.OctLevelImpl;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public abstract class LevelOctMixin implements LevelOctExtension {
    @Unique
    private final OctLevel empiric$octLevelImpl = empiric$createOctLevel();

    @NotNull
    @Override
    public OctLevel empiric$getOctLevel() {
        return empiric$octLevelImpl;
    }

    @Unique
    private OctLevelImpl empiric$createOctLevel() {
        return new OctLevelImpl((Level) (Object) this);
    }
}
