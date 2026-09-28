package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;

final class LootCheckerboard {
    private LootCheckerboard() {
    }

    static void draw(KineticGraphics graphics, int x, int y, int width, int height) {
        KineticTheme.itemGrid(graphics, x, y, width, height);
    }
}
