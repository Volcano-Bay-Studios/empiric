package xyz.volcanobay.empiric.neoforge;

import net.neoforged.bus.api.IEventBus;
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
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

@Mod(Empiric.MODID)
public class EmpiricNeoForge {

    public EmpiricNeoForge(IEventBus modEventBus) {
        Empiric.init(plugins());
        if (EmpiricTestModules.enabled()) {
            TestPlayers.setup(NetworkRegistry::configureMockConnection);
            modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(EmpiricGameTests.class));
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
