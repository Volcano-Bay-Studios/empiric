package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.registry.ValidBlocks;
import xyz.volcanobay.empiric.core.registry.Ref;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BlockEntityTypeAdapter extends VanillaAdapter<BlockEntityType<?>> {

    public BlockEntityTypeAdapter() {
        super(Registries.BLOCK_ENTITY_TYPE, generic(BlockEntityType.class));
    }

    @Override
    public List<Class<?>> sourceTypes() {
        return List.of(BlockEntityType.class, BlockEntity.class);
    }

    @Override
    public Type producedType(RegistrationEntry entry) {
        if (entry.source() == RegistrationEntry.Kind.CLASS) {
            return new AdapterSupport.Generic(BlockEntityType.class, entry.sourceClass());
        }
        return super.producedType(entry);
    }

    @Override
    public List<String> dependsOn(RegistrationEntry entry) {
        return validBlockIds(entry);
    }

    @Override
    public String dependencyRegistry() {
        return Registries.BLOCK.location().toString();
    }

    @Override
    public BlockEntityType<?> create(RegistrationEntry entry) {
        Class<?> type = AdapterSupport.instanceClass(entry);
        if (type == null) {
            return super.create(entry);
        }
        AdapterSupport.requireNamed(type, entry, "BlockEntityType");
        Block[] blocks = new Block[0];
        List<Block> found = new ArrayList<>();
        for (String id : validBlockIds(entry)) {
            ResourceLocation location = ResourceLocation.parse(id);
            found.add(BuiltInRegistries.BLOCK.getOptional(location).orElseThrow(() -> new IllegalStateException(entry.id() + " is valid for the block " + id + ", which isn't registered")));
        }
        Ref<BlockEntityType<?>> holder = Ref.create();
        BlockEntityType.BlockEntitySupplier<BlockEntity> factory = factory(type, holder);
        BlockEntityType<BlockEntity> built = BlockEntityType.Builder.of(factory, found.toArray(blocks)).build(null);
        holder.bind(entry.id(), built);
        return built;
    }

    @Override
    public void register(RegistrationEntry entry, Supplier<BlockEntityType<?>> value) {
        Supplier<BlockEntityType<?>> type = registerValue(entry, value);
        Class<?> instanceClass = AdapterSupport.instanceClass(entry);
        if (instanceClass != null) {
            EmpiricRegistration.addRenderer(instanceClass, type, false);
        }
    }

    private interface TypedFactory {

        BlockEntity create(BlockEntityType<?> type, BlockPos pos, BlockState state);
    }

    private static BlockEntityType.BlockEntitySupplier<BlockEntity> factory(Class<?> type, Ref<BlockEntityType<?>> holder) {
        try {
            Constructor<?> plain = type.getDeclaredConstructor(BlockPos.class, BlockState.class);
            BlockEntityType.BlockEntitySupplier<BlockEntity> bound = AdapterSupport.factory(BlockEntityType.BlockEntitySupplier.class, MethodType.methodType(BlockEntity.class, BlockPos.class, BlockState.class), plain);
            if (bound != null) {
                return bound;
            }
            MethodHandle handle = AdapterSupport.handle(plain);
            return (pos, state) -> create(handle, null, pos, state);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Constructor<?> typed = type.getDeclaredConstructor(BlockEntityType.class, BlockPos.class, BlockState.class);
            TypedFactory bound = AdapterSupport.factory(TypedFactory.class, MethodType.methodType(BlockEntity.class, BlockEntityType.class, BlockPos.class, BlockState.class), typed);
            if (bound != null) {
                return (pos, state) -> bound.create(holder.get(), pos, state);
            }
            MethodHandle handle = AdapterSupport.handle(typed);
            return (pos, state) -> create(handle, holder, pos, state);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type.getName() + " needs a constructor that takes (BlockPos, BlockState) or (BlockEntityType, BlockPos, BlockState)", e);
        }
    }

    private static BlockEntity create(MethodHandle handle, Ref<BlockEntityType<?>> holder, BlockPos pos, BlockState state) {
        try {
            if (holder == null) {
                return (BlockEntity) handle.invoke(pos, state);
            }
            return (BlockEntity) handle.invoke((BlockEntityType<?>) holder.get(), pos, state);
        } catch (Throwable throwable) {
            throw AdapterSupport.rethrow(throwable);
        }
    }

    private static List<String> validBlockIds(RegistrationEntry entry) {
        ValidBlocks valid = entry.annotation(ValidBlocks.class);
        List<String> ids = new ArrayList<>();
        if (valid == null) {
            return ids;
        }
        for (String id : valid.value()) {
            ids.add(id.indexOf(':') >= 0 ? id : entry.namespace() + ":" + id);
        }
        return ids;
    }
}
