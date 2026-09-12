package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.contentstudio.recipe.RecipeMenu;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import dev.xyat.kineticcore.api.client.screen.KineticContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RecipeHubScreen extends KineticContainerScreen<RecipeMenu> {

    public RecipeHubScreen(RecipeMenu recipeMenu, Inventory inv, Component title) {
        super(recipeMenu, inv, title);
        this.imageWidth = 460;
        this.imageHeight = 250;
        this.inventoryLabelY = 1000;
        this.titleLabelY = 10;

        useResponsiveContainer(640F, 360F, 6);
    }

    public void showToast(Component msg) {
        GuiOverlay.toast(msg);
    }

    @Override
    protected void init() {
        super.init();
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

            this.addRenderableWidget(new IconButton(x, y, buttonW, buttonH, type, button -> {
                if (Minecraft.getInstance().player != null) {
                    RecipeNetwork.CHANNEL.sendToServer(
                            new RecipeNetwork.RequestEditPacket("", type.name(), -1)
                    );
                }
            }));
            i++;
        }

        int bottomBtnW = 108;
        int bottomSpacing = 8;
        int bottomY = this.topPos + this.imageHeight - 34;
        int totalBottomWidth = bottomBtnW * 3 + bottomSpacing * 2;
        int bottomStartX = this.leftPos + (this.imageWidth - totalBottomWidth) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.recipe.recipehud.back"), b -> onClose())
                .bounds(bottomStartX, bottomY, bottomBtnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.recipe.recipehud.btn.hub"), b -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                ItemSearchIndex.prepareCache(() -> {
                    this.showToast(Component.translatable("msg.contentstudio.recipe.recipehud.requesting_data"));
                    RecipeNetwork.requestOpen();
                });
            }
        }).bounds(bottomStartX + bottomBtnW + bottomSpacing, bottomY, bottomBtnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.recipe.recipehud.manage.title"), b -> {
            this.showToast(Component.translatable("msg.contentstudio.recipe.recipehud.requesting_recipes"));
            RecipeNetwork.requestRecipeRecords();
        }).bounds(bottomStartX + (bottomBtnW + bottomSpacing) * 2, bottomY, bottomBtnW, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        RecipeEditSessionState.applyPendingAndClear();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD000000);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFFFFFFFF);
    }

    @Override
    protected void renderScreenOverlay(
            GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        for (var renderable : renderables) {
            if (renderable instanceof IconButton button
                    && button.isMouseOver(virtualMouseX, virtualMouseY)) {
                GuiOverlay.requestTooltip(java.util.List.of(button.type.getTitle()), mouseX, mouseY);
                return;
            }
        }
    }

    private static class IconButton extends Button {
        private final RecipeRegistry.EditorType type;
        public IconButton(int x, int y, int w, int h, RecipeRegistry.EditorType type, OnPress onPress) {
            super(x, y, w, h, Component.empty(), onPress, DEFAULT_NARRATION);
            this.type = type;
        }
        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.renderFakeItem(new ItemStack(type.getIcon()), this.getX() + 8, this.getY() + 11);
            Component label = type.getTitle();
            int textX = this.getX() + 32;
            int textY = this.getY() + (this.height - 8) / 2;
            graphics.drawString(Minecraft.getInstance().font, label, textX, textY, 0xFFFFFFFF, false);
        }
    }
}
