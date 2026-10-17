package xyz.volcanobay.empiric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.ext.ChunkExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin implements ChunkExtension {

    @Unique
    private byte[] empiric$pending;
    @Unique
    private CompoundTag empiric$pendingSpatial;
    @Unique
    private ChunkAttachment empiric$attachment;

    @Override
    public byte[] empiric$pending() {
        return empiric$pending;
    }

    @Override
    public void empiric$pending(byte[] pending) {
        this.empiric$pending = pending;
    }

    @Override
    public CompoundTag empiric$pendingSpatial() {
        return empiric$pendingSpatial;
    }

    @Override
    public void empiric$pendingSpatial(CompoundTag pending) {
        this.empiric$pendingSpatial = pending;
    }

    @Override
    public ChunkAttachment empiric$attachment() {
        if (empiric$attachment == null) {
            if (!((Object) this instanceof LevelChunk chunk)) {
                throw new IllegalStateException("Only full chunks have module attachments");
            }
            empiric$attachment = new ChunkAttachment(chunk);
        }
        return empiric$attachment;
    }
}
