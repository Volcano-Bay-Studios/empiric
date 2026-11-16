package xyz.volcanobay.empiric.client.ui;

import net.minecraft.client.gui.Font;
import xyz.volcanobay.empiric.core.ui.UiFont;
import xyz.volcanobay.empiric.core.ui.text.StyledText;

public record McFont(Font font) implements UiFont {

    @Override
    public int width(String text) {
        return font.width(text);
    }

    @Override
    public int width(String text, int style) {
        return font.width(StyledText.format(text, style));
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }
}
