package xyz.volcanobay.empiric.neoforge;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.GameData;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import xyz.volcanobay.empiric.api.registry.ClientRenderer;
import xyz.volcanobay.empiric.api.registry.ClientTooltipSource;
import xyz.volcanobay.empiric.api.registry.OctRendererFor;
import xyz.volcanobay.empiric.api.registry.PieceRendererFor;
import xyz.volcanobay.empiric.core.registry.ClassScanner;
import xyz.volcanobay.empiric.core.registry.CreateRegistry;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.core.registry.RegisterCallback;
import xyz.volcanobay.empiric.core.registry.Registers;
import xyz.volcanobay.empiric.core.registry.ScanResult;
import xyz.volcanobay.empiric.core.system.EmpiricSystem;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.EmpiricEntrypoint;
import xyz.volcanobay.empiric.core.plugin.EmpiricPlugin;
import xyz.volcanobay.empiric.gametest.EmpiricGameTests;
import xyz.volcanobay.empiric.gametest.EmpiricTestModules;
import xyz.volcanobay.empiric.gametest.TestPlayers;
import xyz.volcanobay.empiric.gametest.registry.RegistryFixtures;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Mod(Empiric.MODID)
public class EmpiricNeoForge {

    private static Map<ResourceLocation, Integer> registrationRanks = Map.of();
    private static int rankedSize = -1;

