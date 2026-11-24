package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;

public final class RecipeEditSessionState {
    private static boolean pendingRecipeApply;
    private static boolean watching;
    private static final java.util.List<Class<? extends dev.xyat.kineticcore.api.client.gui.page.KineticPage>> RECIPE_PAGES = java.util.List.of(
            RecipeHubPage.class, RecipePage.class, RecipePreviewPage.class, VanillaRecipeHubPage.class,
            NativeRecipeStationsPage.class, NativeRecipeBrowserPage.class, NativeRecipeEditorPage.class);

    private RecipeEditSessionState() {
    }

    public static void markPendingRecipeApply() {
        pendingRecipeApply = true;
        if (!watching) {
            watching = true;
            // The pages apply on Back and Esc; this also covers a screen closed without asking them (replaced, death).
            dev.xyat.kineticcore.api.client.event.KineticClientEvents.onTick(
                    dev.xyat.kineticcore.api.client.event.KineticClientEvents.TickPhase.END, RecipeEditSessionState::applyOnceClosed);
        }
    }

    private static void applyOnceClosed() {
        if (!pendingRecipeApply || recipePagesOpen()) return;
        if (dev.xyat.kineticcore.api.runtime.KineticClientRuntime.localPlayer() == null) {
            // Left the world: the saved changes load with it next time.
            pendingRecipeApply = false;
            return;
        }
        applyPendingAndClear();
    }

    /** Whether a recipe page is shown, or is the page a selector or child page on top returns to. */
    private static boolean recipePagesOpen() {
        for (Class<? extends dev.xyat.kineticcore.api.client.gui.page.KineticPage> page : RECIPE_PAGES) {
            if (dev.xyat.kineticcore.api.client.gui.KineticGui.findPage(page) != null) return true;
        }
        return false;
    }

    public static void applyPendingAndClear() {
        if (!pendingRecipeApply) {
            return;
        }
        pendingRecipeApply = false;
        RecipeNetwork.sendApplyPendingRecipesRequest();
    }
}
