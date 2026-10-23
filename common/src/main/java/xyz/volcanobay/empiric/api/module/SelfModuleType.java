package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.SelfSpec;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;

/**
 * The module type behind block entity classes annotated with {@link SelfSpec}. It attaches itself to every such block
 * entity, and the block entity's own {@code @Data} fields are the module's data, so they save, load and sync like
 * other modules.
 */
public final class SelfModuleType extends BlockEntityModuleType<SelfModule> {

    private static final ClassValue<Boolean> SELF_SPEC = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.isAnnotationPresent(SelfSpec.class);
        }
    };

    private static final ClassValue<ObjectSpec<Object>> SPECS = new ClassValue<>() {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected ObjectSpec<Object> computeValue(Class<?> type) {
            return (ObjectSpec) EmpiricCore.specs().objectSpec(type);
        }
    };

    SelfModuleType(ResourceLocation id) {
        super(builder(id, SelfModule.class, SelfModule::new)
                .autoAttach(attachment -> SELF_SPEC.get(attachment.blockEntity().getClass())));
    }

    @Override
    public Object data(SelfModule module) {
        return module.attachment().blockEntity();
    }

    @Override
    public ObjectSpec<Object> spec(SelfModule module) {
        return SPECS.get(module.attachment().blockEntity().getClass());
    }
}
