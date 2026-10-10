package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class RecipeRemovalPreviewPage extends KineticPage {
    private static final int PAGE_WIDTH = 640;
    private static final int TEXT_MARGIN = 16;
    private static final int TEXT_WIDTH = PAGE_WIDTH - TEXT_MARGIN * 2;
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
        ui().button(16, 10, 60).text(RecipeRemovalPage.tr("back")).onClick(this::close).build();
        viewerButton = ui().button(432, 328, 60).text(RecipeRemovalPage.tr("open_viewer")).tooltip(RecipeRemovalPage.tr(RecipeJeiBridge.available() ? "viewer_recipe_hint" : "viewer_missing")).onClick(this::openViewerMenu).build();
        viewerButton.setEnabled(RecipeJeiBridge.available());
        toggle = ui().button(498, 328, 60).text(parent.recipeAction(entry)).onClick(() -> {
            parent.toggleRecipe(entry);
            refreshAction();
        }).build();
        ui().button(564, 328, 60).text(RecipeRemovalPage.tr("save")).tooltip(RecipeRemovalPage.tr("save_hint")).onClick(parent::save).build();
        refreshAction();
        if (preview != null) {
            previewScale = Math.min(1, Math.min(580f / Math.max(1, preview.width()), 214f / Math.max(1, preview.height())));
            previewX = (int) ((640 - preview.width() * previewScale) / 2);
            previewY = 74 + (int) ((230 - preview.height() * previewScale) / 2);
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
        openContextMenu(432, 328, items);
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
        graphics.scrollingText(title(), 82, 16, PAGE_WIDTH - TEXT_MARGIN - 82, KineticTheme.current().text(), true);
        Component id = entry.recipe().id() == null ? RecipeRemovalPage.tr("no_id") : Component.literal(entry.recipe().id().toString());
        graphics.scrollingTextCentered(id, PAGE_WIDTH / 2, 36, TEXT_WIDTH, KineticTheme.current().mutedText(), true);
        graphics.scrollingTextCentered(parent.statusLabel(entry), PAGE_WIDTH / 2, 50, TEXT_WIDTH, KineticTheme.current().mutedText(), true);
        KineticTheme.panelAlt(graphics, 16, 62, 608, 250);
        if (dataError) graphics.scrollingTextCentered(RecipeRemovalPage.tr("data_error"), PAGE_WIDTH / 2, 170,
                TEXT_WIDTH - 8, KineticTheme.current().danger(), true);
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

    private void renderFallback(KineticGraphics graphics) {
        var recipe = entry.recipe();
        graphics.scrollingTextCentered(RecipeRemovalPage.tr(dataError ? "data_error" : "fallback_preview"),
                PAGE_WIDTH / 2, 82, TEXT_WIDTH - 8,
                dataError ? KineticTheme.current().danger() : KineticTheme.current().mutedText(), true);
        int columns = recipe.craftingWidth() > 0 ? Math.min(9, recipe.craftingWidth()) : 9;
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < Math.min(recipe.inputs().size(), columns * 6); i++) {
            int x = 100 + i % columns * 22, y = 118 + i / columns * 22;
            RecipeSlots.draw(graphics, x, y, 20);
            try {
                var alternatives = recipe.alternatives(i);
                if (alternatives.length > 0) KineticTheme.item(graphics, alternatives[cycle % alternatives.length], x, y, 20, 1.0F, false);
            } catch (RuntimeException ignored) { markError(); }
        }
        graphics.text("→", 362, 172, KineticTheme.current().text(), false);
        RecipeSlots.draw(graphics, 412, 164, 20);
        KineticTheme.item(graphics, recipe.output(), 412, 164, 20, 1.0F, false);
        graphics.itemDecorations(recipe.output(), 414, 166);
    }

    @Override protected void renderTooltips(int mouseX, int mouseY) {
        if (mouseY >= 32 && mouseY < 62) {
            showTooltip(parent.recipeReasonLines(entry), 360);
            return;
        }
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
                    ItemStack[] alternatives = recipe.alternatives(i);
                    if (alternatives.length > 0) return alternatives[cycle % alternatives.length];
                } catch (RuntimeException ignored) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (mouseX >= 412 && mouseX < 432 && mouseY >= 164 && mouseY < 184) return recipe.output();
        return ItemStack.EMPTY;
    }

}
