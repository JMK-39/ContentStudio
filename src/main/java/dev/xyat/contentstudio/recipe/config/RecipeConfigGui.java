package dev.xyat.contentstudio.recipe.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.recipe.client.gui.RecipeNavigationState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RecipeConfigGui {
    public static final String PAGE_ID = "contentstudio:recipehud";

    private RecipeConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.contentstudio.recipe.recipehud.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(Component.translatable("cfg.contentstudio.recipe.recipehud.description"))
                .action(
                        "open_recipe_editor",
                        Component.translatable("cfg.contentstudio.recipe.recipehud.open_editor"),
                        RecipeNavigationState::requestHub,
                        Component.translatable("cfg.contentstudio.recipe.recipehud.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "contentstudio");
    }
}
