package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;

import dev.xyat.contentstudio.loot.LootEntryInfo;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class LootEditorPage extends AbstractLootEditorPage {
    private static final int ENTITY_GRID_COLS = 4;
    private static final int ENTITY_GRID_ROWS = 6;
    private static final int ENTITY_CELL_SIZE = 48;
    private static final int BLOCK_CELL_SIZE = 18;
    private static final int BLOCK_CELL_GAP = 2;
    private static final int BLOCK_GRID_COLS = (TARGET_WIDTH + BLOCK_CELL_GAP) / (BLOCK_CELL_SIZE + BLOCK_CELL_GAP);
    private static final int BLOCK_GRID_ROWS = (TARGET_HEIGHT + BLOCK_CELL_GAP) / (BLOCK_CELL_SIZE + BLOCK_CELL_GAP);

    private final KineticEntityPreview entityPreviewRenderer = KineticEntityPreview.create();

    // 原带 parentScreen 的重载已移除（由 openChild 决定父界面）/ The former parentScreen overload was removed (openChild decides the parent).
    public LootEditorPage(int mode, List<LootEntryInfo> entries) {
        super(mode, entries, titleKey(mode));
        if (mode != LootEntryInfo.MODE_ENTITY && mode != LootEntryInfo.MODE_BLOCK) {
            throw new IllegalArgumentException("LootEditorPage only supports entity and block loot tables");
        }
        enableLootDraft();
    }

    private static String titleKey(int mode) {
        return mode == LootEntryInfo.MODE_ENTITY
                ? "gui.contentstudio.loot.loots.entity.title"
                : "gui.contentstudio.loot.loots.block.title";
    }

    private int gridCols() {
        return mode == LootEntryInfo.MODE_BLOCK ? BLOCK_GRID_COLS : ENTITY_GRID_COLS;
    }

    private int gridRows() {
        return mode == LootEntryInfo.MODE_BLOCK ? BLOCK_GRID_ROWS : ENTITY_GRID_ROWS;
    }

    private int cellSize() {
        return mode == LootEntryInfo.MODE_BLOCK ? BLOCK_CELL_SIZE : ENTITY_CELL_SIZE;
    }

    private int cellPitch() {
        return cellSize() + (mode == LootEntryInfo.MODE_BLOCK ? BLOCK_CELL_GAP : 0);
    }

    @Override
    protected int targetAreaHeight() {
        if (mode != LootEntryInfo.MODE_BLOCK) {
            return super.targetAreaHeight();
        }
        return gridRows() * BLOCK_CELL_SIZE + (gridRows() - 1) * BLOCK_CELL_GAP;
    }

    @Override
    protected int targetVisibleRows() {
        return gridRows();
    }

    @Override
    protected int targetTotalRows() {
        return (int) Math.ceil((double) displayEntries.size() / gridCols());
    }

    @Override
    protected int targetUnitOf(int index) {
        return index / gridCols();
    }

    @Override
    protected int targetStartIndex() {
        return (int) Math.floor(smoothTargetScroll() + 1.0E-6D) * gridCols();
    }

    @Override
    protected int targetVisibleEntryCount() {
        return gridRows() * gridCols();
    }

    @Override
    protected void renderTargets(KineticGraphics g, int mx, int my) {
        if (mode == LootEntryInfo.MODE_ENTITY) {
            displayEntries.sort(Comparator.comparing((LootEntryInfo entry) -> !isChangedEntry(entry)));
        }
        int cols = gridCols();
        int cell = cellSize();
        int pitch = cellPitch();
        double smoothScroll = smoothTargetScroll();
        int smoothRow = (int) Math.floor(smoothScroll + 1.0E-6D);
        int scrollShift = (int) Math.round((smoothScroll - smoothRow) * pitch);
        int start = smoothRow * cols;
        int end = Math.min(
                displayEntries.size(),
                start + targetVisibleEntryCount() + cols
        );
        g.scissor(LEFT_X,
                targetAreaY(),
                LEFT_X + TARGET_WIDTH,
                targetAreaY() + targetAreaHeight()
        );
        for (int i = start; i < end; i++) {
            LootEntryInfo entry = displayEntries.get(i);
            int local = i - start;
            int x = LEFT_X + local % cols * pitch;
            int y = targetAreaY() + local / cols * pitch - scrollShift;
            boolean hover = mx >= x && mx < x + cell && my >= y && my < y + cell;
            boolean selected = selectedEntry != null
                    && selectedEntry.targetId().equals(entry.targetId())
                    && selectedEntry.lootTableId().equals(entry.lootTableId());
            boolean changed = isChangedEntry(entry);
            if (mode == LootEntryInfo.MODE_ENTITY) {
                KineticEntityPreview.drawCheckerboard(g, x + 1, y + 1, cell - 2, cell - 2);
                boolean rendered = renderEntity(g, entry.targetId(), x, y, cell, hover);
                boolean error = hasEntityDataError(entry) || !rendered;
                renderEntityOutline(g, x, y, cell, selected, hover, changed, error);
                int previewTop = Math.max(y, targetAreaY());
                int previewBottom = Math.min(y + cell, targetAreaY() + targetAreaHeight());
                if (previewBottom > previewTop) {
                    registerPreviewZoomArea(
                            entityPreviewRenderer,
                            entityPreviewKey(entry.targetId()),
                            x,
                            previewTop,
                            cell,
                            previewBottom - previewTop
                    );
                }
            } else {
                ItemStack targetStack = blockStack(entry.targetId());
                drawCheckerboard(g, x + 1, y + 1, cell - 2, cell - 2);
                if (selected) {
                    KineticTheme.stateOutline(g, x, y, cell, cell, true, false, false);
                } else if (changed) {
                    KineticTheme.indicatorOutline(g, x, y, cell, cell, KineticTheme.Indicator.WARNING);
                } else {
                    KineticTheme.stateOutline(g, x, y, cell, cell, false, hover, false);
                }
                KineticTheme.item(g, targetStack, x, y, cell, 0.75F, false);
            }
            flashTarget(g, i, x, y, cell, cell);
            if (hover) {
                if (mode == LootEntryInfo.MODE_ENTITY) {
                    deferredTooltip = List.of(
                            nameComponent(getDisplayName(entry)),
                            idComponent(entry.targetId()),
                            idComponent(entry.lootTableId()),
                            KineticI18n.translatable(
                                    "gui.kineticcore.entity_selector.preview_zoom",
                                    entityPreviewRenderer.zoomPercent(entityPreviewKey(entry.targetId()))
                            )
                    );
                } else {
                    deferredTooltip = List.of(
                            nameComponent(getDisplayName(entry)),
                            idComponent(entry.targetId()),
                            idComponent(entry.lootTableId())
                    );
                }
            }
        }
        g.endScissor();
        renderTargetScrollbar(g, mx, my);
    }

    @Override
    protected LootEntryInfo targetEntryAt(double mx, double my) {
        if (mx < LEFT_X || mx >= LEFT_X + TARGET_WIDTH || my < targetAreaY() || my >= targetAreaY() + targetAreaHeight()) {
            return null;
        }
        int cell = cellSize();
        int pitch = cellPitch();
        int cols = gridCols();
        int localX = (int) (mx - LEFT_X);
        double smoothScroll = smoothTargetScroll();
        int smoothRow = (int) Math.floor(smoothScroll + 1.0E-6D);
        int scrollShift = (int) Math.round((smoothScroll - smoothRow) * pitch);
        int localY = (int) Math.floor(my - targetAreaY() + scrollShift);
        int column = localX / pitch;
        int row = localY / pitch;
        if (column < 0 || column >= cols || row < 0 || row >= gridRows()) {
            return null;
        }
        if (localX % pitch >= cell || localY % pitch >= cell) {
            return null;
        }
        int index = smoothRow * cols + row * cols + column;
        return index >= 0 && index < displayEntries.size() ? displayEntries.get(index) : null;
    }

    @Override
    protected String getDisplayName(LootEntryInfo entry) {
        try {
            ResourceLocation id = KineticResourceIds.parse(entry.targetId());
            if (mode == LootEntryInfo.MODE_ENTITY) {
                EntityType<?> type = KineticRegistries.entityTypes().get(id);
                if (type != null) {
                    return type.getDescription().getString();
                }
            } else {
                String key = Util.makeDescriptionId("block", id);
                String translated = KineticI18n.translatable(key).getString();
                return translated.equals(key) ? entry.targetId() : translated;
            }
        } catch (Exception ignored) {
        }
        return entry.targetId();
    }

    private ItemStack blockStack(String id) {
        try {
            var block = KineticRegistries.blocks().get(KineticResourceIds.parse(id));
            if (block != null) {
                return new ItemStack(block.asItem());
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    private boolean renderEntity(
            KineticGraphics g,
            String id,
            int cellX,
            int cellY,
            int cell,
            boolean hovered
    ) {
        int previewX = cellX + 3;
        int previewY = cellY + 3;
        int previewSize = cell - 6;
        boolean rendered = entityPreviewRenderer.render(
                g,
                id,
                entityPreviewKey(id),
                previewX,
                previewY,
                previewSize,
                previewSize,
                hovered
        );
        if (!rendered) {
            g.scrollingTextCentered(KineticI18n.translatable("gui.contentstudio.loot.loots.preview_failed"), cellX + cell / 2, cellY + cell / 2 - 4, cell - 2 * TEXT_GAP, 0xFFFF5555, true);
        }
        return rendered;
    }

    private boolean isChangedEntry(LootEntryInfo entry) {
        if (entry == null) return false;
        boolean selectedDirty = selectedEntry != null
                && dirty
                && selectedEntry.targetId().equals(entry.targetId())
                && selectedEntry.lootTableId().equals(entry.lootTableId());
        return entry.overridden() || hasPendingDraft(entry) || selectedDirty;
    }

    private static boolean hasEntityDataError(LootEntryInfo entry) {
        if (entry == null) return true;
        ResourceLocation entityId = KineticResourceIds.tryParse(entry.targetId());
        ResourceLocation lootTableId = KineticResourceIds.tryParse(entry.lootTableId());
        return entityId == null
                || lootTableId == null
                || KineticRegistries.entityTypes().get(entityId) == null;
    }

    private static void renderEntityOutline(
            KineticGraphics graphics,
            int x,
            int y,
            int size,
            boolean selected,
            boolean hovered,
            boolean changed,
            boolean error
    ) {
        if (error) {
            KineticTheme.stateOutline(graphics, x, y, size, size, false, false, true);
        } else if (hovered) {
            KineticTheme.stateOutline(graphics, x, y, size, size, false, true, false);
        } else if (changed) {
            KineticTheme.indicatorOutline(graphics, x, y, size, size, KineticTheme.Indicator.SUCCESS);
        } else KineticTheme.stateOutline(graphics, x, y, size, size, selected, false, false);
    }

    private static String entityPreviewKey(String id) {
        return "loots:" + id;
    }

    @Override
    protected void onRemoved() {
        entityPreviewRenderer.clear();
    }
}
