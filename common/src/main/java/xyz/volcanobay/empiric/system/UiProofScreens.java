package xyz.volcanobay.empiric.system;

import xyz.volcanobay.empiric.api.ui.tooltip.TooltipContent;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.annotation.Bind;
import xyz.volcanobay.empiric.core.ui.annotation.Child;
import xyz.volcanobay.empiric.core.ui.annotation.Flex;
import xyz.volcanobay.empiric.core.ui.annotation.OnClick;
import xyz.volcanobay.empiric.core.ui.annotation.Padding;
import xyz.volcanobay.empiric.core.ui.annotation.Place;
import xyz.volcanobay.empiric.core.ui.annotation.Size;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.graph.GraphLink;
import xyz.volcanobay.empiric.core.ui.graph.GraphNode;
import xyz.volcanobay.empiric.core.ui.graph.GraphNodeType;
import xyz.volcanobay.empiric.core.ui.graph.GraphPalette;
import xyz.volcanobay.empiric.core.ui.graph.NodeGraph;
import xyz.volcanobay.empiric.core.ui.graph.NodeGraphView;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.Anchor;
import xyz.volcanobay.empiric.core.ui.layout.Direction;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.Cycle;
import xyz.volcanobay.empiric.core.ui.widget.Dial;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.Panel;
import xyz.volcanobay.empiric.core.ui.widget.Slider;
import xyz.volcanobay.empiric.core.ui.widget.Switch;
import xyz.volcanobay.empiric.core.ui.widget.TextField;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Rough rebuilds of Superposition's inscriber and filter screens on the widget system, as proof that it covers them: a
 * node editor with a right click picker and an inspector, and a filter with dials over a draggable band view. Both run on the shared
 * {@link UiDemo.State}.
 * These will not be removed as they serve as examples.
 */
public final class UiProofScreens {

    public static final String NUMBER = "number";
    public static final String BOOL = "bool";

    /**
     * The inscriber's nodes: numbers flow left to right and the server works out each display's result.
     */
    public static final GraphPalette MATH = GraphPalette.register("empiric:demo_math")
            .add(GraphNodeType.of("constant").title("Constant").description("A number you set with its slider").category("Sources").color("accent").width(90)
                    .output("out", NUMBER)
                    .body(node -> new Slider(-10, 10, 0.5).format(value -> String.format("%.1f", value)).bind(node.number("value", 1))))
            .add(GraphNodeType.of("frequency").title("Frequency").description("The demo's shared frequency, read live from the module").category("Sources").color("accent")
                    .output("hz", NUMBER))
            .add(GraphNodeType.of("toggle").title("Toggle").description("On or off, for gates").category("Sources").color("focus")
                    .output("on", BOOL)
                    .body(node -> new Switch().bind(node.bool("on", true))))
            .add(GraphNodeType.of("add").title("Add").description("Adds its two inputs").category("Maths").color("knob_hover")
                    .input("a", NUMBER).input("b", NUMBER).output("sum", NUMBER))
            .add(GraphNodeType.of("multiply").title("Multiply").description("Multiplies its two inputs").category("Maths").color("knob_hover")
                    .input("a", NUMBER).input("b", NUMBER).output("product", NUMBER))
            .add(GraphNodeType.of("gate").title("Gate").description("Passes the value while open is on, otherwise 0").category("Maths").color("knob_hover")
                    .input("value", NUMBER).input("open", BOOL).output("out", NUMBER))
            .add(GraphNodeType.of("display").title("Display").description("Shows its input, worked out by the server").category("Outputs").color("scrollbar").width(90)
                    .input("in", NUMBER)
                    .body(node -> new Label().align(Align.CENTER).bind(node.shown("result", "-"))));

    private UiProofScreens() {
    }

    /**
     * A starting graph so the inscriber isn't empty the first time.
     */
    public static NodeGraph starter() {
        NodeGraph graph = new NodeGraph();
        GraphNode constant = graph.add("constant", 0, 0);
        constant.set("value", 2.5);
        GraphNode frequency = graph.add("frequency", 0, 60);
        GraphNode multiply = graph.add("multiply", 130, 20);
        GraphNode display = graph.add("display", 240, 28);
        graph.connect(constant.id, "out", multiply.id, "a");
        graph.connect(frequency.id, "hz", multiply.id, "b");
        graph.connect(multiply.id, "product", display.id, "in");
        return graph;
    }

    /**
     * Works out every display node's input and stores it as the node's {@code result}. Returns whether any result
     * changed, so the graph only syncs when it must.
     */
    public static boolean evaluate(NodeGraph graph, UiDemo.State state) {
        Map<String, Double> memo = new HashMap<>();
        boolean changed = false;
        for (GraphNode node : graph.nodes) {
            if (!node.type.equals("display")) {
                continue;
            }
            GraphLink link = graph.linkInto(node.id, "in");
            String result = link == null ? "-" : String.format("%.2f", value(graph, link, state, memo, 0));
            if (!result.equals(node.values.get("result"))) {
                node.values.put("result", result);
                changed = true;
            }
        }
        return changed;
    }

