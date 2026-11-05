package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticSizes;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;

import dev.xyat.kineticcore.api.client.search.KineticSearch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/** Browse the selected item's real recipe types as cards (icon, name, recipe count, ID), four per row. */
final class RecipeTypeFilterPage extends KineticPage {
    private static final int TEXT_GAP = 4;
    private static final int SEARCH_W = 196;
    // Cards: four per row, 2 px apart, below the header row and its divider.
    private static final int GRID_X = 4, GRID_Y = 28, GRID_BOTTOM = 356;
    // A card holds a standard item slot (KineticSizes) with 2 px around it and two text lines beside it.
    private static final int COLS = 4, GAP = 2, CARD_H = KineticSizes.ITEM_SLOT + 4, ROW_PITCH = CARD_H + GAP;
    private static final int SCROLL_X = 632, GRID_W = SCROLL_X - 2 - GRID_X;
    private static final int CARD_W = (GRID_W - (COLS - 1) * GAP) / COLS, COL_PITCH = CARD_W + GAP;
    private static final int VISIBLE_ROWS = (GRID_BOTTOM - GRID_Y + GAP) / ROW_PITCH;
    private static final int GRID_H = VISIBLE_ROWS * ROW_PITCH - GAP;
    private record Row(ResourceLocation type, long count) { }

    private final RecipeRemovalPage parent;
    private final List<Row> all = new ArrayList<>();
    private final List<Row> visible = new ArrayList<>();
    private final KineticScrollController scroll = new KineticScrollController();
    private final ResourceLocation selected;
    private KineticAutoCompleteField search;
    private String query = "";

    RecipeTypeFilterPage(RecipeRemovalPage parent, Map<ResourceLocation, Long> counts,
                           ResourceLocation selected) {
        super(RecipeRemovalPage.tr("filter_type"));
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        this.parent = parent;
        this.selected = selected;
        counts.forEach((type, count) -> all.add(new Row(type, count)));
        // 中键跳回当前生效的类型筛选卡片所在行 / Middle-click jumps back to the row of the active type filter.
        scroll.bindSelection(() -> {
            int index = selectedIndex();
            return index < 0 ? -1 : index / COLS;
        });
    }

    private int selectedIndex() {
        for (int i = 0; i < visible.size(); i++) {
            if (Objects.equals(visible.get(i).type(), selected)) return i;
        }
        return -1;
    }

    @Override protected void build(KineticUi ui) {
        ui().button(4, 4, 40).text(RecipeRemovalPage.tr("back")).onClick(this::close).build();
        search = ui().autoComplete(48, 4, SEARCH_W, KineticSuggestion.fromStrings(() -> all.stream()
                        .map(row -> row.type().toString()).toList())).label(RecipeRemovalPage.tr("filter_type_search")).placeholder(RecipeRemovalPage.tr("filter_type_search")).build();
        search.onTextChange(value -> { query = value; refresh(); scroll.setOffset(0); });
        refresh();
    }

    private void refresh() {
        visible.clear();
        visible.add(new Row(null, all.stream().mapToLong(Row::count).sum()));
        for (Row row : all) {
            if (KineticSearch.match(row.type().toString() + " "
                    + RecipeRemovalPage.typeName(row.type()).getString(), query)) visible.add(row);
        }
        scroll.update(rowCount(), VISIBLE_ROWS);
    }

    private int rowCount() {
        return (visible.size() + COLS - 1) / COLS;
    }

