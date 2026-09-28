package dev.xyat.contentstudio.loot.client;

import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.contentstudio.loot.client.gui.AbstractLootEditorPage;
import dev.xyat.contentstudio.loot.client.gui.ChestLootEditorPage;
import dev.xyat.contentstudio.loot.client.gui.LootEditorPage;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import net.minecraft.network.chat.Component;

import java.util.List;

public class LootClientHandler {
    public static void requestOpenEditor(int mode) {
        LootNetwork.requestOpenEditor(mode);
    }

    // 返回到回包时的当前界面（无界面时回到游戏）/ Back returns to the screen current when the reply arrives (or the game).
    public static void openScreen(int mode, List<LootEntryInfo> entries) {
        KineticGui.openChild(mode == LootEntryInfo.MODE_CHEST
                ? new ChestLootEditorPage(entries)
                : new LootEditorPage(mode, entries));
    }

    public static void applyDetail(int mode, String targetId, String lootTableId, String json, boolean overridden) {
        if (KineticGui.currentPage() instanceof AbstractLootEditorPage page) {
            page.applyDetail(mode, targetId, lootTableId, json, overridden);
        }
    }

    public static void applyResetPreview(int mode, String targetId, String lootTableId, String json) {
        if (KineticGui.currentPage() instanceof AbstractLootEditorPage page) {
            page.applyResetPreview(mode, targetId, lootTableId, json);
        }
    }

    public static void applySaveResult(int mode, String targetId, String lootTableId, String json, boolean overridden, boolean success, Component message) {
        if (KineticGui.currentPage() instanceof AbstractLootEditorPage page) {
            page.applySaveResult(mode, targetId, lootTableId, json, overridden, success, message);
        } else if (success) {
            KineticOverlays.toast("loots_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        } else {
            KineticOverlays.toast("loots_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        }
    }

    public static void applyGlobalRemoveDetail(List<GlobalRemoveRule> rules) {
        if (KineticGui.currentPage() instanceof ChestLootEditorPage page) {
            page.applyGlobalRemoveDetail(rules);
        }
    }

    public static void applyGlobalRemoveSaveResult(List<GlobalRemoveRule> rules, boolean success, Component message) {
        if (KineticGui.currentPage() instanceof ChestLootEditorPage page) {
            page.applyGlobalRemoveSaveResult(rules, success, message);
        } else if (success) {
            KineticOverlays.toast("loots_global_remove_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        } else {
            KineticOverlays.toast("loots_global_remove_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        }
    }

    public static void applyGlobalExcludeDetail(List<String> lootTableIds) {
        if (KineticGui.currentPage() instanceof ChestLootEditorPage page) {
            page.applyGlobalExcludeDetail(lootTableIds);
        }
    }

    public static void applyGlobalExcludeSaveResult(List<String> lootTableIds, boolean success, Component message) {
        if (KineticGui.currentPage() instanceof ChestLootEditorPage page) {
            page.applyGlobalExcludeSaveResult(lootTableIds, success, message);
        } else if (success) {
            KineticOverlays.toast("loots_global_exclude_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        } else {
            KineticOverlays.toast("loots_global_exclude_save_result", message, KineticOverlays.Position.CENTER, 4000, 0, 0);
        }
    }

}
