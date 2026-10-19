package xyz.volcanobay.empiric.api.ui;

import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.entry.DataEntryType;
import xyz.volcanobay.empiric.core.ui.UiTheme;

/**
 * Themes are data entries in {@code data/<namespace>/ui/themes}, so data packs can add and restyle them.
 */
public final class UiThemes {

    public static final DataEntryType<UiTheme> THEMES = new DataEntryType<>("empiric:ui/themes", UiTheme.class, UiTheme::new);

    private UiThemes() {
    }

    public static void registerData(DataEntrySystem data) {
        data.register(THEMES);
    }

    public static UiTheme get(String id) {
        UiTheme theme = THEMES.get(id);
        if (theme == null && !UiTheme.DEFAULT.equals(id)) {
            theme = THEMES.get(UiTheme.DEFAULT);
        }
        return theme == null ? UiTheme.FALLBACK : theme;
    }
}
