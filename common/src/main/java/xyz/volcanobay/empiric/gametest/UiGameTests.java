package xyz.volcanobay.empiric.gametest;

import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.api.ui.UiBehavior;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.api.ui.UiThemes;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipContent;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipFields;
import xyz.volcanobay.empiric.api.ui.widget.PlayerInventoryGrid;
import xyz.volcanobay.empiric.api.ui.widget.SlotGrid;
import xyz.volcanobay.empiric.command.EmpiricCommands;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachment;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.data.tree.TreeDataBuffer;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiFont;
import xyz.volcanobay.empiric.core.ui.UiHost;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.UiSprite;
import xyz.volcanobay.empiric.core.ui.UiTheme;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.annotation.Bind;
import xyz.volcanobay.empiric.core.ui.annotation.Child;
import xyz.volcanobay.empiric.core.ui.annotation.ContextAction;
import xyz.volcanobay.empiric.core.ui.annotation.Flex;
import xyz.volcanobay.empiric.core.ui.annotation.Layout;
import xyz.volcanobay.empiric.core.ui.annotation.OnChange;
import xyz.volcanobay.empiric.core.ui.annotation.OnClick;
import xyz.volcanobay.empiric.core.ui.annotation.Padding;
import xyz.volcanobay.empiric.core.ui.annotation.Ref;
import xyz.volcanobay.empiric.core.ui.annotation.Size;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.bind.UiBinder;
import xyz.volcanobay.empiric.core.ui.bind.UiException;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintNode;
import xyz.volcanobay.empiric.core.ui.blueprint.BlueprintView;
import xyz.volcanobay.empiric.core.ui.blueprint.Blueprints;
import xyz.volcanobay.empiric.core.ui.graph.GraphLink;
import xyz.volcanobay.empiric.core.ui.graph.GraphNode;
import xyz.volcanobay.empiric.core.ui.graph.GraphNodeType;
import xyz.volcanobay.empiric.core.ui.graph.GraphPalette;
import xyz.volcanobay.empiric.core.ui.graph.NodeGraph;
import xyz.volcanobay.empiric.core.ui.graph.NodeGraphView;
import xyz.volcanobay.empiric.core.ui.graph.NodePicker;
import xyz.volcanobay.empiric.core.ui.graph.NodeWidget;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.Anchor;
import xyz.volcanobay.empiric.core.ui.layout.Direction;
import xyz.volcanobay.empiric.core.ui.layout.FlexLayout;
import xyz.volcanobay.empiric.core.ui.layout.Insets;
import xyz.volcanobay.empiric.core.ui.layout.Justify;
import xyz.volcanobay.empiric.core.ui.layout.Length;
import xyz.volcanobay.empiric.core.ui.surface.SurfacePlane;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.ColorPicker;
import xyz.volcanobay.empiric.core.ui.widget.ContextMenu;
import xyz.volcanobay.empiric.core.ui.widget.Cycle;
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
import xyz.volcanobay.empiric.data.JsonNodes;
import xyz.volcanobay.empiric.networking.packet.UiOpenC2SPacket;
import xyz.volcanobay.empiric.system.DebugSubscriptionSystem;
import xyz.volcanobay.empiric.system.UiDemo;
import xyz.volcanobay.empiric.system.UiProofScreens;
import xyz.volcanobay.empiric.system.UiSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Game tests for the UI system. Rendering needs a client, so these drive layout, binding, input and the open/close
 * protocol through a host with a fixed-width font.
 */
final class UiGameTests {

    static ChunkModuleType<Console> CONSOLE;
    static ModuleType<ConsoleView, ModuleAttachment> CONSOLE_VIEW;
    static UiScreenType<Console> CONSOLE_SCREEN;
    static UiScreenType<Console> CONSOLE_MENU;

    private UiGameTests() {
    }

    static void register(ModuleSystem modules) {
        CONSOLE = modules.register(ChunkModuleType.builder(Empiric.id("test_console"), Console.class, Console::new).build());
        CONSOLE_VIEW = modules.register(ModuleType.builder(() -> "empiric:test_console_view", ConsoleView.class, EmpiricAttachments.module(), ConsoleView::new).build());
        CONSOLE_SCREEN = UiScreens.register(Empiric.id("test_console"), CONSOLE, console -> new ConsoleScreen());
        CONSOLE_MENU = UiScreens.register(Empiric.id("test_console_menu"), CONSOLE, console -> new ConsoleMenuScreen());
        modules.subscribe(EmpiricTestModules.SUBSCRIBER, CONSOLE);
        modules.subscribe(EmpiricTestModules.SUBSCRIBER, CONSOLE_VIEW);
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test, BiFunction<String, Consumer<GameTestHelper>, TestFunction> isolated) {
        return List.of(
                test.apply("ui_layout", UiGameTests::layout),
                test.apply("ui_binding", UiGameTests::binding),
                test.apply("ui_input", UiGameTests::input),
                test.apply("ui_open_close", UiGameTests::openClose),
                test.apply("ui_theme", UiGameTests::theme),
                test.apply("ui_surface", UiGameTests::surface),
                test.apply("ui_tooltip", UiGameTests::tooltip),
                test.apply("ui_menu", UiGameTests::menu),
                test.apply("ui_world_slots", UiGameTests::worldSlots),
                test.apply("ui_blueprint", UiGameTests::blueprint),
                test.apply("ui_node_graph", UiGameTests::nodeGraph),
                test.apply("ui_layout_editor", UiEditorGameTests::editor),
                isolated.apply("ui_commands", UiGameTests::commands));
    }

