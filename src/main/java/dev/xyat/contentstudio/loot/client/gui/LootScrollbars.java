package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;

/**
 * 原 KineticScroll 裸滚动条状态工具与 GuiTheme.scrollbar 的等价实现（核心 v2 未公开这些逐像素的小数偏移工具）。
 * Equivalents of the old raw KineticScroll state helpers and GuiTheme.scrollbar (core v2 does not expose these
 * fractional-offset helpers).
 */
final class LootScrollbars {
    private LootScrollbars() {
    }

    /** 原 KineticScroll.stateThumbHeight / Former KineticScroll.stateThumbHeight. */
    static int thumbHeight(int trackHeight, int visibleItems, int totalItems, int minHeight) {
        if (trackHeight <= 0) return 0;
        if (totalItems <= 0) return trackHeight;
        int minimum = Math.min(trackHeight, Math.max(1, minHeight));
        int calculated = (int) ((double) Math.max(0, visibleItems) / totalItems * trackHeight);
        return Math.min(trackHeight, Math.max(minimum, calculated));
    }

    /** 原 KineticScroll.stateOffsetFromPointerPrecise / Former KineticScroll.stateOffsetFromPointerPrecise. */
    static double offsetFromPointer(double pointerY, int trackY, int trackHeight, int thumbHeight, int maxOffset) {
        if (!Double.isFinite(pointerY) || trackHeight <= 0 || maxOffset <= 0) return 0D;
        double relativeY = pointerY - trackY - thumbHeight / 2.0D;
        double scrollableHeight = (double) trackHeight - thumbHeight;
        if (scrollableHeight <= 0D) return 0D;
        return Math.max(0D, Math.min(maxOffset, (relativeY / scrollableHeight) * maxOffset));
    }

    /**
     * 原 KineticScroll.renderScrollbarState：无边框、视觉宽度最多 4 像素。
     * Former KineticScroll.renderScrollbarState: borderless, at most 4 px visual width.
     */
    static void renderState(KineticGraphics g, int mouseX, int mouseY, int x, int y, int width, int height,
                            int thumbHeight, int maxOffset, double offset, boolean dragging) {
        if (maxOffset <= 0 || height <= 0 || width <= 0) return;
        int thumbH = Math.max(1, Math.min(height, thumbHeight));
        double safeOffset = Math.max(0D, Math.min(maxOffset, offset));
        int thumbY = y + (int) Math.round(safeOffset / maxOffset * (height - thumbH));
        int visualWidth = Math.min(4, Math.max(1, width));
        int visualX = x + Math.max(0, width - visualWidth);
        boolean hovered = mouseX >= visualX && mouseX <= visualX + visualWidth
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        KineticTheme.Palette theme = KineticTheme.current();
        g.fill(visualX, y, visualX + visualWidth, y + height, theme.scrollTrack());
        g.fill(visualX, thumbY, visualX + visualWidth, thumbY + thumbH,
                dragging || hovered ? theme.scrollThumbHover() : theme.scrollThumb());
    }

    /**
     * 原 GuiTheme.scrollbar(graphics, …, thumbHeight, maxOffset, offset, dragging)：带边框的主题滚动条。
     * Former GuiTheme.scrollbar(graphics, …, thumbHeight, maxOffset, offset, dragging): bordered themed scrollbar.
     */
    static void renderThemed(KineticGraphics g, int mouseX, int mouseY, int x, int y, int width, int height,
                             int thumbHeight, int maxOffset, double offset, boolean dragging) {
        if (maxOffset <= 0 || width <= 0 || height <= 0) return;
        KineticTheme.Palette palette = KineticTheme.current();
        int safeThumb = Math.max(1, Math.min(height, thumbHeight));
        double safeOffset = Math.max(0D, Math.min(offset, maxOffset));
        int thumbY = y + (int) Math.round((height - safeThumb) * (safeOffset / maxOffset));
        boolean hovered = KineticTheme.hovering(mouseX, mouseY, x, thumbY, width, safeThumb);
        g.fill(x, y, x + width, y + height, palette.border());
        if (width > 2 && height > 2) {
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, palette.scrollTrack());
        }
        g.fill(x, thumbY, x + width, thumbY + safeThumb,
                dragging || hovered ? palette.scrollThumbHover() : palette.scrollThumb());
    }
}
