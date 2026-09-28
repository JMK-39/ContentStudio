package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.contentstudio.recipe.RecipeDatabase;
import dev.xyat.contentstudio.recipe.RecipeRecord;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecipePreviewPage extends KineticPage {
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final float ITEM_SCALE = 1.2F;
    private static final int COUNT_COLOR = 0xFF55FF55;
    private static final int SCISSOR_MARGIN = 2;

    // 原 parent != null：打开时是否存在父界面 / Former parent != null: whether a parent screen existed when opened.
    private final boolean hasParentScreen;
    private KineticTextField searchBox;
    private int gridX;
    private int gridY;
    private int gridW;
    private int columns = 1;
    private int visibleRows = 1;
    private int gridH;

    private final KineticScrollController gridScroll =
            new KineticScrollController();

    private boolean compactToolbar;
    private KineticButton saveButton;
    private final List<RecipeRecord> displayRecords = new ArrayList<>();
    private final Set<RecipeKey> pendingDeletes = new LinkedHashSet<>();
    // 网格无选中概念：中键跳转目标为最近点击的配方记录（按键跟踪，记录会随服务端刷新重建）
    // The grid has no selection: the middle-click target is the last clicked recipe record (tracked by key because
    // records are rebuilt when the server refreshes them).
    private RecipeKey lastClickedKey;

    public RecipePreviewPage() {
        super(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.manage.title"));
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        // 构造紧接在 KineticGui.openChild 之前，当前界面即父界面 / Constructed right before KineticGui.openChild, so the current screen becomes the parent.
        this.hasParentScreen = KineticGui.isScreenOpen();
        configureStandaloneDraft(this::capturePreviewSnapshot, this::restorePreviewSnapshot);
        gridScroll.bindSelection(this::lastClickedRecordIndex, index -> index / safeColumns() - safeVisibleRows() / 2);
    }

    private int lastClickedRecordIndex() {
        if (lastClickedKey == null) return -1;
        for (int i = 0; i < displayRecords.size(); i++) {
            RecipeRecord record = displayRecords.get(i);
            if (lastClickedKey.equals(new RecipeKey(record.uuid, record.configIndex, record.editorType))) return i;
        }
        return -1;
    }

    private record RecipeKey(String uuid, int configIndex, String editorType) {
    }

    private record PreviewSnapshot(List<RecipeKey> pendingDeletes) {
    }

    private PreviewSnapshot capturePreviewSnapshot() {
        return new PreviewSnapshot(List.copyOf(pendingDeletes));
    }

    private void restorePreviewSnapshot(PreviewSnapshot snapshot) {
        if (snapshot == null) return;
        pendingDeletes.clear();
        pendingDeletes.addAll(snapshot.pendingDeletes());
        refreshPendingDeleteState();
    }

    private RecipeKey keyOf(RecipeRecord record) {
        return new RecipeKey(
                record == null || record.uuid == null ? "" : record.uuid,
                record == null ? -1 : record.configIndex,
                record == null || record.editorType == null ? "" : record.editorType
        );
    }

    private boolean isNotPendingDeleted(RecipeRecord record) {
        return !pendingDeletes.contains(keyOf(record));
    }

    private RecipeRecord findRecord(RecipeKey key) {
        for (RecipeRecord record : RecipeDatabase.records) {
            if (keyOf(record).equals(key)) return record;
        }
        for (RecipeRecord record : RecipeDatabase.invalidRecords) {
            if (keyOf(record).equals(key)) return record;
        }
        return null;
    }

    private void refreshPendingDeleteState() {
        if (searchBox != null) onSearchUpdate(searchBox.textValue());
        if (saveButton != null) saveButton.setEnabled(!pendingDeletes.isEmpty());
    }

    private void savePendingDeletes() {
        if (pendingDeletes.isEmpty()) return;
        List<RecipeKey> saved = List.copyOf(pendingDeletes);
        for (RecipeKey key : saved) {
            RecipeRecord record = findRecord(key);
            if (record == null) continue;
            RecipeNetwork.sendRecipeChange(
                    new RecipeNetwork.RecipeChangePacket(
                            record.uuid,
                            record.configIndex,
                            record.editorType,
                            record.isShapeless,
                            record.inputModes,
                            record.outputUseNbt,
                            1,
                            record.inputs,
                            record.output
                    )
            );
        }
        RecipeEditSessionState.markPendingRecipeApply();
        commitDraft();
    }

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    public void refreshFromServer() {
        pendingDeletes.removeIf(key -> findRecord(key) == null);
        refreshPendingDeleteState();
    }

    @Override
    protected void build(KineticUi ui) {
        RecipePreviewState.returnToPreview = true;

        int sidePadding = 12;

        compactToolbar = false;

        int buttonY = 5;
        int backWidth = 60;
        int saveWidth = 60;
        int refreshWidth = 60;
        int toolbarGap = 6;

        ui().button(sidePadding, buttonY, backWidth).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.back")).onClick(() -> {
                    if (!isAttached()) {
                        return;
                    }

                    if (hasParentScreen) {
                        navigateBack();
                    } else if (KineticClientRuntime.localPlayer() != null) {
                        RecipeNavigationState.requestHub();
                    }
                }).build();

        int refreshX =
                width()
                        - sidePadding
                        - refreshWidth;
        int saveX = refreshX - toolbarGap - saveWidth;

        saveButton = ui().button(saveX, buttonY, saveWidth).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.save_deferred")).onClick(this::savePendingDeletes).build();
        saveButton.setEnabled(!pendingDeletes.isEmpty());

        ui().button(refreshX, buttonY, refreshWidth).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.preview.refresh")).onClick(RecipeNetwork::requestRecipeRecords).build();

        int searchY;
        int searchX;
        int searchWidth;

        if (compactToolbar) {
            searchY = 31;
            searchX = sidePadding;
            searchWidth =
                    Math.max(
                            80,
                            width()
                                    - sidePadding * 2
                    );
            gridY = 59;
        } else {
            searchY = 5;

            int searchAreaStart =
                    sidePadding
                            + backWidth
                            + toolbarGap;

            int searchAreaEnd =
                    saveX
                            - toolbarGap;

            int availableSearchWidth =
                    Math.max(
                            100,
                            searchAreaEnd - searchAreaStart
                    );

            searchWidth =
                    Math.max(
                            100,
                            Math.min(
                                    320,
                                    availableSearchWidth
                            )
                    );

            searchX =
                    searchAreaStart
                            + Math.max(
                                    0,
                                    (availableSearchWidth - searchWidth) / 2
                            );

            gridY = 35;
        }

        searchBox = ui().textField(searchX, searchY, searchWidth).placeholder(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.search_hint")).build();

        searchBox.onTextChange(
                this::onSearchUpdate
        );

        searchBox.setTextValue(
                RecipePreviewState.searchQuery
        );


        int scrollbarReserve = 10;

        int availableGridWidth =
                Math.max(
                        SLOT_SIZE,
                        width()
                                - sidePadding * 2
                                - scrollbarReserve
                );

        columns =
                Math.max(
                        1,
                        (availableGridWidth + SLOT_GAP) / CELL_SIZE
                );

        gridW =
                columns * SLOT_SIZE
                        + Math.max(0, columns - 1) * SLOT_GAP;

        gridX =
                Math.max(
                        sidePadding,
                        (
                                width()
                                        - gridW
                                        - scrollbarReserve
                        ) / 2
                );

        int bottomPadding = 8;

        int availableGridHeight =
                Math.max(
                        SLOT_SIZE,
                        height()
                                - gridY
                                - bottomPadding
                );

        visibleRows =
                Math.max(
                        1,
                        (availableGridHeight + SLOT_GAP) / CELL_SIZE
                );

        gridH =
                visibleRows * SLOT_SIZE
                        + Math.max(0, visibleRows - 1) * SLOT_GAP;

        onSearchUpdate(
                searchBox.textValue()
        );

        gridScroll.setOffset(
                RecipePreviewState.scrollOffset
        );

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );
    }

    @Override
    protected void onRemoved() {
        RecipePreviewState.scrollOffset =
                gridScroll.offset();

        RecipePreviewState.searchQuery =
                searchBox == null
                        ? ""
                        : searchBox.textValue();
    }

    private void onSearchUpdate(String query) {
        displayRecords.clear();
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        List<RecipeRecord> validRecords = new ArrayList<>();
        List<RecipeRecord> invalidRecords = new ArrayList<>();

        for (RecipeRecord record : RecipeDatabase.records) {
            if (isNotPendingDeleted(record) && isDisplayableRecord(record) && matchesSearch(record, lowerQuery)) {
                validRecords.add(record);
            }
        }
        for (RecipeRecord record : RecipeDatabase.invalidRecords) {
            if (isNotPendingDeleted(record) && isDisplayableRecord(record) && matchesSearch(record, lowerQuery)) {
                invalidRecords.add(record);
            }
        }

        displayRecords.addAll(validRecords);
        displayRecords.addAll(invalidRecords);

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );
    }

    private boolean matchesSearch(RecipeRecord record, String lowerQuery) {
        if (lowerQuery.isEmpty()) {
            return true;
        }
        ResourceLocation id = KineticRegistries.items().id(record.output.getItem());
        if (id == null) {
            return false;
        }
        if (lowerQuery.startsWith("@")) {
            return id.getNamespace().contains(lowerQuery.substring(1));
        }
        if (lowerQuery.startsWith("#")) {
            String tagQuery = lowerQuery.substring(1);
            return record.output.getTags().anyMatch(tag -> tag.location().toString().contains(tagQuery));
        }
        String name = record.output.getHoverName().getString().toLowerCase(Locale.ROOT);
        String searchStr = id + " " + name + " " + KineticSearch.pinyin(name);
        return KineticSearch.match(searchStr, lowerQuery);
    }


    private boolean isDisplayableRecord(RecipeRecord record) {
        if (record == null || record.output == null || record.output.isEmpty()) {
            return false;
        }
        try {
            RecipeRegistry.EditorType.valueOf(record.editorType);
            ResourceLocation id = KineticRegistries.items().id(record.output.getItem());
            return id != null && KineticRegistries.items().contains(id);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void renderBackground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        KineticTheme.surface(
                graphics,
                0,
                0,
                width(),
                height(),
                KineticTheme.Surface.PANEL_ALT,
                0.73F
        );

        if (gridW <= 0 || gridH <= 0) {
            return;
        }

        KineticTheme.panel(
                graphics,
                gridX - 2,
                gridY - 2,
                gridW + 4,
                gridH + 4
        );

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        int startRow = gridScroll.smoothIndexOffset();
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int startIndex = startRow * safeColumns();

        int endIndex =
                Math.min(
                        startIndex
                                + (safeVisibleRows() + 1) * safeColumns(),
                        displayRecords.size()
                );

        graphics.scissor(gridX - SCISSOR_MARGIN,
                gridY - SCISSOR_MARGIN,
                gridX + gridW + SCISSOR_MARGIN,
                gridY + gridH + SCISSOR_MARGIN
        );

        for (int i = startIndex;
             i < endIndex;
             i++) {
            int localIndex =
                    i - startIndex;

            int column =
                    localIndex % safeColumns();

            int row =
                    localIndex / safeColumns();

            int x =
                    gridX
                            + column * CELL_SIZE;

            int y =
                    gridY
                            + row * CELL_SIZE
                            - visualShift;

            boolean hovered = mouseX >= x
                    && mouseX < x + SLOT_SIZE
                    && mouseY >= y
                    && mouseY < y + SLOT_SIZE;

            RecipeRecord record =
                    displayRecords.get(i);

            KineticTheme.itemGrid(graphics, x, y, SLOT_SIZE, SLOT_SIZE);

            KineticTheme.stateOutline(
                    graphics,
                    x,
                    y,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    false,
                    hovered,
                    record.invalidConfig
            );

            KineticTheme.item(
                    graphics,
                    record.output,
                    x,
                    y,
                    SLOT_SIZE,
                    ITEM_SCALE,
                    false
            );

            renderGreenCount(
                    graphics,
                    record.output,
                    x,
                    y
            );
            gridScroll.renderSelectionFlash(graphics, i, x, y, SLOT_SIZE, SLOT_SIZE);

        }

        graphics.endScissor();

        gridScroll.render(
                graphics,
                mouseX,
                mouseY,
                gridX + gridW + 4,
                gridY,
                4,
                gridH,
                20
        );
    }

    @Override
    protected void renderForeground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
    }

    @Override
    protected void renderTooltips(
            int scaledMouseX,
            int scaledMouseY
    ) {
        if (gridW <= 0 || gridH <= 0) {
            return;
        }

        if (scaledMouseX < gridX
                || scaledMouseX >= gridX + gridW
                || scaledMouseY < gridY
                || scaledMouseY >= gridY + gridH) {
            return;
        }

        int index =
                recordIndexAt(
                        scaledMouseX,
                        scaledMouseY
                );

        if (index < 0
                || index >= displayRecords.size()) {
            return;
        }

        RecipeRecord record =
                displayRecords.get(index);

        RecipeRegistry.EditorType editorType =
                RecipeRegistry.EditorType.valueOf(
                        record.editorType
                );

        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                Component.literal("[")
                        .append(
                                editorType.getTitle()
                        )
                        .append("] ")
                        .append(
                                record.output.getHoverName()
                        )
        );

        tooltip.add(
                Component.empty()
        );

        if (record.invalidConfig) {
            tooltip.add(
                    KineticI18n.translatable(
                            "gui.contentstudio.recipe.recipehud.tooltip.invalid_recipe.colored"
                    )
            );
        }

        tooltip.add(
                KineticI18n.translatable(
                        "gui.contentstudio.recipe.recipehud.tooltip.left_edit.colored"
                )
        );

        tooltip.add(
                KineticI18n.translatable(
                        "gui.contentstudio.recipe.recipehud.tooltip.right_delete.colored"
                )
        );

        showTooltip(tooltip, 260);
    }

    private boolean sameRecord(RecipeRecord left, RecipeRecord right) {
        if (left == null || right == null) {
            return false;
        }
        if (right.configIndex >= 0) {
            return left.configIndex == right.configIndex;
        }
        return left.uuid != null && left.uuid.equals(right.uuid);
    }

    private void renderGreenCount(
            KineticGraphics graphics,
            net.minecraft.world.item.ItemStack stack,
            int x,
            int y
    ) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }

        String countText = String.valueOf(stack.getCount());
        int textX = x + SLOT_SIZE - KineticText.width(countText) - 1;
        int textY = y + SLOT_SIZE - KineticText.lineHeight();

        graphics.push();
        graphics.translate(
                0, 0);
        graphics.text(countText, textX, textY, COUNT_COLOR, true);
        graphics.pop();
    }

    private int recordIndexAt(double mouseX, double mouseY) {
        if (gridW <= 0 || gridH <= 0
                || mouseX < gridX
                || mouseX >= gridX + gridW
                || mouseY < gridY
                || mouseY >= gridY + gridH) {
            return -1;
        }

        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int relativeX = (int) Math.floor(mouseX - gridX);
        int relativeY = (int) Math.floor(mouseY - gridY + visualShift);
        int column = relativeX / CELL_SIZE;
        int row = relativeY / CELL_SIZE;

        if (column < 0 || column >= safeColumns()
                || row < 0 || row > safeVisibleRows()
                || relativeX % CELL_SIZE >= SLOT_SIZE
                || relativeY % CELL_SIZE >= SLOT_SIZE) {
            return -1;
        }

        return gridScroll.smoothIndexOffset()
                * safeColumns()
                + row * safeColumns()
                + column;
    }

    private int totalRows() {
        int activeColumns = safeColumns();
        return (
                displayRecords.size()
                        + activeColumns
                        - 1
        ) / activeColumns;
    }

    private int safeColumns() {
        return Math.max(1, columns);
    }

    private int safeVisibleRows() {
        return Math.max(1, visibleRows);
    }


    @Override
    protected boolean onCloseRequested() {
        RecipeEditSessionState.applyPendingAndClear();
        return false;
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在控件之前失焦搜索框（不消费点击）
        // The old canvasMouseClicked blurred the search box before controls (without consuming the click).
        if (searchBox != null
                && !searchBox.contains(
                        input.x(),
                        input.y()
                )) {
            blur(searchBox);
        }
        return false;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        if (gridScroll.beginDrag(
                        mouseX,
                        mouseY,
                        input.button(),
                        gridX + gridW + 4,
                        gridY,
                        4,
                        gridH,
                        20,
                        0
                )) {
            return true;
        }

        if ((!input.isLeft() && !input.isRight())
                || mouseX < gridX
                || mouseX >= gridX + gridW
                || mouseY < gridY
                || mouseY >= gridY + gridH) {
            return false;
        }

        int index =
                recordIndexAt(
                        mouseX,
                        mouseY
                );

        if (index < 0
                || index >= displayRecords.size()) {
            return false;
        }

        RecipeRecord record =
                displayRecords.get(index);
        lastClickedKey = new RecipeKey(record.uuid, record.configIndex, record.editorType);

        if (input.isLeft()) {
            RecipeNetwork.requestEdit(
                    record.uuid,
                    record.editorType,
                    record.configIndex
            );

            return true;
        }

        pendingDeletes.add(keyOf(record));
        displayRecords.remove(index);
        refreshPendingDeleteState();
        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        return true;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseY = input.y();
        if (gridScroll.drag(
                mouseY,
                gridY,
                gridH,
                20
        )) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        if (gridScroll.release(input.button())) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double delta = input.deltaY();
        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        return gridScroll.scroll(delta);
    }
}
