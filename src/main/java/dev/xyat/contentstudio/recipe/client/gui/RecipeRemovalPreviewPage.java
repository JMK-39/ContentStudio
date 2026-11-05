package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticSizes;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class RecipeRemovalPreviewPage extends KineticPage {
    private static final int PAGE_WIDTH = 640;
    // Header: Back, title and the three actions on one row; the recipe ID and its status on a small second line.
    private static final int SAVE_X = 596, TOGGLE_X = SAVE_X - 4 - 60, VIEWER_X = TOGGLE_X - 4 - 60;
    private static final int INFO_Y = 25, DIVIDER_Y = 36;
    // The content area below the divider; the recipe is centred in it.
    private static final int AREA_X = 4, AREA_Y = DIVIDER_Y + 2, AREA_W = 632, AREA_H = 356 - AREA_Y;
    private static final int TEXT_WIDTH = AREA_W - 8;
    // Fallback (no viewer layout): standard item slots (KineticSizes).
    private static final int SLOT = KineticSizes.ITEM_SLOT, PITCH = KineticSizes.ITEM_PITCH, ARROW_W = 28;
    private final RecipeRemovalPage parent;
    private final RecipeJeiBridge.Entry entry;
    private RecipeJeiBridge.Preview preview;
    private KineticButton toggle;
    private KineticButton viewerButton;
    private boolean dataError;
    private float previewScale = 1;
    private int previewX, previewY;
    private long previewGeneration=RecipeJeiBridge.generation();

    RecipeRemovalPreviewPage(RecipeRemovalPage parent, RecipeJeiBridge.Entry entry) {
        super(entry.category());
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        this.parent = parent;
        this.entry = entry;
        if (entry.preview() != null) {
            try { preview = entry.preview().get(); }
            catch (RuntimeException ignored) { markError(); }
        }
    }

    @Override protected void build(KineticUi ui) {
        ui().button(4, 4, 40).text(RecipeRemovalPage.tr("back")).onClick(this::close).build();
        viewerButton = ui().button(VIEWER_X, 4, 60).text(RecipeRemovalPage.tr("open_viewer")).tooltip(RecipeRemovalPage.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing")).onClick(this::openViewerMenu).build();
        viewerButton.setEnabled(RecipeJeiBridge.available());
        toggle = ui().button(TOGGLE_X, 4, 60).text(parent.recipeAction(entry)).onClick(() -> {
            parent.toggleRecipe(entry);
            refreshAction();
        }).build();
        ui().button(SAVE_X, 4, 40).text(RecipeRemovalPage.tr("save")).tooltip(RecipeRemovalPage.tr("save_hint")).onClick(parent::save).build();
        refreshAction();
        if (preview != null) {
            previewScale = Math.min(1, Math.min((AREA_W - 16f) / Math.max(1, preview.width()), (AREA_H - 16f) / Math.max(1, preview.height())));
            previewX = AREA_X + (int) ((AREA_W - preview.width() * previewScale) / 2);
            previewY = AREA_Y + (int) ((AREA_H - preview.height() * previewScale) / 2);
        }
    }

    private void openViewerMenu() {
        List<RecipeJeiBridge.Viewer> viewers = RecipeJeiBridge.availableViewers();
        if (viewers.isEmpty()) {
            parent.showToast(RecipeRemovalPage.tr("viewer_missing"));
            return;
        }
        if (viewers.size() == 1) {
            openViewer(viewers.get(0));
            return;
        }
        List<KineticOverlays.MenuItem> items = viewers.stream()
                .map(viewer -> KineticOverlays.MenuItem.action(
                        viewer.displayName(),
                        RecipeRemovalPage.tr("viewer_choice_hint", viewer.displayName()),
                        () -> openViewer(viewer)
                ))
                .toList();
        openContextMenu(VIEWER_X, 22, items);
    }

    private void openViewer(RecipeJeiBridge.Viewer viewer) {
        if (RecipeJeiBridge.show(viewer, entry.recipe().output(), entry)) return;
        parent.markRecipeError();
        parent.showToast(RecipeRemovalPage.tr("viewer_failed", viewer.displayName()));
    }

    private void refreshAction() {
        toggle.setText(parent.recipeAction(entry));
        toggle.setEnabled(parent.canToggle(entry) && !dataError);
        toggle.setTooltip(RecipeRemovalPage.tr(parent.canToggle(entry) ? "exact_hint" : "view_only_hint"));
        if (viewerButton != null) {
            viewerButton.setEnabled(RecipeJeiBridge.available());
            viewerButton.setTooltip(RecipeRemovalPage.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing"));
        }
    }

    private void markError() {
        dataError = true;
        parent.markRecipeError();
        if (toggle != null) refreshAction();
    }

    @Override protected void onTick() {
        if(previewGeneration!=RecipeJeiBridge.generation()) {
            previewGeneration=RecipeJeiBridge.generation();preview=null;dataError=false;
            if(RecipeJeiBridge.jeiAvailable())try {
                for(var fresh:RecipeJeiBridge.recipes(entry.recipe().output()))if(java.util.Objects.equals(fresh.recipe().id(),entry.recipe().id())&&fresh.preview()!=null) {
                    preview=fresh.preview().get();break;
                }
            }catch(RuntimeException ignored){markError();}
            rebuild();
        }
        if (preview != null && !dataError) {
            try { preview.tick(); } catch (RuntimeException ignored) { markError(); }
        }
        if (viewerButton != null) refreshAction();
    }

    @Override protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, 640, 360);
        graphics.scrollingText(title(), 48, 8, VIEWER_X - 4 - 48, KineticTheme.current().text(), false);
        Component id = entry.recipe().id() == null ? RecipeRemovalPage.tr("no_id") : Component.literal(entry.recipe().id().toString());
        Component status = parent.statusLabel(entry);
        int statusW = 200;
        graphics.scrollingText(id, 6, INFO_Y, AREA_W - statusW - 12, KineticTheme.current().mutedText(), false);
        graphics.scrollingTextRight(status, 634, INFO_Y, statusW, KineticTheme.current().mutedText(), false);
        graphics.hLine(AREA_X, AREA_X + AREA_W, DIVIDER_Y, KineticTheme.current().accentHover());
        if (dataError) graphics.scrollingTextCentered(RecipeRemovalPage.tr("data_error"), PAGE_WIDTH / 2, AREA_Y + AREA_H / 2 - 4,
                TEXT_WIDTH, KineticTheme.current().danger(), false);
        else if (preview != null) drawPreview(graphics, mouseX, mouseY, false);
        else {
            try { renderFallback(graphics); } catch (RuntimeException ignored) { markError(); }
        }
    }

    @Override protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (preview != null && !dataError) drawPreview(graphics, mouseX, mouseY, true);
    }

    private void drawPreview(KineticGraphics graphics, int mouseX, int mouseY, boolean overlays) {
        if(previewGeneration!=RecipeJeiBridge.generation())return;
        graphics.push();
        try {
            graphics.translate(previewX, previewY);
            graphics.scale(previewScale, previewScale);
            int x = (int) ((mouseX - previewX) / previewScale), y = (int) ((mouseY - previewY) / previewScale);
            // 原版上下文只在 JEI 适配器内解包，页面始终使用 KineticGraphics。
            // The JEI adapter unwraps the native context; pages always use KineticGraphics.
            if (overlays) preview.drawOverlays(graphics, x, y); else preview.draw(graphics, x, y);
        } catch (RuntimeException ignored) {
            markError();
        } finally { graphics.pop(); }
    }

    /** The fallback block: the inputs as a grid, an arrow, then the output, centred in the content area. */
    private record FallbackLayout(int columns, int count, int gridX, int gridY, int arrowX, int outputX, int outputY) { }

    private FallbackLayout fallbackLayout() {
        var recipe = entry.recipe();
        int columns = recipe.craftingWidth() > 0 ? Math.min(9, recipe.craftingWidth()) : Math.min(9, Math.max(1, recipe.inputs().size()));
        int count = Math.min(recipe.inputs().size(), columns * 12);
        int rows = Math.max(1, (count + columns - 1) / columns);
        int gridW = columns * PITCH - 2, gridH = rows * PITCH - 2;
        int blockW = gridW + ARROW_W + SLOT;
        int gridX = AREA_X + (AREA_W - blockW) / 2;
        int gridY = AREA_Y + 16 + (AREA_H - 16 - gridH) / 2;
        int arrowX = gridX + gridW;
        int outputX = arrowX + ARROW_W;
        int outputY = gridY + (gridH - SLOT) / 2;
        return new FallbackLayout(columns, count, gridX, gridY, arrowX, outputX, outputY);
    }

    private void renderFallback(KineticGraphics graphics) {
        var recipe = entry.recipe();
        FallbackLayout layout = fallbackLayout();
        graphics.scrollingTextCentered(RecipeRemovalPage.tr(dataError ? "data_error" : "fallback_preview"),
                PAGE_WIDTH / 2, layout.gridY() - 14, TEXT_WIDTH,
                dataError ? KineticTheme.current().danger() : KineticTheme.current().mutedText(), false);
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < layout.count(); i++) {
            int x = layout.gridX() + i % layout.columns() * PITCH, y = layout.gridY() + i / layout.columns() * PITCH;
            RecipeSlots.draw(graphics, x, y, SLOT);
            try {
                var alternatives = recipe.alternatives(i);
                if (alternatives.length > 0) KineticTheme.item(graphics, alternatives[cycle % alternatives.length], x, y, SLOT, 1.0F, false);
            } catch (RuntimeException ignored) { markError(); }
        }
        graphics.centeredText("\u2192", layout.arrowX() + ARROW_W / 2, layout.outputY() + (SLOT - 8) / 2, KineticTheme.current().text(), false);
        RecipeSlots.draw(graphics, layout.outputX(), layout.outputY(), SLOT);
        KineticTheme.item(graphics, recipe.output(), layout.outputX(), layout.outputY(), SLOT, 1.0F, false);
        graphics.itemDecorations(recipe.output(), layout.outputX() + (SLOT - 16) / 2, layout.outputY() + (SLOT - 16) / 2);
    }

    @Override protected void renderTooltips(int mouseX, int mouseY) {
        if (mouseY >= INFO_Y - 1 && mouseY < DIVIDER_Y) {
            showTooltip(parent.recipeReasonLines(entry), 360);
            return;
        }
        if (preview != null || dataError) return;
        ItemStack hovered = hoveredFallbackStack(mouseX, mouseY);
        if (!hovered.isEmpty()) showItemTooltip(hovered);
    }

    private ItemStack hoveredFallbackStack(double mouseX, double mouseY) {
        var recipe = entry.recipe();
        FallbackLayout layout = fallbackLayout();
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < layout.count(); i++) {
            int x = layout.gridX() + i % layout.columns() * PITCH, y = layout.gridY() + i / layout.columns() * PITCH;
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                try {
                    ItemStack[] alternatives = recipe.alternatives(i);
                    if (alternatives.length > 0) return alternatives[cycle % alternatives.length];
                } catch (RuntimeException ignored) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (mouseX >= layout.outputX() && mouseX < layout.outputX() + SLOT
                && mouseY >= layout.outputY() && mouseY < layout.outputY() + SLOT) return recipe.output();
        return ItemStack.EMPTY;
    }

}
