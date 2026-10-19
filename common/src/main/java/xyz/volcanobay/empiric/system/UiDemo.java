package xyz.volcanobay.empiric.system;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.volcanobay.empiric.api.ui.widget.ItemView;
import xyz.volcanobay.empiric.api.ui.widget.ModelView;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.ui.widget.PlayerInventoryGrid;
import xyz.volcanobay.empiric.api.ui.widget.SlotGrid;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.module.BlockModuleType;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipBehavior;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipContent;
import xyz.volcanobay.empiric.core.ui.annotation.Tooltip;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.bind.UiBinder;
import net.minecraft.world.entity.player.Player;
import xyz.volcanobay.empiric.api.behavior.ServerTickBehavior;
import xyz.volcanobay.empiric.api.module.ServerModuleType;
import xyz.volcanobay.empiric.api.ui.UiBehavior;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.graph.NodeGraph;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.core.ui.annotation.Bind;
import xyz.volcanobay.empiric.core.ui.annotation.Child;
import xyz.volcanobay.empiric.core.ui.annotation.ContextAction;
import xyz.volcanobay.empiric.core.ui.annotation.Flex;
import xyz.volcanobay.empiric.core.ui.annotation.Hint;
import xyz.volcanobay.empiric.core.ui.annotation.OnChange;
import xyz.volcanobay.empiric.core.ui.annotation.OnClick;
import xyz.volcanobay.empiric.core.ui.annotation.Padding;
import xyz.volcanobay.empiric.core.ui.annotation.Place;
import xyz.volcanobay.empiric.core.ui.annotation.Size;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.Anchor;
import xyz.volcanobay.empiric.core.ui.layout.Direction;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.Checkbox;
import xyz.volcanobay.empiric.core.ui.widget.ColorPicker;
import xyz.volcanobay.empiric.core.ui.widget.Dial;
import xyz.volcanobay.empiric.core.ui.widget.Dropdown;
import xyz.volcanobay.empiric.core.ui.widget.Graph;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.Panel;
import xyz.volcanobay.empiric.core.ui.widget.ProgressBar;
import xyz.volcanobay.empiric.core.ui.widget.ScrollPanel;
import xyz.volcanobay.empiric.core.ui.widget.Slider;
import xyz.volcanobay.empiric.core.ui.widget.Switch;
import xyz.volcanobay.empiric.core.ui.widget.Tabs;
import xyz.volcanobay.empiric.core.ui.widget.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A shared demo screen on a server module, opened with {@code /empiric ui demo}. Everyone who opens it edits the same
 * state, which makes it the manual check for widget rendering and multi-viewer sync.
 */
public final class UiDemo {

    public static ServerModuleType<State> TYPE;
    public static UiScreenType<State> SCREEN;
    public static UiScreenType<State> LAYOUT_SCREEN;
    public static UiScreenType<State> INSCRIBER_SCREEN;
    public static UiScreenType<State> FILTER_SCREEN;
    public static BlockModuleType<Gauge> GAUGE;

    private UiDemo() {
    }

    public static void register(ModuleSystem modules) {
        TYPE = modules.register(ServerModuleType.builder(Empiric.id("ui_demo"), State.class, State::new).build());
        SCREEN = UiScreens.register(Empiric.id("demo"), TYPE, state -> new DemoScreen()).presence(true);
        LAYOUT_SCREEN = UiScreens.registerLayout(Empiric.id("demo_layout"), TYPE, "empiric:demo_panel").presence(true);
        INSCRIBER_SCREEN = UiScreens.register(Empiric.id("demo_inscriber"), TYPE, state -> new UiProofScreens.InscriberScreen()).presence(true);
        FILTER_SCREEN = UiScreens.register(Empiric.id("demo_filter"), TYPE, state -> new UiProofScreens.FilterScreen()).presence(true);
        GAUGE = modules.register(BlockModuleType.builder(Empiric.id("ui_gauge"), Gauge.class, Gauge::new).build());
    }

    /**
     * A block module whose floating tooltip shows and edits its fields, attached with {@code /empiric ui tooltip}.
     */
    public static class Gauge extends Module<BlockAttachment> implements NetworkBehavior, ServerTickBehavior, TooltipBehavior {

        @Tooltip(value = "Name", edit = true)
        @Data(clientMutable = true)
        public String name = "Gauge";
        @Tooltip(value = "Running", edit = true)
        @Data(clientMutable = true)
        public boolean running = true;
        @Tooltip(value = "Rate", edit = true, min = 0, max = 10, step = 0.5, format = "%.1f/s")
        @Data(clientMutable = true)
        public double rate = 2;
        @Tooltip(value = "Count", format = "%.0f")
        @Data(watch = true)
        public double count;

        @Override
        public void serverTick() {
            if (running) {
                count += rate / 20;
            }
        }

        @Override
        public String tooltipTitle(Player viewer) {
            return name;
        }

        @Override
        public ItemStack tooltipIcon() {
            return new ItemStack(Items.OBSERVER);
        }

        @Override
        public void tooltip(TooltipContent content, Player viewer) {
            content.row("Lap", new ProgressBar(0, 100).bind(TooltipContent.computed(() -> count % 100)));
            Property<Double> rateProperty = UiBinder.property(this, "rate", Double.class, "gauge reset");
            content.line(new Button("Reset rate", () -> rateProperty.set(2.0)));
        }
    }

    public static class State extends Module<ServerAttachment> implements UiBehavior, ServerTickBehavior {

