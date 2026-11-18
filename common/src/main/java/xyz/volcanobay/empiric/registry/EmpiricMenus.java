package xyz.volcanobay.empiric.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.platform.Platform;
import xyz.volcanobay.empiric.platform.Registrar;

import java.util.function.Supplier;

public final class EmpiricMenus {

    @Deprecated
    public static final Registrar<MenuType<?>> MENUS = Platform.registrar(Registries.MENU);

    @Register("ui")
    private static final MenuType<UiMenu> UI_TYPE = new MenuType<>(UiMenu::fromClient, FeatureFlags.VANILLA_SET);

    /**
     * The one menu type every UI screen with slots opens as. Which screen and module it shows travels separately.
     */
    public static final Supplier<MenuType<UiMenu>> UI = () -> UI_TYPE;

    private EmpiricMenus() {
    }

    public static void bootstrap() {
    }
}