    private static double value(NodeGraph graph, GraphLink link, UiDemo.State state, Map<String, Double> memo, int depth) {
        String key = link.from + ":" + link.output;
        Double known = memo.get(key);
        if (known != null) {
            return known;
        }
        GraphNode node = graph.node(link.from);
        if (node == null || depth > 64) {
            return 0;
        }
        double result = switch (node.type) {
            case "constant" -> node.number("value", 1);
            case "frequency" -> state.frequency;
            case "toggle" -> node.bool("on", true) ? 1 : 0;
            case "add" -> input(graph, node, "a", state, memo, depth) + input(graph, node, "b", state, memo, depth);
            case "multiply" -> input(graph, node, "a", state, memo, depth) * input(graph, node, "b", state, memo, depth);
            case "gate" -> input(graph, node, "open", state, memo, depth) != 0 ? input(graph, node, "value", state, memo, depth) : 0;
            default -> 0;
        };
        memo.put(key, result);
        return result;
    }

    private static double input(NodeGraph graph, GraphNode node, String input, UiDemo.State state, Map<String, Double> memo, int depth) {
        GraphLink link = graph.linkInto(node.id, input);
        return link == null ? 0 : value(graph, link, state, memo, depth + 1);
    }

    /**
     * The card's name on top and the graph below. Right click empty space to add nodes, as in Superposition; the
     * inspector only shows while a node is selected.
     */
    @Place(Anchor.CENTER)
    @Size(width = 380, height = 230)
    @Flex(value = Direction.COLUMN, gap = 4, align = Align.STRETCH)
    @Padding(6)
    public static class InscriberScreen extends Panel {

        @Bind("card")
        TextField title = new TextField().placeholder("Card name");
        @Child
        @Size(height = Size.FILL)
        InscriberBody body = new InscriberBody();
    }

    @Flex(value = Direction.ROW, gap = 4, align = Align.STRETCH)
    public static class InscriberBody extends Widget {

        @Bind("graph")
        @Size(width = Size.FILL)
        NodeGraphView graph = new NodeGraphView(MATH);
        @Child
        @Size(width = 100)
        InscriberInspector inspector = new InscriberInspector();

        public InscriberBody() {
            inspector.view = graph;
            inspector.visible(false);
            graph.onSelect(node -> {
                inspector.visible(node != null);
                requestLayout();
            });
        }
    }

    @Flex(value = Direction.COLUMN, gap = 4, align = Align.STRETCH)
    public static class InscriberInspector extends Widget {

        NodeGraphView view;
        @Child
        Label heading = new Label("Inspector").textColor("text_dim");
        @Child
        Label selected = new Label().wrap(true);
        @Child
        Label description = new Label().wrap(true).textColor("text_dim");
        @Child
        Label values = new Label().wrap(true).textColor("text_dim");
        @Child
        Button delete = new Button("Delete node");
        @Child
        Button centre = new Button("Reset view");

        @Override
        protected void onAttach() {
            selected.bind(TooltipContent.computed(() -> {
                GraphNode node = view == null ? null : view.selectedNode();
                return node == null ? "Click a node" : MATH.type(node.type).title() + " #" + node.id;
            }));
            description.bind(TooltipContent.computed(() -> {
                GraphNode node = view == null ? null : view.selectedNode();
                return node == null ? "" : MATH.type(node.type).description();
            }));
            values.bind(TooltipContent.computed(() -> {
                GraphNode node = view == null ? null : view.selectedNode();
                if (node == null) {
                    return "";
                }
                StringJoiner joined = new StringJoiner(", ");
                node.values.forEach((key, value) -> joined.add(key + " " + value));
                return joined.toString();
            }));
        }

        @OnClick("delete")
        void delete() {
            GraphNode node = view.selectedNode();
            if (node != null) {
                view.removeNode(node.id);
            }
        }

        @OnClick("centre")
        void centre() {
            view.view(120, 40, 1);
        }
    }

    @Place(Anchor.CENTER)
    @Size(width = 250)
    @Flex(value = Direction.COLUMN, gap = 6, align = Align.STRETCH)
    @Padding(8)
    public static class FilterScreen extends Panel {

        @Child
        Label title = new Label("Filter").textColor("accent");
        @Child
        @Size(height = 64)
        BandView band = new BandView();
        @Bind("filterType")
        Cycle<Integer> type = Cycle.indexed("Low pass", "High pass", "Band pass", "Band stop");
        @Child
        FilterDials dials = new FilterDials();
    }