    private static void layout(GameTestHelper helper) {
        TestHost host = new TestHost();
        Widget content = new Widget().layout(FlexLayout.row(2)).size(100, 20).anchor(Anchor.CENTER);
        Widget fixed = content.add(new Widget().size(10, 10));
        Widget single = content.add(new Widget().size(Length.FILL, Length.px(10)));
        Widget twice = content.add(new Widget().size(Length.fill(2), Length.FILL));
        UiRoot root = new UiRoot(content, null, host);
        root.resize(200, 100);
        root.layout();
        helper.assertTrue(content.x() == 50 && content.y() == 40, "centered content sat at " + content.x() + "," + content.y());
        helper.assertTrue(fixed.x() == 0 && fixed.width() == 10, "fixed child was " + fixed.x() + "+" + fixed.width());
        helper.assertTrue(single.x() == 12 && single.width() == 29, "fill child was " + single.x() + "+" + single.width());
        helper.assertTrue(twice.x() == 43 && twice.width() == 57 && twice.height() == 20, "weighted fill child was " + twice.x() + "+" + twice.width() + "x" + twice.height());

        content.layout(new FlexLayout(Direction.ROW, 0, Align.CENTER, Justify.SPACE_BETWEEN));
        single.size(10, 4);
        twice.size(10, 6);
        root.layout();
        helper.assertTrue(fixed.x() == 0 && single.x() == 45 && twice.x() == 90, "space between placed " + fixed.x() + "," + single.x() + "," + twice.x());
        helper.assertTrue(single.y() == 8 && twice.y() == 7, "center align placed " + single.y() + "," + twice.y());

        Widget corner = new Widget().size(50, 50);
        Widget badge = corner.add(new Widget().size(10, 10).anchor(Anchor.BOTTOM_RIGHT, -2, -2));
        content.add(corner);
        root.layout();
        helper.assertTrue(badge.x() == 38 && badge.y() == 38, "anchored badge sat at " + badge.x() + "," + badge.y());

        Widget column = new Widget().layout(FlexLayout.column(3)).padding(Insets.of(4));
        column.add(new Label("abc"));
        column.add(new Label("abcdef"));
        UiRoot measured = new UiRoot(column, null, host);
        measured.resize(300, 300);
        measured.layout();
        helper.assertTrue(column.width() == 6 * TestHost.CHAR + 8 && column.height() == 2 * TestHost.LINE + 3 + 8, "column fit to " + column.width() + "x" + column.height());
        helper.succeed();
    }