        @Data(clientMutable = true)
        public boolean power = true;
        @Data(clientMutable = true)
        public boolean muted;
        @Data(clientMutable = true)
        public double frequency = 440;
        @Data(clientMutable = true)
        public float gain = 0.5f;
        @Data(clientMutable = true)
        public int mode;
        @Data(clientMutable = true)
        public String label = "Hello";
        @Data(clientMutable = true)
        public int tab;
        @Data(clientMutable = true)
        public int color = 0xFF3FA866;
        @Data(watch = true)
        public double level;
        @Data(watch = true)
        public double wave;
        @Data
        public List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        @Data(clientMutable = true)
        public NodeGraph graph = UiProofScreens.starter();
        @Data(clientMutable = true)
        public String card = "Untitled card";
        @Data(clientMutable = true)
        public double low = 200;
        @Data(clientMutable = true)
        public double high = 2000;
        @Data(clientMutable = true)
        public int filterType = 2;

        @Override
        public void onClientUpdate(ServerPlayer player, String field) {
            if (field.equals("graph")) {
                if (graph == null) {
                    graph = new NodeGraph();
                }
                graph.sanitize(UiProofScreens.MATH);
            }
        }

        @Override
        public void serverTick() {
            if (graph != null && UiProofScreens.evaluate(graph, this)) {
                markDirty("graph");
            }
            if (!power) {
                return;
            }
            level = (level + frequency / 40000) % 1;
            double output = switch (mode) {
                case 1 -> level < 0.5 ? 1 : 0;
                case 2 -> level;
                default -> 0.5 + 0.5 * Math.sin(level * Math.PI * 2);
            };
            wave = muted ? 0 : output * gain;
        }
    }

    @Place(Anchor.CENTER)
    @Size(width = 240)
    @Flex(value = Direction.COLUMN, gap = 4, align = Align.STRETCH)
    @Padding(8)
    public static class DemoScreen extends Panel {

        @Child
        Label title = new Label("Empiric UI demo").shadow(true);
        @Bind("tab")
        @Size(height = 150)
        DemoTabs tabs = new DemoTabs();
        @Bind("wave")
        ProgressBar wave = new ProgressBar(0, 1).format(value -> "Output " + Math.round(value * 100) + "%");
        @Child
        Button close = new Button("Close");

        @OnClick("close")
        void close() {
            root().close();
        }

        @OnChange("power")
        void powerChanged(boolean on) {
            title.setValue(on ? "Empiric UI demo" : "Empiric UI demo (off)");
        }
    }

    public static class DemoTabs extends Tabs {

        @Child(id = "Controls")
        Controls controls = new Controls();
        @Child(id = "Text")
        TextPage text = new TextPage();
        @Child(id = "Items")
        ItemsPage items = new ItemsPage();
        @Child(id = "Widgets")
        WidgetsPage widgets = new WidgetsPage();
    }

    @Flex(value = Direction.COLUMN, gap = 6, align = Align.STRETCH)
    @Padding(6)
    public static class Controls extends Widget {

        @Child
        PowerRow power = new PowerRow();
        @Child
        FrequencyRow frequency = new FrequencyRow();
        @Bind("gain")
        Slider gain = new Slider(0, 1, 0.05);
    }

    @Flex(value = Direction.ROW, gap = 6, align = Align.CENTER)
    public static class PowerRow extends Widget {

        @Bind("power")
        Switch power = new Switch();
        @Child
        Label label = new Label("Power");
        @Bind("muted")
        Checkbox muted = new Checkbox("Muted");
    }

    @Flex(value = Direction.ROW, gap = 6, align = Align.CENTER)
    public static class FrequencyRow extends Widget {

        @Bind("frequency")
        @Size(width = 28, height = 28)
        Dial dial = new Dial(20, 2000, 10);
        @Bind("frequency")
        Label value = new Label();
        @Bind("mode")
        Dropdown<Integer> mode = Dropdown.indexed("Sine", "Square", "Saw");
    }

    @Flex(value = Direction.COLUMN, gap = 6, align = Align.STRETCH)
    @Padding(6)
    public static class WidgetsPage extends Widget {

        @Bind("wave")
        @Size(height = 40)
        @Hint("The output over the last 100 ticks")
        Graph graph = new Graph(100).range(0, 1);
        @Child
        ShowcaseRow showcase = new ShowcaseRow();
    }

    @Flex(value = Direction.ROW, gap = 8, align = Align.CENTER)
    public static class ShowcaseRow extends Widget {

        @Bind("color")
        @Hint("Shared colour, right click for presets")
        ColorPicker color = new ColorPicker();
        @Child
        @Size(width = 40, height = 40)
        @Hint("Drag to turn")
        ModelView model = new ModelView(new ItemStack(Items.GRASS_BLOCK));
        @Child
        ItemView item = new ItemView(new ItemStack(Items.DIAMOND, 3)).scale(2);

        @ContextAction(value = "Reset colour", target = "color")
        void resetColor() {
            color.setValue(0xFF3FA866);
        }

        @ContextAction(value = "White", target = "color")
        void white() {
            color.setValue(0xFFFFFFFF);
        }
    }

    @Flex(value = Direction.COLUMN, gap = 6)
    @Padding(6)
    public static class ItemsPage extends Widget {

        @Bind("items")
        SlotGrid shared = new SlotGrid(9, 9);
        @Child
        PlayerInventoryGrid inventory = new PlayerInventoryGrid();
    }

    @Flex(value = Direction.COLUMN, gap = 4, align = Align.STRETCH)
    @Padding(6)
    public static class TextPage extends Widget {

        @Bind("label")
        TextField name = new TextField().placeholder("Every viewer sees this");
        @Bind("label")
        Label echo = new Label();
        @Child
        @Size(height = Size.FILL)
        ScrollPanel list = new ScrollPanel();

        public TextPage() {
            for (int i = 1; i <= 30; i++) {
                list.add(new Label("Scrolled line " + i));
            }
        }
    }
}
