package xyz.volcanobay.empiric.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4d;
import org.joml.Vector4f;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.DataException;
import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.data.SpecFactory;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.data.Types;
import xyz.volcanobay.empiric.core.data.spec.SimpleSpec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

public final class MinecraftSpecs {

    public static final Spec<ResourceLocation> RESOURCE_LOCATION = SimpleSpec.keyed((buffer, value) -> buffer.putString(value.toString()), buffer -> ResourceLocation.parse(buffer.takeString()), ResourceLocation::toString, ResourceLocation::parse);
    public static final Spec<BlockPos> BLOCK_POS = SimpleSpec.of((buffer, value) -> {
        buffer.putSignedVarInt(value.getX());
        buffer.putSignedVarInt(value.getY());
        buffer.putSignedVarInt(value.getZ());
    }, buffer -> new BlockPos(buffer.takeSignedVarInt(), buffer.takeSignedVarInt(), buffer.takeSignedVarInt()));
    public static final Spec<Vec3i> VEC3I = SimpleSpec.of((buffer, value) -> {
        buffer.putSignedVarInt(value.getX());
        buffer.putSignedVarInt(value.getY());
        buffer.putSignedVarInt(value.getZ());
    }, buffer -> new Vec3i(buffer.takeSignedVarInt(), buffer.takeSignedVarInt(), buffer.takeSignedVarInt()));
    public static final Spec<ChunkPos> CHUNK_POS = SimpleSpec.of((buffer, value) -> {
        buffer.putSignedVarInt(value.x);
        buffer.putSignedVarInt(value.z);
    }, buffer -> new ChunkPos(buffer.takeSignedVarInt(), buffer.takeSignedVarInt()));
    public static final Spec<SectionPos> SECTION_POS = SimpleSpec.of((buffer, value) -> {
        buffer.putSignedVarInt(value.x());
        buffer.putSignedVarInt(value.y());
        buffer.putSignedVarInt(value.z());
    }, buffer -> SectionPos.of(buffer.takeSignedVarInt(), buffer.takeSignedVarInt(), buffer.takeSignedVarInt()));
    public static final Spec<Vec3> VEC3 = SimpleSpec.of((buffer, value) -> {
        buffer.putDouble(value.x);
        buffer.putDouble(value.y);
        buffer.putDouble(value.z);
    }, buffer -> new Vec3(buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble()));
    public static final Spec<AABB> AABB_SPEC = SimpleSpec.of((buffer, value) -> {
        buffer.putDouble(value.minX);
        buffer.putDouble(value.minY);
        buffer.putDouble(value.minZ);
        buffer.putDouble(value.maxX);
        buffer.putDouble(value.maxY);
        buffer.putDouble(value.maxZ);
    }, buffer -> new AABB(buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble()));