    public EmpiricNeoForge(IEventBus modEventBus) {
        ScanResult scan = scan();
        Empiric.init(plugins(), scan, EmpiricNeoForge::rank, key -> {
            Registry<Object> registry = new RegistryBuilder<>(key).sync(false).create();
            modEventBus.addListener((NewRegistryEvent event) -> event.register(registry));
            return registry;
        });
        modEventBus.addListener((RegisterEvent event) -> EmpiricRegistration.runForeign(event.getRegistryKey().location().toString()));
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(EmpiricRegistration::requireResolved));
        modEventBus.addListener((EntityAttributeCreationEvent event) -> {
            for (EmpiricRegistration.PendingAttributes attributes : EmpiricRegistration.attributes()) {
                AttributeSupplier.Builder builder = attributes.builder().get();
                if (builder != null) {
                    event.put(attributes.type().get(), builder.build());
                }
            }
        });
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            List<Supplier<? extends ItemLike>> items = EmpiricRegistration.creativeTabs().get(event.getTabKey());
            if (items == null) {
                return;
            }
            for (Supplier<? extends ItemLike> item : items) {
                event.accept(item.get());
            }
        });
        if (EmpiricTestModules.enabled()) {
            TestPlayers.setup(NetworkRegistry::configureMockConnection);
            Registry<RegistryFixtures.ForeignThing> foreign = new RegistryBuilder<>(RegistryFixtures.Foreign.KEY).sync(false).create();
            modEventBus.addListener((NewRegistryEvent event) -> event.register(foreign));
            modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
                if (rank("minecraft:data_component_type") >= rank("minecraft:item") || rank("minecraft:armor_material") >= rank("minecraft:sound_event") || rank("minecraft:data_component_type") < 0) {
                    throw new IllegalStateException("The registry ranks don't match NeoForge's registration order");
                }
            }));
            modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(EmpiricGameTests.class));
        }
    }

    private static int rank(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return -1;
        }
        int size = BuiltInRegistries.REGISTRY.size();
        if (rankedSize != size) {
            Map<ResourceLocation, Integer> ranks = new HashMap<>();
            for (ResourceLocation registry : GameData.getRegistrationOrder()) {
                ranks.put(registry, ranks.size());
            }
            registrationRanks = ranks;
            rankedSize = size;
        }
        Integer rank = registrationRanks.get(location);
        return rank == null ? -1 : rank;
    }

    private static ScanResult scan() {
        Set<String> candidates = Set.of(Register.class.getName(), Registers.class.getName(), RegisterCallback.class.getName(), CreateRegistry.class.getName(), ClientRenderer.class.getName(), PieceRendererFor.class.getName(), OctRendererFor.class.getName(), ClientTooltipSource.class.getName());
        ClassScanner scanner = new ClassScanner();
        Set<String> scanned = new HashSet<>();
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            Map<String, String> mods = mainPackages(data);
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (candidates.contains(annotation.annotationType().getClassName())) {
                    scanClass(scanner, scanned, annotation.clazz().getClassName(), modId(mods, annotation.clazz().getClassName()));
                }
            }
        }
        String registerDesc = Type.getDescriptor(Register.class);
        Set<String> metas = new HashSet<>(Set.of(EmpiricSystem.class.getName()));
        for (ScanResult.ScannedElement element : scanner.result().elements()) {
            if (element.kind() == ScanResult.ScannedElement.Kind.CLASS && element.annotationDesc().equals(registerDesc)) {
                metas.add(element.className());
            }
        }
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            Map<String, String> mods = mainPackages(data);
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (metas.contains(annotation.annotationType().getClassName())) {
                    scanClass(scanner, scanned, annotation.clazz().getClassName(), modId(mods, annotation.clazz().getClassName()));
                }
            }
        }
        return scanner.result();
    }

    private static Map<String, String> mainPackages(ModFileScanData data) {
        Map<String, String> mods = new LinkedHashMap<>();
        String modAnnotation = Type.getDescriptor(Mod.class);
        for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
            if (annotation.annotationType().getDescriptor().equals(modAnnotation)) {
                String className = annotation.clazz().getClassName();
                int dot = className.lastIndexOf('.');
                mods.put(dot < 0 ? "" : className.substring(0, dot), (String) annotation.annotationData().get("value"));
            }
        }
        if (mods.isEmpty()) {
            mods.put("", data.getIModInfoData().get(0).getMods().get(0).getModId());
        }
        return mods;
    }

    private static String modId(Map<String, String> mods, String className) {
        if (mods.size() == 1) {
            return mods.values().iterator().next();
        }
        int dot = className.lastIndexOf('.');
        String packageName = dot < 0 ? "" : className.substring(0, dot);
        String best = null;
        int length = -1;
        boolean ambiguous = false;
        for (Map.Entry<String, String> mod : mods.entrySet()) {
            String prefix = mod.getKey();
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) {
                if (prefix.length() > length) {
                    best = mod.getValue();
                    length = prefix.length();
                    ambiguous = false;
                } else if (prefix.length() == length) {
                    ambiguous = true;
                }
            }
        }
        if (best == null || ambiguous) {
            throw new IllegalStateException(className + " can't be matched to one of the mods " + mods.values() + " in its jar. Put @Namespace on its package.");
        }
        return best;
    }

    private static void scanClass(ClassScanner scanner, Set<String> scanned, String className, String modId) {
        ClassLoader loader = EmpiricNeoForge.class.getClassLoader();
        if (scanned.add(className)) {
            scanner.scanClass(className, loader, modId);
        }
        int dot = className.lastIndexOf('.');
        String packageInfo = (dot < 0 ? "" : className.substring(0, dot + 1)) + "package-info";
        if (scanned.add(packageInfo) && loader.getResource(packageInfo.replace('.', '/') + ".class") != null) {
            scanner.scanClass(packageInfo, loader, modId);
        }
    }

    private static List<EmpiricPlugin> plugins() {
        Type entrypoint = Type.getType(EmpiricEntrypoint.class);
        List<EmpiricPlugin> plugins = new ArrayList<>();
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (!entrypoint.equals(annotation.annotationType())) {
                    continue;
                }
                String name = annotation.clazz().getClassName();
                try {
                    Class<?> type = Class.forName(name, true, EmpiricNeoForge.class.getClassLoader());
                    plugins.add((EmpiricPlugin) type.getDeclaredConstructor().newInstance());
                } catch (ReflectiveOperationException | ClassCastException e) {
                    Empiric.LOGGER.error("Couldn't load the Empiric plugin {}", name, e);
                }
            }
        }
        return plugins;
    }
}
