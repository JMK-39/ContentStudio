package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.GuiSession;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class RecipeRemovalPreviewScreen extends KineticScreen {
    private final RecipeRemovalScreen parent;
    private final RecipeJeiBridge.Entry entry;
    private RecipeJeiBridge.Preview preview;
    private Button toggle;
    private Button viewerButton;
    private final List<Button> popupButtons = new ArrayList<>();
    private final Map<Button, Component> buttonTooltips = new IdentityHashMap<>();
    private boolean dataError;
    private float previewScale = 1;
    private int previewX, previewY;

    RecipeRemovalPreviewScreen(RecipeRemovalScreen parent, RecipeJeiBridge.Entry entry) {
        super(entry.category());
        this.parent = parent;
        this.entry = entry;
        useCanvas(640, 360, 6);
        GuiSession.setParent(this, parent);
        if (entry.preview() != null) {
            try { preview = entry.preview().get(); }
            catch (RuntimeException ignored) { markError(); }
        }
    }

    @Override protected void buildUi() {
        popupButtons.clear();
        buttonTooltips.clear();
        Button backButton = addRenderableWidget(Button.builder(RecipeRemovalScreen.tr("back"), button -> onClose()).bounds(366, 328, 60, 20).build());
        viewerButton = addRenderableWidget(Button.builder(RecipeRemovalScreen.tr("open_viewer"), button -> openViewerMenu()).bounds(432, 328, 60, 20).build());
        viewerButton.active = RecipeJeiBridge.available();
        buttonTooltips.put(viewerButton, RecipeRemovalScreen.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing"));
        toggle = addRenderableWidget(Button.builder(parent.recipeAction(entry), button -> {
            parent.toggleRecipe(entry);
            refreshAction();
        }).bounds(498, 328, 60, 20).build());
        Button saveButton = addRenderableWidget(Button.builder(RecipeRemovalScreen.tr("save"), button -> parent.save()).bounds(564, 328, 60, 20).build());
        buttonTooltips.put(saveButton, RecipeRemovalScreen.tr("save_hint"));
        refreshAction();
        if (preview != null) {
            previewScale = Math.min(2, Math.min(580f / Math.max(1, preview.width()), 214f / Math.max(1, preview.height())));
            previewX = (int) ((640 - preview.width() * previewScale) / 2);
            previewY = 74 + (int) ((230 - preview.height() * previewScale) / 2);
        }
    }

    private void openViewerMenu() {
        List<RecipeJeiBridge.Viewer> viewers = RecipeJeiBridge.availableViewers();
        if (viewers.isEmpty()) {
            parent.showToast(RecipeRemovalScreen.tr("viewer_missing"));
            return;
        }
        if (viewers.size() == 1) {
            openViewer(viewers.get(0));
            return;
        }
        discardPopupButtons();
        int width = 60;
        int totalHeight = viewers.size() * 22 - 2;
        int px = Math.max(4, Math.min(432, 640 - width - 4));
        int py = Math.max(4, Math.min(324 - totalHeight, 360 - totalHeight - 4));
        for (int i = 0; i < viewers.size(); i++) {
            RecipeJeiBridge.Viewer viewer = viewers.get(i);
            KineticWidgets.HighZButton button = new KineticWidgets.HighZButton(
                    px,
                    py + i * 22,
                    width,
                    20,
                    viewer.displayName(),
                    ignored -> {
                        discardPopupButtons();
                        openViewer(viewer);
                    },
                    Tooltip.create(RecipeRemovalScreen.tr("viewer_choice_hint", viewer.displayName()))
            );
            popupButtons.add(addRenderableWidget(button));
        }
    }

    private void openViewer(RecipeJeiBridge.Viewer viewer) {
        if (!RecipeJeiBridge.show(viewer, entry.recipe().output(), entry)) {
            parent.markRecipeError();
            parent.showToast(RecipeRemovalScreen.tr("viewer_failed", viewer.displayName()));
        }
    }

    private void discardPopupButtons() {
        if (!popupButtons.isEmpty()) {
            for (Button button : List.copyOf(popupButtons)) removeWidget(button);
            popupButtons.clear();
        }
    }

    private static boolean inside(Button widget, double mouseX, double mouseY) {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
                && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }

    private boolean handlePopupClick(double mouseX, double mouseY, int mouseButton) {
        if (popupButtons.isEmpty()) return false;
        for (Button widget : List.copyOf(popupButtons)) {
            if (!widget.visible || !inside(widget, mouseX, mouseY)) continue;
            if (mouseButton == 0 && widget.active) widget.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        discardPopupButtons();
        return true;
    }

    private void refreshAction() {
        toggle.setMessage(parent.recipeAction(entry));
        toggle.active = parent.canToggle(entry) && !dataError;
        buttonTooltips.put(toggle, RecipeRemovalScreen.tr(entry.removable() ? "exact_hint" : "view_only_hint"));
        if (viewerButton != null) {
            viewerButton.active = RecipeJeiBridge.available();
            buttonTooltips.put(viewerButton, RecipeRemovalScreen.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing"));
        }
    }

    private void markError() {
        dataError = true;
        parent.markRecipeError();
        if (toggle != null) refreshAction();
    }

    @Override public void tick() {
        super.tick();
        if (preview != null && !dataError) {
            try { preview.tick(); } catch (RuntimeException ignored) { markError(); }
        }
        if (viewerButton != null) refreshAction();
    }

    @Override protected void renderCanvasBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 0, 0, 640, 360);
        graphics.drawCenteredString(font, title, 320, 16, GuiTheme.current().text());
        Component id = entry.recipe().id() == null ? RecipeRemovalScreen.tr("no_id") : Component.literal(entry.recipe().id().toString());
        graphics.drawCenteredString(font, font.plainSubstrByWidth(id.getString(), 608), 320, 36, GuiTheme.current().mutedText());
        GuiTheme.panelAlt(graphics, 16, 62, 608, 250);
        if (dataError) graphics.drawCenteredString(font, RecipeRemovalScreen.tr("data_error"), 320, 170, GuiTheme.current().danger());
        else if (preview != null) drawPreview(graphics, mouseX, mouseY, false);
        else {
            try { renderFallback(graphics); } catch (RuntimeException ignored) { markError(); }
        }
    }

    @Override protected void renderCanvasForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (preview != null && !dataError) drawPreview(graphics, mouseX, mouseY, true);
    }

    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY, boolean overlays) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(previewX, previewY, 0);
            graphics.pose().scale(previewScale, previewScale, 1);
            int x = (int) ((mouseX - previewX) / previewScale), y = (int) ((mouseY - previewY) / previewScale);
            if (overlays) preview.drawOverlays(graphics, x, y); else preview.draw(graphics, x, y);
        } catch (RuntimeException ignored) {
            markError();
        } finally { graphics.pose().popPose(); }
    }

    private void renderFallback(GuiGraphics graphics) {
        var recipe = entry.recipe();
        graphics.drawCenteredString(font, RecipeRemovalScreen.tr(dataError ? "data_error" : "fallback_preview"), 320, 82,
                dataError ? GuiTheme.current().danger() : GuiTheme.current().mutedText());
        int columns = recipe.craftingWidth() > 0 ? Math.min(9, recipe.craftingWidth()) : 9;
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < Math.min(recipe.inputs().size(), columns * 6); i++) {
            int x = 100 + i % columns * 22, y = 118 + i / columns * 22;
            GuiTheme.itemSlot(graphics, x, y, 20, false);
            try {
                var alternatives = recipe.inputs().get(i).getItems();
                if (alternatives.length > 0) graphics.renderItem(alternatives[cycle % alternatives.length], x + 2, y + 2);
            } catch (RuntimeException ignored) { markError(); }
        }
        graphics.drawString(font, "→", 362, 172, GuiTheme.current().text(), false);
        GuiTheme.itemSlot(graphics, 412, 164, 20, false);
        graphics.renderItem(recipe.output(), 414, 166);
        graphics.renderItemDecorations(font, recipe.output(), 414, 166);
    }

    @Override protected void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY, int screenMouseX, int screenMouseY) {
        if (!popupButtons.isEmpty()) return;
        for (var tooltipEntry : buttonTooltips.entrySet()) {
            Button widget = tooltipEntry.getKey();
            if (!widget.visible || !inside(widget, mouseX, mouseY)) continue;
            GuiOverlay.requestTooltip(tooltipEntry.getValue(), 220, screenMouseX, screenMouseY);
            return;
        }
        if (preview != null || dataError) return;
        ItemStack hovered = hoveredFallbackStack(mouseX, mouseY);
        if (!hovered.isEmpty()) GuiOverlay.requestItemTooltip(hovered, screenMouseX, screenMouseY);
    }

    private ItemStack hoveredFallbackStack(double mouseX, double mouseY) {
        var recipe = entry.recipe();
        int columns = recipe.craftingWidth() > 0 ? Math.min(9, recipe.craftingWidth()) : 9;
        int limit = Math.min(recipe.inputs().size(), columns * 6);
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < limit; i++) {
            int x = 100 + i % columns * 22, y = 118 + i / columns * 22;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                try {
                    ItemStack[] alternatives = recipe.inputs().get(i).getItems();
                    if (alternatives.length > 0) return alternatives[cycle % alternatives.length];
                } catch (RuntimeException ignored) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (mouseX >= 412 && mouseX < 432 && mouseY >= 164 && mouseY < 184) return recipe.output();
        return ItemStack.EMPTY;
    }

    @Override protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (!popupButtons.isEmpty()) return handlePopupClick(mouseX, mouseY, button);
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (!popupButtons.isEmpty()) {
            discardPopupButtons();
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!popupButtons.isEmpty()) return true;
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (!popupButtons.isEmpty()) return true;
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && !popupButtons.isEmpty()) {
            discardPopupButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (!popupButtons.isEmpty()) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
