package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticContainerPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;

import dev.xyat.contentstudio.recipe.RecipeMenu;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.network.chat.Component;

public class RecipeHubPage extends KineticContainerPage<RecipeMenu> {

    public RecipeHubPage(RecipeMenu recipeMenu, Component title) {
        super(recipeMenu, title);
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        // 原 setParentScreen(新建的配置索引)：v2 容器页无法指定返回目标，返回到打开时的当前界面（见迁移报告）
        // Former setParentScreen(a fresh config index): v2 container pages cannot set a back target, so Back returns
        // to the screen current when the hub opened (see the migration report).
        setImageSize(460, 250);
        setTitleLabelPosition(8, 10);
        setInventoryLabelPosition(8, 1000);
    }

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    @Override
    protected void build(KineticUi ui) {
        RecipePreviewState.returnToPreview = false;

        int buttonW = 128;
        int buttonH = 38;
        int paddingX = 10;
        int paddingY = 10;
        int columns = 3;

        int totalWidth = columns * buttonW + (columns - 1) * paddingX;
        int startX = leftPos() + (imageWidth() - totalWidth) / 2;
        int startY = topPos() + 42;

        int i = 0;
        for (RecipeRegistry.EditorType type : RecipeRegistry.EditorType.values()) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * (buttonW + paddingX);
            int y = startY + row * (buttonH + paddingY);

            ui.itemButton(x, y, buttonW, new net.minecraft.world.item.ItemStack(type.getIcon()))
                    .text(type.getTitle())
                    .tooltip(type.getTitle())
                    .onClick(() -> {
                        if (KineticClientRuntime.localPlayer() != null) {
                            RecipeNetwork.requestEdit("", type.name(), -1);
                        }
                    })
                    .build();
            i++;
        }

        int bottomBtnW = 108;
        int bottomSpacing = 8;
        int bottomY = topPos() + imageHeight() - 34;
        int totalBottomWidth = bottomBtnW * 3 + bottomSpacing * 2;
        int bottomStartX = leftPos() + (imageWidth() - totalBottomWidth) / 2;

        ui().button(bottomStartX, bottomY, bottomBtnW).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.back")).onClick(this::close).build();

        ui().button(bottomStartX + bottomBtnW + bottomSpacing, bottomY, bottomBtnW).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.btn.hub")).onClick(() -> {
                    if (KineticClientRuntime.localPlayer() != null) {
                        KineticItemSearch.prepare(() -> {
                            this.showToast(KineticI18n.translatable("msg.contentstudio.recipe.recipehud.requesting_data"));
                            RecipeNetwork.requestOpen();
                        });
                    }
                }).build();

        ui().button(bottomStartX + (bottomBtnW + bottomSpacing) * 2, bottomY, bottomBtnW).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.manage.title")).onClick(() -> {
                    this.showToast(KineticI18n.translatable("msg.contentstudio.recipe.recipehud.requesting_recipes"));
                    RecipeNetwork.requestRecipeRecords();
                }).build();
    }


    @Override
    protected boolean onCloseRequested() {
        RecipeEditSessionState.applyPendingAndClear();
        return false;
    }

    @Override
    protected void renderContainerBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panelAlt(graphics, leftPos(), topPos(), imageWidth(), imageHeight());
    }

}
