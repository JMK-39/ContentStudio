package dev.xyat.contentstudio.villager.client;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.contentstudio.villager.client.gui.VillagerFollowItemEditorPage;
import dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorPage;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

// NeoForge 26.1 no longer strips @OnlyIn members and warns about the annotation; this class is only used on the client.
//? if <26.1
@OnlyIn(Dist.CLIENT)
public final class VillagerClientActions {
    private static boolean requestPending;
    private static KineticGui.NavigationParent requestParent;
    private static LocalPlayer sessionPlayer;
    private static long sessionRevision = -1;
    private static VillagerTradeEditorPage tradeSession;
    private VillagerClientActions() {
    }

    public static void openFollowItemEditor(List<String> items) {
        KineticGui.openChild(VillagerFollowItemEditorPage.create(items));
    }

    public static void handleFollowItemSaveResult(boolean success) {
        if (success) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.follow_item_editor.saved"));
        } else {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.follow_item_editor.save_failed"));
        }
    }

    public static void openTradeEditor(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean lateOverride
    ) {
        openTradeEditor(groups, offers, overrides, lateOverride, VillagerConfig.getTradeSourceModeValue());
    }

    public static boolean requestTradeEditor() {
        if (!hasPlayer()) return false;
        ensureConnection();
        if (requestPending) return true;
        if (tradeSession != null) {
            tradeSession.resumeFromShortcut();
            KineticGui.openChild(tradeSession, KineticGui.captureNavigationParent());
            return true;
        }
        requestParent = KineticGui.captureNavigationParent();
        requestPending = true;
        VillagerNetwork.requestTradeEditor();
        return true;
    }

    public static boolean toggleTradeEditor() {
        if (!hasPlayer()) return false;
        ensureConnection();
        VillagerTradeEditorPage current = KineticGui.currentPage(VillagerTradeEditorPage.class);
        if (current != null) {
            if (current.isSavePending()) return true;
            tradeSession = current;
            current.suspendForShortcut();
            current.navigateBack();
            return true;
        }
        if (KineticGui.isScreenOpen() && !(KineticClientRuntime.currentScreen() instanceof CreativeModeInventoryScreen)) return false;
        return requestTradeEditor();
    }

    public static void openTradeEditor(List<String> groups, List<String> offers, List<String> overrides,
                                       boolean lateOverride, String sourceMode) {
        if (!hasPlayer()) return;
        if (requestPending && !sameConnection()) { clearSession(); return; }
        ensureConnection();
        KineticGui.NavigationParent parent = requestPending ? requestParent : KineticGui.captureNavigationParent();
        requestPending = false;
        requestParent = null;
        VillagerConfig.replaceTradeLists(groups, offers, overrides);
        VillagerConfig.enableVillagerTradeLateOverride = lateOverride;
        VillagerConfig.setTradeSourceMode(sourceMode);
        tradeSession = new VillagerTradeEditorPage();
        KineticGui.openChild(tradeSession, parent);
    }

    public static void handleTradeSaveResult(boolean success, boolean notifySuccess) {
        handleTradeSaveResult(success, notifySuccess, success ? "" : "unknown");
    }

    public static void handleTradeSaveResult(boolean success, boolean notifySuccess, String failureCode) {
        VillagerTradeEditorPage page = editorForReply();
        if (page != null) { page.handleTradeSaveResult(success, failureCode); return; }
        if (success) {
            if (notifySuccess) {
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.saved"));
            }
            return;
        }
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.save_failed." + safeFailureCode(failureCode)));
    }

    public static void handleTradeSourceModeResult(boolean success, String mode, String failureCode) {
        VillagerTradeEditorPage page = editorForReply();
        if (page != null) { page.handleTradeSourceModeResult(success, mode, safeFailureCode(failureCode)); return; }
        if (sameConnection() && success) VillagerConfig.setTradeSourceMode(mode);
    }

    public static void handleTradeEditorOpenDenied(String failureCode) {
        if (!requestPending || !sameConnection()) { clearSession(); return; }
        requestPending = false;
        requestParent = null;
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.open_failed." + safeFailureCode(failureCode)));
    }

    public static void onTradeEditorSessionClosed(VillagerTradeEditorPage page) {
        if (tradeSession == page) clearSession();
    }

    private static VillagerTradeEditorPage editorForReply() {
        if (!sameConnection()) { clearSession(); return null; }
        VillagerTradeEditorPage page = KineticGui.findPage(VillagerTradeEditorPage.class);
        return page != null ? page : tradeSession;
    }

    private static boolean hasPlayer() {
        if (KineticClientRuntime.localPlayer() != null && KineticClientRuntime.currentLevel() != null) return true;
        clearSession();
        return false;
    }

    private static boolean sameConnection() {
        return sessionPlayer != null && sessionPlayer == KineticClientRuntime.localPlayer()
                && sessionRevision == KineticClientRuntime.connectionRevision();
    }

    private static void ensureConnection() {
        if (!sameConnection()) clearSession();
        sessionPlayer = KineticClientRuntime.localPlayer();
        sessionRevision = KineticClientRuntime.connectionRevision();
    }

    private static void clearSession() {
        requestPending = false;
        requestParent = null;
        tradeSession = null;
        sessionPlayer = null;
        sessionRevision = -1;
    }

    private static String safeFailureCode(String code) {
        return switch (code == null ? "unknown" : code) {
            case "permission_denied", "server_unavailable", "invalid_data", "invalid_mode", "write_failed" -> code;
            default -> "unknown";
        };
    }
}
