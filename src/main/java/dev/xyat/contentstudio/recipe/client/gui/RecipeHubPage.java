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
        // Fixed header and pinned vanilla entry; the remaining canvas belongs to workstation cards.
        setImageSize(640, 360);
        setTitleLabelPosition(8, 1000);
        setInventoryLabelPosition(8, 1000);
    }

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    @Override
    protected void build(KineticUi ui) {
        RecipePreviewState.returnToPreview = false;

        int x=leftPos()+4, y=topPos()+26;
        // The cards filter while typing; there is no separate search button.
        ui.textField(x,y,632).value(stations.query).maxLength(256)
                .placeholder(NativeRecipeBrowserPage.tr("station_search")).onChange(stations::searchLive).build();
        ui.itemButton(x,y+28,NativeRecipeStationMenu.cardWidth(632),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE))
                .text(NativeRecipeBrowserPage.tr("vanilla")).onClick(()->openChild(new VanillaRecipeHubPage())).build();
        stations.build(ui,x,y+70,632,260);

        ui().button(leftPos() + 4, topPos() + 4, 40).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.back")).onClick(this::close).build();
        // One management entry; added, removed and changed third-party recipes are its tabs.
        // Only as wide as its label.
        int manageW = MANAGE_W;
        ui().button(leftPos() + imageWidth() - manageW - 4, topPos() + 4, manageW).text(RecipeManagementTabs.label("title"))
                .tooltip(RecipeManagementTabs.label("title.tooltip")).onClick(() -> {
                    if (KineticClientRuntime.localPlayer() == null) return;
                    this.showToast(KineticI18n.translatable("msg.contentstudio.recipe.recipehud.requesting_recipes"));
                    RecipeManagementTabs.open(RecipeManagementTabs.ADDED);
                }).build();
    }


    private static final int MANAGE_W = 56;

    /** The vanilla recipe hub and the three management tabs, for screenshot tools. */
    @Override
    public java.util.List<ReviewView> reviewViews() {
        java.util.List<ReviewView> views = new java.util.ArrayList<>();
        views.add(new ReviewView("vanilla", () -> openChild(new VanillaRecipeHubPage())));
        views.addAll(RecipeManagementTabs.views());
        return views;
    }

    @Override
    protected boolean onCloseRequested() {
        RecipeEditSessionState.applyPendingAndClear();
        return false;
    }

    @Override
    protected void renderContainerBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, leftPos(), topPos(), imageWidth(), imageHeight());
        // Centred on the screen, clear of Back and the management button on both sides.
        graphics.scrollingTextCentered(title(), leftPos() + imageWidth() / 2, topPos() + 8, imageWidth() - 2 * (4 + MANAGE_W + 4),
                KineticTheme.current().text(), false);
        stations.render(graphics,mouseX,mouseY);
    }

    @Override protected boolean onMouseScroll(dev.xyat.kineticcore.api.client.gui.input.ScrollInput i){return stations.wheel(i);}
    @Override protected boolean onMouseClick(dev.xyat.kineticcore.api.client.gui.input.MouseInput i){return stations.click(i);}
    @Override protected boolean onMouseClickCapture(dev.xyat.kineticcore.api.client.gui.input.MouseInput i){return stations.click(i);}
    @Override protected boolean onMouseDrag(dev.xyat.kineticcore.api.client.gui.input.MouseDragInput i){return stations.drag(i);}
    @Override protected boolean onMouseRelease(dev.xyat.kineticcore.api.client.gui.input.MouseInput i){return stations.release(i);}
}
