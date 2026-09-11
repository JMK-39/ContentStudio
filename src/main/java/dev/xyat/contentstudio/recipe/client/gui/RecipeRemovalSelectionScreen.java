package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.screen.GuiSession;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.contentstudio.recipe.removal.RemovalMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

import java.util.ArrayList;
import java.util.List;

final class RecipeRemovalSelectionScreen extends KineticScreen {
    private final RecipeRemovalScreen parent;
    private final RemovalMode mode;
    private final List<RecipeRemovalScreen.SelectionEntry> options;
    private final List<RecipeRemovalScreen.SelectionEntry> visible = new ArrayList<>();
    private final GridScrollController scroll = new GridScrollController();
    private AutoCompleteBox search;
    private String query;

    RecipeRemovalSelectionScreen(RecipeRemovalScreen parent, RemovalMode mode,
                                 List<RecipeRemovalScreen.SelectionEntry> options, String query) {
        super(RecipeRemovalScreen.tr("rule_editor", mode.getDisplayName()));
        this.parent = parent; this.mode = mode; this.options = options; this.query = query;
        useCanvas(640, 360, 6);
        GuiSession.setParent(this, parent);
    }

    private String prefix() { return mode == RemovalMode.MOD ? "@" : mode == RemovalMode.TAG ? "#" : ""; }

    @Override protected void buildUi() {
        search = addRenderableWidget(new AutoCompleteBox(font, 16, 42, 440, 20,
                RecipeRemovalScreen.tr("rule_search", prefix()), () -> options.stream().map(entry -> prefix() + entry.value).toList()));
        search.setValue(query);
        search.setResponder(value -> { query = value; filter(); scroll.setOffset(0); });
        addRenderableWidget(Button.builder(RecipeRemovalScreen.tr("toggle_visible"), button -> {
            boolean select = visible.stream().anyMatch(entry -> !entry.isSelected);
            visible.forEach(entry -> entry.isSelected = select);
        }).bounds(564, 42, 60, 20).build());
        addRenderableWidget(Button.builder(RecipeRemovalScreen.tr("back"), button -> onClose()).bounds(498, 328, 60, 20).build());
        addRenderableWidget(Button.builder(RecipeRemovalScreen.tr("confirm_rules"), button -> {
            for (var entry : options) {
                if (entry.isSelected && !entry.alreadyExists) parent.addEntryFromSelection(mode, entry.value);
                else if (!entry.isSelected && entry.alreadyExists) parent.removeEntryDirectly(mode, entry.value);
            }
            onClose();
        }).bounds(564, 328, 60, 20).build());
        filter();
    }

    private void filter() {
        String value = query.startsWith("@") || query.startsWith("#") ? query.substring(1) : query;
        visible.clear();
        for (var entry : options) if (KineticSearch.match(entry.value, value)) visible.add(entry);
        scroll.update(visible.size(), 12);
    }

    @Override protected void renderCanvasBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 0, 0, 640, 360);
        graphics.drawString(font, title, 16, 12, GuiTheme.current().text(), false);
        graphics.drawString(font, RecipeRemovalScreen.tr("rule_scope"), 16, 28, GuiTheme.current().mutedText(), false);
        scroll.update(visible.size(), 12);
        enableCanvasScissor(graphics, 16, 72, 624, 312);
        int start = scroll.smoothIndexOffset(), shift = scroll.visualShift(20);
        for (int i = start; i < Math.min(visible.size(), start + 13); i++) {
            var entry = visible.get(i);
            int y = 72 + (i - start) * 20 - shift;
            boolean hover = mouseX >= 16 && mouseX < 624 && mouseY >= y && mouseY < y + 20;
            graphics.fill(16, y + 1, 624, y + 19, GuiTheme.current().panelAlt());
            graphics.renderOutline(16, y + 1, 608, 18, entry.isSelected ? 0xFFFFD740 : hover ? 0xFF55AAFF : GuiTheme.current().border());
            String label = (entry.isSelected ? "☑ " : "☐ ") + prefix() + entry.value;
            graphics.drawString(font, font.plainSubstrByWidth(label, 584), 24, y + 6, GuiTheme.current().text(), false);
        }
        if (visible.isEmpty()) graphics.drawString(font, RecipeRemovalScreen.tr("empty"), 24, 82, GuiTheme.current().mutedText(), false);
        graphics.disableScissor();
        var theme = GuiTheme.current();
        scroll.render(graphics, mouseX, mouseY, 628, 72, 4, 240, 16, theme.scrollTrack(), theme.scrollThumb(), theme.scrollThumbHover());
    }

    @Override protected void renderCanvasForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (search.getValue().isEmpty() && !search.isFocused()) {
            graphics.drawString(font, RecipeRemovalScreen.tr("rule_search", prefix()), 20, 48, GuiTheme.current().mutedText(), false);
        }
        search.renderSuggestions(graphics, mouseX, mouseY);
    }

    @Override protected boolean canvasMouseClicked(double x, double y, int button) {
        if (button == 0 && search.handleMouseClick(x, y)) return true;
        if (super.canvasMouseClicked(x, y, button)) return true;
        search.setFocused(false);
        if (button != 0) return false;
        if (scroll.beginDrag(x, y, 628, 72, 4, 240, 16, 1)) return true;
        if (x >= 16 && x < 624 && y >= 72 && y < 312) {
            int index = scroll.smoothIndexOffset() + (int) ((y - 72 + scroll.visualShift(20)) / 20);
            if (index < visible.size()) visible.get(index).isSelected = !visible.get(index).isSelected;
            return true;
        }
        return false;
    }

    @Override protected boolean canvasMouseScrolled(double x, double y, double delta) {
        if (search.handleMouseScrolled(delta)) return true;
        return x >= 16 && x < 634 && y >= 72 && y < 312 ? scroll.scroll(delta) : super.canvasMouseScrolled(x, y, delta);
    }

    @Override protected boolean canvasMouseDragged(double x, double y, int button, double dx, double dy) {
        return search.handleMouseDragged(x, y) || scroll.drag(y, 72, 240, 16) || super.canvasMouseDragged(x, y, button, dx, dy);
    }

    @Override protected boolean canvasMouseReleased(double x, double y, int button) {
        boolean handled = search.handleMouseReleased(button) | scroll.release(button);
        return handled || super.canvasMouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 264 || keyCode == 265 || keyCode == 257 || keyCode == 335) && search.handleKeyPressed(keyCode)) return true;
        if (keyCode == 258 && search.isFocused()) { search.handleKeyPressed(264); if (search.handleKeyPressed(257)) return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
