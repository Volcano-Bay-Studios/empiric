package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;

import java.util.function.Supplier;

public final class EmpiricMenus {

    public static final Registrar<MenuType<?>> MENUS = Platform.registrar(Registries.MENU);

    /**
     * The one menu type every UI screen with slots opens as. Which screen and module it shows travels separately.
     */
    public static final Supplier<MenuType<UiMenu>> UI = MENUS.register("ui", () -> new MenuType<>(UiMenu::fromClient, FeatureFlags.VANILLA_SET));

    private EmpiricMenus() {
    }

    public static void bootstrap() {
    }
}
