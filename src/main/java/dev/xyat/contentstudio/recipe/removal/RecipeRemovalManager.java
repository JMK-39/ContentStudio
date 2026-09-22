package dev.xyat.contentstudio.recipe.removal;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.recipe.RecipeConfigStore;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RecipeRemovalManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<RemovalEntry> REMOVAL_LIST = new ArrayList<>();
    private static boolean loaded;

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

    public static synchronized void addEntry(RemovalEntry entry) {
        loadData();
        if (!REMOVAL_LIST.contains(entry)) {
            REMOVAL_LIST.add(entry);
        }
    }

    public static synchronized void removeEntry(RemovalEntry entry) {
        loadData();
        REMOVAL_LIST.remove(entry);
    }

    public static synchronized List<RemovalEntry> snapshot() {
        loadData();
        return new ArrayList<>(REMOVAL_LIST);
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<RemovalEntry> entries = snapshot();
        java.util.Map<net.minecraft.world.item.Item, net.minecraft.world.item.ItemStack> modifiedItems = new java.util.LinkedHashMap<>();
        for (var recipe : RecipeMemoryManager.recipeCatalog(player.server.getRecipeManager())) {
            try {
                if (RecipeMemoryManager.matchesRemoval(recipe, entries, player.level().registryAccess())) {
                    var output = recipe.getResultItem(player.level().registryAccess());
                    if (!output.isEmpty()) modifiedItems.putIfAbsent(output.getItem(), output.copy());
                }
            } catch (RuntimeException ignored) {
                // Recipes with dynamic results are identified by JEI when browsing the item.
            }
        }
        RecipeNetwork.sendSyncToPlayer(player, entries, List.copyOf(modifiedItems.values()));
    }

    public static void saveAndApply(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        try {
            RecipeConfigStore.updateRemovals(snapshot());
            var server = player.getServer();
            RecipeMemoryManager.reloadDatapacks(server).whenComplete((unused, error) -> server.execute(() -> {
                if (error == null) {
                    RecipeNetwork.sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.msg.removals_saved_applied"));
                } else {
                    LOGGER.error("Failed to reload RecipeModule datapack after saving removals", error);
                    RecipeNetwork.sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.save_failed_plain"));
                }
            }));
        } catch (Exception e) {
            LOGGER.error("Failed to save recipe removals", e);
            RecipeNetwork.sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.save_failed_plain"));
        }
    }
}
