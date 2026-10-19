package xyz.volcanobay.empiric.api.ui.widget;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.ui.menu.ListContainer;
import xyz.volcanobay.empiric.core.ui.layout.GridLayout;
import xyz.volcanobay.empiric.core.ui.widget.ValueWidget;

import java.util.List;

/**
 * A grid of slots over part of a container. With {@code @Bind}, it holds a module's synced {@code List<ItemStack>}
 * field, and edits made through the slots mark that field dirty so every viewer sees them.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SlotGrid extends ValueWidget<List> {

    private final Container container;
    private final int start;
    private final int count;

    public SlotGrid(int columns, int count) {
        this(null, columns, 0, count);
    }

    public SlotGrid(int columns, int start, int count) {
        this(null, columns, start, count);
    }

    public SlotGrid(Container container, int columns, int start, int count) {
        super(List.class, null);
        this.container = container;
        this.start = start;
        this.count = count;
        layout(new GridLayout(columns, 0));
    }

    private List<ItemStack> items() {
        return (List<ItemStack>) property().get();
    }

    @Override
    protected void onAttach() {
        if (!children().isEmpty()) {
            return;
        }
        Container target = container != null ? container : new ListContainer(this::items, () -> property().markChanged());
        int size = Math.max(0, Math.min(count, target.getContainerSize() - start));
        for (int i = 0; i < size; i++) {
            add(new SlotWidget(target, start + i));
        }
    }
}
