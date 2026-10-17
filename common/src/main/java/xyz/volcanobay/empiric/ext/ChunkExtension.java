package xyz.volcanobay.empiric.ext;

import net.minecraft.nbt.CompoundTag;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;

public interface ChunkExtension {

    byte[] empiric$pending();

    void empiric$pending(byte[] pending);

    CompoundTag empiric$pendingSpatial();

    void empiric$pendingSpatial(CompoundTag pending);

    ChunkAttachment empiric$attachment();
}
