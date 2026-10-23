package xyz.volcanobay.empiric.api.module;

import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;

/**
 * Stands in for a block entity whose class is annotated with {@code @SelfSpec}. It has no data of its own: the block
 * entity's {@code @Data} fields are its data, see {@link SelfModuleType}.
 * <pre>{@code
 * SelfModule.TYPE.get(this).markDirty("energy");
 * }</pre>
 */
public final class SelfModule extends Module<BlockEntityAttachment> implements NetworkBehavior {

    public static final SelfModuleType TYPE = new SelfModuleType(Empiric.id("self"));

    public static void register(ModuleSystem modules) {
        modules.register(TYPE);
        modules.subscribe("empiric", TYPE);
    }
}
