package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachmentType;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;

public final class EmpiricAttachments {

    public static final LevelAttachment.Type LEVEL = new LevelAttachment.Type();
    public static final BlockAttachment.Type BLOCK = new BlockAttachment.Type();
    public static final NetworkAttachment.Type NETWORK = new NetworkAttachment.Type();
    public static final OctAttachment.Type OCT = new OctAttachment.Type();
    public static final ChunkAttachment.Type CHUNK = new ChunkAttachment.Type();
    public static final EntityAttachment.Type ENTITY = new EntityAttachment.Type();
    public static final BlockEntityAttachment.Type BLOCK_ENTITY = new BlockEntityAttachment.Type();
    public static final ItemStackAttachment.Type ITEM_STACK = new ItemStackAttachment.Type();
    public static final ServerAttachment.Type SERVER = new ServerAttachment.Type();

    private EmpiricAttachments() {
    }

    public static ModuleAttachmentType module() {
        return EmpiricCore.modules().moduleAttachments();
    }

    public static void register(ModuleSystem modules) {
        modules.registerAttachment(LEVEL);
        modules.registerAttachment(CHUNK);
        modules.registerAttachment(BLOCK);
        modules.registerAttachment(OCT);
        modules.registerAttachment(NETWORK);
        modules.registerAttachment(ENTITY);
        modules.registerAttachment(BLOCK_ENTITY);
        modules.registerAttachment(ITEM_STACK);
        modules.registerAttachment(SERVER);
    }
}
