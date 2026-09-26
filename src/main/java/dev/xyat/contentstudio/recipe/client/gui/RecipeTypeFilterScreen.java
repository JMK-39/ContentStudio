package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Browse the selected item's real recipe types with an icon and recipe count. */
final class RecipeTypeFilterScreen extends KineticScreen {
    private record Row(ResourceLocation type, long count) { }

    private final RecipeRemovalScreen parent;
    private final List<Row> all = new ArrayList<>();
    private final List<Row> visible = new ArrayList<>();
    private final GridScrollController scroll = new GridScrollController();
    private final ResourceLocation selected;
    private AutoCompleteBox search;
    private String query = "";

    RecipeTypeFilterScreen(RecipeRemovalScreen parent, Map<ResourceLocation, Long> counts,
                           ResourceLocation selected) {
        super(RecipeRemovalScreen.tr("filter_type"));
        setParentScreen(parent);
        this.parent = parent;
        this.selected = selected;
        counts.forEach((type, count) -> all.add(new Row(type, count)));
    }

    @Override protected void buildUi() {
        search = addAutoCompleteField(16, 36, 608, RecipeRemovalScreen.tr("filter_type_search"),
                RecipeRemovalScreen.tr("filter_type_search"),
                KineticAutoComplete.stringDictionary(() -> all.stream()
                        .map(row -> row.type().toString()).toList()), null);
        search.setResponder(value -> { query = value; refresh(); scroll.setOffset(0); });
        addButton(564, 328, 60, RecipeRemovalScreen.tr("back"), null, this::onClose);
        refresh();
    }

    private void refresh() {
        visible.clear();
        visible.add(new Row(null, all.stream().mapToLong(Row::count).sum()));
        for (Row row : all) {
            if (KineticSearch.match(row.type().toString() + " "
                    + RecipeRemovalScreen.typeName(row.type()).getString(), query)) visible.add(row);
        }
        scroll.update(visible.size(), 10);
    }

    @Override protected void renderCanvasBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 0, 0, 640, 360);
        graphics.drawString(font, title, 16, 12, GuiTheme.current().text(), false);
        scroll.update(visible.size(), 10);
        enableUiScissor(graphics, 16, 68, 624, 308);
        int start = scroll.smoothIndexOffset(), shift = scroll.visualShift(24);
        for (int i = start; i < Math.min(visible.size(), start + 11); i++) {
            Row row = visible.get(i);
            int y = 68 + (i - start) * 24 - shift;
            boolean hovered = mouseX >= 16 && mouseX < 624 && mouseY >= y && mouseY < y + 24;
            GuiTheme.stateSurface(graphics, 16, y + 1, 608, 22, GuiTheme.Surface.PANEL_ALT,
                    row.type() == null ? selected == null : row.type().equals(selected), hovered, false);
            if (row.type() != null) {
                ItemStack icon = RecipeRemovalScreen.typeIcon(row.type());
                if (!icon.isEmpty()) graphics.renderItem(icon, 21, y + 4);
            }
            String name = row.type() == null ? RecipeRemovalScreen.tr("all_types").getString()
                    : RecipeRemovalScreen.typeName(row.type()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(name + " · " + row.count(), 320),
                    44, y + 8, GuiTheme.current().text(), false);
            if (row.type() != null) graphics.drawString(font,
                    font.plainSubstrByWidth(row.type().toString(), 240), 370, y + 8,
                    GuiTheme.current().mutedText(), false);
        }
        disableUiScissor(graphics);
        scroll.render(graphics, mouseX, mouseY, 628, 68, 4, 240, 16);
    }

    @Override protected boolean canvasMouseClicked(double x, double y, int button) {
        if (super.canvasMouseClicked(x, y, button)) return true;
        blurControl(search);
        if (!KineticMouseButtons.isPrimary(button)) return false;
        if (scroll.beginDrag(x, y, 628, 68, 4, 240, 16, 1)) return true;
        if (x >= 16 && x < 624 && y >= 68 && y < 308) {
            int index = scroll.smoothIndexOffset() + (int) ((y - 68 + scroll.visualShift(24)) / 24);
            if (index >= 0 && index < visible.size()) {
                parent.setTypeFilter(visible.get(index).type());
                onClose();
            }
            return true;
        }
        return false;
    }

    @Override protected boolean canvasMouseScrolled(double x, double y, double delta) {
        return x >= 16 && x < 634 && y >= 68 && y < 308
                ? scroll.scroll(delta) : super.canvasMouseScrolled(x, y, delta);
    }

    @Override protected boolean canvasMouseDragged(double x, double y, int button, double dx, double dy) {
        return scroll.drag(y, 68, 240, 16) || super.canvasMouseDragged(x, y, button, dx, dy);
    }

    @Override protected boolean canvasMouseReleased(double x, double y, int button) {
        return scroll.release(button) || super.canvasMouseReleased(x, y, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}
