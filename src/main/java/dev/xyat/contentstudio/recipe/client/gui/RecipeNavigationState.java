package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import net.minecraft.client.gui.screens.Screen;

public final class RecipeNavigationState {
    private RecipeNavigationState() {
    }

    public static void requestHub() {
        RecipeNetwork.requestOpenHub();
    }

    static Screen resolveHubParent(Screen current) {
        // The recipe hub is an editor launched from Kinetic's configuration
        // pages. Its Back action goes to the main plugin list, not to a stale
        // module detail screen that may have been replaced by a server menu.
        return KTConfigApi.createIndexScreen(null);
    }
}
