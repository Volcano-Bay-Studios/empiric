package xyz.volcanobay.empiric.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import xyz.volcanobay.empiric.core.ui.Keys;
import xyz.volcanobay.empiric.core.ui.UiFont;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.widget.RichTextEditor;
import xyz.volcanobay.empiric.core.ui.text.StyledText;
import xyz.volcanobay.empiric.core.ui.text.TextAlign;
import xyz.volcanobay.empiric.core.ui.text.TextLayout;
import xyz.volcanobay.empiric.core.ui.text.TextSelection;

/**
 * Game tests for the shared text path: styled text, layout and selection, on the fixed-width test font (6 wide, 9 tall).
 */
final class UiTextGameTests {

    private UiTextGameTests() {
    }

    private static UiFont font() {
        return new UiGameTests.TestHost().font();
    }

    private static void expect(GameTestHelper helper, boolean condition, String what) {
        if (!condition) {
            helper.fail(what);
        }
    }

    static void styledText(GameTestHelper helper) {
        StyledText text = new StyledText("hello world");
        text.setStyle(0, 5, StyledText.BOLD, true);
        text.setColor(6, 11, 4);
        expect(helper, text.hasStyle(0, 5, StyledText.BOLD), "bold range");
        expect(helper, !text.hasStyle(0, 6, StyledText.BOLD), "bold stops at the space");
        expect(helper, text.runs(0, 11).size() == 3, "runs split on style and color, got " + text.runs(0, 11).size());

        text.type(5, 5, "!");
        expect(helper, text.style(5) == StyledText.BOLD, "typing continues the previous style");
        text.type(0, 0, ">");
        expect(helper, text.style(0) == StyledText.BOLD, "typing at the start takes the next character's style");

        StyledText paragraphs = new StyledText("one\ntwo");
        paragraphs.align(1, TextAlign.RIGHT);
        paragraphs.type(7, 7, "\nthree");
        expect(helper, paragraphs.paragraphCount() == 3, "newline adds a paragraph");
        expect(helper, paragraphs.align(2) == TextAlign.RIGHT, "a split paragraph keeps its alignment");
        paragraphs.type(3, 4, "");
        expect(helper, paragraphs.paragraphCount() == 2, "removing a newline joins paragraphs");
        expect(helper, paragraphs.text().equals("onetwo\nthree"), "joined text, got " + paragraphs.text());
        helper.succeed();
    }

    static void layout(GameTestHelper helper) {
        UiFont font = font();
        StyledText text = new StyledText("aaa bbb ccc");
        TextLayout narrow = new TextLayout(text, font, 42);
        expect(helper, narrow.lines().size() == 2, "wraps ccc down, got " + narrow.lines().size());
        expect(helper, narrow.lines().get(1).start() == 8, "second line starts at ccc");
        expect(helper, narrow.height() == 18, "two lines tall");
        expect(helper, new TextLayout(text, font, 0).lines().size() == 1, "no wrapping without a width");

        TextLayout tiny = new TextLayout(new StyledText("abcdefgh"), font, 18);
        expect(helper, tiny.lines().size() == 3, "long words break by character, got " + tiny.lines().size());

        expect(helper, narrow.caret(3).x0() == 18, "caret after aaa");
        expect(helper, narrow.caret(8).y0() == 9, "caret at the start of the wrapped line");
        expect(helper, narrow.hitTest(20, 0) == 3, "hit test snaps to the nearest boundary");
        expect(helper, narrow.hitTest(0, 100) == 8, "below the text clamps to the last line");
        expect(helper, narrow.hitTest(500, 0) == 7, "past the end of a wrapped line stops before its spaces, got " + narrow.hitTest(500, 0));

        TextLayout blank = new TextLayout(new StyledText(), font, 100);
        expect(helper, blank.lines().size() == 1 && blank.caret(0).x0() == 0, "empty text still has a line");
        helper.succeed();
    }

    static void alignment(GameTestHelper helper) {
        UiFont font = font();
        StyledText text = new StyledText("ab\ncd cd cd\nef");
        text.align(0, TextAlign.RIGHT);
        text.align(1, TextAlign.JUSTIFY);
        text.align(2, TextAlign.CENTER);
        TextLayout layout = new TextLayout(text, font, 60);
        expect(helper, layout.lines().get(0).x() == 48, "right aligned, got " + layout.lines().get(0).x());
        TextLayout.Line justified = layout.lines().get(1);
        expect(helper, justified.extra() == 0, "a last line is never stretched");
        expect(helper, layout.lines().get(2).x() == 24, "centered, got " + layout.lines().get(2).x());

        StyledText stretch = new StyledText("aa bb cc dd");
        stretch.align(0, TextAlign.JUSTIFY);
        TextLayout wrapped = new TextLayout(stretch, font, 42);
        TextLayout.Line first = wrapped.lines().get(0);
        expect(helper, first.extra() > 0 && first.width() == 42, "wrapped justified line fills the width");
        expect(helper, wrapped.xOf(first, first.start() + 3) > 18, "space stretching moves later characters right");
        helper.succeed();
    }