    private static void binding(GameTestHelper helper) {
        Console console = attachConsole(helper);
        console.power = false;
        console.gain = 0.5f;
        console.locked = 3;
        TestHost host = new TestHost();
        ConsoleScreen screen = new ConsoleScreen();
        UiRoot root = new UiRoot(screen, console, host);
        root.resize(400, 300);
        root.layout();
        helper.assertTrue(!screen.power.on(), "the switch didn't start from the module's value");
        helper.assertTrue(screen.locked.readOnly() && screen.lockedBar.readOnly(), "a field that isn't client mutable was writable");
        helper.assertTrue("3".equals(screen.locked.text()), "the label showed " + screen.locked.text() + " for an int field");

        click(root, screen.power);
        helper.assertTrue(console.power && screen.power.on(), "clicking the switch didn't write the module field");

        root.focus(screen.gain);
        root.key(Keys.RIGHT, 0);
        helper.assertTrue(Math.abs(console.gain - 0.55f) < 1e-4, "the slider wrote " + console.gain + " to a float field");

        root.focus(screen.name);
        for (char character : "probe".toCharArray()) {
            root.character(character, 0);
        }
        helper.assertTrue("probe".equals(console.name), "typing wrote \"" + console.name + "\"");

        console.locked = 7;
        console.name = "remote";
        root.tick();
        helper.assertTrue(screen.changes.equals(List.of(7)), "@OnChange saw " + screen.changes);
        helper.assertTrue("7".equals(screen.locked.text()) && screen.lockedBar.fraction() == 0.7, "a remote change didn't reach the bound widgets");
        helper.assertTrue("probe".equals(screen.name.text()), "a sync overwrote the text field while it was being edited");
        root.focus(null);
        root.tick();
        helper.assertTrue("remote".equals(screen.name.text()), "the text field didn't pick up the synced value after editing");

        console.markDirty("power");
        BitSet gainOnly = new BitSet();
        gainOnly.set(console.type().spec().syncIndex("gain"));
        EmpiricCore.modules().acceptRemote(console, gainOnly);
        helper.assertTrue(console.isDirty("power"), "a sync of another field dropped a pending edit");

        UiPoint gainPoint = screen.gain.toRoot(2, 2);
        root.pointerDown(gainPoint.x(), gainPoint.y(), Keys.RIGHT_BUTTON);
        helper.assertTrue(root.popups().size() == 1 && root.popups().getFirst() instanceof ContextMenu, "right clicking the slider didn't open its context menu");
        UiPoint entry = root.popups().getFirst().toRoot(3, 3);
        root.pointerDown(entry.x(), entry.y(), Keys.LEFT_BUTTON);
        root.pointerUp(entry.x(), entry.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(console.gain == 0 && root.popups().isEmpty(), "the @ContextAction didn't run");

        click(root, screen.reset);
        helper.assertTrue(console.gain == 0 && !console.power, "@OnClick didn't run");
        helper.assertTrue(Boolean.FALSE.equals(screen.powerProperty.get()) && screen.powerProperty.writable(), "the Property field wasn't bound");

        ConsoleView view = CONSOLE_VIEW.attach(console.children());
        ViewScreen viewScreen = new ViewScreen();
        UiRoot viewRoot = new UiRoot(viewScreen, view, host);
        viewRoot.resize(200, 100);
        viewRoot.layout();
        click(viewRoot, viewScreen.power);
        helper.assertTrue(console.power, "a child module's screen didn't reach the parent's field");
        viewScreen.tabs.setValue(1);
        helper.assertTrue(view.page == 1 && !viewScreen.tabs.page(0).isVisible() && viewScreen.tabs.page(1).isVisible(), "binding tabs to the child module didn't switch pages");

        expectFailure(helper, () -> new UiRoot(new BadScreen(), console, host), "binding a missing field");
        expectFailure(helper, () -> new UiRoot(new NotAValue(), console, host), "binding a widget without a value");
        viewRoot.dispose();
        root.dispose();
        CONSOLE.detach(console.attachment());
        helper.succeed();
    }

    private static void input(GameTestHelper helper) {
        TestHost host = new TestHost();
        Widget content = new Widget().layout(FlexLayout.column(2)).size(200, 200);
        TextField field = content.add(new TextField().maxLength(12));
        Button button = content.add(new Button("go"));
        Dial dial = content.add(new Dial(0, 10, 1));
        Cycle<Integer> cycle = content.add(Cycle.indexed("a", "b", "c"));
        Switch toggle = content.add(new Switch());
        ScrollPanel scroll = content.add(new ScrollPanel().size(50, 20));
        for (int i = 0; i < 10; i++) {
            scroll.add(new Label("line"));
        }
        Dropdown<Integer> dropdown = content.add(Dropdown.indexed("x", "y", "z"));
        UiRoot root = new UiRoot(content, null, host);
        root.resize(200, 200);
        root.layout();

        root.focusNext(false);
        helper.assertTrue(root.focused() == field, "tab didn't start at the text field");
        root.key(Keys.TAB, 0);
        helper.assertTrue(root.focused() == button, "tab didn't move to the button");
        root.key(Keys.TAB, Keys.SHIFT);
        helper.assertTrue(root.focused() == field, "shift tab didn't move back");

        typeText(root, "hi you ");
        root.character(0x1F600, 0);
        helper.assertTrue("hi you 😀".equals(field.text()), "typed " + field.text());
        root.key(Keys.BACKSPACE, 0);
        helper.assertTrue("hi you ".equals(field.text()), "backspace split a surrogate pair: " + field.text());
        root.key(Keys.LEFT, Keys.CONTROL);
        helper.assertTrue(field.cursor() == 3, "word jump stopped at " + field.cursor());
        root.key(Keys.END, Keys.SHIFT);
        root.key(Keys.C, Keys.CONTROL);
        helper.assertTrue("you ".equals(host.clipboard), "copied \"" + host.clipboard + "\"");
        root.key(Keys.A, Keys.CONTROL);
        host.clipboard = "a long pasted line";
        root.key(Keys.V, Keys.CONTROL);
        helper.assertTrue("a long paste".equals(field.text()), "max length let through \"" + field.text() + "\"");
        root.key(Keys.ESCAPE, 0);
        helper.assertTrue(root.focused() == null, "escape didn't drop focus");

        UiPoint dialPoint = dial.toRoot(dial.width() / 2.0, dial.height() / 2.0);
        root.scroll(dialPoint.x(), dialPoint.y(), 1);
        root.scroll(dialPoint.x(), dialPoint.y(), 1);
        helper.assertTrue(dial.amount() == 2, "scrolling the dial gave " + dial.amount());
        helper.assertTrue(host.sounds.contains("minecraft:block.wooden_button.click_on"), "the dial didn't click");

        UiPoint right = dial.toRoot(dial.width() - 0.5, dial.height() / 2.0);
        UiPoint below = dial.toRoot(dial.width() / 2.0, dial.height() - 0.5);
        root.pointerDown(right.x(), right.y(), Keys.LEFT_BUTTON);
        root.pointerDrag(below.x(), below.y(), below.x() - right.x(), below.y() - right.y(), Keys.LEFT_BUTTON);
        root.pointerUp(below.x(), below.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(Math.abs(dial.amount() - (2 + 10 / 3.0)) < 0.51, "a quarter turn moved the dial to " + dial.amount());

        click(root, cycle);
        helper.assertTrue(cycle.value() == 1, "the cycle didn't step");
        UiPoint cyclePoint = cycle.toRoot(2, 2);
        root.pointerDown(cyclePoint.x(), cyclePoint.y(), Keys.RIGHT_BUTTON);
        root.pointerUp(cyclePoint.x(), cyclePoint.y(), Keys.RIGHT_BUTTON);
        helper.assertTrue(cycle.value() == 0, "right click didn't step back");

        int[] presses = {0};
        button.onPress(() -> presses[0]++);
        UiPoint buttonPoint = button.toRoot(2, 2);
        root.pointerDown(buttonPoint.x(), buttonPoint.y(), Keys.LEFT_BUTTON);
        root.pointerUp(buttonPoint.x() + 500, buttonPoint.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(presses[0] == 0, "releasing off the button still pressed it");
        click(root, button);
        helper.assertTrue(presses[0] == 1, "the button didn't press");

        click(root, toggle);
        helper.assertTrue(toggle.on(), "the switch didn't toggle");
        toggle.enabled(false);
        click(root, toggle);
        helper.assertTrue(toggle.on(), "a disabled switch toggled");

        UiPoint scrollPoint = scroll.toRoot(5, 5);
        root.scroll(scrollPoint.x(), scrollPoint.y(), -1);
        helper.assertTrue(scroll.scrollOffset() > 0, "the scroll panel didn't scroll");
        Widget hit = root.pick(scrollPoint.x(), scrollPoint.y());
        helper.assertTrue(hit == scroll, "picked " + hit + " inside the scroll panel");
        root.scroll(scrollPoint.x(), scrollPoint.y(), -100);
        helper.assertTrue(scroll.scrollOffset() == scroll.maxScroll(), "scrolling past the end gave " + scroll.scrollOffset());

        click(root, dropdown);
        helper.assertTrue(dropdown.isOpen() && root.popups().size() == 1, "clicking the dropdown didn't open its list");
        Widget popup = root.popups().getFirst();
        UiPoint second = popup.toRoot(3, 1 + TestHost.LINE + 4 + 2);
        root.pointerDown(second.x(), second.y(), Keys.LEFT_BUTTON);
        root.pointerUp(second.x(), second.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(dropdown.value() == 1 && !dropdown.isOpen() && root.popups().isEmpty(), "choosing an option gave " + dropdown.value());
        click(root, dropdown);
        root.pointerDown(199, 1, Keys.LEFT_BUTTON);
        root.pointerUp(199, 1, Keys.LEFT_BUTTON);
        helper.assertTrue(!dropdown.isOpen(), "clicking outside didn't close the dropdown");
        root.focus(dropdown);
        root.key(Keys.DOWN, 0);
        helper.assertTrue(dropdown.value() == 2, "down didn't step the dropdown");
        root.focus(null);

        ColorPicker picker = content.add(new ColorPicker(0xFF000000));
        Graph graph = content.add(new Graph(10));
        root.layout();
        click(root, picker);
        helper.assertTrue(picker.isOpen(), "the colour picker didn't open");
        Widget area = root.popups().getFirst().children().getFirst();
        UiPoint topRight = area.toRoot(area.width() - 0.5, 0);
        root.pointerDown(topRight.x(), topRight.y(), Keys.LEFT_BUTTON);
        root.pointerUp(topRight.x(), topRight.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(picker.argb() == 0xFFFF0000, "the top right of a red hue gave " + Integer.toHexString(picker.argb()));
        float[] hsv = ColorPicker.toHsv(0xFF3FA866);
        int back = ColorPicker.toRgb(hsv[0], hsv[1], hsv[2]);
        helper.assertTrue(Math.abs((back >> 16 & 0xFF) - 0x3F) <= 1 && Math.abs((back >> 8 & 0xFF) - 0xA8) <= 1 && Math.abs((back & 0xFF) - 0x66) <= 1, "HSV round tripped to " + Integer.toHexString(back));
        root.pointerDown(199, 199, Keys.LEFT_BUTTON);
        helper.assertTrue(!picker.isOpen() && !picker.isEditing(), "closing the colour picker left it editing");
        graph.setValue(0.5);
        for (int i = 0; i < 12; i++) {
            root.tick();
        }
        helper.assertTrue(graph.samples() == 10 && graph.sample(0) == 0.5, "the graph kept " + graph.samples() + " samples");
        helper.succeed();
    }

    private static void openClose(GameTestHelper helper) {
        Console console = attachConsole(helper);
        ServerPlayer player = playerAt(helper);
        helper.assertTrue(UiSystem.open(player, CONSOLE_SCREEN, console), "the console screen didn't open");
        helper.assertTrue(UiSystem.viewers(console).size() == 1 && console.opened == 1, "the viewer wasn't tracked");

        ServerPlayer other = playerAt(helper);
        console.allow = false;
        helper.assertTrue(!UiSystem.open(other, CONSOLE_SCREEN, console), "canOpen didn't stop a second player");
        console.allow = true;
        helper.assertTrue(UiSystem.open(other, CONSOLE_SCREEN, console) && UiSystem.viewers(console).size() == 2, "two players couldn't share the screen");

        UiSystem.Target target = WorldModuleSystem.withLevel(helper.getLevel(), () -> UiSystem.decode(UiSystem.encode(CONSOLE_SCREEN, console), Side.SERVER));
        helper.assertTrue(target != null && target.module() == console && target.screen() == CONSOLE_SCREEN, "the screen target didn't round trip");

        UiSystem.close(player);
        helper.assertTrue(UiSystem.viewers(console).size() == 1 && console.closed == 1, "closing didn't drop the viewer");
        UiSystem.handleOpen(player, new UiOpenC2SPacket(UiSystem.encode(CONSOLE_SCREEN, console)));
        helper.assertTrue(UiSystem.viewers(console).size() == 2, "a client request didn't open the screen");

        player.teleportTo(player.getX() + 500, player.getY(), player.getZ());
        for (int i = 0; i < 20; i++) {
            UiSystem.tick(helper.getLevel().getServer());
        }
        helper.assertTrue(UiSystem.viewers(console).size() == 1, "an out of range viewer was kept");
        other.discard();
        for (int i = 0; i < 20; i++) {
            UiSystem.tick(helper.getLevel().getServer());
        }
        helper.assertTrue(UiSystem.viewers(console).isEmpty(), "a removed player stayed a viewer");

        UiSystem.open(player, CONSOLE_SCREEN, console);
        player.teleportTo(player.getX() - 500, player.getY(), player.getZ());
        UiSystem.open(player, CONSOLE_SCREEN, console);
        helper.assertTrue(UiSystem.viewers(console).size() == 1, "reopening counted the player twice");
        CONSOLE.detach(console.attachment());
        helper.assertTrue(UiSystem.viewers(console).isEmpty(), "detaching the module kept its viewers");
        player.discard();
        helper.succeed();
    }

    private static void menu(GameTestHelper helper) {
        Console console = attachConsole(helper);
        ServerPlayer player = playerAt(helper);
        helper.assertTrue(UiSystem.open(player, CONSOLE_MENU, console), "the menu screen didn't open");
        helper.assertTrue(player.containerMenu instanceof UiMenu, "a screen with slots didn't open a menu");
        UiMenu menu = (UiMenu) player.containerMenu;
        helper.assertTrue(menu.slots.size() == 4 + 36 && UiSystem.viewers(console).size() == 1, "the menu had " + menu.slots.size() + " slots");

        console.items.set(0, new ItemStack(Items.DIAMOND, 5));
        menu.quickMoveStack(player, 0);
        helper.assertTrue(console.items.get(0).isEmpty() && player.getInventory().countItem(Items.DIAMOND) == 5, "shift-click didn't move the stack to the player");
        helper.assertTrue(console.isDirty("items"), "moving items out didn't mark the module field dirty");
        int held = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container instanceof Inventory && menu.slots.get(i).getItem().is(Items.DIAMOND)) {
                held = i;
            }
        }
        menu.quickMoveStack(player, held);
        helper.assertTrue(console.items.get(0).getCount() == 5, "shift-click didn't move the stack back into the module");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(3, 1, ClickType.PICKUP, player);
        helper.assertTrue(console.items.get(3).getCount() == 1 && menu.getCarried().getCount() == 4, "right click didn't place one item");
        menu.clicked(0, 0, ClickType.PICKUP, player);

        player.closeContainer();
        helper.assertTrue(UiSystem.viewers(console).isEmpty(), "closing the menu kept the viewer");
        UiSystem.open(player, CONSOLE_MENU, console);
        UiMenu reopened = (UiMenu) player.containerMenu;
        CONSOLE.detach(console.attachment());
        helper.assertTrue(!reopened.stillValid(player) && UiSystem.viewers(console).isEmpty(), "the menu stayed valid after its module was detached");
        player.closeContainer();
        player.discard();
        helper.succeed();
    }

    private static void worldSlots(GameTestHelper helper) {
        Console console = attachConsole(helper);
        ServerPlayer player = playerAt(helper);
        Collections.fill(console.items, ItemStack.EMPTY);
        byte[] target = UiSystem.encode(CONSOLE_MENU, console);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 10));

        helper.assertTrue(UiSystem.slotClick(player, target, 0, Keys.RIGHT_BUTTON), "using a slot with an item didn't take");
        helper.assertTrue(console.items.get(0).getCount() == 1 && player.getMainHandItem().getCount() == 9, "using a slot didn't put one item in");
        player.setShiftKeyDown(true);
        UiSystem.slotClick(player, target, 0, Keys.RIGHT_BUTTON);
        helper.assertTrue(console.items.get(0).getCount() == 10 && player.getMainHandItem().isEmpty(), "sneak-using didn't put the whole stack in");
        player.setShiftKeyDown(false);

        UiSystem.slotClick(player, target, 0, Keys.LEFT_BUTTON);
        helper.assertTrue(console.items.get(0).getCount() == 9 && player.getInventory().countItem(Items.DIAMOND) == 1, "attacking a slot didn't take one item out");
        player.getInventory().clearContent();
        UiSystem.slotClick(player, target, 0, Keys.RIGHT_BUTTON);
        helper.assertTrue(console.items.get(0).isEmpty() && player.getMainHandItem().getCount() == 9, "using a slot with an empty hand didn't take the stack");

        console.items.set(1, new ItemStack(Items.DIRT));
        helper.assertTrue(!UiSystem.slotClick(player, target, 1, Keys.RIGHT_BUTTON) && console.items.get(1).is(Items.DIRT), "a different item went into an occupied slot");
        helper.assertTrue(!UiSystem.slotClick(player, target, 4, Keys.RIGHT_BUTTON), "the player's inventory counted as surface slots");
        Collections.fill(console.items, ItemStack.EMPTY);
        player.discard();
        helper.succeed();
    }

    static final String TEST_LAYOUT = """
            {"type": "panel", "id": "main", "layout": "column", "gap": 4, "padding": [6, 4], "children": [
              {"type": "switch", "id": "power", "bind": "power"},
              {"type": "slider", "id": "gain", "bind": "gain", "min": 0, "max": 10, "step": 0.5, "width": "fill"},
              {"type": "label", "id": "echo", "bind": "gain", "format": "%.1f"},
              {"type": "button", "id": "reset", "text": "Reset", "on_click": [
                {"action": "set", "field": "gain", "value": 2},
                {"action": "toggle", "field": "power"}]},
              {"type": "slot_grid", "bind": "items", "columns": 2, "count": 4}
            ]}""";

    private static void blueprint(GameTestHelper helper) {
        BlueprintNode node = BlueprintNode.parse(JsonNodes.toNode(JsonParser.parseString(TEST_LAYOUT)));
        helper.assertTrue(BlueprintNode.parse(node.write()).write().equals(node.write()), "a layout changed after writing and reading it back");
        helper.assertTrue(UiLayouts.get("empiric:demo_panel") != null, "the demo layout file didn't load");
        UiLayouts.override("empiric:test_blueprint", node);
        Console console = attachConsole(helper);
        console.power = false;
        console.gain = 3;

        UiRoot root = new UiRoot(new BlueprintView("empiric:test_blueprint"), console, new TestHost());
        root.resize(200, 200);
        root.tick();
        root.layout();
        Widget main = root.content().children().getFirst();
        helper.assertTrue(main instanceof Panel && main.children().size() == 5 && main.padding().left() == 6 && main.padding().top() == 4, "the layout file built " + main.getClass().getSimpleName() + " with " + main.children().size() + " children and padding " + main.padding());
        Slider gain = (Slider) byId(main, "gain");
        Label echo = (Label) byId(main, "echo");
        helper.assertTrue(gain.amount() == 3 && echo.text().equals("3.0"), "bound widgets showed " + gain.amount() + " and " + echo.text());
        helper.assertTrue(gain.width() == main.width() - 12, "a fill width wasn't applied");
        ((Button) byId(main, "reset")).press();
        helper.assertTrue(console.gain == 2 && console.power && console.isDirty("gain"), "the button's actions didn't change the fields");
        helper.assertTrue(UiMenu.slots(root.content()).size() == 4, "the slot grid didn't build its slots");
        root.dispose();

        LayoutScreen screen = new LayoutScreen();
        UiRoot controlled = new UiRoot(screen, console, new TestHost());
        helper.assertTrue(screen.gain != null && screen.label == byId(screen, "echo"), "@Ref fields weren't filled from the layout");
        ((Button) byId(screen, "reset")).press();
        helper.assertTrue(screen.resets == 1 && !console.power, "@OnClick didn't run beside the file's actions");
        controlled.dispose();

        List<String> problems = new ArrayList<>();
        BlueprintNode bad = new BlueprintNode(BlueprintNode.DEFAULT_TYPE)
                .add(new BlueprintNode("no_such_widget"))
                .add(new BlueprintNode("slider").prop("bind", DataNode.of("missing")));
        Widget built = Blueprints.build(bad, Blueprints.Context.lenient(console, problems::add));
        helper.assertTrue(problems.size() == 2 && built.children().size() == 1, "a lenient build reported " + problems);
        boolean threw = false;
        try {
            Blueprints.build(bad, Blueprints.Context.strict(console));
        } catch (UiException e) {
            threw = true;
        }
        helper.assertTrue(threw, "a strict build accepted an unknown widget type");
        helper.succeed();
    }

    private static void nodeGraph(GameTestHelper helper) {
        NodeGraph data = new NodeGraph();
        GraphNode one = data.add("constant", 0, 0);
        GraphNode two = data.add("constant", 0, 60);
        GraphNode sum = data.add("add", 120, 0);
        helper.assertTrue(data.connect(one.id, "out", sum.id, "a") && data.connect(two.id, "out", sum.id, "a"), "linking failed");
        helper.assertTrue(data.links.size() == 1 && data.linkInto(sum.id, "a").from == two.id, "a second link into an input didn't replace the first");
        helper.assertTrue(data.reaches(two.id, sum.id) && !data.reaches(sum.id, two.id), "reachability was wrong");

        NodeGraph dirty = new NodeGraph();
        dirty.nodes.add(new GraphNode(1, "constant", 0, 0));
        dirty.nodes.add(new GraphNode(1, "add", 0, 0));
        dirty.nodes.add(new GraphNode(2, "no_such_node", 0, 0));
        dirty.nodes.add(new GraphNode(3, "toggle", 0, 0));
        dirty.nodes.add(new GraphNode(4, "add", 0, 0));
        dirty.nodes.add(new GraphNode(5, "add", 0, 0));
        dirty.links.add(new GraphLink(3, "on", 4, "a"));
        dirty.links.add(new GraphLink(1, "out", 4, "a"));
        dirty.links.add(new GraphLink(1, "out", 9, "a"));
        dirty.links.add(new GraphLink(4, "sum", 5, "a"));
        dirty.links.add(new GraphLink(5, "sum", 4, "b"));
        helper.assertTrue(dirty.sanitize(UiProofScreens.MATH), "sanitizing changed nothing");
        helper.assertTrue(dirty.nodes.size() == 4 && dirty.links.size() == 2 && dirty.linkInto(4, "a").from == 1 && dirty.linkInto(4, "b") == null,
                "sanitizing left " + dirty.nodes.size() + " nodes and links " + dirty.links.stream().map(GraphLink::key).toList());

        GraphPalette optional = GraphPalette.get("test:optional");
        if (optional == null) {
            optional = GraphPalette.register("test:optional")
                    .add(GraphNodeType.of("source").output("out", "x"))
                    .add(GraphNodeType.of("sink").optionalInput("fed", "Fed", "x", "fed.on"));
        }
        NodeGraph fed = new NodeGraph();
        GraphNode feeder = fed.add("source", 0, 0);
        GraphNode sink = fed.add("sink", 100, 0);
        fed.links.add(new GraphLink(feeder.id, "out", sink.id, "fed"));
        helper.assertTrue(sink.type.equals("sink") && optional.type("sink").inputs(sink).isEmpty(), "an optional port showed while off");
        helper.assertTrue(fed.sanitize(optional) && fed.links.isEmpty(), "a link into a hidden port survived");
        sink.set("fed.on", true);
        fed.links.add(new GraphLink(feeder.id, "out", sink.id, "fed"));
        helper.assertTrue(optional.type("sink").inputs(sink).size() == 1 && !fed.sanitize(optional) && fed.links.size() == 1, "a link into a shown optional port was dropped");

        ObjectSpec<NodeGraph> spec = EmpiricCore.modules().specs().objectSpec(NodeGraph.class);
        TreeDataBuffer writer = TreeDataBuffer.writer();
        spec.write(writer, data, EmpiricCore.modules().specs().context(Encoding.KEYED));
        NodeGraph read = new NodeGraph();
        spec.update(TreeDataBuffer.reader(writer.result()), read, EmpiricCore.modules().specs().context(Encoding.KEYED));
        helper.assertTrue(read.nodes.size() == 3 && read.links.equals(data.links) && read.node(sum.id).type.equals("add"), "a graph didn't survive writing and reading");

        Console console = attachConsole(helper);
        console.graph = data;
        NodeGraphView view = new NodeGraphView(UiProofScreens.MATH);
        view.bind(UiBinder.property(console, "graph", NodeGraph.class, "test"));
        UiRoot root = new UiRoot(view, console, new TestHost());
        root.resize(300, 200);
        root.tick();
        root.layout();
        helper.assertTrue(view.nodeWidget(one.id) != null && view.linkWidgets().size() == 1, "the view didn't build widgets for the graph");

        NodeWidget source = view.nodeWidget(one.id);
        NodeWidget target = view.nodeWidget(sum.id);
        UiPoint from = source.portPoint(source.type().output("out"), false);
        UiPoint to = target.portPoint(target.type().input("b"), true);
        root.pointerDown(from.x() - 1, from.y(), Keys.LEFT_BUTTON);
        root.pointerDrag(to.x() + 1, to.y(), to.x() - from.x(), to.y() - from.y(), Keys.LEFT_BUTTON);
        root.pointerUp(to.x() + 1, to.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(console.graph.linkInto(sum.id, "b") != null && console.isDirty("graph"), "dragging between ports didn't link them");

        root.pointerDown(source.x() + 10, source.y() + 4, Keys.LEFT_BUTTON);
        root.pointerDrag(source.x() + 50, source.y() + 24, 40, 20, Keys.LEFT_BUTTON);
        root.pointerUp(source.x() + 50, source.y() + 24, Keys.LEFT_BUTTON);
        helper.assertTrue(console.graph.node(one.id).x == 40 && console.graph.node(one.id).y == 20, "dragging a header moved the node to "
                + console.graph.node(one.id).x + "," + console.graph.node(one.id).y);

        NodeGraph remote = new NodeGraph();
        remote.nodes.add(new GraphNode(sum.id, "add", 5, 5));
        console.graph = remote;
        root.tick();
        root.layout();
        helper.assertTrue(view.nodeWidget(one.id) == null && view.nodeWidget(sum.id) != null && view.linkWidgets().isEmpty() && view.nodeWidget(sum.id).x() == 5,
                "the view didn't follow a replaced graph");

        root.pointerDown(40, 170, Keys.RIGHT_BUTTON);
        Widget first = root.popups().isEmpty() ? null : root.popups().getFirst();
        root.pointerDown(250, 150, Keys.RIGHT_BUTTON);
        helper.assertTrue(first instanceof NodePicker && root.popups().size() == 1 && root.popups().getFirst() != first,
                "a right click outside an open picker didn't move it in one click");
        helper.assertTrue(root.popups().getFirst() instanceof NodePicker picker && root.focused() == picker.search(),
                "a right click on empty space didn't open a focused node picker");
        for (char c : "mult".toCharArray()) {
            root.character(c, 0);
        }
        root.key(Keys.ENTER, 0);
        GraphNode placed = console.graph.nodes.getLast();
        helper.assertTrue(root.popups().isEmpty() && placed.type.equals("multiply") && placed.x == 252 && placed.y == 152,
                "the picker placed " + placed.type + " at " + placed.x + "," + placed.y);
        root.pointerDown(0, 0, Keys.LEFT_BUTTON);
        root.pointerUp(0, 0, Keys.LEFT_BUTTON);
        view.view(0, 0, 0.5f);
        root.layout();
        NodeWidget zoomed = view.nodeWidget(sum.id);
        float zoomedX = console.graph.node(sum.id).x;
        UiPoint headerPoint = view.toRoot(view.panX() + (zoomed.x() + 10) * view.zoom(), view.panY() + (zoomed.y() + 4) * view.zoom());
        root.pointerDown(headerPoint.x(), headerPoint.y(), Keys.LEFT_BUTTON);
        root.pointerDrag(headerPoint.x() + 20, headerPoint.y(), 20, 0, Keys.LEFT_BUTTON);
        root.pointerUp(headerPoint.x() + 20, headerPoint.y(), Keys.LEFT_BUTTON);
        helper.assertTrue(Math.abs(console.graph.node(sum.id).x - (zoomedX + 40)) <= 4, "a drag at half zoom moved the node by "
                + (console.graph.node(sum.id).x - zoomedX) + " instead of following the pointer");
        helper.assertTrue(console.graph.node(sum.id).x % view.grid() == 0 && console.graph.node(sum.id).y % view.grid() == 0, "a dragged node didn't land on the grid");
        view.view(0, 0, 1);
        root.layout();

        Label wrapped = new Label("alpha beta gamma delta epsilon zeta").wrap(true);
        Widget narrow = new Widget().layout(FlexLayout.column(0).align(Align.STRETCH));
        narrow.width(Length.px(6 * TestHost.CHAR));
        narrow.add(wrapped);
        UiRoot wrapRoot = new UiRoot(narrow, null, new TestHost());
        wrapRoot.resize(300, 300);
        wrapRoot.layout();
        wrapRoot.layout();
        helper.assertTrue(wrapped.height() > TestHost.LINE, "a wrapped label in a stretched column stayed one line tall: " + wrapped.height());
        wrapRoot.dispose();

        root.dispose();
        console.graph = new NodeGraph();
        helper.succeed();
    }

    private static Widget byId(Widget widget, String id) {
        if (widget.id().equals(id)) {
            return widget;
        }
        for (Widget child : widget.children()) {
            Widget found = byId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Layout("empiric:test_blueprint")
    static class LayoutScreen extends Widget {

        @Ref
        Slider gain;
        @Ref("echo")
        Label label;
        int resets;

        @OnClick("reset")
        void reset() {
            resets++;
        }
    }

    private static void theme(GameTestHelper helper) {
        UiTheme loaded = UiThemes.THEMES.get(UiTheme.DEFAULT);
        helper.assertTrue(loaded != null && loaded.colors.containsKey("accent") && loaded.color("accent") == 0xFF4651D2, "the default theme didn't load from data");
        String json = "{\"colors\": {\"accent\": \"#80112233\"}, \"sprites\": {\"panel\": {\"texture\": \"empiric:textures/gui/panel.png\", \"width\": 32, \"height\": 32, \"border\": 4}}, \"sounds\": {}}";
        UiTheme custom = UiThemes.THEMES.parse("empiric:custom", JsonNodes.toNode(JsonParser.parseString(json))).value();
        helper.assertTrue(custom.color("accent") == 0x80112233, "parsed accent was " + Integer.toHexString(custom.color("accent")));
        helper.assertTrue(custom.color("text") == UiTheme.FALLBACK.color("text"), "a missing colour didn't fall back");
        UiSprite panel = custom.sprite("panel");
        helper.assertTrue(panel != null && panel.border == 4 && panel.width == 32 && panel.textureWidth == 256, "the sprite didn't parse");
        helper.assertTrue("minecraft:ui.button.click".equals(custom.sound("click")), "a missing sound didn't fall back");
        helper.assertTrue(UiThemes.get("empiric:missing") == loaded, "an unknown theme didn't fall back to the default");
        helper.succeed();
    }

    private static void surface(GameTestHelper helper) {
        // 4 blocks wide and 2 high, 200 by 100 pixels, hung at z=10 for a viewer standing at the origin looking south (+z).
        SurfacePlane plane = new SurfacePlane(2, 1, 10, -4, 0, 0, 0, -2, 0, 200, 100);
        SurfacePlane.Hit middle = plane.intersect(0, 0, 0, 0, 0, 1);
        helper.assertTrue(middle != null && middle.inside(), "a ray through the middle missed");
        helper.assertTrue(Math.abs(middle.x() - 100) < 1 && Math.abs(middle.y() - 50) < 1, "the middle ray landed at " + middle.x() + ", " + middle.y());
        SurfacePlane.Hit corner = plane.intersect(0, 0, 0, 1.9 / 10, 0.9 / 10, 1);
        helper.assertTrue(corner != null && corner.inside() && corner.x() < 6 && corner.y() < 6, "the top left of the surface was at " + corner);
        SurfacePlane.Hit right = plane.intersect(0, 0, 0, -1.5 / 10, 0, 1);
        helper.assertTrue(right != null && Math.abs(right.x() - 175) < 1, "moving the ray to the viewer's right should raise x: " + right);
        helper.assertTrue(Math.abs(corner.distance() - 10) < 0.1, "the distance was " + corner.distance());
        SurfacePlane.Hit outside = plane.intersect(0, 0, 0, 3.0 / 10, 0, 1);
        helper.assertTrue(outside != null && !outside.inside() && outside.x() < 0, "a ray past the left edge should be outside with x below 0");
        helper.assertTrue(plane.intersect(0, 0, 20, 0, 0, 1) == null, "a ray pointing away hit the surface");
        helper.assertTrue(plane.intersect(0, 0, 20, 0, 0, -1) == null, "a ray at the back of the surface hit it");
        helper.assertTrue(plane.facesPoint(0, 0, 0) && !plane.facesPoint(0, 0, 20), "the surface faced the wrong side");
        double[] topLeft = plane.toWorld(0, 0);
        double[] bottomRight = plane.toWorld(200, 100);
        helper.assertTrue(topLeft[0] == 2 && topLeft[1] == 1 && bottomRight[0] == -2 && bottomRight[1] == -1, "corners didn't map back to the world");
        helper.succeed();
    }

    private static void tooltip(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper);
        EmpiricCommands.ensureEnabled(player.createCommandSourceStack().withSuppressedOutput(), UiDemo.GAUGE);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        UiDemo.Gauge gauge = UiDemo.GAUGE.attach(helper.getLevel(), pos);
        helper.assertTrue(TooltipFields.has(UiDemo.Gauge.class) && TooltipContent.provides(gauge, player), "the gauge didn't offer a tooltip");
        TooltipContent content = TooltipContent.of(List.of(gauge), player);
        UiRoot root = new UiRoot(content, gauge, new TestHost());
        root.resize(content.measuredWidth(), content.measuredHeight());
        root.layout();
        helper.assertTrue(content.children().size() == 3, "expected a title, one form and a button, got " + content.children().size());
        helper.assertTrue(content.children().get(1).children().size() == 10, "expected five label and control pairs in the form");
        List<Widget> form = content.children().get(1).children();
        for (int i = 2; i < form.size(); i += 2) {
            helper.assertTrue(form.get(i).x() == form.get(0).x(), "form labels didn't share a column");
            helper.assertTrue(form.get(i + 1).x() == form.get(1).x() && form.get(i + 1).width() == form.get(1).width(), "form controls didn't share a column");
            helper.assertTrue(form.get(i + 1).x() >= form.get(i).x() + form.get(i).width(), "a control overlapped its label");
        }
        helper.assertTrue(content.children().getFirst().x() >= 6 && content.children().getFirst().y() >= 6, "the content didn't respect the panel padding");

        TextField name = first(content, TextField.class);
        Switch running = first(content, Switch.class);
        Slider rate = first(content, Slider.class);
        helper.assertTrue(name != null && "Gauge".equals(name.value()), "the name row didn't bind");
        helper.assertTrue(running != null && Boolean.TRUE.equals(running.value()), "the running row didn't bind");
        helper.assertTrue(rate != null && rate.amount() == 2, "the rate row didn't bind");

        click(root, running);
        helper.assertTrue(!gauge.running && gauge.isDirty("running"), "toggling the switch didn't edit the field");
        gauge.count = 42.4;
        root.tick();
        boolean shown = false;
        for (Label label : all(content, Label.class)) {
            shown |= "42".equals(label.value());
        }
        helper.assertTrue(shown, "the count row didn't show the formatted value");
        gauge.rate = 5;
        click(root, first(content, Button.class));
        helper.assertTrue(gauge.rate == 2, "the reset button didn't set the rate, it was " + gauge.rate);
        root.dispose();
        UiDemo.GAUGE.detach(helper.getLevel(), pos);
        player.discard();
        helper.succeed();
    }

    static <W extends Widget> W first(Widget widget, Class<W> type) {
        List<W> found = all(widget, type);
        return found.isEmpty() ? null : found.getFirst();
    }

    static <W extends Widget> List<W> all(Widget widget, Class<W> type) {
        List<W> found = new ArrayList<>();
        collect(widget, type, found);
        return found;
    }

    private static <W extends Widget> void collect(Widget widget, Class<W> type, List<W> found) {
        if (type.isInstance(widget)) {
            found.add(type.cast(widget));
        }
        for (Widget child : widget.children()) {
            collect(child, type, found);
        }
    }

    private static void commands(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper);
        Console console = attachConsole(helper);
        command(helper, player, "empiric ui demo");
        UiDemo.State state = UiDemo.TYPE.get(helper.getLevel().getServer());
        helper.assertTrue(state != null && UiSystem.viewers(state).size() == 1, "/empiric ui demo didn't open the demo");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        command(helper, player, "empiric ui open empiric:test_console " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        helper.assertTrue(UiSystem.viewers(console).size() == 1 && UiSystem.viewers(state).isEmpty(), "/empiric ui open didn't move the player to the console");
        helper.assertTrue(command(helper, player, "empiric ui") >= 1, "/empiric ui listed no open screens");
        command(helper, player, "empiric ui close");
        helper.assertTrue(UiSystem.viewers(console).isEmpty(), "/empiric ui close left the viewer");

        UiRoot root = new UiRoot(new UiDemo.DemoScreen(), state, new TestHost());
        root.resize(400, 300);
        root.layout();
        root.tick();
        helper.assertTrue(root.content().width() == 240 && root.content().height() > 150, "the demo screen measured " + root.content().width() + "x" + root.content().height());
        root.dispose();
        UiDemo.TYPE.detach(helper.getLevel().getServer());
        DebugSubscriptionSystem.unsubscribe(UiDemo.TYPE);
        CONSOLE.detach(console.attachment());
        player.discard();
        helper.succeed();
    }

    static Console attachConsole(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        ChunkAttachment attachment = ChunkAttachment.of(helper.getLevel().getChunkAt(origin));
        Console existing = CONSOLE.get(attachment);
        return existing != null ? existing : CONSOLE.attach(attachment);
    }

    private static ServerPlayer playerAt(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(origin.getX(), origin.getY(), origin.getZ());
        return player;
    }

    private static int command(GameTestHelper helper, ServerPlayer player, String command) {
        ServerLevel level = helper.getLevel();
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4).withSuppressedOutput());
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("/" + command + " failed: " + e.getMessage(), e);
        }
    }

