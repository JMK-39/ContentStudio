package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.contentstudio.recipe.RecipeDatabase;
import dev.xyat.contentstudio.recipe.RecipeRecord;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecipePreviewScreen extends KineticScreen {
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final float ITEM_SCALE = 1.2F;
    private static final int COUNT_COLOR = 0xFF55FF55;
    private static final int SCISSOR_MARGIN = 2;

    private final Screen parent;
    private KineticEditBox searchBox;
    private int gridX;
    private int gridY;
    private int gridW;
    private int columns = 1;
    private int visibleRows = 1;
    private int gridH;

    private final GridScrollController gridScroll =
            new GridScrollController();

    private boolean compactToolbar;
    private StateButton saveButton;
    private final List<RecipeRecord> displayRecords = new ArrayList<>();
    private final Set<RecipeKey> pendingDeletes = new LinkedHashSet<>();

    public RecipePreviewScreen(Screen parent) {
        super(Component.translatable("gui.contentstudio.recipe.recipehud.manage.title"));
        this.parent = parent;
        setParentScreen(parent);
configureStandaloneDraft(this::capturePreviewSnapshot, this::restorePreviewSnapshot);
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
        if (searchBox != null) onSearchUpdate(searchBox.getValue());
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
    protected void buildUi() {
        RecipePreviewState.returnToPreview = true;

        int sidePadding = 12;

        compactToolbar = false;

        int buttonY = 5;
        int backWidth = 60;
        int saveWidth = 60;
        int refreshWidth = 60;
        int toolbarGap = 6;

        addButton(
                sidePadding, buttonY, backWidth,
                Component.translatable("gui.contentstudio.recipe.recipehud.back"),
                null,
                () -> {
                    if (minecraft == null) {
                        return;
                    }

                    if (parent != null) {
                        navigateBack();
                    } else if (KineticClientRuntime.localPlayer() != null) {
                        RecipeNavigationState.requestHub();
                    }
                }
        );

        int refreshX =
                canvasWidth()
                        - sidePadding
                        - refreshWidth;
        int saveX = refreshX - toolbarGap - saveWidth;

        saveButton = addButton(
                saveX, buttonY, saveWidth,
                Component.translatable("gui.contentstudio.recipe.recipehud.save_deferred"),
                null,
                this::savePendingDeletes
        );
        saveButton.setEnabled(!pendingDeletes.isEmpty());

        addButton(
                refreshX, buttonY, refreshWidth,
                Component.translatable("gui.contentstudio.recipe.recipehud.preview.refresh"),
                null,
                RecipeNetwork::requestRecipeRecords
        );

        int searchY;
        int searchX;
        int searchWidth;

        if (compactToolbar) {
            searchY = 31;
            searchX = sidePadding;
            searchWidth =
                    Math.max(
                            80,
                            canvasWidth()
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

        searchBox = addTextField(
                searchX,
                searchY,
                searchWidth,
                Component.empty(),
                Component.translatable("gui.contentstudio.recipe.recipehud.search_hint"),
                null,
                null
        );

        searchBox.setResponder(
                this::onSearchUpdate
        );

        searchBox.setValue(
                RecipePreviewState.searchQuery
        );


        int scrollbarReserve = 10;

        int availableGridWidth =
                Math.max(
                        SLOT_SIZE,
                        canvasWidth()
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
                                canvasWidth()
                                        - gridW
                                        - scrollbarReserve
                        ) / 2
                );

        int bottomPadding = 8;

        int availableGridHeight =
                Math.max(
                        SLOT_SIZE,
                        canvasHeight()
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
                searchBox.getValue()
        );

        gridScroll.restoreOffset(
                RecipePreviewState.scrollOffset
        );

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );
    }

    @Override
    protected void screenRemoved() {
        RecipePreviewState.scrollOffset =
                gridScroll.offset();

        RecipePreviewState.searchQuery =
                searchBox == null
                        ? ""
                        : searchBox.getValue();
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
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.surface(
                graphics,
                0,
                0,
                canvasWidth(),
                canvasHeight(),
                GuiTheme.Surface.PANEL_ALT,
                0.73F
        );

        if (gridW <= 0 || gridH <= 0) {
            return;
        }

        GuiTheme.panel(
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

        enableUiScissor(
                graphics,
                gridX - SCISSOR_MARGIN,
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

            GuiTheme.itemSlot(
                    graphics,
                    x,
                    y,
                    SLOT_SIZE,
                    4,
                    false
            );

            GuiTheme.stateOutline(
                    graphics,
                    x,
                    y,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    false,
                    hovered,
                    record.invalidConfig
            );

            GuiTheme.item(
                    graphics,
                    font,
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

        }

        disableUiScissor(graphics);

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
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
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
                    Component.translatable(
                            "gui.contentstudio.recipe.recipehud.tooltip.invalid_recipe.colored"
                    )
            );
        }

        tooltip.add(
                Component.translatable(
                        "gui.contentstudio.recipe.recipehud.tooltip.left_edit.colored"
                )
        );

        tooltip.add(
                Component.translatable(
                        "gui.contentstudio.recipe.recipehud.tooltip.right_delete.colored"
                )
        );

        KineticOverlays.requestTooltip(tooltip, 260, mouseX, mouseY);
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
            GuiGraphics graphics,
            net.minecraft.world.item.ItemStack stack,
            int x,
            int y
    ) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }

        String countText = String.valueOf(stack.getCount());
        int textX = x + SLOT_SIZE - font.width(countText) - 1;
        int textY = y + SLOT_SIZE - font.lineHeight;

        graphics.pose().pushPose();
        graphics.pose().translate(
                0,
                0,
                250
        );
        graphics.drawString(
                font,
                countText,
                textX,
                textY,
                COUNT_COLOR,
                true
        );
        graphics.pose().popPose();
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected boolean handleCloseRequest() {
        RecipeEditSessionState.applyPendingAndClear();
        return false;
    }

    @Override
    protected boolean canvasMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (searchBox != null
                && !searchBox.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            blurControl(searchBox);
        }

        if (super.canvasMouseClicked(
                mouseX,
                mouseY,
                button
        )) {
            return true;
        }

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        if (KineticMouseButtons.isPrimary(button)
                && gridScroll.beginDrag(
                        mouseX,
                        mouseY,
                        gridX + gridW + 4,
                        gridY,
                        4,
                        gridH,
                        20,
                        0
                )) {
            return true;
        }

        if ((!KineticMouseButtons.isPrimary(button) && !KineticMouseButtons.isSecondary(button))
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

        if (KineticMouseButtons.isPrimary(button)) {
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
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (gridScroll.drag(
                mouseY,
                gridY,
                gridH,
                20
        )) {
            return true;
        }

        return super.canvasMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean canvasMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (gridScroll.release(button)) {
            return true;
        }

        return super.canvasMouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        return gridScroll.scroll(delta)
                || super.canvasMouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                );
    }
}
