package xyz.volcanobay.empiric.registry;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import org.objectweb.asm.Type;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.api.module.BlockEntityModuleType;
import xyz.volcanobay.empiric.api.module.BlockModuleType;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.api.module.EntityModuleType;
import xyz.volcanobay.empiric.api.module.ItemModuleType;
import xyz.volcanobay.empiric.api.module.LevelModuleType;
import xyz.volcanobay.empiric.api.module.OctModuleType;
import xyz.volcanobay.empiric.api.module.ServerModuleType;
import xyz.volcanobay.empiric.api.module.SubLevelModuleType;
import xyz.volcanobay.empiric.api.registry.ClientRenderer;
import xyz.volcanobay.empiric.api.registry.CreativeTab;
import xyz.volcanobay.empiric.core.EmpiricEngine;
import xyz.volcanobay.empiric.core.data.Types;
import xyz.volcanobay.empiric.core.registry.ModuleTypeAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationAdapter;
import xyz.volcanobay.empiric.core.registry.RegistrationPlatform;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.core.registry.RegistrySystem;
import xyz.volcanobay.empiric.core.registry.ScanResult;
import xyz.volcanobay.empiric.platform.ModCompat;
import xyz.volcanobay.empiric.registry.adapter.BlockAdapter;
import xyz.volcanobay.empiric.registry.adapter.BlockEntityTypeAdapter;
import xyz.volcanobay.empiric.registry.adapter.EntityTypeAdapter;
import xyz.volcanobay.empiric.registry.adapter.ItemAdapter;
import xyz.volcanobay.empiric.registry.adapter.ModelAdapter;
import xyz.volcanobay.empiric.registry.adapter.PartInteractionAdapter;
import xyz.volcanobay.empiric.registry.adapter.PartTypeAdapter;
import xyz.volcanobay.empiric.registry.adapter.PlacementFailureAdapter;
import xyz.volcanobay.empiric.registry.adapter.SpatialNetworkTypeAdapter;
import xyz.volcanobay.empiric.registry.adapter.TraitKeyAdapter;
import xyz.volcanobay.empiric.registry.adapter.UiScreenTypeAdapter;
import xyz.volcanobay.empiric.registry.adapter.VanillaAdapter;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public final class EmpiricRegistration {

    private static final String CLIENT_RENDERER_DESC = Type.getDescriptor(ClientRenderer.class);

    private static final List<PendingAttributes> ATTRIBUTES = new ArrayList<>();
    private static final List<PendingRenderer> RENDERERS = new ArrayList<>();
    private static final Map<ResourceKey<CreativeModeTab>, List<Supplier<? extends ItemLike>>> CREATIVE_TABS = new LinkedHashMap<>();
    private static RegistrySystem registries;
    private static ScanResult scan = new ScanResult(List.of(), List.of(), List.of());

    private EmpiricRegistration() {
    }

    public record PendingAttributes(Supplier<EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> builder) {
    }

    public record PendingRenderer(Supplier<?> type, String rendererClass, boolean entity) {
    }

    public record ClientEntry(String className, Map<String, Object> values) {

        public String classValue(String name) {
            Object value = values.get(name);
            return value instanceof Type asmType ? asmType.getClassName() : String.valueOf(value);
        }
    }

    public static void setup(EmpiricEngine engine, ScanResult scanResult, ToIntFunction<String> rank, Function<ResourceKey<Registry<Object>>, Registry<Object>> registryFactory) {
        registries = engine.registries();
        scan = scanResult;
        registries.bindAdapter(new BlockAdapter());
        registries.bindAdapter(new ItemAdapter());
        registries.bindAdapter(new BlockEntityTypeAdapter());
        registries.bindAdapter(new EntityTypeAdapter());
        registries.bindAdapter(new PartTypeAdapter());
        registries.bindAdapter(new ModelAdapter());
        registries.bindAdapter(new PartInteractionAdapter());
        registries.bindAdapter(new TraitKeyAdapter());
        registries.bindAdapter(new PlacementFailureAdapter());
        registries.bindAdapter(new UiScreenTypeAdapter());
        registries.bindAdapter(new SpatialNetworkTypeAdapter(engine.modules()), true);
        bindVanilla();
        bindModuleKinds(engine.moduleTypes());
        registries.platform(new McPlatform(rank, registryFactory));
        registries.addScan(scanResult);
    }

    private static void bindVanilla() {
        Set<String> bound = new HashSet<>(Set.of(Registries.BLOCK.location().toString(), Registries.ITEM.location().toString(), Registries.BLOCK_ENTITY_TYPE.location().toString(), Registries.ENTITY_TYPE.location().toString()));
        bindTyped(bound, Registries.SOUND_EVENT, SoundEvent.class);
        bindTyped(bound, Registries.PARTICLE_TYPE, ParticleType.class);
        bindTyped(bound, Registries.MENU, MenuType.class);
        bindTyped(bound, Registries.DATA_COMPONENT_TYPE, DataComponentType.class);
        bindTyped(bound, Registries.CREATIVE_MODE_TAB, CreativeModeTab.class);
        bindTyped(bound, Registries.RECIPE_TYPE, RecipeType.class);
        bindTyped(bound, Registries.RECIPE_SERIALIZER, RecipeSerializer.class);
        bindTyped(bound, Registries.MOB_EFFECT, MobEffect.class);
        bindTyped(bound, Registries.POTION, Potion.class);
        bindTyped(bound, Registries.ATTRIBUTE, Attribute.class);
        bindTyped(bound, Registries.FLUID, Fluid.class);
        bindTyped(bound, Registries.GAME_EVENT, GameEvent.class);
        bindTyped(bound, Registries.POINT_OF_INTEREST_TYPE, PoiType.class);
        bindTyped(bound, Registries.VILLAGER_PROFESSION, VillagerProfession.class);
        bindTyped(bound, Registries.COMMAND_ARGUMENT_TYPE, ArgumentTypeInfo.class);
        bindTyped(bound, Registries.STAT_TYPE, StatType.class);
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            if (bound.add(registry.key().location().toString())) {
                registries.bindAdapter(VanillaAdapter.named(registry.key()));
            }
        }
    }

    private static <T> void bindTyped(Set<String> bound, ResourceKey<? extends Registry<T>> key, Class<?> type) {
        bound.add(key.location().toString());
        registries.bindAdapter(VanillaAdapter.typed(key, type));
    }

    private static void bindModuleKinds(ModuleTypeAdapter adapter) {
        adapter.<BlockAttachment>kind(BlockAttachment.class, BlockModuleType.class, BlockModuleType::builder);
        adapter.<BlockEntityAttachment>kind(BlockEntityAttachment.class, BlockEntityModuleType.class, BlockEntityModuleType::builder);
        adapter.<ChunkAttachment>kind(ChunkAttachment.class, ChunkModuleType.class, ChunkModuleType::builder);
        adapter.<EntityAttachment>kind(EntityAttachment.class, EntityModuleType.class, EntityModuleType::builder);
        adapter.<ItemStackAttachment>kind(ItemStackAttachment.class, ItemModuleType.class, ItemModuleType::builder);
        adapter.<LevelAttachment>kind(LevelAttachment.class, LevelModuleType.class, LevelModuleType::builder);
        adapter.<OctAttachment>kind(OctAttachment.class, OctModuleType.class, OctModuleType::builder);
        adapter.<ServerAttachment>kind(ServerAttachment.class, ServerModuleType.class, ServerModuleType::builder);
        if (ModCompat.SABLE) {
            bindSubLevelKind(adapter);
        }
    }

    private static void bindSubLevelKind(ModuleTypeAdapter adapter) {
        adapter.<SubLevelAttachment>kind(SubLevelAttachment.class, SubLevelModuleType.class, SubLevelModuleType::builder);
    }

    public static void runVanilla() {
        registries.run(adapter -> !registries.isCore(adapter));
    }

    public static void runForeign(String registryId) {
        registries.resolvePending();
        registries.run(adapter -> adapter.registry().equals(registryId), false);
    }

    public static void runForeign() {
        registries.resolvePending();
        registries.run(adapter -> !registries.isCore(adapter), false);
    }

    public static void requireResolved() {
        registries.requireResolved();
    }

    public static synchronized List<PendingAttributes> attributes() {
        return Collections.unmodifiableList(new ArrayList<>(ATTRIBUTES));
    }

    public static synchronized List<PendingRenderer> renderers() {
        return Collections.unmodifiableList(new ArrayList<>(RENDERERS));
    }

    public static synchronized Map<ResourceKey<CreativeModeTab>, List<Supplier<? extends ItemLike>>> creativeTabs() {
        return Collections.unmodifiableMap(CREATIVE_TABS);
    }

    public static synchronized void addAttributes(Supplier<EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> builder) {
        ATTRIBUTES.add(new PendingAttributes(type, builder));
    }

    public static synchronized void addCreativeTabs(RegistrationEntry entry, Supplier<? extends ItemLike> item) {
        CreativeTab tab = entry.annotation(CreativeTab.class);
        if (tab == null) {
            return;
        }
        for (String id : tab.value()) {
            ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.parse(id));
            CREATIVE_TABS.computeIfAbsent(key, name -> new ArrayList<>()).add(item);
        }
    }

    public static synchronized void addRenderer(Class<?> instanceClass, Supplier<?> type, boolean entity) {
        for (ScanResult.ScannedElement element : scan.elements()) {
            if (element.kind() != ScanResult.ScannedElement.Kind.CLASS || !element.className().equals(instanceClass.getName()) || !element.annotationDesc().equals(CLIENT_RENDERER_DESC)) {
                continue;
            }
            Object value = element.values().get("value");
            String name = value instanceof Type asmType ? asmType.getClassName() : String.valueOf(value);
            RENDERERS.add(new PendingRenderer(type, name, entity));
            return;
        }
    }

    public static synchronized List<ClientEntry> clientEntries(Class<? extends Annotation> annotation) {
        String desc = Type.getDescriptor(annotation);
        List<ClientEntry> found = new ArrayList<>();
        for (ScanResult.ScannedElement element : scan.elements()) {
            if (element.kind() == ScanResult.ScannedElement.Kind.CLASS && element.annotationDesc().equals(desc)) {
                found.add(new ClientEntry(element.className(), element.values()));
            }
        }
        return found;
    }

    private static final class McPlatform implements RegistrationPlatform {

        private final ToIntFunction<String> rank;
        private final Function<ResourceKey<Registry<Object>>, Registry<Object>> registryFactory;

        private McPlatform(ToIntFunction<String> rank, Function<ResourceKey<Registry<Object>>, Registry<Object>> registryFactory) {
            this.rank = rank;
            this.registryFactory = registryFactory;
        }

        @Override
        public int rank(String registry) {
            return rank.applyAsInt(registry);
        }

        @Override
        public int order(String registry) {
            int fired = rank.applyAsInt(registry);
            if (fired >= 0) {
                return fired;
            }
            ResourceLocation id = ResourceLocation.tryParse(registry);
            @SuppressWarnings("unchecked")
            Registry<Object> registries = (Registry<Object>) (Registry<?>) BuiltInRegistries.REGISTRY;
            Object found = id == null ? null : registries.get(id);
            int index = found == null ? -1 : registries.getId(found);
            return index < 0 ? Integer.MAX_VALUE - 1 : index;
        }

        @Override
        public ClassLoader loader(String className) {
            return Empiric.class.getClassLoader();
        }

        @Override
        public RegistrationAdapter<?> adapterFor(String registryId) {
            ResourceLocation location = ResourceLocation.tryParse(registryId);
            Registry<?> registry = location == null ? null : BuiltInRegistries.REGISTRY.get(location);
            return registry == null ? null : VanillaAdapter.named(registry.key(), registry);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Object createRegistry(String id, java.lang.reflect.Type fieldRefType) {
            java.lang.reflect.Type registryType = Types.argument(fieldRefType, 0);
            if (Types.raw(registryType) != Registry.class) {
                return null;
            }
            Class<Object> valueType = (Class<Object>) Types.raw(Types.argument(registryType, 0));
            ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(ResourceLocation.parse(id));
            Registry<Object> registry = registryFactory.apply(key);
            registries.bindAdapter(new VanillaAdapter(key, valueType, registry));
            return registry;
        }
    }
}
