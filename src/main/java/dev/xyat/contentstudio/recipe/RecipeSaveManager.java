package dev.xyat.contentstudio.recipe;

import dev.xyat.kineticcore.api.text.KineticI18n;
import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;

public final class RecipeSaveManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean pendingRecipeReload;
    // Recipes already changed in the running recipe manager that the players have not been sent yet.
    private static boolean pendingRecipeSync;
    // Who made the changes that wait; they are applied when that player leaves the recipe editor or the game.
    @javax.annotation.Nullable private static java.util.UUID pendingBy;

    private RecipeSaveManager() {
    }

    /**
     * A saved change waits until the recipe editor is left: for one datapack reload ({@code needsReload}), or, when it
     * is already in the running recipe manager, only for the recipes to be sent to the players. Saving ten recipes
     * then reloads once, not ten times.
     */
    public static synchronized void markPending(ServerPlayer player, boolean needsReload) {
        if (needsReload) pendingRecipeReload = true;
        else pendingRecipeSync = true;
        if (player != null) pendingBy = player.getUUID();
    }

    /** Applies what waits when the player who made the changes leaves the game without closing the editor. */
    public static synchronized void playerLeft(ServerPlayer player) {
        if (player == null || !player.getUUID().equals(pendingBy)) return;
        // In a single-player world the host leaving closes the world; the saved changes load with it next time.
        if (player.getServer() != null && player.getServer().isDedicatedServer()) applyPending(player);
    }

    public static synchronized void saveOnly(ServerPlayer player, RecipeNetwork.RecipeChangePacket packet) {
        try {
            RecipeRecord record = new RecipeRecord();
            if (packet.uuid() != null && !packet.uuid().isBlank()) {
                record.uuid = packet.uuid();
            }
            record.editorType = packet.editorType();
            record.isShapeless = packet.isShapeless();
            record.outputUseNbt = packet.outputUseNbt();
            record.output = packet.output().copy();
            record.inputs = copyStacks(packet.inputs());
            record.inputModes = new ArrayList<>(packet.inputNbtModes());
            record.configIndex = packet.configIndex();
            record.invalidConfig = false;
            record.invalidReason = "";

            if (packet.configIndex() >= 0) {
                RecipeConfigStore.replaceRecipeAtConfigIndex(packet.configIndex(), record);
            } else {
                RecipeDatabase.loadDatabase();
                ArrayList<RecipeRecord> candidateRecords = copyRecords(RecipeDatabase.snapshot());
                candidateRecords.add(record);
                RecipeConfigStore.updateRecipes(candidateRecords);
            }

            RecipeDatabase.reloadDatabase();
            markPending(player, true);
            RecipeNetwork.syncRecipeRecords(player);
            RecipeNetwork.sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.msg.saved_pending"));
        } catch (Exception e) {
            LOGGER.error("Failed to save RecipeModule datapack recipe", e);
            RecipeNetwork.sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.save_failed_plain"));
        }
    }

    public static synchronized void deleteOnly(ServerPlayer player, String uuid, int configIndex) {
        try {
            if (configIndex >= 0) {
                RecipeConfigStore.deleteRecipeAtConfigIndex(configIndex);
            } else {
                RecipeDatabase.loadDatabase();
                ArrayList<RecipeRecord> candidateRecords = copyRecords(RecipeDatabase.snapshot());
                candidateRecords.removeIf(record -> record.uuid != null && record.uuid.equals(uuid));
                RecipeConfigStore.updateRecipes(candidateRecords);
            }

            RecipeDatabase.reloadDatabase();
            markPending(player, true);
            RecipeNetwork.syncRecipeRecords(player);
            RecipeNetwork.sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.msg.deleted_pending"));
        } catch (Exception e) {
            LOGGER.error("Failed to delete RecipeModule datapack recipe {} at bundle index {}", uuid, configIndex, e);
            RecipeNetwork.sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.save_failed_plain"));
        }
    }

    private static ArrayList<ItemStack> copyStacks(java.util.List<ItemStack> source) {
        ArrayList<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : source) {
            copies.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return copies;
    }

    private static ArrayList<RecipeRecord> copyRecords(java.util.List<RecipeRecord> source) {
        ArrayList<RecipeRecord> copies = new ArrayList<>();
        for (RecipeRecord record : source) {
            if (record != null) {
                copies.add(RecipeRecord.loadFromNBT(record.saveToNBT()));
            }
        }
        return copies;
    }

    public static synchronized void applyPending(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        var server = player.getServer();
        pendingBy = null;
        if (!pendingRecipeReload) {
            if (pendingRecipeSync) {
                pendingRecipeSync = false;
                RecipeMemoryManager.syncRecipes(server);
            }
            return;
        }
        // A reload sends the recipes too.
        pendingRecipeReload = false;
        pendingRecipeSync = false;
        RecipeMemoryManager.reloadDatapacks(server).whenComplete((unused, error) -> {
            if (error == null) {
                return;
            }
            server.execute(() -> {
                synchronized (RecipeSaveManager.class) {
                    pendingRecipeReload = true;
                }
                LOGGER.error("Failed to reload RecipeModule datapack", error);
                RecipeNetwork.sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.save_failed_plain"));
            });
        });
    }

    public static synchronized void markApplied() {
        pendingRecipeReload = false;
        pendingRecipeSync = false;
        pendingBy = null;
    }
}
