package dev.xyat.contentstudio.villager.client;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.contentstudio.villager.client.gui.VillagerFollowItemEditorScreen;
import dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorScreen;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import net.minecraft.client.Minecraft;
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
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        minecraft.setScreen(VillagerFollowItemEditorScreen.create(parent, items));
    }

    public static void handleFollowItemSaveResult(boolean success) {
        if (success) {
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.follow_item_editor.saved"));
        } else {
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.follow_item_editor.save_failed"));
        }
    }

    public static void openTradeEditor(
            List<String> groups,
            List<String> offers,
            List<String> overrides
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        VillagerConfig.replaceTradeLists(groups, offers, overrides);
        minecraft.setScreen(new VillagerTradeEditorScreen(parent));
    }

    public static void handleTradeSaveResult(boolean success, boolean notifySuccess) {
        if (success) {
            if (notifySuccess) {
                GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.saved"));
            }
            return;
        }
        GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.save_failed"));
    }
}