    static void selection(GameTestHelper helper) {
        UiFont font = font();
        StyledText text = new StyledText("aaa bbb ccc\nddd");
        TextSelection selection = new TextSelection();
        selection.selectWord(text, 5);
        expect(helper, selection.start() == 4 && selection.end() == 7, "word select, got " + selection.start() + ".." + selection.end());
        selection.selectParagraph(text, 13);
        expect(helper, selection.start() == 12 && selection.end() == 15, "paragraph select");
        selection.selectAll(text);
        expect(helper, selection.end() == 15, "select all");
        expect(helper, TextSelection.moveWord(text, 0, 1) == 3, "word right");
        expect(helper, TextSelection.moveWord(text, 3, 1) == 7, "word right skips the space");
        expect(helper, TextSelection.moveWord(text, 7, -1) == 4, "word left");
        expect(helper, TextSelection.moveWord(text, 12, -1) == 11, "word left stops at a newline");

        TextLayout layout = new TextLayout(text, font, 0);
        var rects = layout.selection(2, 14);
        expect(helper, rects.size() == 2, "a selection across a break has two boxes, got " + rects.size());
        expect(helper, rects.get(0).x0() == 12 && rects.get(0).x1() > 66, "first box runs from the start to past the line end");
        expect(helper, layout.selection(3, 3).isEmpty(), "no box for an empty selection");
        helper.succeed();
    }

    static void editor(GameTestHelper helper) {
        UiGameTests.TestHost host = new UiGameTests.TestHost();
        RichTextEditor editor = new RichTextEditor().rows(5);
        editor.size(60, 60);
        UiRoot root = new UiRoot(editor, null, host);
        root.resize(60, 60);
        root.layout();
        root.focusNext(false);
        expect(helper, root.focused() == editor, "the editor takes focus");

        UiGameTests.typeText(root, "aaaa bbbb cccc");
        expect(helper, editor.text().text().equals("aaaa bbbb cccc"), "typed " + editor.text().text());
        root.key(Keys.UP, 0);
        expect(helper, editor.selection().caret() == 9, "up moves to an earlier wrapped line, at " + editor.selection().caret());
        root.key(Keys.DOWN, 0);
        root.key(Keys.END, Keys.CONTROL);

        root.key(Keys.B, Keys.CONTROL);
        UiGameTests.typeText(root, "!");
        expect(helper, editor.text().style(14) == StyledText.BOLD, "typing after the bold shortcut is bold");
        expect(helper, editor.text().style(13) == 0, "earlier text stays plain");
        root.key(Keys.Z, Keys.CONTROL);
        expect(helper, editor.text().text().equals("aaaa bbbb cccc"), "undo removes the typed character");
        root.key(Keys.Y, Keys.CONTROL);
        expect(helper, editor.text().text().endsWith("!"), "redo puts it back");

        editor.selection().set(0, 4);
        root.key(Keys.I, Keys.CONTROL);
        expect(helper, editor.text().hasStyle(0, 4, StyledText.ITALIC), "italic on the selection");
        root.key(Keys.I, Keys.CONTROL);
        expect(helper, !editor.text().hasStyle(0, 4, StyledText.ITALIC), "italic toggles off");
        editor.setColor(4);
        expect(helper, editor.text().color(0) == 4, "color applies to the selection");
        editor.align(TextAlign.CENTER);
        expect(helper, editor.text().align(0) == TextAlign.CENTER, "align applies to the paragraph");

        editor.selection().set(0, 4);
        root.key(Keys.C, Keys.CONTROL);
        expect(helper, host.clipboard.equals("aaaa"), "copy gives plain text, got " + host.clipboard);
        root.key(Keys.ENTER, 0);
        expect(helper, editor.text().paragraphCount() == 2 && editor.text().align(1) == TextAlign.CENTER, "enter splits and keeps the alignment");
        helper.succeed();
    }

