package xyz.volcanobay.empiric.api.ui.tooltip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.widget.ItemView;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.FlexLayout;
import xyz.volcanobay.empiric.core.ui.layout.FormLayout;
import xyz.volcanobay.empiric.core.ui.layout.Insets;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.Panel;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The body of a floating tooltip: a column of rows on the theme's panel. Any widget can be a row; the helpers
 * cover text, live values and labelled controls.
 */
public class TooltipContent extends Panel {

    private TooltipPlacement placement = TooltipPlacement.ANCHORED;
    private Widget form;

    public TooltipContent() {
        padding(Insets.of(6));
        layout(FlexLayout.column(4).align(Align.STRETCH));
    }

    /**
     * Whether a module adds anything to tooltips for this viewer.
     */
    public static boolean provides(Module<?> module, Player viewer) {
        Object owner = TooltipTargets.owner(module);
        if (owner instanceof TooltipBehavior behavior) {
            return behavior.showsTooltip(viewer);
        }
        return TooltipFields.has(owner.getClass());
    }

    /**
     * The tooltip for some modules on one target: each one's heading, its {@code @Tooltip} rows, then its own rows.
     */
    public static TooltipContent of(List<? extends Module<?>> modules, Player viewer) {
        TooltipContent content = new TooltipContent();
        for (Module<?> module : modules) {
            Object owner = TooltipTargets.owner(module);
            if (owner instanceof TooltipBehavior behavior) {
                String title = behavior.tooltipTitle(viewer);
                if (title != null) {
                    ItemStack icon = behavior.tooltipIcon();
                    if (icon.isEmpty()) {
                        content.title(title);
                    } else {
                        content.title(icon, title);
                    }
                }
            }
            TooltipFields.build(content, module);
            if (owner instanceof TooltipBehavior behavior) {
                behavior.tooltip(content, viewer);
            }
        }
        return content;
    }

    /**
     * The tooltip of a target that isn't a module, such as one from a {@link TooltipSource}.
     */
    public static TooltipContent of(TooltipBehavior behavior, Player viewer) {
        TooltipContent content = new TooltipContent();
        String title = behavior.tooltipTitle(viewer);
        if (title != null) {
            ItemStack icon = behavior.tooltipIcon();
            if (icon.isEmpty()) {
                content.title(title);
            } else {
                content.title(icon, title);
            }
        }
        behavior.tooltip(content, viewer);
        return content;
    }

    public boolean isEmpty() {
        return children().isEmpty();
    }

    public TooltipPlacement placement() {
        return placement;
    }

    public TooltipContent placement(TooltipPlacement placement) {
        this.placement = placement;
        return this;
    }

    /**
     * Draws the background with other theme colours or sprites than the panel's.
     */
    public TooltipContent background(String style, String border) {
        style(style, border);
        return this;
    }

    public TooltipContent title(String text) {
        return line(new Label(text).textColor("accent"));
    }

    public TooltipContent title(ItemStack icon, String text) {
        Widget row = new Widget().layout(FlexLayout.row(4).align(Align.CENTER));
        row.add(new ItemView(icon));
        row.add(new Label(text).textColor("accent"));
        return line(row);
    }

    public TooltipContent text(String text) {
        return line(new Label(text));
    }

    public TooltipContent text(Supplier<String> text) {
        return line(new Label().bind(computed(text)));
    }

    /**
     * Adds a widget across the whole width, such as a button or a graph.
     */
    public TooltipContent line(Widget widget) {
        form = null;
        add(widget);
        return this;
    }

    /**
     * A dim label followed by a live value.
     */
    public TooltipContent value(String label, Supplier<?> value) {
        return row(label, new Label().bind(computed(() -> Objects.toString(value.get()))));
    }

    /**
     * A dim label beside any widget, such as a switch or slider bound to a field. Rows added one after another line up in
     * a two column form.
     */
    public TooltipContent row(String label, Widget widget) {
        if (form == null) {
            form = new Widget().layout(new FormLayout(6, 3));
            add(form);
        }
        form.add(new Label(label).textColor("text_dim"));
        form.add(widget);
        return this;
    }

    /**
     * A read-only property that asks the supplier each time, for widgets that show a derived value.
     */
    public static <T> Property<T> computed(Supplier<T> supplier) {
        return new Property<>() {
            @Override
            public T get() {
                return supplier.get();
            }

            @Override
            public void set(T value) {
            }

            @Override
            public boolean writable() {
                return false;
            }
        };
    }

}
