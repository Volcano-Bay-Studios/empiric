package xyz.volcanobay.empiric.platform;

import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.system.AtmosphereSystem;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.chemistry.ChemistrySpecs;
import xyz.volcanobay.empiric.system.TemperatureSystem;
import xyz.volcanobay.empiric.system.UiDemo;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.api.ui.UiThemes;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.plugin.EmpiricPlugin;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.data.MinecraftSpecs;

public final class EmpiricPlatform implements EmpiricPlugin {

    @Override
    public void registerSpecs(SpecRegistry specs) {
        MinecraftSpecs.register(specs);
        ChemistrySpecs.register(specs);
    }

    @Override
    public void registerData(DataEntrySystem data) {
        TemperatureSystem.registerData(data);
        AtmosphereSystem.registerData(data);
        ChemicalRegistry.registerData(data);
        UiThemes.registerData(data);
        UiLayouts.registerData(data);
    }

    @Override
    public void registerAttachments(ModuleSystem modules) {
        modules.bufferFactory(EmpiricBuffers::buffer);
        modules.errorHandler(Empiric.LOGGER::error);
        EmpiricAttachments.register(modules);
    }

    @Override
    public void registerBehaviors(ModuleSystem modules) {
        EmpiricBehaviors.register(modules);
    }

    @Override
    public void registerModules(ModuleSystem modules) {
        AtmosphereSystem.register(modules);
        TemperatureSystem.register(modules);
        UiDemo.register(modules);
    }
}