    static void markdown(GameTestHelper helper) {
        UiGameTests.TestHost host = new UiGameTests.TestHost();
        RichTextEditor editor = new RichTextEditor().rows(10).paper(120, 100, 6, 4);
        editor.size(140, 100);
        UiRoot root = new UiRoot(editor, null, host);
        root.resize(140, 100);
        root.layout();
        root.focusNext(false);

        UiGameTests.typeText(root, "# Title");
        expect(helper, editor.text().kind(0) == StyledText.HEADING_1 && editor.text().text().equals("Title"), "# turns the line into a heading, got " + editor.text().text());
        root.key(Keys.ENTER, 0);
        expect(helper, editor.text().paragraphCount() == 2 && editor.text().kind(1) == StyledText.PARAGRAPH, "enter after a heading starts a plain line");
        UiGameTests.typeText(root, "- one");
        expect(helper, editor.text().kind(1) == StyledText.BULLET, "- makes a bullet");
        root.key(Keys.ENTER, 0);
        expect(helper, editor.text().kind(2) == StyledText.BULLET, "enter continues the list");
        root.key(Keys.ENTER, 0);
        expect(helper, editor.text().kind(2) == StyledText.PARAGRAPH && editor.text().paragraphCount() == 3, "enter on an empty item ends the list");
        UiGameTests.typeText(root, "a **bold** and *it* and ++u++ ~~s~~ &cred");
        StyledText text = editor.text();
        expect(helper, text.text().substring(text.paragraphStart(2)).equals("a bold and it and u s red"), "markers are removed, got " + text.text());
        int at = text.paragraphStart(2);
        expect(helper, text.hasStyle(at + 2, at + 6, StyledText.BOLD), "**bold** is bold");
        expect(helper, text.hasStyle(at + 11, at + 13, StyledText.ITALIC), "*it* is italic");
        expect(helper, text.hasStyle(at + 18, at + 19, StyledText.UNDERLINE) && text.hasStyle(at + 20, at + 21, StyledText.STRIKE), "++u++ and ~~s~~");
        expect(helper, text.color(text.length() - 1) == 12, "&c colors what follows");
        expect(helper, !text.hasStyle(at, at + 1, StyledText.BOLD), "text before stays plain");

        root.key(Keys.TAB, 0);
        expect(helper, editor.text().text().endsWith("    ") && root.focused() == editor, "tab types spaces and keeps focus");
        editor.setText(new StyledText());
        UiGameTests.typeText(root, "a");
        root.key(Keys.ENTER, 0);
        root.key(Keys.ENTER, 0);
        UiGameTests.typeText(root, "b");
        TextLayout.Rect caret = new TextLayout(editor.text(), host.font(), 108).caret(editor.selection().caret());
        expect(helper, caret.y0() == 18 && caret.x0() == 6, "the caret follows onto new lines, at " + caret);
        StyledText long_ = new StyledText(("word ").repeat(200));
        TextLayout paged = new TextLayout(long_, host.font(), 108, new TextLayout.Pages(100, 4, 6));
        expect(helper, paged.pageCount() > 2, "long text flows onto more pages, got " + paged.pageCount());
        expect(helper, paged.lines().get(paged.lines().size() - 1).y() >= 104, "later lines sit on later pages");
        helper.succeed();
    }

    static void pinning(GameTestHelper helper) {
        UiGameTests.TestHost host = new UiGameTests.TestHost();
        StyledText text = new StyledText("one\ntwo");
        text.pin(1, 2);
        TextLayout.Pages pages = new TextLayout.Pages(100, 4, 6);
        TextLayout layout = new TextLayout(text, host.font(), 108, pages);
        expect(helper, layout.lines().get(1).y() == 2 * 104 + 6, "a pinned paragraph starts at the top of its page, got " + layout.lines().get(1).y());
        expect(helper, layout.pageCount() == 3, "pages count up to the pinned one");
        StyledText flowing = new StyledText(("word ").repeat(120) + "\ntwo");
        flowing.pin(1, 1);
        TextLayout pushed = new TextLayout(flowing, host.font(), 108, pages);
        expect(helper, pushed.lines().get(pushed.lines().size() - 1).y() > 104 + 6, "earlier text that reaches the page pushes the pinned paragraph down, got " + pushed.lines().get(pushed.lines().size() - 1).y());
        text.type(3, 3, "\nmid");
        expect(helper, text.pin(1) == -1 && text.pin(2) == 2, "a new paragraph isn't pinned and the pin stays with its paragraph");

        RichTextEditor editor = new RichTextEditor().paper(100, 100, 6, 4).sheets(4);
        editor.size(100, 100);
        UiRoot root = new UiRoot(editor, null, host);
        root.resize(100, 100);
        root.layout();
        root.focusNext(false);
        root.pointerDown(50, 50, Keys.LEFT_BUTTON);
        root.pointerUp(50, 50, Keys.LEFT_BUTTON);
        UiGameTests.typeText(root, "a");
        root.key(Keys.EQUAL, Keys.CONTROL);
        expect(helper, editor.zoom() > 1f, "ctrl plus zooms in");
        root.key(Keys.MINUS, Keys.CONTROL);
        root.key(Keys.MINUS, Keys.CONTROL);
        expect(helper, editor.zoom() < 1f, "ctrl minus zooms out");
        helper.succeed();
    }
}
