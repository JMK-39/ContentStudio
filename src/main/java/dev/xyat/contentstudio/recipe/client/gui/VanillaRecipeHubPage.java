package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.world.item.ItemStack;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** The original vanilla workstation buttons, unchanged except that they open from the recipe hub's vanilla entry. */
public final class VanillaRecipeHubPage extends KineticPage {
    // The same cards as the workstation list: three columns from the left edge, right under the header.
    private static final int BUTTON_W = NativeRecipeStationMenu.cardWidth(632), BUTTON_H = 38, GAP = 4, COLUMNS = 3, PANEL_W = 640;
    private int left, top, panelH;

    public VanillaRecipeHubPage() {
        super(tr("vanilla"));
        setPausesGame(false);
    }

    @Override protected void build(KineticUi ui) {
        int rows = (RecipeRegistry.EditorType.values().length + COLUMNS - 1) / COLUMNS;
        panelH = 360;
        left = (width() - PANEL_W) / 2;
        top = (height() - panelH) / 2;
        int startX = left + 4;
        int i = 0;
        for (var type : RecipeRegistry.EditorType.values()) {
            int x = startX + i % COLUMNS * (BUTTON_W + GAP), y = top + 28 + i / COLUMNS * (BUTTON_H + GAP);
            ui.itemButton(x, y, BUTTON_W, new ItemStack(type.getIcon())).text(type.getTitle()).tooltip(type.getTitle())
                    .onClick(() -> { if (KineticClientRuntime.localPlayer() != null) RecipeNetwork.requestEdit("", type.name(), -1); }).build();
            i++;
        }
        ui.button(left + 4, top + 4, 40).text(tr("back")).onClick(this::close).build();
    }

    @Override protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        KineticTheme.panel(g, left, top, PANEL_W, panelH);
        KineticTheme.separator(g, left + 4, top + 24, PANEL_W - 8);
        g.scrollingTextCentered(title(), left + PANEL_W / 2, top + 8, PANEL_W - 2 * 48, 0xFFFFAA00, false);
    }
}
