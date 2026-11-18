package xyz.volcanobay.empiric.client;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;
import xyz.volcanobay.empiric.api.oct.OctBlockRenderer;
import xyz.volcanobay.empiric.api.oct.OctBlockRendererRegistry;
import xyz.volcanobay.empiric.api.part.PartTraits;
import xyz.volcanobay.empiric.api.part.TraitKey;
import xyz.volcanobay.empiric.api.part.client.PartRenderers;
import xyz.volcanobay.empiric.api.part.client.PieceRenderer;
import xyz.volcanobay.empiric.api.registry.ClientTooltipSource;
import xyz.volcanobay.empiric.api.registry.OctRendererFor;
import xyz.volcanobay.empiric.api.registry.PieceRendererFor;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipSource;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipSources;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;

import java.lang.invoke.MethodHandles;

/**
 * Reads the client registration annotations. Reflection here runs only during startup registration.
 */
public final class ClientRegistrations {

    private ClientRegistrations() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void bind() {
        for (EmpiricRegistration.ClientEntry entry : EmpiricRegistration.clientEntries(PieceRendererFor.class)) {
            String traitId = String.valueOf(entry.values().get("value"));
            TraitKey<?> trait = PartTraits.get(ResourceLocation.parse(traitId));
            if (trait == null) {
                throw new IllegalStateException(entry.className() + " renders the part trait " + traitId + ", which isn't registered");
            }
            PartRenderers.register(trait, (PieceRenderer) instance(entry.className()));
        }
        for (EmpiricRegistration.ClientEntry entry : EmpiricRegistration.clientEntries(OctRendererFor.class)) {
            Class<? extends OctBlockEntity> type = (Class<? extends OctBlockEntity>) load(entry.classValue("value"));
            OctBlockRendererRegistry.register((Class) type, () -> (OctBlockRenderer) instance(entry.className()));
        }
        for (EmpiricRegistration.ClientEntry entry : EmpiricRegistration.clientEntries(ClientTooltipSource.class)) {
            TooltipSources.register((TooltipSource) instance(entry.className()));
        }
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, true, ClientRegistrations.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Can't load " + className, e);
        }
    }

    private static Object instance(String className) {
        try {
            return MethodHandles.lookup().unreflectConstructor(load(className).getConstructor()).invoke();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(className + " needs a public constructor with no parameters", e);
        }
    }
}
