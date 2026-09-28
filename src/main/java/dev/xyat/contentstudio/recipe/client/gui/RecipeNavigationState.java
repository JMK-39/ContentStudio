package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;

public final class RecipeNavigationState {
    private RecipeNavigationState() {
    }

    public static void requestHub() {
        RecipeNetwork.requestOpenHub();
    }

}
