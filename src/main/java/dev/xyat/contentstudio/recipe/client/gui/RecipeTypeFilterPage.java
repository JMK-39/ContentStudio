package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
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

/** Browse the selected item's real recipe types with an icon and recipe count. */
final class RecipeTypeFilterPage extends KineticPage {
    private static final int NAME_X = 44;
    private static final int TYPE_X = 370;
    private static final int ROW_RIGHT = 624;
    private static final int TEXT_GAP = 4;
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
        // 中键跳回当前生效的类型筛选行 / Middle-click jumps back to the row of the active type filter.
        scroll.bindSelection(this::selectedRowIndex);
    }

    private int selectedRowIndex() {
        for (int i = 0; i < visible.size(); i++) {
            ResourceLocation type = visible.get(i).type();
            if (Objects.equals(type, selected)) return i;
        }
        return -1;
    }

    @Override protected void build(KineticUi ui) {
        search = ui().autoComplete(16, 36, 608, KineticSuggestion.fromStrings(() -> all.stream()
                        .map(row -> row.type().toString()).toList())).label(RecipeRemovalPage.tr("filter_type_search")).placeholder(RecipeRemovalPage.tr("filter_type_search")).build();
        search.onTextChange(value -> { query = value; refresh(); scroll.setOffset(0); });
        ui().button(564, 328, 60).text(RecipeRemovalPage.tr("back")).onClick(this::close).build();
        refresh();
    }

    private void refresh() {
        visible.clear();
        visible.add(new Row(null, all.stream().mapToLong(Row::count).sum()));
        for (Row row : all) {
            if (KineticSearch.match(row.type().toString() + " "
                    + RecipeRemovalPage.typeName(row.type()).getString(), query)) visible.add(row);
        }
        scroll.update(visible.size(), 10);
    }

    @Override protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, 640, 360);
        graphics.scrollingText(title(), 16, 12, ROW_RIGHT - 16, KineticTheme.current().text(), false);
        scroll.update(visible.size(), 10);
        graphics.scissor(16, 68, 624, 308);
        int start = scroll.smoothIndexOffset(), shift = scroll.visualShift(24);
        for (int i = start; i < Math.min(visible.size(), start + 11); i++) {
            Row row = visible.get(i);
            int y = 68 + (i - start) * 24 - shift;
            boolean hovered = mouseX >= 16 && mouseX < 624 && mouseY >= y && mouseY < y + 24;
            KineticTheme.stateSurface(graphics, 16, y + 1, 608, 22, KineticTheme.Surface.PANEL_ALT,
                    row.type() == null ? selected == null : row.type().equals(selected), hovered, false);
            if (row.type() != null) {
                ItemStack icon = RecipeRemovalPage.typeIcon(row.type());
                if (!icon.isEmpty()) graphics.item(icon, 21, y + 4);
            }
            String name = row.type() == null ? RecipeRemovalPage.tr("all_types").getString()
                    : RecipeRemovalPage.typeName(row.type()).getString();
            graphics.scrollingText(Component.literal(name + " · " + row.count()), NAME_X, y + 8,
                    TYPE_X - NAME_X - TEXT_GAP, KineticTheme.current().text(), false);
            if (row.type() != null) graphics.scrollingText(Component.literal(row.type().toString()), TYPE_X, y + 8,
                    ROW_RIGHT - TYPE_X - TEXT_GAP, KineticTheme.current().mutedText(), false);
            scroll.renderSelectionFlash(graphics, i, 16, y + 1, 608, 22);
        }
        graphics.endScissor();
        scroll.render(graphics, mouseX, mouseY, 628, 68, 4, 240, 16);
    }

    @Override protected boolean onMouseClick(MouseInput input) {
        // 原逻辑在控件之后运行 / The old logic ran after controls.
        double x = input.x();
        double y = input.y();
        blur(search);
        if (!input.isLeft()) return false;
        if (scroll.beginDrag(x, y, input.button(), 628, 68, 4, 240, 16, 1)) return true;
        if (x >= 16 && x < 624 && y >= 68 && y < 308) {
            int index = scroll.smoothIndexOffset() + (int) ((y - 68 + scroll.visualShift(24)) / 24);
            if (index >= 0 && index < visible.size()) {
                parent.setTypeFilter(visible.get(index).type());
                close();
            }
            return true;
        }
        return false;
    }

    @Override protected boolean onMouseScroll(ScrollInput input) {
        double x = input.x();
        double y = input.y();
        double delta = input.deltaY();
        return x >= 16 && x < 634 && y >= 68 && y < 308
                && scroll.scroll(delta);
    }

    @Override protected boolean onMouseDrag(MouseDragInput input) {
        double y = input.y();
        return scroll.drag(y, 68, 240, 16);
    }

    @Override protected boolean onMouseRelease(MouseInput input) {
        return scroll.release(input.button());
    }

}