    private static void click(UiRoot root, Widget widget) {
        UiPoint point = widget.toRoot(1, 1);
        root.pointerDown(point.x(), point.y(), Keys.LEFT_BUTTON);
        root.pointerUp(point.x(), point.y(), Keys.LEFT_BUTTON);
    }

    static void typeText(UiRoot root, String text) {
        text.codePoints().forEach(codepoint -> root.character(codepoint, 0));
    }

    private static void expectFailure(GameTestHelper helper, Runnable action, String what) {
        try {
            action.run();
        } catch (UiException expected) {
            return;
        }
        helper.fail(what + " didn't throw");
    }

    static final class TestHost implements UiHost {

        static final int CHAR = 6;
        static final int LINE = 9;
        private static final UiFont FONT = new UiFont() {
            @Override
            public int width(String text) {
                return text.codePointCount(0, text.length()) * CHAR;
            }

            @Override
            public int lineHeight() {
                return LINE;
            }
        };

        String clipboard = "";
        final List<String> sounds = new ArrayList<>();

        @Override
        public UiFont font() {
            return FONT;
        }

        @Override
        public String clipboard() {
            return clipboard;
        }

        @Override
        public void setClipboard(String text) {
            clipboard = text;
        }

        @Override
        public void playSound(String id, float pitch) {
            sounds.add(id);
        }

