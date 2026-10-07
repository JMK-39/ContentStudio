package dev.xyat.contentstudio.villager.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VillagerFollowItemEditorPage extends KineticPage {
    private static final int PANEL_X = 42;
    private static final int PANEL_WIDTH = 556;
    private static final int GRID_X = 69;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final int COLUMNS = 27;
    private static final int GRID_WIDTH = COLUMNS * CELL_SIZE - SLOT_GAP;
    private static final int SCROLL_X = GRID_X + GRID_WIDTH + 6;
    // The grid shows the rows its items need plus one free row (4 to 13), and the panel sits in the middle of the canvas.
    private static final int MIN_ROWS = 4;
    private static final int MAX_ROWS = 13;
    private int panelY, panelHeight, gridY, rowsVisible = MAX_ROWS, gridHeight;

    private final List<String> items = new ArrayList<>();
    private final Map<String, ItemStack> previewCache = new HashMap<>();
    private final KineticScrollController scroll = new KineticScrollController();
    private int hoveredIndex = -1;
    // 本页无选中概念：中键跳转目标为最近左键点击的格子，-1 表示无 / This page has no selection: the middle-click target is the last left-clicked cell; -1 means none.
    private int lastClickedIndex = -1;

    private VillagerFollowItemEditorPage(List<String> initialItems) {
        super(KineticI18n.translatable("gui.contentstudio.villager.follow_item_editor.title"));
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        if (initialItems != null) {
            Set<String> unique = new LinkedHashSet<>();
            for (String itemId : initialItems) {
                String normalized = normalizeItemId(itemId);
                if (!normalized.isEmpty()) unique.add(normalized);
            }
            items.addAll(unique);
        }
        scroll.bindSelection(
                () -> lastClickedIndex < items.size() ? lastClickedIndex : -1,
                index -> index / COLUMNS - rowsVisible / 2
        );
    }

    public static VillagerFollowItemEditorPage create(List<String> items) {
        return new VillagerFollowItemEditorPage(items);
    }

    @Override
    protected void build(KineticUi ui) {
        rowsVisible = wantedRows();
        gridHeight = rowsVisible * CELL_SIZE - SLOT_GAP;
        // Title row, the grid, the buttons and their margins.
        panelHeight = 42 + gridHeight + 10 + 20 + 12;
        panelY = Math.max(0, (height() - panelHeight) / 2);
        gridY = panelY + 42;
        updateScrollRange();

        int buttonY = gridY + gridHeight + 10;
        ui().button(70, buttonY, 130).text(KineticI18n.translatable("gui.kineticcore.items.list_editor.add")).onClick(this::openSelector).build();
        ui().button(255, buttonY, 130).text(KineticI18n.translatable("gui.kineticcore.config.back")).onClick(this::close).build();
        ui().button(440, buttonY, 130).text(KineticI18n.translatable("gui.kineticcore.hud_editor.save")).onClick(this::save).build();
    }

    private int wantedRows() {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, totalRows() + 1));
    }

    // The window grows or shrinks when an added or removed item changes the row count.
    private void refreshLayout() {
        if (isAttached() && wantedRows() != rowsVisible) rebuild();
        else updateScrollRange();
    }

    private void openSelector() {
        KineticSelectors.openItemSelector(this::acceptSelection);
    }

    private void acceptSelection(KineticSelectors.ItemSelection selection) {
        if (selection == null) return;
        if (!selection.isItem()) {
            KineticOverlays.toast(
                    "contentstudio_follow_item_item_only",
                    KineticI18n.translatable("gui.kineticcore.items.list_editor.item_only")
            );
            return;
        }

        ResourceLocation id = KineticRegistries.items().id(selection.stack().getItem());
        if (id == null) return;
        String value = id.toString();
        if (!items.contains(value)) {
            items.add(value);
            refreshLayout();
        }
    }

    private void save() {
        VillagerNetwork.saveFollowItems(List.copyOf(items));
    }

    private void updateScrollRange() {
        scroll.update(totalRows(), rowsVisible);
    }

    private int totalRows() {
        return (items.size() + COLUMNS - 1) / COLUMNS;
    }

    @Override
    protected void renderBackground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        KineticTheme.panel(graphics, PANEL_X, panelY, PANEL_WIDTH, panelHeight);
        graphics.scrollingTextCentered(title(), width() / 2, panelY + 12, PANEL_WIDTH - 16, 0xFFFFAA00, true);
        // The frame sits 3 px outside the slots so no slot lies on its lines.
        KineticTheme.panel(graphics, GRID_X - 3, gridY - 3, GRID_WIDTH + 6, gridHeight + 6);
        renderItems(graphics, mouseX, mouseY);
        scroll.render(graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                gridY,
                4,
                gridHeight,
                18
        );
    }

    @Override
    protected void renderForeground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (items.isEmpty()) {
            graphics.scrollingTextCentered(KineticI18n.translatable("gui.kineticcore.items.list_editor.empty"), GRID_X + GRID_WIDTH / 2, gridY + gridHeight / 2 - KineticText.lineHeight() / 2, GRID_WIDTH - 8, 0xFFAAAAAA, true);
        }
    }

    private void renderItems(KineticGraphics graphics, int mouseX, int mouseY) {
        hoveredIndex = indexAt(mouseX, mouseY);
        int baseRow = scroll.smoothIndexOffset();
        int visualShift = scroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(items.size(), first + (rowsVisible + 2) * COLUMNS);

        graphics.scissor(GRID_X, gridY, GRID_X + GRID_WIDTH, gridY + gridHeight);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int column = visible % COLUMNS;
            int row = visible / COLUMNS;
            int x = GRID_X + column * CELL_SIZE;
            int y = gridY + row * CELL_SIZE - visualShift;
            boolean hovered = index == hoveredIndex;

            KineticTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, hovered);
            ItemStack stack = previewStack(items.get(index));
            if (!stack.isEmpty()) {
                KineticTheme.item(
                        graphics,
                        stack,
                        x,
                        y,
                        SLOT_SIZE,
                        1.0F,
                        true
                );
            }
            scroll.renderSelectionFlash(graphics, index, x, y, SLOT_SIZE, SLOT_SIZE);
        }
        graphics.endScissor();
    }

    private ItemStack previewStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        return previewCache.computeIfAbsent(itemId, VillagerFollowItemEditorPage::buildPreviewStack);
    }

    private static ItemStack buildPreviewStack(String itemId) {
        ResourceLocation id = KineticResourceIds.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = KineticRegistries.items().get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!KineticTheme.hovering(mouseX, mouseY, GRID_X, gridY, GRID_WIDTH, gridHeight)) {
            return -1;
        }
        int localX = (int) (mouseX - GRID_X);
        int visualShift = scroll.visualShift(CELL_SIZE);
        int localY = (int) Math.floor(mouseY - gridY + visualShift);
        int column = localX / CELL_SIZE;
        int row = localY / CELL_SIZE;
        if (column >= COLUMNS || row > rowsVisible) return -1;
        if (localX % CELL_SIZE == SLOT_SIZE || localY % CELL_SIZE == SLOT_SIZE) return -1;
        int index = (scroll.smoothIndexOffset() + row) * COLUMNS + column;
        return index >= 0 && index < items.size() ? index : -1;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        // 原逻辑在控件之后运行 / The old logic ran after controls.
        if (scroll.beginDrag(
                mouseX,
                mouseY,
                input.button(),
                SCROLL_X,
                gridY,
                6,
                gridHeight,
                18,
                2
        )) {
            return true;
        }

        if (input.isRight()) {
            int index = indexAt(mouseX, mouseY);
            if (index >= 0) {
                items.remove(index);
                if (lastClickedIndex == index) lastClickedIndex = -1;
                else if (lastClickedIndex > index) lastClickedIndex--;
                refreshLayout();
                return true;
            }
        }
        if (input.isLeft()) {
            int index = indexAt(mouseX, mouseY);
            if (index >= 0) lastClickedIndex = index;
        }
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseY = input.y();
        return scroll.drag(mouseY, gridY, gridHeight, 18);
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        return scroll.release(input.button());
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        double delta = input.deltaY();
        return KineticTheme.hovering(mouseX, mouseY, GRID_X, gridY, GRID_WIDTH + 12, gridHeight)
                && scroll.scroll(delta);
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (hoveredIndex < 0 || hoveredIndex >= items.size()) return;
        String itemId = items.get(hoveredIndex);
        List<Component> lines = new ArrayList<>();
        ItemStack stack = previewStack(itemId);
        if (!stack.isEmpty()) {
            lines.add(stack.getHoverName());
        }
        lines.add(Component.literal(itemId));
        lines.add(KineticI18n.translatable("gui.kineticcore.items.list_editor.remove_hint"));
        showTooltip(lines, 300);
    }

    private static String normalizeItemId(String itemId) {
        if (itemId == null) return "";
        String normalized = itemId.trim();
        return KineticResourceIds.tryParse(normalized) == null ? "" : normalized;
    }

    @Override
    protected boolean onCloseRequested() {
        navigateBack();
        return true;
    }

}
