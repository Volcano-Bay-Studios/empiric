package xyz.volcanobay.empiric.api.ui.tooltip;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.annotation.Tooltip;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.bind.UiBinder;
import xyz.volcanobay.empiric.core.ui.widget.Cycle;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.Slider;
import xyz.volcanobay.empiric.core.ui.widget.Switch;
import xyz.volcanobay.empiric.core.ui.widget.TextField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code @Tooltip} fields of a module class, found once per class and turned into rows bound to the module's
 * synced fields.
 */
public final class TooltipFields {

    private static final ClassValue<List<Row>> ROWS = new ClassValue<>() {
        @Override
        protected List<Row> computeValue(Class<?> type) {
            return scan(type);
        }
    };

    private record Row(String id, String label, Tooltip spec, Class<?> type) {
    }

    private TooltipFields() {
    }

    public static boolean has(Class<?> moduleClass) {
        return !ROWS.get(moduleClass).isEmpty();
    }

    private static List<Row> scan(Class<?> type) {
        List<Row> rows = new ArrayList<>();
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            chain.addFirst(current);
        }
        for (Class<?> current : chain) {
            for (Field field : current.getDeclaredFields()) {
                Tooltip spec = field.getAnnotation(Tooltip.class);
                if (spec == null) {
                    continue;
                }
                Data data = field.getAnnotation(Data.class);
                if (data == null || !data.sync()) {
                    throw new IllegalStateException("@Tooltip on " + type.getName() + "." + field.getName() + " needs a synced @Data field");
                }
                String id = data.id().isEmpty() ? field.getName() : data.id();
                String label = spec.value().isEmpty() ? field.getName() : spec.value();
                rows.add(new Row(id, label, spec, field.getType()));
            }
        }
        rows.sort(Comparator.comparingInt(row -> row.spec().order()));
        return List.copyOf(rows);
    }

    /**
     * Adds a row for each {@code @Tooltip} field of the module.
     */
    public static void build(TooltipContent content, Module<?> module) {
        for (Row row : ROWS.get(module.getClass())) {
            content.row(row.label(), widget(row, module));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Widget widget(Row row, Module<?> module) {
        Tooltip spec = row.spec();
        String user = "@Tooltip " + row.id();
        Class<?> type = row.type();
        if (spec.edit()) {
            if (type == boolean.class || type == Boolean.class) {
                return new Switch().bind(UiBinder.property(module, row.id(), Boolean.class, user));
            }
            if (type.isEnum()) {
                Cycle cycle = Cycle.of((Class) type);
                cycle.bindUnchecked(UiBinder.property(module, row.id(), Object.class, user));
                return cycle;
            }
            if (type == String.class) {
                return new TextField().bind(UiBinder.property(module, row.id(), String.class, user));
            }
            if (isNumber(type) && !Double.isNaN(spec.min()) && !Double.isNaN(spec.max())) {
                Slider slider = new Slider(spec.min(), spec.max(), spec.step());
                if (!spec.format().isEmpty()) {
                    slider.format(value -> String.format(spec.format(), value));
                }
                return slider.bind(UiBinder.property(module, row.id(), Double.class, user));
            }
        }
        Property<Object> value = UiBinder.property(module, row.id(), Object.class, user);
        String format = spec.format();
        return new Label().bind(TooltipContent.computed(() -> format.isEmpty() ? String.valueOf(value.get()) : String.format(format, value.get())));
    }

    private static boolean isNumber(Class<?> type) {
        return type.isPrimitive() ? type != boolean.class && type != char.class : Number.class.isAssignableFrom(type);
    }
}