    @Override protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, 640, 360);
        int titleX = 48 + SEARCH_W + TEXT_GAP;
        graphics.scrollingText(title(), titleX, 8, 636 - titleX, KineticTheme.current().text(), false);
        graphics.hLine(4, 636, GRID_Y - 3, KineticTheme.current().accentHover());
        scroll.update(rowCount(), VISIBLE_ROWS);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        int startRow = scroll.smoothIndexOffset(), shift = scroll.visualShift(ROW_PITCH);
        for (int row = startRow; row <= startRow + VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int i = row * COLS + col;
                if (i >= visible.size()) break;
                Row entry = visible.get(i);
                int x = GRID_X + col * COL_PITCH, y = GRID_Y + (row - startRow) * ROW_PITCH - shift;
                boolean hovered = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H
                        && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H;
                boolean current = entry.type() == null ? selected == null : entry.type().equals(selected);
                KineticTheme.stateSurface(graphics, x, y, CARD_W, CARD_H, KineticTheme.Surface.PANEL_ALT, current, hovered, false);
                int textX = x + 4;
                if (entry.type() != null) {
                    ItemStack icon = RecipeRemovalPage.typeIcon(entry.type());
                    int slot = KineticSizes.ITEM_SLOT;
                    KineticTheme.itemSlot(graphics, x + 2, y + 2, slot, false);
                    if (!icon.isEmpty()) KineticTheme.item(graphics, icon, x + 2, y + 2, slot, KineticSizes.ITEM_SCALE, false);
                    textX = x + 2 + slot + 4;
                }
                String name = entry.type() == null ? RecipeRemovalPage.tr("all_types").getString()
                        : RecipeRemovalPage.typeName(entry.type()).getString();
                int textW = x + CARD_W - 3 - textX;
                Component label = Component.literal(name + " · " + entry.count());
                if (entry.type() == null) {
                    graphics.scrollingText(label, textX, y + (CARD_H - 8) / 2, textW, KineticTheme.current().text(), false);
                } else {
                    graphics.scrollingText(label, textX, y + 3, textW, KineticTheme.current().text(), false);
                    graphics.scrollingText(Component.literal(entry.type().toString()), textX, y + 14, textW,
                            KineticTheme.current().mutedText(), false);
                }
            }
            scroll.renderSelectionFlash(graphics, row, GRID_X, GRID_Y + (row - startRow) * ROW_PITCH - shift, GRID_W, CARD_H);
        }
        graphics.endScissor();
        scroll.render(graphics, mouseX, mouseY, SCROLL_X, GRID_Y, 4, GRID_H, 16);
    }

    private int indexAt(double x, double y) {
        if (x < GRID_X || x >= GRID_X + GRID_W || y < GRID_Y || y >= GRID_Y + GRID_H) return -1;
        int col = (int) ((x - GRID_X) / COL_PITCH);
        if (col >= COLS || (x - GRID_X) - col * COL_PITCH >= CARD_W) return -1;
        double rowPos = (y - GRID_Y + scroll.visualShift(ROW_PITCH)) / ROW_PITCH;
        if ((rowPos - Math.floor(rowPos)) * ROW_PITCH >= CARD_H) return -1;
        int index = (scroll.smoothIndexOffset() + (int) Math.floor(rowPos)) * COLS + col;
        return index >= 0 && index < visible.size() ? index : -1;
    }

    @Override protected boolean onMouseClick(MouseInput input) {
        // 原逻辑在控件之后运行 / The old logic ran after controls.
        double x = input.x();
        double y = input.y();
        blur(search);
        if (!input.isLeft()) return false;
        if (scroll.beginDrag(x, y, input.button(), SCROLL_X, GRID_Y, 4, GRID_H, 16, 1)) return true;
        int index = indexAt(x, y);
        if (index >= 0) {
            parent.setTypeFilter(visible.get(index).type());
            close();
            return true;
        }
        return false;
    }

    @Override protected boolean onMouseScroll(ScrollInput input) {
        double x = input.x();
        double y = input.y();
        return x >= GRID_X && x < 640 && y >= GRID_Y && y < GRID_Y + GRID_H && scroll.scroll(input.deltaY());
    }

    @Override protected boolean onMouseDrag(MouseDragInput input) {
        return scroll.drag(input.y(), GRID_Y, GRID_H, 16);
    }

    @Override protected boolean onMouseRelease(MouseInput input) {
        return scroll.release(input.button());
    }

}
