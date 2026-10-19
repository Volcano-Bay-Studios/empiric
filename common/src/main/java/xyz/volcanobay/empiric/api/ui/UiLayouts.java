package xyz.volcanobay.empiric.api.ui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.ui.widget.ItemView;
import xyz.volcanobay.empiric.api.ui.widget.ModelView;
import xyz.volcanobay.empiric.api.ui.widget.PlayerInventoryGrid;
import xyz.volcanobay.empiric.api.ui.widget.SlotGrid;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.Blueprints;
import xyz.volcanobay.empiric.core.ui.blueprint.PropertyDef;
import xyz.volcanobay.empiric.core.ui.blueprint.Props;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetType;
import xyz.volcanobay.empiric.core.ui.blueprint.WidgetTypes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layout files are data entries in {@code data/<namespace>/ui/layouts}, synced to clients like themes:
 * <pre>{@code
 * { "description": "...", "root": { "type": "panel", "layout": "column", "gap": 4, "children": [ ... ] } }
 * }</pre>
 * A screen uses one through {@link UiScreens#registerLayout}, or a widget class through {@code @Layout}. Layouts the
 * editor applies live override loaded ones on this side until the next reload.
 */
public final class UiLayouts {

    public static final String ROOT = "root";
    public static final DataEntryType<UiLayout> LAYOUTS = new DataEntryType<>("empiric:ui/layouts", UiLayout.class, UiLayout::new, ROOT);

    private static final Map<String, BlueprintNode> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, BlueprintNode> OVERRIDES = new ConcurrentHashMap<>();
    private static boolean registered;

    private UiLayouts() {
    }

    public static void registerData(DataEntrySystem data) {
        data.register(LAYOUTS);
        LAYOUTS.onReload(UiLayouts::reload);
        Blueprints.provider(UiLayouts::get);
        registerWidgets();
    }

    private static void reload() {
        LOADED.clear();
        OVERRIDES.clear();
        for (DataEntry<UiLayout> entry : LAYOUTS.entries()) {
            DataNode root = entry.extras() == null ? null : entry.extras().get(ROOT);
            if (root == null) {
                Empiric.LOGGER.error("Layout {} has no \"root\"", entry.id());
                continue;
            }
            try {
                LOADED.put(entry.id(), BlueprintNode.parse(root));
            } catch (RuntimeException e) {
                Empiric.LOGGER.error("Layout {} couldn't be read: {}", entry.id(), e.getMessage());
            }
        }
    }

    /**
     * A copy of the layout with this id, or null. Each build gets its own copy, so nothing can change a loaded file.
     */
    public static BlueprintNode get(String id) {
        BlueprintNode node = OVERRIDES.getOrDefault(id, LOADED.get(id));
        return node == null ? null : node.copy();
    }

    public static Map<String, BlueprintNode> loaded() {
        return Map.copyOf(LOADED);
    }

    /**
     * Uses this tree for the id on this side until the next reload, so screens opened after see an edit at once.
     */
    public static void override(String id, BlueprintNode node) {
        OVERRIDES.put(id, node.copy());
    }

    /**
     * The JSON written for a layout file.
     */
    public static DataNode write(String description, BlueprintNode root) {
        DataNode file = DataNode.object();
        if (!description.isEmpty()) {
            file.put("description", DataNode.of(description));
        }
        file.put(ROOT, root.write());
        return file;
    }

    private static synchronized void registerWidgets() {
        if (registered) {
            return;
        }
        registered = true;
        WidgetTypes.register(WidgetType.of("slot_grid", props -> new SlotGrid(props.integer("columns", 9), props.integer("start", 0), props.integer("count", 9)))
                .category("Items")
                .property(PropertyDef.integer("columns", 9, "Slots per row"), PropertyDef.integer("start", 0, "First index in the bound list"),
                        PropertyDef.integer("count", 9, "How many slots")));
        WidgetTypes.register(WidgetType.of("player_inventory", props -> new PlayerInventoryGrid()).category("Items"));
        WidgetTypes.register(WidgetType.of("item", props -> new ItemView(stack(props)).scale(props.integer("scale", 1)))
                .category("Items")
                .property(PropertyDef.string("item", "minecraft:air", "Item id, when not bound"), PropertyDef.integer("scale", 1, "Size multiplier")));
        WidgetTypes.register(WidgetType.of("model", props -> new ModelView(stack(props))
                        .spin((float) props.number("spin", 0))
                        .angle((float) props.number("yaw", 30), (float) props.number("pitch", 20)))
                .category("Items")
                .property(PropertyDef.string("item", "minecraft:air", "Item id, when not bound"), PropertyDef.number("spin", 0, "Degrees per tick"),
                        PropertyDef.number("yaw", 30, "Starting yaw"), PropertyDef.number("pitch", 20, "Pitch")));
    }

    private static ItemStack stack(Props props) {
        ResourceLocation id = ResourceLocation.tryParse(props.string("item", "minecraft:air"));
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
