package dev.xyat.contentstudio.villager.client;

import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.contentstudio.villager.client.gui.VillagerFollowItemEditorScreen;
import dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorScreen;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class VillagerClientActions {
    private VillagerClientActions() {
    }

    public static void openFollowItemEditor(List<String> items) {
        Screen parent = KineticClientRuntime.currentScreen();
        KineticClientRuntime.openScreen(VillagerFollowItemEditorScreen.create(parent, items));
    }

    public static void handleFollowItemSaveResult(boolean success) {
        if (success) {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.villager.follow_item_editor.saved"));
        } else {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.villager.follow_item_editor.save_failed"));
        }
    }

    public static void openTradeEditor(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean lateOverride
    ) {
        Screen parent = KineticClientRuntime.currentScreen();
        VillagerConfig.replaceTradeLists(groups, offers, overrides);
        VillagerConfig.enableVillagerTradeLateOverride = lateOverride;
        KineticClientRuntime.openScreen(new VillagerTradeEditorScreen(parent));
    }

    public static void handleTradeSaveResult(boolean success, boolean notifySuccess) {
        if (success) {
            if (notifySuccess) {
                KineticOverlays.toast(Component.translatable("msg.contentstudio.villager.villager.trade.saved"));
            }
            return;
        }
        KineticOverlays.toast(Component.translatable("msg.contentstudio.villager.villager.trade.save_failed"));
    }
}