    @Flex(value = Direction.ROW, gap = 8, align = Align.CENTER)
    public static class FilterDials extends Widget {

        @Bind("low")
        @Size(width = 30, height = 30)
        Dial low = new Dial(20, 20000, 10);
        @Bind("low")
        Label lowValue = new Label();
        @Bind("high")
        @Size(width = 30, height = 30)
        Dial high = new Dial(20, 20000, 10);
        @Bind("high")
        Label highValue = new Label();
    }

    /**
     * A made-up spectrum across 20 Hz to 20 kHz, bright where the filter passes and dim where it cuts, with its cutoffs
     * marked.
     */
    public static class BandView extends Widget {

        private static final int BARS = 48;
        private static final double MIN = Math.log10(20);
        private static final double MAX = Math.log10(20000);
        private static final int GRAB = 5;
        private static final int NONE = 0;
        private static final int LOW = 1;
        private static final int HIGH = 2;

        private int dragging = NONE;

        @Bind("low")
        Property<Object> low;
        @Bind("high")
        Property<Object> high;
        @Bind("filterType")
        Property<Object> type;

        @Override
        protected void draw(UiCanvas canvas, float partialTick) {
            canvas.fill(0, 0, width(), height(), color("field"));
            double lowHz = ((Number) low.get()).doubleValue();
            double highHz = ((Number) high.get()).doubleValue();
            int filter = ((Number) type.get()).intValue();
            float ticks = root().ticks() + partialTick;
            float barWidth = width() / (float) BARS;
            for (int i = 0; i < BARS; i++) {
                double hz = Math.pow(10, MIN + (MAX - MIN) * (i + 0.5) / BARS);
                double level = 0.45 + 0.35 * Math.sin(i * 0.7 + ticks * 0.15) * Math.cos(i * 0.23 - ticks * 0.05);
                int barHeight = (int) (level * (height() - 4));
                int color = passes(filter, hz, lowHz, highHz) ? color("accent") : color("track");
                int left = Math.round(i * barWidth) + 1;
                canvas.fill(left, height() - 2 - barHeight, Math.round((i + 1) * barWidth) - 1, height() - 2, color);
            }
            int hovered = dragging != NONE ? dragging : isHovered() ? edgeAt(toLocal(root().pointerX(), root().pointerY()).x()) : NONE;
            marker(canvas, lowHz, hovered == LOW);
            if (filter >= 2) {
                marker(canvas, highHz, hovered == HIGH);
            }
        }

        private void marker(UiCanvas canvas, double hz, boolean active) {
            int x = toX(hz);
            if (active) {
                canvas.fill(x - 1, 0, x + 2, height(), color("focus"));
                canvas.fill(x - 3, 0, x + 4, 3, color("focus"));
            } else {
                canvas.fill(x, 0, x + 1, height(), color("text"));
            }
        }

        private int toX(double hz) {
            return (int) ((Math.log10(Math.max(20, hz)) - MIN) / (MAX - MIN) * width());
        }

        private double toHz(double x) {
            double fraction = Math.max(0, Math.min(1, x / Math.max(1, width())));
            double hz = Math.pow(10, MIN + (MAX - MIN) * fraction);
            return Math.round(hz / 10) * 10.0;
        }

        private double number(Property<Object> property) {
            return ((Number) property.get()).doubleValue();
        }

        /**
         * The cutoff within grabbing distance of x, preferring the nearer one.
         */
        private int edgeAt(double x) {
            double toLow = Math.abs(x - toX(number(low)));
            double toHigh = ((Number) type.get()).intValue() >= 2 ? Math.abs(x - toX(number(high))) : Double.MAX_VALUE;
            if (Math.min(toLow, toHigh) > GRAB) {
                return NONE;
            }
            return toLow <= toHigh ? LOW : HIGH;
        }

        @Override
        protected boolean interactive() {
            return true;
        }

        @Override
        protected boolean pointerDown(double x, double y, int button) {
            if (button != Keys.LEFT_BUTTON || !low.writable()) {
                return false;
            }
            dragging = edgeAt(x);
            return dragging != NONE;
        }

        @Override
        protected void pointerDrag(double x, double y, double dx, double dy, int button) {
            double hz = toHz(x);
            boolean band = ((Number) type.get()).intValue() >= 2;
            if (dragging == LOW) {
                low.set(band ? Math.min(hz, number(high)) : hz);
            } else if (dragging == HIGH) {
                high.set(Math.max(hz, number(low)));
            }
        }

        @Override
        protected void pointerUp(double x, double y, int button) {
            dragging = NONE;
        }

        private static boolean passes(int filter, double hz, double low, double high) {
            return switch (filter) {
                case 0 -> hz <= low;
                case 1 -> hz >= low;
                case 2 -> hz >= low && hz <= high;
                default -> hz < low || hz > high;
            };
        }
    }
}
