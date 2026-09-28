package dev.xyat.contentstudio.recipe.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.recipe.client.gui.RecipeNavigationState;

public final class RecipeConfigGui {
    public static final String PAGE_ID = "contentstudio:recipehud";

    private RecipeConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.contentstudio.recipe.recipehud.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(KineticI18n.translatable("cfg.contentstudio.recipe.recipehud.description"))
                .action(
                        "open_recipe_editor",
                        KineticI18n.translatable("cfg.contentstudio.recipe.recipehud.open_editor"),
                        RecipeNavigationState::requestHub,
                        KineticI18n.translatable("cfg.contentstudio.recipe.recipehud.open_editor.tooltip")
                )
                .build());
    }

    // 原 create(Screen parent)：以当前界面为父打开本模组配置中心 / Former create(Screen parent): opens this mod's config hub as a child of the current screen.
    public static void open() {
        KTConfigApi.openOwner("contentstudio");
    }
}
