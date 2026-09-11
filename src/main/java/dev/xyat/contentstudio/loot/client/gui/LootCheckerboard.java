package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

final class LootCheckerboard {
    private LootCheckerboard() {
    }

    static void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, ItemStack.EMPTY, x, y, width, height, 4, false);
    }

    static void draw(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height) {
        draw(graphics, stack, x, y, width, height, 4, false);
    }

    static void draw(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height, boolean hovered) {
        draw(graphics, stack, x, y, width, height, 4, hovered);
    }

    static void draw(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height, int cellSize, boolean hovered) {
        GuiTheme.itemSlot(graphics, stack, x, y, width, height, cellSize, hovered);
    }
}