    public static final Spec<Vector3d> VECTOR3D = MutableSpec.of((buffer, value) -> {
        buffer.putDouble(value.x);
        buffer.putDouble(value.y);
        buffer.putDouble(value.z);
    }, (buffer, value) -> value.set(buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble()), Vector3d::new, Vector3d::set);
    public static final Spec<Vector3f> VECTOR3F = MutableSpec.of((buffer, value) -> {
        buffer.putFloat(value.x);
        buffer.putFloat(value.y);
        buffer.putFloat(value.z);
    }, (buffer, value) -> value.set(buffer.takeFloat(), buffer.takeFloat(), buffer.takeFloat()), Vector3f::new, Vector3f::set);
    public static final Spec<Vector3i> VECTOR3I = MutableSpec.of((buffer, value) -> {
        buffer.putSignedVarInt(value.x);
        buffer.putSignedVarInt(value.y);
        buffer.putSignedVarInt(value.z);
    }, (buffer, value) -> value.set(buffer.takeSignedVarInt(), buffer.takeSignedVarInt(), buffer.takeSignedVarInt()), Vector3i::new, Vector3i::set);
    public static final Spec<Vector2d> VECTOR2D = MutableSpec.of((buffer, value) -> {
        buffer.putDouble(value.x);
        buffer.putDouble(value.y);
    }, (buffer, value) -> value.set(buffer.takeDouble(), buffer.takeDouble()), Vector2d::new, Vector2d::set);
    public static final Spec<Vector2f> VECTOR2F = MutableSpec.of((buffer, value) -> {
        buffer.putFloat(value.x);
        buffer.putFloat(value.y);
    }, (buffer, value) -> value.set(buffer.takeFloat(), buffer.takeFloat()), Vector2f::new, Vector2f::set);
    public static final Spec<Vector2i> VECTOR2I = MutableSpec.of((buffer, value) -> {
        buffer.putSignedVarInt(value.x);
        buffer.putSignedVarInt(value.y);
    }, (buffer, value) -> value.set(buffer.takeSignedVarInt(), buffer.takeSignedVarInt()), Vector2i::new, Vector2i::set);
    public static final Spec<Vector4d> VECTOR4D = MutableSpec.of((buffer, value) -> {
        buffer.putDouble(value.x);
        buffer.putDouble(value.y);
        buffer.putDouble(value.z);
        buffer.putDouble(value.w);
    }, (buffer, value) -> value.set(buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble()), Vector4d::new, Vector4d::set);
    public static final Spec<Vector4f> VECTOR4F = MutableSpec.of((buffer, value) -> {
        buffer.putFloat(value.x);
        buffer.putFloat(value.y);
        buffer.putFloat(value.z);
        buffer.putFloat(value.w);
    }, (buffer, value) -> value.set(buffer.takeFloat(), buffer.takeFloat(), buffer.takeFloat(), buffer.takeFloat()), Vector4f::new, Vector4f::set);
    public static final Spec<Quaternionf> QUATERNIONF = MutableSpec.of((buffer, value) -> {
        buffer.putFloat(value.x);
        buffer.putFloat(value.y);
        buffer.putFloat(value.z);
        buffer.putFloat(value.w);
    }, (buffer, value) -> value.set(buffer.takeFloat(), buffer.takeFloat(), buffer.takeFloat(), buffer.takeFloat()), Quaternionf::new, Quaternionf::set);
    public static final Spec<Quaterniond> QUATERNIOND = MutableSpec.of((buffer, value) -> {
        buffer.putDouble(value.x);
        buffer.putDouble(value.y);
        buffer.putDouble(value.z);
        buffer.putDouble(value.w);
    }, (buffer, value) -> value.set(buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble(), buffer.takeDouble()), Quaterniond::new, Quaterniond::set);
    public static final Spec<Matrix4f> MATRIX4F = MutableSpec.of((buffer, value) -> {
        float[] values = value.get(new float[16]);
        for (float entry : values) {
            buffer.putFloat(entry);
        }
    }, (buffer, value) -> {
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) {
            values[i] = buffer.takeFloat();
        }
        value.set(values);
    }, Matrix4f::new, Matrix4f::set);

    public static final Spec<Tag> TAG = new TagSpec<>(false);
    public static final Spec<CompoundTag> COMPOUND_TAG = new TagSpec<>(true);
    public static final Spec<BlockState> BLOCK_STATE = new BlockStateSpec();
    public static final Spec<ItemStack> ITEM_STACK = codec(ItemStack.OPTIONAL_CODEC, ItemStack.OPTIONAL_STREAM_CODEC, ItemStack::copy, ItemStack::matches);
    public static final Spec<Component> COMPONENT = codec(ComponentSerialization.CODEC, ComponentSerialization.STREAM_CODEC, null, null);

    private static final Map<Class<?>, Registry<?>> REGISTRIES = new IdentityHashMap<>();

    public static final SpecFactory HOLDERS = (type, raw, resolution, registry) -> {
        if (raw != Holder.class) {
            return null;
        }
        Registry<?> entries = REGISTRIES.get(Types.raw(Types.argument(type, 0)));
        if (entries == null) {
            throw new DataException("No built-in registry is known for " + type.getTypeName());
        }
        return holder(entries);
    };

    public static final SpecFactory RESOURCE_KEYS = (type, raw, resolution, registry) -> raw == ResourceKey.class ? new ResourceKeySpec<>() : null;

    private MinecraftSpecs() {
    }

    public static void register(SpecRegistry registry) {
        registry.register(ResourceLocation.class, RESOURCE_LOCATION);
        registry.register(BlockPos.class, BLOCK_POS);
        registry.register(Vec3i.class, VEC3I);
        registry.register(ChunkPos.class, CHUNK_POS);
        registry.register(SectionPos.class, SECTION_POS);
        registry.register(Vec3.class, VEC3);
        registry.register(AABB.class, AABB_SPEC);
        registry.register(Vector3d.class, VECTOR3D);
        registry.register(Vector3f.class, VECTOR3F);
        registry.register(Vector3i.class, VECTOR3I);
        registry.register(Vector2d.class, VECTOR2D);
        registry.register(Vector2f.class, VECTOR2F);
        registry.register(Vector2i.class, VECTOR2I);
        registry.register(Vector4d.class, VECTOR4D);
        registry.register(Vector4f.class, VECTOR4F);
        registry.register(Quaternionf.class, QUATERNIONF);
        registry.register(Quaterniond.class, QUATERNIOND);
        registry.register(Matrix4f.class, MATRIX4F);
        registry.register(Tag.class, TAG);
        registry.register(CompoundTag.class, COMPOUND_TAG);
        registry.register(BlockState.class, BLOCK_STATE);
        registry.register(ItemStack.class, ITEM_STACK);
        registry.register(Component.class, COMPONENT);
        registerEntries(registry, Block.class, BuiltInRegistries.BLOCK);
        registerEntries(registry, Item.class, BuiltInRegistries.ITEM);
        registerEntries(registry, Fluid.class, BuiltInRegistries.FLUID);
        registerEntries(registry, EntityType.class, BuiltInRegistries.ENTITY_TYPE);
        registerEntries(registry, BlockEntityType.class, BuiltInRegistries.BLOCK_ENTITY_TYPE);
        registerEntries(registry, MobEffect.class, BuiltInRegistries.MOB_EFFECT);
        registerEntries(registry, SoundEvent.class, BuiltInRegistries.SOUND_EVENT);
        registerEntries(registry, ParticleType.class, BuiltInRegistries.PARTICLE_TYPE);
        registry.register(HOLDERS);
        registry.register(RESOURCE_KEYS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void registerEntries(SpecRegistry registry, Class<? super T> type, Registry<T> entries) {
        REGISTRIES.put(type, entries);
        registry.register((Class) type, entry(entries));
    }

    public static <T> Spec<T> entry(Registry<T> registry) {
        return new Spec<>() {
            @Override
            public void write(DataBuffer buffer, T value, DataContext context) {
                if (context.keyed()) {
                    ResourceLocation key = registry.getKey(value);
                    if (key == null) {
                        throw new DataException(value + " isn't registered in " + registry.key().location());
                    }
                    buffer.putString(key.toString());
                } else {
                    buffer.putVarInt(registry.getId(value));
                }
            }

            @Override
            public T read(DataBuffer buffer, DataContext context) {
                T value = context.keyed() ? registry.get(ResourceLocation.parse(buffer.takeString())) : registry.byId(buffer.takeVarInt());
                if (value == null) {
                    throw new DataException("Unknown entry in " + registry.key().location());
                }
                return value;
            }

            @Override
            public boolean keyed() {
                return true;
            }

            @Override
            public String toKey(T value) {
                return String.valueOf(registry.getKey(value));
            }

            @Override
            public T fromKey(String key) {
                T value = registry.get(ResourceLocation.parse(key));
                if (value == null) {
                    throw new DataException("Unknown entry " + key + " in " + registry.key().location());
                }
                return value;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Spec<Holder<T>> holder(Registry<?> registry) {
        Registry<T> entries = (Registry<T>) registry;
        Spec<T> entry = entry(entries);
        return new Spec<>() {
            @Override
            public void write(DataBuffer buffer, Holder<T> value, DataContext context) {
                entry.write(buffer, value.value(), context);
            }

            @Override
            public Holder<T> read(DataBuffer buffer, DataContext context) {
                return entries.wrapAsHolder(entry.read(buffer, context));
            }
        };
    }

    public static <T> Spec<T> stream(StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return new Spec<>() {
            @Override
            public void write(DataBuffer buffer, T value, DataContext context) {
                writeStream(buffer, codec, value);
            }

            @Override
            public T read(DataBuffer buffer, DataContext context) {
                return readStream(buffer, codec);
            }
        };
    }

    public static <T> Spec<T> codec(Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> stream, UnaryOperator<T> copier, BiPredicate<T, T> same) {
        return new CodecSpec<>(codec, stream, copier, same);
    }

    private static <T> void writeStream(DataBuffer buffer, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf registryBuffer = EmpiricBuffers.registryBuffer(buffer);
        if (registryBuffer != null) {
            codec.encode(registryBuffer, value);
            return;
        }
        RegistryFriendlyByteBuf temporary = EmpiricBuffers.create();
        codec.encode(temporary, value);
        buffer.putBytes(EmpiricBuffers.bytes(temporary));
    }

    private static <T> T readStream(DataBuffer buffer, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf registryBuffer = EmpiricBuffers.registryBuffer(buffer);
        if (registryBuffer != null) {
            return codec.decode(registryBuffer);
        }
        return codec.decode(EmpiricBuffers.create(buffer.takeBytes()));
    }

    public static byte[] writeTag(Tag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeAnyTag(tag, new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new DataException("Couldn't write a tag", e);
        }
    }

    public static Tag readTag(byte[] bytes) {
        try {
            return NbtIo.readAnyTag(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new DataException("Couldn't read a tag", e);
        }
    }

    private static final class TagSpec<T extends Tag> implements Spec<T> {

        private final boolean compound;

        private TagSpec(boolean compound) {
            this.compound = compound;
        }

        @Override
        public void write(DataBuffer buffer, T value, DataContext context) {
            if (buffer.structured()) {
                buffer.putNode(JsonNodes.toNode(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value)));
                return;
            }
            buffer.putBytes(writeTag(value));
        }

        @Override
        @SuppressWarnings("unchecked")
        public T read(DataBuffer buffer, DataContext context) {
            Tag tag = buffer.structured() ? JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, JsonNodes.toJson(buffer.takeNode())) : readTag(buffer.takeBytes());
            if (compound && !(tag instanceof CompoundTag)) {
                throw new DataException("Expected a compound tag but read " + tag.getType().getName());
            }
            return (T) tag;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T copy(T value) {
            return (T) value.copy();
        }
    }

    private static final class BlockStateSpec implements Spec<BlockState> {

        @Override
        public void write(DataBuffer buffer, BlockState value, DataContext context) {
            if (buffer.structured()) {
                buffer.putNode(JsonNodes.toNode(BlockState.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow(DataException::new)));
                return;
            }
            if (context.keyed()) {
                COMPOUND_TAG.write(buffer, NbtUtils.writeBlockState(value), context);
            } else {
                buffer.putVarInt(Block.getId(value));
            }
        }

        @Override
        public BlockState read(DataBuffer buffer, DataContext context) {
            if (buffer.structured()) {
                return BlockState.CODEC.parse(JsonOps.INSTANCE, JsonNodes.toJson(buffer.takeNode())).getOrThrow(DataException::new);
            }
            if (context.keyed()) {
                return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), COMPOUND_TAG.read(buffer, context));
            }
            return Block.stateById(buffer.takeVarInt());
        }
    }

    private static final class CodecSpec<T> implements Spec<T> {

        private final Codec<T> codec;
        private final StreamCodec<? super RegistryFriendlyByteBuf, T> stream;
        private final UnaryOperator<T> copier;
        private final BiPredicate<T, T> same;

        private CodecSpec(Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> stream, UnaryOperator<T> copier, BiPredicate<T, T> same) {
            this.codec = codec;
            this.stream = stream;
            this.copier = copier;
            this.same = same;
        }

        @Override
        public void write(DataBuffer buffer, T value, DataContext context) {
            if (buffer.structured()) {
                RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, EmpiricBuffers.registries());
                buffer.putNode(JsonNodes.toNode(codec.encodeStart(ops, value).getOrThrow(DataException::new)));
                return;
            }
            if (context.keyed()) {
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, EmpiricBuffers.registries());
                TAG.write(buffer, codec.encodeStart(ops, value).getOrThrow(DataException::new), context);
            } else {
                writeStream(buffer, stream, value);
            }
        }

        @Override
        public T read(DataBuffer buffer, DataContext context) {
            if (buffer.structured()) {
                RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, EmpiricBuffers.registries());
                return codec.parse(ops, JsonNodes.toJson(buffer.takeNode())).getOrThrow(DataException::new);
            }
            if (context.keyed()) {
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, EmpiricBuffers.registries());
                return codec.parse(ops, TAG.read(buffer, context)).getOrThrow(DataException::new);
            }
            return readStream(buffer, stream);
        }

        @Override
        public T copy(T value) {
            return copier == null ? value : copier.apply(value);
        }

        @Override
        public boolean same(T first, T second) {
            return same == null ? first.equals(second) : same.test(first, second);
        }
    }

    private static final class ResourceKeySpec<T> implements Spec<ResourceKey<T>> {

        @Override
        public void write(DataBuffer buffer, ResourceKey<T> value, DataContext context) {
            buffer.putString(value.registry().toString());
            buffer.putString(value.location().toString());
        }

        @Override
        public ResourceKey<T> read(DataBuffer buffer, DataContext context) {
            ResourceKey<? extends Registry<T>> registry = ResourceKey.createRegistryKey(ResourceLocation.parse(buffer.takeString()));
            return ResourceKey.create(registry, ResourceLocation.parse(buffer.takeString()));
        }
    }
}
