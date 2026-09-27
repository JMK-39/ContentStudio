package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import net.minecraft.client.gui.GuiGraphics;

final class LootCheckerboard {
    private LootCheckerboard() {
    }

    static void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        GuiTheme.itemGrid(graphics, x, y, width, height);
    }
}
