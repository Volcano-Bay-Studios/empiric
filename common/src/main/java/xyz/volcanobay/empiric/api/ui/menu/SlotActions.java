package xyz.volcanobay.empiric.api.ui.menu;

import xyz.volcanobay.empiric.api.ui.widget.SlotWidget;

/**
 * What a click on a slot does when no menu owns it, such as on a world surface. A host offers one as a root service.
 * Without it, slots outside a menu only show their items.
 */
@FunctionalInterface
public interface SlotActions {

    /**
     * Returns true if the click was taken.
     */
    boolean click(SlotWidget slot, int button);
}
