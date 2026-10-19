package xyz.volcanobay.empiric.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import xyz.volcanobay.empiric.api.ui.UiThemes;
import xyz.volcanobay.empiric.core.ui.UiFont;
import xyz.volcanobay.empiric.core.ui.UiHost;
import xyz.volcanobay.empiric.core.ui.UiTheme;

/**
 * The client's font, clipboard, sounds and themes, for any screen that hosts a widget tree.
 */
public final class ClientUiHost implements UiHost {

    private final UiFont font;
    private final Runnable close;

    public ClientUiHost(Font font, Runnable close) {
        this.font = new McFont(font);
        this.close = close;
    }

    @Override
    public UiFont font() {
        return font;
    }

    @Override
    public String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public void playSound(String id, float pitch) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        SoundEvent sound = location == null ? null : BuiltInRegistries.SOUND_EVENT.get(location);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    @Override
    public UiTheme theme(String id) {
        return UiThemes.get(id);
    }

    @Override
    public void close() {
        close.run();
    }

    @Override
    public boolean shiftDown() {
        return Screen.hasShiftDown();
    }

    @Override
    public boolean controlDown() {
        return Screen.hasControlDown();
    }

    /**
     * Joins surrogate pairs that arrive as two typed chars into one code point.
     */
    public static final class Characters {

        private char highSurrogate;

        public int accept(char character) {
            if (Character.isHighSurrogate(character)) {
                highSurrogate = character;
                return -1;
            }
            int codepoint = character;
            if (Character.isLowSurrogate(character) && highSurrogate != 0) {
                codepoint = Character.toCodePoint(highSurrogate, character);
            }
            highSurrogate = 0;
            return codepoint;
        }
    }
}
