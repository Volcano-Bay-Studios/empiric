package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachmentType;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.registry.Register;

public final class EmpiricAttachments {

    @Register
    public static final LevelAttachment.Type LEVEL = new LevelAttachment.Type();

    @Register
    public static final ChunkAttachment.Type CHUNK = new ChunkAttachment.Type();

    @Register
    public static final BlockAttachment.Type BLOCK = new BlockAttachment.Type();

    @Register
    public static final OctAttachment.Type OCT = new OctAttachment.Type();

    @Register
    public static final NetworkAttachment.Type NETWORK = new NetworkAttachment.Type();

    @Register
    public static final EntityAttachment.Type ENTITY = new EntityAttachment.Type();

    @Register
    public static final BlockEntityAttachment.Type BLOCK_ENTITY = new BlockEntityAttachment.Type();

    @Register
    public static final ItemStackAttachment.Type ITEM_STACK = new ItemStackAttachment.Type();

    @Register
    public static final ServerAttachment.Type SERVER = new ServerAttachment.Type();

    private EmpiricAttachments() {
    }

    /** @deprecated registration is automatic, so this does nothing. */
    @Deprecated
    public static void register(ModuleSystem modules) {
    }

    public static ModuleAttachmentType module() {
        return EmpiricCore.modules().moduleAttachments();
    }
}
