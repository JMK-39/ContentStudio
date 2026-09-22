package dev.xyat.contentstudio.loot.client;

import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.contentstudio.loot.client.gui.AbstractLootEditorScreen;
import dev.xyat.contentstudio.loot.client.gui.ChestLootEditorScreen;
import dev.xyat.contentstudio.loot.client.gui.LootEditorScreen;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class LootClientHandler {
    private static Screen pendingParentScreen;

    public static void requestOpenEditor(int mode) {
        pendingParentScreen = KineticClientRuntime.currentScreen();
        LootNetwork.requestOpenEditor(mode);
    }

    public static void openScreen(int mode, List<LootEntryInfo> entries) {
        Screen parentScreen = pendingParentScreen != null ? pendingParentScreen : KineticClientRuntime.currentScreen();
        pendingParentScreen = null;
        KineticClientRuntime.openScreen(mode == LootEntryInfo.MODE_CHEST
                ? new ChestLootEditorScreen(entries, parentScreen)
                : new LootEditorScreen(mode, entries, parentScreen));
    }

    public static void applyDetail(int mode, String targetId, String lootTableId, String json, boolean overridden) {
        if (KineticClientRuntime.currentScreen() instanceof AbstractLootEditorScreen screen) {
            screen.applyDetail(mode, targetId, lootTableId, json, overridden);
        }
    }

    public static void applyResetPreview(int mode, String targetId, String lootTableId, String json) {
        if (KineticClientRuntime.currentScreen() instanceof AbstractLootEditorScreen screen) {
            screen.applyResetPreview(mode, targetId, lootTableId, json);
        }
    }

    public static void applySaveResult(int mode, String targetId, String lootTableId, String json, boolean overridden, boolean success, Component message) {
        if (KineticClientRuntime.currentScreen() instanceof AbstractLootEditorScreen screen) {
            screen.applySaveResult(mode, targetId, lootTableId, json, overridden, success, message);
        } else if (success) {
            KineticOverlays.toast("loots_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        } else {
            KineticOverlays.toast("loots_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        }
    }

    public static void applyGlobalRemoveDetail(List<GlobalRemoveRule> rules) {
        if (KineticClientRuntime.currentScreen() instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalRemoveDetail(rules);
        }
    }

    public static void applyGlobalRemoveSaveResult(List<GlobalRemoveRule> rules, boolean success, Component message) {
        if (KineticClientRuntime.currentScreen() instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalRemoveSaveResult(rules, success, message);
        } else if (success) {
            KineticOverlays.toast("loots_global_remove_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        } else {
            KineticOverlays.toast("loots_global_remove_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        }
    }

    public static void applyGlobalExcludeDetail(List<String> lootTableIds) {
        if (KineticClientRuntime.currentScreen() instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalExcludeDetail(lootTableIds);
        }
    }

    public static void applyGlobalExcludeSaveResult(List<String> lootTableIds, boolean success, Component message) {
        if (KineticClientRuntime.currentScreen() instanceof ChestLootEditorScreen screen) {
            screen.applyGlobalExcludeSaveResult(lootTableIds, success, message);
        } else if (success) {
            KineticOverlays.toast("loots_global_exclude_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        } else {
            KineticOverlays.toast("loots_global_exclude_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        }
    }

}
