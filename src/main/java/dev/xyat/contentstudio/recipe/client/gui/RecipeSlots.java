package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;

/** Complete native recipe slot, centred with one scale for both axes. */
final class RecipeSlots {
    private static final KineticTexture BACKGROUND =
            KineticTexture.of("minecraft", "textures/gui/container/crafting_table.png");
    private static final int NATIVE_SIZE = 18;

    private RecipeSlots() {}

    static void draw(KineticGraphics graphics, int x, int y, int areaSize) {
        int size = Math.min(NATIVE_SIZE, areaSize);
        if (size <= 0) return;
        int offset = (areaSize - size) / 2;
        graphics.texture(BACKGROUND, x + offset, y + offset, size, size, 29f, 16f, NATIVE_SIZE, NATIVE_SIZE);
    }
}
