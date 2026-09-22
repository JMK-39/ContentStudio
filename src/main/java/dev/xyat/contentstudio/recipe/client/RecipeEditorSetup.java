package dev.xyat.contentstudio.recipe.client;

import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.client.gui.RecipeHubScreen;
import dev.xyat.contentstudio.recipe.client.gui.RecipeScreen;
import dev.xyat.kineticcore.api.client.registry.KineticClientMenus;

public final class RecipeEditorSetup {
    private RecipeEditorSetup() {
    }

    public static void register() {
        KineticClientMenus.register(RecipeRegistry.HUB_MENU, RecipeHubScreen::new);
        KineticClientMenus.register(RecipeRegistry.EDITOR_MENU, RecipeScreen::new);
    }
}
