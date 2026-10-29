package xyz.volcanobay.empiric.api.ui.tooltip;

import xyz.volcanobay.empiric.api.module.SelfModule;
import xyz.volcanobay.empiric.core.module.Module;

/**
 * Finds the object that carries a module's tooltip: the block entity behind a {@link SelfModule}, the module otherwise.
 */
public final class TooltipTargets {

    private TooltipTargets() {
    }

    public static Object owner(Module<?> module) {
        if (module instanceof SelfModule self) {
            return self.attachment().blockEntity();
        }
        return module;
    }
}
