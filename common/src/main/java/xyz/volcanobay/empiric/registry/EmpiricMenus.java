package xyz.volcanobay.empiric.registry;

import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.ui.menu.UiMenu;

public final class EmpiricMenus {

    public static final RegistrationProvider<MenuType<?>> MENUS = RegistrationProvider.get(Registries.MENU, Empiric.MODID);

    /**
     * The one menu type every UI screen with slots opens as. Which screen and module it shows travels separately.
     */
    public static final RegistryObject<MenuType<UiMenu>> UI = MENUS.register("ui", () -> new MenuType<>(UiMenu::fromClient, FeatureFlags.VANILLA_SET));

    private EmpiricMenus() {
    }

    public static void bootstrap() {
    }
}
