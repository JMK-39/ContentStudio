package dev.xyat.contentstudio.recipe.network;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.contentstudio.recipe.RecipeDatabase;
import dev.xyat.contentstudio.recipe.client.gui.RecipeHubScreen;
import dev.xyat.contentstudio.recipe.client.gui.RecipePreviewScreen;
import dev.xyat.contentstudio.recipe.client.gui.RecipeRemovalScreen;
import dev.xyat.contentstudio.recipe.client.gui.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RecipeNetworkClient {
    private static java.lang.ref.WeakReference<RecipeRemovalScreen> recipeBrowser = new java.lang.ref.WeakReference<>(null);

    public static void requestItemRecipes(RecipeRemovalScreen screen, net.minecraft.world.item.ItemStack item) {
        recipeBrowser = new java.lang.ref.WeakReference<>(screen);
        RecipeNetwork.CHANNEL.sendToServer(new RecipeNetwork.RequestItemRecipesPacket(item));
    }
    private RecipeNetworkClient() {
    }

    public static void handleItemRecipes(RecipeNetwork.ItemRecipesPacket packet) {
        RecipeRemovalScreen screen = recipeBrowser.get();
        if (screen != null) screen.acceptRecipes(packet.item(), packet.recipes(), packet.dataError());
    }

    public static void handleSync(RecipeNetwork.SyncPacket packet) {
        Screen current = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new RecipeRemovalScreen(current, packet.entries(), packet.modifiedItems()));
    }

    public static void handleRecipeRecords(RecipeNetwork.RecipeRecordsSyncPacket packet) {
        RecipeDatabase.setClientRecords(packet.records());
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof RecipePreviewScreen preview) {
            preview.refreshFromServer();
        } else if (current instanceof RecipeScreen) {
            return;
        } else {
            Minecraft.getInstance().setScreen(new RecipePreviewScreen(current));
        }
    }

    public static void handleToast(RecipeNetwork.ToastPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RecipeHubScreen value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipeScreen value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipeRemovalScreen value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipePreviewScreen value) {
            value.showToast(packet.message());
        } else {
            GuiOverlay.toast(packet.message());
        }
    }
}
