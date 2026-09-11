package dev.xyat.contentstudio.loot.client;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.contentstudio.loot.client.gui.AbstractLootEditorScreen;
import dev.xyat.contentstudio.loot.client.gui.ChestLootEditorScreen;
import dev.xyat.contentstudio.loot.client.gui.LootEditorScreen;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class LootClientHandler {
    private static Screen pendingParentScreen;

    public static void requestOpenEditor(int mode) {
        pendingParentScreen = Minecraft.getInstance().screen;
        LootNetwork.requestOpenEditor(mode);
    }

    public static void openScreen(int mode, List<LootEntryInfo> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parentScreen = pendingParentScreen != null ? pendingParentScreen : minecraft.screen;
        pendingParentScreen = null;
        minecraft.setScreen(mode == LootEntryInfo.MODE_CHEST
                ? new ChestLootEditorScreen(entries, parentScreen)
                : new LootEditorScreen(mode, entries, parentScreen));
    }

    public static void applyDetail(int mode, String targetId, String lootTableId, String json, boolean overridden) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AbstractLootEditorScreen screen) {
            screen.applyDetail(mode, targetId, lootTableId, json, overridden);
        }
    }

    public static void applyResetPreview(int mode, String targetId, String lootTableId, String json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AbstractLootEditorScreen screen) {
            screen.applyResetPreview(mode, targetId, lootTableId, json);
        }
    }

    public static void applySaveResult(int mode, String targetId, String lootTableId, String json, boolean overridden, boolean success, Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AbstractLootEditorScreen screen) {
            screen.applySaveResult(mode, targetId, lootTableId, json, overridden, success, message);
        } else if (success) {
            GuiOverlay.toast("loots_save_result", message, GuiOverlay.Position.CENTER, 4000, 0, 0);
        } else {
            GuiOverlay.toast("loots_save_result", message, GuiOverlay.Position.CENTER, 4000, 0, 0);
        }
    }

    public static void applyGlobalRemoveDetail(List<GlobalRemoveRule> rules) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalRemoveDetail(rules);
        }
    }

    public static void applyGlobalRemoveSaveResult(List<GlobalRemoveRule> rules, boolean success, Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalRemoveSaveResult(rules, success, message);
        } else if (success) {
            GuiOverlay.toast("loots_global_remove_save_result", message, GuiOverlay.Position.CENTER, 4000, 0, 0);
        } else {
            GuiOverlay.toast("loots_global_remove_save_result", message, GuiOverlay.Position.CENTER, 4000, 0, 0);
        }
    }

    public static void applyGlobalExcludeDetail(List<String> lootTableIds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalExcludeDetail(lootTableIds);
        }
    }

    public static void applyGlobalExcludeSaveResult(List<String> lootTableIds, boolean success, Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalExcludeSaveResult(lootTableIds, success, message);
        } else if (success) {
            GuiOverlay.toast("loots_global_exclude_save_result", message, GuiOverlay.Position.CENTER, 4000, 0, 0);
        } else {
            GuiOverlay.toast("loots_global_exclude_save_result", message, GuiOverlay.Position.CENTER, 4000, 0, 0);
        }
    }

}
