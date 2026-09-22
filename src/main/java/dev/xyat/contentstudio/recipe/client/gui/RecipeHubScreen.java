package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.contentstudio.recipe.RecipeMenu;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.screen.KineticContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class RecipeHubScreen extends KineticContainerScreen<RecipeMenu> {

    public RecipeHubScreen(RecipeMenu recipeMenu, Inventory inv, Component title) {
        super(recipeMenu, inv, title);
        setParentScreen(RecipeNavigationState.resolveHubParent(KineticClientRuntime.currentScreen()));
        this.imageWidth = 460;
        this.imageHeight = 250;
        this.inventoryLabelY = 1000;
        this.titleLabelY = 10;
}

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    @Override
    protected void buildUi() {
        RecipePreviewState.returnToPreview = false;

        int buttonW = 128;
        int buttonH = 38;
        int paddingX = 10;
        int paddingY = 10;
        int columns = 3;

        int totalWidth = columns * buttonW + (columns - 1) * paddingX;
        int startX = this.leftPos + (this.imageWidth - totalWidth) / 2;
        int startY = this.topPos + 42;

        int i = 0;
        for (RecipeRegistry.EditorType type : RecipeRegistry.EditorType.values()) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * (buttonW + paddingX);
            int y = startY + row * (buttonH + paddingY);

            addItemButton(
                    x, y, buttonW, new net.minecraft.world.item.ItemStack(type.getIcon()), type.getTitle(), type.getTitle(),
                    () -> {
                        if (KineticClientRuntime.localPlayer() != null) {
                            RecipeNetwork.requestEdit("", type.name(), -1);
                        }
                    }
            );
            i++;
        }

        int bottomBtnW = 108;
        int bottomSpacing = 8;
        int bottomY = this.topPos + this.imageHeight - 34;
        int totalBottomWidth = bottomBtnW * 3 + bottomSpacing * 2;
        int bottomStartX = this.leftPos + (this.imageWidth - totalBottomWidth) / 2;

        addButton(
                bottomStartX, bottomY, bottomBtnW,
                Component.translatable("gui.contentstudio.recipe.recipehud.back"),
                null,
                this::onClose
        );

        addButton(
                bottomStartX + bottomBtnW + bottomSpacing, bottomY, bottomBtnW,
                Component.translatable("gui.contentstudio.recipe.recipehud.btn.hub"),
                null,
                () -> {
                    if (KineticClientRuntime.localPlayer() != null) {
                        KineticItemSearch.prepare(() -> {
                            this.showToast(Component.translatable("msg.contentstudio.recipe.recipehud.requesting_data"));
                            RecipeNetwork.requestOpen();
                        });
                    }
                }
        );

        addButton(
                bottomStartX + (bottomBtnW + bottomSpacing) * 2, bottomY, bottomBtnW,
                Component.translatable("gui.contentstudio.recipe.recipehud.manage.title"),
                null,
                () -> {
                    this.showToast(Component.translatable("msg.contentstudio.recipe.recipehud.requesting_recipes"));
                    RecipeNetwork.requestRecipeRecords();
                }
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected boolean handleCloseRequest() {
        RecipeEditSessionState.applyPendingAndClear();
        return false;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiTheme.panelAlt(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

}
