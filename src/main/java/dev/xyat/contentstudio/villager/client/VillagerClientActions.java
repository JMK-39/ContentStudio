package dev.xyat.contentstudio.villager.client;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.contentstudio.villager.client.gui.VillagerFollowItemEditorPage;
import dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorPage;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

// NeoForge 26.1 no longer strips @OnlyIn members and warns about the annotation; this class is only used on the client.
//? if <26.1
@OnlyIn(Dist.CLIENT)
public final class VillagerClientActions {
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
        VillagerConfig.replaceTradeLists(groups, offers, overrides);
        VillagerConfig.enableVillagerTradeLateOverride = lateOverride;
        KineticGui.openChild(new VillagerTradeEditorPage());
    }

    public static void handleTradeSaveResult(boolean success, boolean notifySuccess) {
        if (success) {
            if (notifySuccess) {
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.saved"));
            }
            return;
        }
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.save_failed"));
    }
}
