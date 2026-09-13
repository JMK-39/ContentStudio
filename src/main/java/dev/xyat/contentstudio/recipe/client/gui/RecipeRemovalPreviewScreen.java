package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.text.KineticText;
import dev.xyat.kineticcore.api.client.screen.GuiSession;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class RecipeRemovalPreviewScreen extends KineticScreen {
    private final RecipeRemovalScreen parent;
    private final RecipeJeiBridge.Entry entry;
    private RecipeJeiBridge.Preview preview;
    private Button toggle;
    private Button viewerButton;
    private boolean dataError;
    private float previewScale = 1;
    private int previewX, previewY;

    RecipeRemovalPreviewScreen(RecipeRemovalScreen parent, RecipeJeiBridge.Entry entry) {
        super(entry.category());
        this.parent = parent;
        this.entry = entry;
        useStandardCanvas();
        GuiSession.setParent(this, parent);
        if (entry.preview() != null) {
            try { preview = entry.preview().get(); }
            catch (RuntimeException ignored) { markError(); }
        }
    }

    @Override protected void buildUi() {
        Button backButton = addButton(366, 328, 60, RecipeRemovalScreen.tr("back"), null, button -> onClose());
        viewerButton = addButton(
                432, 328, 60, RecipeRemovalScreen.tr("open_viewer"),
                RecipeRemovalScreen.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing"),
                button -> openViewerMenu()
        );
        viewerButton.active = RecipeJeiBridge.available();
        toggle = addButton(
                498, 328, 60, parent.recipeAction(entry),
                RecipeRemovalScreen.tr(entry.removable() ? "exact_hint" : "view_only_hint"),
                button -> {
            parent.toggleRecipe(entry);
            refreshAction();
                }
        );
        Button saveButton = addButton(
                564, 328, 60, RecipeRemovalScreen.tr("save"), RecipeRemovalScreen.tr("save_hint"),
                button -> parent.save()
        );
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
        List<dev.xyat.kineticcore.api.client.overlay.GuiOverlay.MenuItem> items = new ArrayList<>();
        for (RecipeJeiBridge.Viewer viewer : viewers) {
            items.add(dev.xyat.kineticcore.api.client.overlay.GuiOverlay.MenuItem.action(
                    viewer.displayName(),
                    RecipeRemovalScreen.tr("viewer_choice_hint", viewer.displayName()),
                    () -> openViewer(viewer)
            ));
        }
        openContextMenu(432, 324, items);
    }

    private void openViewer(RecipeJeiBridge.Viewer viewer) {
        if (!RecipeJeiBridge.show(viewer, entry.recipe().output(), entry)) {
            parent.markRecipeError();
            parent.showToast(RecipeRemovalScreen.tr("viewer_failed", viewer.displayName()));
        }
    }

    private void refreshAction() {
        toggle.setMessage(parent.recipeAction(entry));
        toggle.active = parent.canToggle(entry) && !dataError;
        registerWidgetTooltip(toggle, RecipeRemovalScreen.tr(entry.removable() ? "exact_hint" : "view_only_hint"));
        if (viewerButton != null) {
            viewerButton.active = RecipeJeiBridge.available();
            registerWidgetTooltip(viewerButton, RecipeRemovalScreen.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing"));
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
        KineticText.drawScrollingCentered(graphics, font, id, 320, 36, 608, GuiTheme.current().mutedText(), false);
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
        if (preview != null || dataError) return;
        ItemStack hovered = hoveredFallbackStack(mouseX, mouseY);
        if (!hovered.isEmpty()) showItemTooltip(hovered);
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
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
