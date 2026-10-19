package xyz.volcanobay.empiric.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.volcanobay.empiric.client.ui.UiKeyboard;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void empiric$worldUiKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && UiKeyboard.key(key, action, modifiers)) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void empiric$worldUiCharacter(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && UiKeyboard.character(codePoint, modifiers)) {
            ci.cancel();
        }
    }
}
