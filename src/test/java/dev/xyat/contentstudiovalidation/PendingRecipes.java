package dev.xyat.contentstudiovalidation;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import net.minecraft.client.Minecraft;

/**
 * Applies saved recipe changes the way leaving the recipe editor does, for fixtures that then look at the live recipes:
 * saves no longer reload the datapacks one by one.
 */
final class PendingRecipes {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PendingRecipes.class);
    private static Runnable reloaded;
    private static long from, deadline;
    private static boolean installed;

    private PendingRecipes() {
    }

    /** Asks the server to apply what waits, as the closing editor does; {@code next} runs once the datapacks have reloaded. */
    static void applyThen(Runnable next) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (!installed) {
            installed = true;
            KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, PendingRecipes::tick);
        }
        from = dev.xyat.contentstudio.recipe.RecipeMemoryManager.catalogVersion(server.getRecipeManager());
        deadline = System.currentTimeMillis() + 300_000L;
        reloaded = next;
        dev.xyat.contentstudio.recipe.network.RecipeNetwork.sendApplyPendingRecipesRequest();
    }

    private static void tick() {
        Runnable next = reloaded;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (next == null || server == null) return;
        // A reload makes a new recipe manager with a new catalog.
        if (dev.xyat.contentstudio.recipe.RecipeMemoryManager.catalogVersion(server.getRecipeManager()) != from) {
            reloaded = null;
            next.run();
        } else if (System.currentTimeMillis() > deadline) {
            reloaded = null;
            LOG.error("CONTENT_PENDING_FAIL the saved recipes were not applied by a datapack reload within five minutes");
            dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
        }
    }
}
