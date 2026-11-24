package dev.xyat.contentstudio.recipe.removal;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.recipe.RecipeConfigStore;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RecipeRemovalManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<RemovalEntry> REMOVAL_LIST = new ArrayList<>();
    private static boolean loaded;
    private static boolean applyingDraft;

    private RecipeRemovalManager() {
    }

    public static synchronized void loadData() {
        if (loaded) {
            return;
        }
        reloadData();
    }

    public static synchronized void reloadData() {
        REMOVAL_LIST.clear();
        REMOVAL_LIST.addAll(RecipeConfigStore.load().removals());
        loaded = true;
    }

    public static synchronized List<RemovalEntry> snapshot() {
        loadData();
        return new ArrayList<>(REMOVAL_LIST);
    }

    /** Whether an exact-ID rule currently removes this original recipe. */
    public static synchronized boolean isRecipeRemoved(net.minecraft.resources.ResourceLocation id) {
        return snapshot().stream().anyMatch(rule -> rule.mode() == RemovalMode.RECIPE_ID && rule.value().equals(id.toString()));
    }

    /**
     * Adds or drops the exact-ID rule for one original recipe, leaving every other rule as it is. The rule is saved at
     * once and applied by the one datapack reload that runs when the player leaves the recipe editor. Completes with
     * {@code true} once the change is saved.
     */
    public static synchronized java.util.concurrent.CompletableFuture<Boolean> setRecipeRemoved(ServerPlayer player,
            net.minecraft.resources.ResourceLocation id, boolean removed) {
        if (player == null || player.getServer() == null || applyingDraft) return java.util.concurrent.CompletableFuture.completedFuture(false);
        List<RemovalEntry> draft = snapshot();
        RemovalEntry rule = new RemovalEntry(RemovalMode.RECIPE_ID, id.toString(), "");
        draft.removeIf(existing -> existing.key().equals(rule.key()));
        if (removed) draft.add(rule);
        applyingDraft = true;
        return RemovalDraftCommitter.commit(draft, RecipeConfigStore::replaceRemovalsAtomic, () -> {
                    reloadData();
                    dev.xyat.contentstudio.recipe.RecipeSaveManager.markPending(player, true);
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                })
                .thenApply(outcome -> {
                    synchronized (RecipeRemovalManager.class) {
                        applyingDraft = false;
                    }
                    if (outcome.cause() != null) LOGGER.error("Could not apply recipe removal for {} ({})", id, outcome.status(), outcome.cause());
                    return outcome.status() == RemovalDraftCommitter.Status.APPLIED;
                });
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<RemovalEntry> entries = snapshot();
        RecipeNetwork.sendRuleState(player, 0L, RecipeNetwork.SaveStatus.SYNC, entries);
    }

    public static synchronized void saveDraftAndApply(ServerPlayer player, long requestId, List<RemovalEntry> draft) {
        if (player == null || player.getServer() == null) {
            return;
        }
        if (applyingDraft) {
            RecipeNetwork.sendRuleState(player, requestId, RecipeNetwork.SaveStatus.REJECTED, snapshot());
            return;
        }
        applyingDraft = true;
        var server = player.getServer();
        RemovalDraftCommitter.commit(draft, RecipeConfigStore::replaceRemovalsAtomic,
                () -> RecipeMemoryManager.reloadDatapacks(server))
                .thenAccept(outcome -> server.execute(() -> {
                    synchronized (RecipeRemovalManager.class) {
                        applyingDraft = false;
                    }
                    if (outcome.cause() != null) {
                        LOGGER.error("Could not apply recipe-removal draft ({})", outcome.status(), outcome.cause());
                    }
                    RecipeNetwork.SaveStatus status = switch (outcome.status()) {
                        case APPLIED -> RecipeNetwork.SaveStatus.APPLIED;
                        case REJECTED -> RecipeNetwork.SaveStatus.REJECTED;
                        case PERSISTED_RELOAD_FAILED -> RecipeNetwork.SaveStatus.PERSISTED_RELOAD_FAILED;
                    };
                    RecipeNetwork.sendRuleState(player, requestId, status,
                            status == RecipeNetwork.SaveStatus.APPLIED || status == RecipeNetwork.SaveStatus.REJECTED
                                    ? snapshot() : draft);
                }));
    }
}