        @Override
        public UiTheme theme(String id) {
            return UiTheme.FALLBACK;
        }

        @Override
        public void close() {
        }
    }

    public static class Console extends Module<ChunkAttachment> implements UiBehavior {

        public boolean allow = true;
        public int opened;
        public int closed;

        @Data(clientMutable = true)
        public boolean power;
        @Data(clientMutable = true)
        public float gain;
        @Data(clientMutable = true)
        public String name = "";
        @Data
        public int locked;
        @Data
        public List<ItemStack> items = new ArrayList<>(Collections.nCopies(4, ItemStack.EMPTY));
        @Data(clientMutable = true)
        public NodeGraph graph = new NodeGraph();

        @Override
        public boolean canOpen(ServerPlayer player, UiScreenType<?> screen) {
            return allow && inRange(player);
        }

        @Override
        public void onOpened(ServerPlayer player, UiScreenType<?> screen) {
            opened++;
        }

        @Override
        public void onClosed(ServerPlayer player, UiScreenType<?> screen) {
            closed++;
        }
    }

    public static class ConsoleView extends Module<ModuleAttachment> {

        @Data(clientMutable = true)
        public int page;
    }

    @Flex(value = Direction.COLUMN, gap = 2)
    @Padding(4)
    static class ConsoleScreen extends Widget {

