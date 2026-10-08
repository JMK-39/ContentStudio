package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticContainerPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;

import dev.xyat.contentstudio.recipe.RecipeMenu;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.network.chat.Component;

public class RecipeHubPage extends KineticContainerPage<RecipeMenu> {
    private final NativeRecipeStationMenu stations=new NativeRecipeStationMenu(this::rebuild,this::openChild,this::isAttached,"","",null);

    public RecipeHubPage(RecipeMenu recipeMenu, Component title) {
        super(recipeMenu, title);
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        // 原 setParentScreen(新建的配置索引)：v2 容器页无法指定返回目标，返回到打开时的当前界面（见迁移报告）
        // Former setParentScreen(a fresh config index): v2 container pages cannot set a back target, so Back returns
        // to the screen current when the hub opened (see the migration report).
        // Fixed header, pinned vanilla entry, four scrollable mod rows and the existing session actions.
        setImageSize(600, 316);
        setTitleLabelPosition(8, 10);
        setInventoryLabelPosition(8, 1000);
    }

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    @Override
    protected void build(KineticUi ui) {
        RecipePreviewState.returnToPreview = false;

        int x=leftPos()+12, y=topPos()+26;
        ui.textField(x,y,448).value(stations.query).maxLength(256)
                .placeholder(NativeRecipeBrowserPage.tr("station_search")).onChange(v->stations.query=v).build();
        ui.button(x+456,y,120).text(NativeRecipeBrowserPage.tr("search_button")).onClick(stations::search).build();
        ui.itemButton(x,y+28,NativeRecipeStationMenu.CARD_WIDTH,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE))
                .text(NativeRecipeBrowserPage.tr("vanilla")).onClick(()->openChild(new VanillaRecipeHubPage())).build();
        stations.build(ui,x,y+76,576,168);
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
        stations.render(graphics,mouseX,mouseY);
    }

    @Override protected boolean onMouseScroll(dev.xyat.kineticcore.api.client.gui.input.ScrollInput i){return stations.wheel(i);}
    @Override protected boolean onMouseClick(dev.xyat.kineticcore.api.client.gui.input.MouseInput i){return stations.click(i);}
    @Override protected boolean onMouseDrag(dev.xyat.kineticcore.api.client.gui.input.MouseDragInput i){return stations.drag(i);}
    @Override protected boolean onMouseRelease(dev.xyat.kineticcore.api.client.gui.input.MouseInput i){return stations.release(i);}
}
