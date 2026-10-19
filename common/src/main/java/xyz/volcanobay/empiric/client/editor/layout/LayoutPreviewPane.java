package xyz.volcanobay.empiric.client.editor.layout;

import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.ui.ContextEntry;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.core.ui.bind.Property;
import xyz.volcanobay.empiric.core.ui.layout.Align;
import xyz.volcanobay.empiric.core.ui.layout.FlexLayout;
import xyz.volcanobay.empiric.core.ui.layout.Length;
import xyz.volcanobay.empiric.core.ui.widget.Button;
import xyz.volcanobay.empiric.core.ui.widget.Checkbox;
import xyz.volcanobay.empiric.core.ui.widget.ContextMenu;
import xyz.volcanobay.empiric.core.ui.widget.Label;
import xyz.volcanobay.empiric.core.ui.widget.NumberField;
import xyz.volcanobay.empiric.core.ui.widget.ScrollPanel;

import java.util.ArrayList;
import java.util.List;

/**
 * The middle of the editor: preview size, zoom and mode controls over the {@link PreviewCanvas}, and the problems the
 * last build reported under it.
 */
final class LayoutPreviewPane extends Widget {

    private static final int MAX_PROBLEM_LINES = 5;

    private final LayoutEditorView view;
    private final PreviewCanvas canvas;
    private final ScrollPanel problems = new ScrollPanel();
    private List<String> shownProblems = List.of();

    LayoutPreviewPane(LayoutEditorView view) {
        this.view = view;
        this.canvas = new PreviewCanvas(view);
        layout(FlexLayout.column(2).align(Align.STRETCH));

        Widget toolbar = new Widget().layout(FlexLayout.row(2).align(Align.CENTER));
        NumberField width = new NumberField(320).integer(true).range(16, 1024).width(Length.px(44));
        width.compact();
        width.bind(new Property<>() {
            @Override
            public Double get() {
                return (double) canvas.previewWidth();
            }

            @Override
            public void set(Double value) {
                canvas.previewSize((int) Math.round(value), canvas.previewHeight());
            }
        });
        width.hint("Preview width in widget pixels: the size of the screen the layout is shown in");
        NumberField height = new NumberField(200).integer(true).range(16, 1024).width(Length.px(44));
        height.compact();
        height.bind(new Property<>() {
            @Override
            public Double get() {
                return (double) canvas.previewHeight();
            }

            @Override
            public void set(Double value) {
                canvas.previewSize(canvas.previewWidth(), (int) Math.round(value));
            }
        });
        height.hint("Preview height in widget pixels");
        Button fit = new Button("Fit", canvas::fit);
        fit.hint("Zoom so the whole preview fits the canvas");
        Button actual = new Button("100%", canvas::actualSize);
        actual.hint("One canvas pixel per widget pixel");
        Checkbox interact = new Checkbox("Interact");
        interact.bind(new Property<>() {
            @Override
            public Boolean get() {
                return canvas.interact();
            }

            @Override
            public void set(Boolean value) {
                canvas.interact(value);
            }
        });
        interact.hint("Off: clicking the preview selects widgets. On: the mouse uses the widgets, so you can drag sliders and press buttons.\n"
                + "Pan with the middle mouse button (or the right button when off). Zoom with the wheel (control and the wheel when on).");
        Button source = new Button(sourceLabel(null)).width(Length.FILL);
        source.watch(view::module, module -> source.text(sourceLabel(module)));
        source.onPress(() -> openSources(source));
        source.hint("The module bindings read from and write to. Pick one to see real values; without one, bindings are listed as problems.");
        toolbar.add(width);
        toolbar.add(new Label("x").textColor("text_dim"));
        toolbar.add(height);
        toolbar.add(fit);
        toolbar.add(actual);
        toolbar.add(interact);
        toolbar.add(source);
        add(toolbar);

        canvas.fill();
        add(canvas);
        problems.content().layout(FlexLayout.column(1).align(Align.STRETCH));
        problems.visible(false);
        add(problems);
    }

    PreviewCanvas canvas() {
        return canvas;
    }

    private static String sourceLabel(Module<?> module) {
        return module == null ? "No module (bindings show as problems)" : module.type().id().toString();
    }

    private void openSources(Button anchor) {
        List<ContextEntry> entries = new ArrayList<>();
        entries.add(ContextEntry.of("No module", "Bindings aren't resolved and show as problems", () -> view.module(null)));
        List<Module<?>> modules = view.environment().modules();
        for (int i = 0; i < modules.size(); i++) {
            Module<?> module = modules.get(i);
            entries.add(ContextEntry.of(module.type().id() + " #" + i, () -> view.module(module)));
        }
        UiPoint below = anchor.toRoot(0, anchor.height());
        anchor.root().openPopup(new ContextMenu(entries), below.x(), below.y());
    }

    @Override
    protected void tick() {
        canvas.update();
        List<String> current = canvas.problems();
        if (!current.equals(shownProblems)) {
            shownProblems = List.copyOf(current);
            problems.content().clear();
            for (String problem : shownProblems) {
                problems.add(new Label(problem).textColor("error"));
            }
            problems.visible(!shownProblems.isEmpty());
            if (root() != null) {
                int line = root().font().lineHeight() + 1;
                problems.height(Length.px(Math.min(MAX_PROBLEM_LINES, Math.max(1, shownProblems.size())) * line + 2));
            }
        }
    }
}