        final List<Integer> changes = new ArrayList<>();

        @Bind("power")
        Switch power = new Switch();
        @Bind("gain")
        Slider gain = new Slider(0, 1, 0.05);
        @Bind("name")
        TextField name = new TextField();
        @Bind("locked")
        Label locked = new Label();
        @Bind("locked")
        @Size(width = 50)
        ProgressBar lockedBar = new ProgressBar(0, 10);
        @Child
        Button reset = new Button("reset");
        @Bind("power")
        Property<Object> powerProperty;

        @ContextAction(value = "Zero gain", target = "gain")
        void zeroGain() {
            gain.setValue(0.0);
        }

        @OnClick("reset")
        void reset() {
            gain.setValue(0.0);
            power.setValue(false);
        }

        @OnChange("locked")
        void lockedChanged(int value) {
            changes.add(value);
        }
    }

    @Flex(value = Direction.COLUMN)
    static class ViewScreen extends Widget {

        @Bind("power")
        Switch power = new Switch();
        @Bind("page")
        Tabs tabs = new Tabs();

        ViewScreen() {
            tabs.add(new Label("first").id("One"));
            tabs.add(new Label("second").id("Two"));
        }
    }

    @Flex(value = Direction.COLUMN, gap = 4)
    static class ConsoleMenuScreen extends Widget {

        @Bind("items")
        SlotGrid items = new SlotGrid(4, 4);
        @Child
        PlayerInventoryGrid inventory = new PlayerInventoryGrid();
    }

    static class BadScreen extends Widget {

        @Bind("missing")
        Switch missing = new Switch();
    }

    static class NotAValue extends Widget {

        @Bind("power")
        Button button = new Button("no value");
    }
}
