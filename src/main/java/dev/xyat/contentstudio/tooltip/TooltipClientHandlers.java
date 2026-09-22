package dev.xyat.contentstudio.tooltip;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.text.KineticText;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.contentstudio.tooltip.config.TooltipConfigGui;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.CycleButton;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;

import com.google.gson.reflect.TypeToken;

import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TooltipClientHandlers {
    public static Map<String, List<TooltipManager.TooltipRule>> clientData = new HashMap<>();

    private enum SaveIntent {
        NONE,
        SAVE,
        DELETE,
        EDIT
    }

    private static SaveIntent pendingSaveIntent = SaveIntent.NONE;

    private static void sendSave(SaveIntent intent) {
        pendingSaveIntent = intent;
        TooltipNetwork.saveToServer(TooltipManager.GSON.toJson(clientData));
    }

    private static List<TooltipManager.TooltipRule> copyRules(List<TooltipManager.TooltipRule> source) {
        List<TooltipManager.TooltipRule> copy = new ArrayList<>();
        if (source == null) return copy;
        for (TooltipManager.TooltipRule original : source) {
            TooltipManager.TooltipRule rule = new TooltipManager.TooltipRule();
            rule.mode = original.mode;
            rule.line = original.line;
            rule.keyCond = original.keyCond;
            rule.text = original.text;
            copy.add(rule);
        }
        return copy;
    }

    public static void handleOpenFailure(int reason) {
        KineticOverlays.toast(Component.translatable(
                reason == 0
                        ? "msg.contentstudio.tooltip.tooltipeditor.permission_denied.colored"
                        : "msg.contentstudio.tooltip.tooltipeditor.load_failed.colored"
        ));
    }

    public static void handleSaveResult(boolean success) {
        Screen currentScreen = KineticClientRuntime.currentScreen();
        SaveIntent intent = pendingSaveIntent;

        if (success) {
            KTConfigApi.notifySaved(TooltipConfigGui.PAGE_ID);
            if (intent == SaveIntent.SAVE && currentScreen instanceof TooltipHubScreen hubScreen) {
                hubScreen.commitServerBaseline();
            } else if (intent == SaveIntent.EDIT && currentScreen instanceof TooltipEditScreen editScreen) {
                editScreen.handleSuccessfulSave();
            }
        } else {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.tooltip.tooltipeditor.save_failed.colored"));
        }

        pendingSaveIntent = SaveIntent.NONE;
    }

    private static String selectedItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final String[] CODES = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
    private static final Pattern COLOR_PATTERN = Pattern.compile("§[0-9a-fk-or]");

    public static void openHubScreen(String json) {
        try {
            Map<String, List<TooltipManager.TooltipRule>> loaded = TooltipManager.GSON.fromJson(
                    json,
                    new TypeToken<Map<String, List<TooltipManager.TooltipRule>>>(){}.getType()
            );
            if (!TooltipManager.isValidData(loaded)) {
                KineticOverlays.toast(Component.translatable("msg.contentstudio.tooltip.tooltipeditor.load_failed.colored"));
                return;
            }
            clientData = loaded;
            KineticClientRuntime.openScreen(new TooltipHubScreen());
        } catch (RuntimeException exception) {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.tooltip.tooltipeditor.load_failed.colored"));
        }
    }

    public static class TooltipHubScreen extends KineticScreen {
        private static final int CELL_SIZE = 36;

        private final KineticSearch.Model<String> itemModel;
        private final GridScrollController gridScroll =
                new GridScrollController();

        private int gridX;
        private int gridY;
        private int gridW;
        private int gridH;
        private int columns;
        private int visibleRows;

        private KineticEditBox searchBox;
        private String lastSearch = "";

        public TooltipHubScreen() {
            super(Component.translatable(
                    "gui.contentstudio.tooltip.tooltipeditor.hub.title"
            ));


            itemModel = new KineticSearch.Model<>(
                    clientData.keySet(),
                    (id, query) -> KineticSearch.match(itemSearchData(id), query)
            );

            itemModel.setComparator(
                    String::compareTo
            );

            itemModel.refresh("");
            configureStandaloneDraft(this::captureTooltipSnapshot, this::restoreTooltipSnapshot);
        }

        private record TooltipDataSnapshot(String json) {
        }

        private TooltipDataSnapshot captureTooltipSnapshot() {
            return new TooltipDataSnapshot(TooltipManager.GSON.toJson(clientData));
        }

        private void restoreTooltipSnapshot(TooltipDataSnapshot snapshot) {
            Map<String, List<TooltipManager.TooltipRule>> restored = TooltipManager.GSON.fromJson(
                    snapshot == null ? "{}" : snapshot.json(),
                    new TypeToken<Map<String, List<TooltipManager.TooltipRule>>>() { }.getType()
            );
            clientData = restored == null ? new HashMap<>() : new HashMap<>(restored);
            updateSearch(searchBox == null ? lastSearch : searchBox.getValue());
        }

        private void commitServerBaseline() {
            commitDraft();
        }

        @Override
        protected void buildUi() {
            int padding = 20;
            int topY = 15;

            searchBox = addTextField(
                    padding,
                    topY,
                    150,
                    Component.empty(),
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.hub.search_hint"),
                    null,
                    null
            );

            searchBox.setValue(lastSearch);
            searchBox.setResponder(this::updateSearch);

            int btnW = 80;
            int backBtnX =
                    canvasWidth() - padding - btnW;

            int addBtnX =
                    backBtnX - btnW - 5;

            int saveBtnX =
                    addBtnX - btnW - 5;

            addButton(
                    saveBtnX,
                    topY,
                    btnW,
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.save"),
                    null,
                    this::saveChanges
            );

            addButton(
                    addBtnX,
                    topY,
                    btnW,
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.hub.add"),
                    null,
                    this::openAddItemSelector
            );

            addButton(
                    backBtnX,
                    topY,
                    btnW,
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.hub.close"),
                    null,
                    this::onClose
            );

            gridY = 50;

            int availableWidth =
                    canvasWidth() - padding * 2 - 10;

            columns = Math.max(
                    1,
                    availableWidth / CELL_SIZE
            );

            gridW =
                    columns * CELL_SIZE;

            gridX =
                    (canvasWidth() - gridW) / 2;

            int availableHeight =
                    canvasHeight() - gridY - 15;

            visibleRows =
                    Math.max(
                            1,
                            availableHeight / CELL_SIZE
                    );

            gridH =
                    visibleRows * CELL_SIZE;

            updateSearch(lastSearch);
        }

        private void openAddItemSelector() {
            KineticSelectors.openItemSelector(this, selection -> {
                if (!selection.isItem()) return;
                String cleanId = selectedItemId(selection.stack());
                if (cleanId.isEmpty()) return;
                clientData.putIfAbsent(cleanId, new ArrayList<>());
                KineticClientRuntime.execute(() ->
                        KineticClientRuntime.openScreen(new TooltipEditScreen(this, cleanId))
                );
            });
        }

        private String itemSearchData(String id) {
            Item item =
                    KineticRegistries.items().get(
                            KineticResourceIds.parse(id)
                    );

            String name = item == null
                    ? ""
                    : KineticText.get(item.getDescriptionId()).toLowerCase(Locale.ROOT);

            return id.toLowerCase(Locale.ROOT)
                    + " "
                    + name
                    + " "
                    + KineticSearch.pinyin(name);
        }

        private void saveChanges() {
            for (List<TooltipManager.TooltipRule> rules
                    : clientData.values()) {
                for (TooltipManager.TooltipRule rule : rules) {
                    if (rule.text == null
                            || rule.text.trim().isEmpty()) {
                        KineticOverlays.toast(
                                Component.translatable(
                                        "gui.contentstudio.tooltip.tooltipeditor.err.empty.colored"
                                )
                        );
                        return;
                    }
                }
            }

            sendSave(SaveIntent.SAVE);
        }

        private void updateSearch(String query) {
            lastSearch = query;

            itemModel.setSource(
                    clientData.keySet()
            );

            itemModel.refresh(query);
            gridScroll.reset();
            updateScrollRange();
        }

        private void updateScrollRange() {
            int totalRows =
                    (itemModel.items().size()
                            + columns
                            - 1)
                            / columns;

            gridScroll.update(
                    totalRows,
                    visibleRows
            );
        }

        @Override
        protected void renderCanvasBackground(
                @NotNull GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            GuiTheme.canvasBackground(graphics, canvasWidth(), canvasHeight());

            graphics.drawCenteredString(
                    font,
                    title,
                    canvasWidth() / 2,
                    5,
                    0xFFFFFF
            );

            GuiTheme.panel(graphics, gridX - 2, gridY - 2, gridW + 4, gridH + 4);
        }

        @Override
        protected void renderCanvasForeground(
                @NotNull GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            List<String> items =
                    itemModel.items();

            int startRow = gridScroll.smoothIndexOffset();
            int visualShift = gridScroll.visualShift(CELL_SIZE);
            int startIndex = startRow * columns;

            int endIndex = Math.min(
                    startIndex + (visibleRows + 1) * columns,
                    items.size()
            );

            enableUiScissor(graphics, gridX, gridY, gridX + gridW, gridY + gridH);

            for (int i = startIndex;
                 i < endIndex;
                 i++) {
                String itemId =
                        items.get(i);

                int localIndex =
                        i - startIndex;

                int column =
                        localIndex % columns;

                int row =
                        localIndex / columns;

                int x =
                        gridX
                                + column * CELL_SIZE;

                int y =
                        gridY
                                + row * CELL_SIZE
                                - visualShift;

                boolean hovered =
                        mouseX >= x
                                && mouseX < x + CELL_SIZE
                                && mouseY >= y
                                && mouseY < y + CELL_SIZE;

                Item item =
                        KineticRegistries.items().get(
                                KineticResourceIds.parse(itemId)
                        );

                ItemStack stack =
                        item != null
                                ? new ItemStack(item)
                                : ItemStack.EMPTY;

                GuiTheme.itemSlot(graphics, x, y, CELL_SIZE, 4, hovered);

                graphics.pose().pushPose();
                graphics.pose().translate(
                        x + 6,
                        y + 6,
                        0
                );

                graphics.pose().scale(
                        1.5f,
                        1.5f,
                        1f
                );

                graphics.renderItem(
                        stack,
                        0,
                        0
                );

                graphics.pose().popPose();

                String countText =
                        String.valueOf(
                                clientData.get(itemId).size()
                        );

                graphics.pose().pushPose();
                graphics.pose().translate(
                        x
                                + CELL_SIZE
                                - 2
                                - font.width(countText) * 0.7f,
                        y + CELL_SIZE - 8,
                        200
                );

                graphics.pose().scale(
                        0.7f,
                        0.7f,
                        1f
                );

                graphics.drawString(
                        font,
                        countText,
                        0,
                        0,
                        0xFFFF55,
                        true
                );

                graphics.pose().popPose();

                boolean deleteHovered =
                        GuiTheme.hovering(
                                mouseX,
                                mouseY,
                                x + CELL_SIZE - 10,
                                y + 2,
                                8,
                                8
                        );

                graphics.pose().pushPose();
                graphics.pose().translate(
                        x + CELL_SIZE - 8,
                        y + 2,
                        200
                );

                graphics.pose().scale(
                        0.8f,
                        0.8f,
                        1f
                );

                graphics.drawString(
                        font,
                        "x",
                        0,
                        0,
                        deleteHovered
                                ? 0xFFFF5555
                                : 0xFF888888,
                        false
                );

                graphics.pose().popPose();
            }

            disableUiScissor(graphics);

            gridScroll.render(
                    graphics,
                    mouseX,
                    mouseY,
                    gridX + gridW + 8,
                    gridY,
                    4,
                    gridH,
                    20
            );

        }

        @Override
        protected void renderTooltips(
                GuiGraphics graphics,
                int scaledMouseX,
                int scaledMouseY,
                int mouseX,
                int mouseY
        ) {
            if (!GuiTheme.hovering(
                    scaledMouseX,
                    scaledMouseY,
                    gridX,
                    gridY,
                    gridW,
                    gridH
            )) {
                return;
            }

            int column =
                    (scaledMouseX - gridX)
                            / CELL_SIZE;

            int visualShift = gridScroll.visualShift(CELL_SIZE);
            int row =
                    (scaledMouseY - gridY + visualShift)
                            / CELL_SIZE;

            if (column < 0
                    || column >= columns
                    || row < 0
                    || row >= visibleRows) {
                return;
            }

            int index =
                    gridScroll.smoothIndexOffset()
                            * columns
                            + row * columns
                            + column;

            List<String> items =
                    itemModel.items();

            if (index < 0 || index >= items.size()) {
                return;
            }

            int cellX =
                    gridX + column * CELL_SIZE;

            int cellY =
                    gridY + row * CELL_SIZE - visualShift;

            if (GuiTheme.hovering(
                    scaledMouseX,
                    scaledMouseY,
                    cellX + CELL_SIZE - 10,
                    cellY + 2,
                    8,
                    8
            )) {
                showTooltipLine(Component.translatable(
                        "gui.contentstudio.tooltip.tooltipeditor.hub.delete"
                ));
                return;
            }

            Item item =
                    KineticRegistries.items().get(
                            KineticResourceIds.parse(items.get(index))
                    );

            if (item != null) {
                showItemTooltip(new ItemStack(item));
            }
        }

        @Override
        public boolean canvasMouseClicked(
                double mouseX,
                double mouseY,
                int button
        ) {
            if (searchBox != null
                    && !searchBox.isMouseOver(
                            mouseX,
                            mouseY
                    )) {
                clearControlFocus();
            }

            if (super.canvasMouseClicked(
                    mouseX,
                    mouseY,
                    button
            )) {
                return true;
            }

            if (KineticMouseButtons.isPrimary(button)
                    && gridScroll.beginDrag(
                            mouseX,
                            mouseY,
                            gridX + gridW + 8,
                            gridY,
                            4,
                            gridH,
                            20,
                            0
                    )) {
                return true;
            }

            if (mouseX >= gridX
                    && mouseX < gridX + gridW
                    && mouseY >= gridY
                    && mouseY < gridY + gridH) {
                int column =
                        (int) ((mouseX - gridX) / CELL_SIZE);

                int visualShift = gridScroll.visualShift(CELL_SIZE);
                int row =
                        (int) ((mouseY - gridY + visualShift) / CELL_SIZE);

                int index =
                        gridScroll.smoothIndexOffset()
                                * columns
                                + row * columns
                                + column;

                List<String> items =
                        itemModel.items();

                if (index >= 0 && index < items.size()) {
                    String itemId =
                            items.get(index);

                    int cellX =
                            gridX
                                    + column * CELL_SIZE;

                    int cellY =
                            gridY
                                    + row * CELL_SIZE
                                    - visualShift;

                    if (GuiTheme.hovering(
                            mouseX,
                            mouseY,
                            cellX + CELL_SIZE - 10,
                            cellY + 2,
                            8,
                            8
                    )) {
                        clientData.remove(itemId);

                        updateSearch(
                                searchBox.getValue()
                        );

                        return true;
                    }

                    KineticClientRuntime.openScreen(
                                    new TooltipEditScreen(
                                            this,
                                            itemId
                                    )
                            );

                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean canvasMouseReleased(
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
        public boolean canvasMouseDragged(
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
        public boolean canvasMouseScrolled(
                double mouseX,
                double mouseY,
                double delta
        ) {
            if (gridScroll.scroll(delta)) {
                return true;
            }

            return super.canvasMouseScrolled(
                    mouseX,
                    mouseY,
                    delta
            );
        }
    }

    public static class TooltipEditScreen extends KineticScreen {
        private final TooltipHubScreen parent;
        private String itemId;
        private ItemStack itemStack;
        private List<TooltipManager.TooltipRule> rules;
        private final List<RuleWidget> widgets = new ArrayList<>();

        private double scrollOffset = 0D;
        private int maxScroll = 0;
        private boolean isDraggingScrollbar = false;
        private final KineticScroll.State scrollState = new KineticScroll.State();
        private int draggingIndex = -1, hoverTargetIndex = -1;

        private final int ROW_HEIGHT = 28;
        private int listStartY, listH, visibleRows, startX, listW, infoX;

        public TooltipEditScreen(TooltipHubScreen parent, String itemId) {
            super(Component.empty());
            this.parent = parent;
            setParentScreen(parent);
            this.itemId = itemId;
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(itemId));
            this.itemStack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            this.rules = clientData.computeIfAbsent(itemId, ignored -> new ArrayList<>());
            configureStandaloneDraft(this::captureEditSnapshot, this::restoreEditSnapshot);
        }

        private record TooltipEditSnapshot(String json, String itemId) {
        }

        private TooltipEditSnapshot captureEditSnapshot() {
            return new TooltipEditSnapshot(TooltipManager.GSON.toJson(clientData), itemId);
        }

        private void restoreEditSnapshot(TooltipEditSnapshot snapshot) {
            Map<String, List<TooltipManager.TooltipRule>> restored = TooltipManager.GSON.fromJson(
                    snapshot == null ? "{}" : snapshot.json(),
                    new TypeToken<Map<String, List<TooltipManager.TooltipRule>>>() { }.getType()
            );
            clientData = restored == null ? new HashMap<>() : new HashMap<>(restored);
            itemId = snapshot == null ? itemId : snapshot.itemId();
            rules = clientData.computeIfAbsent(itemId, ignored -> new ArrayList<>());
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(itemId));
            itemStack = item == null ? ItemStack.EMPTY : new ItemStack(item);
            scrollOffset = Math.min(scrollOffset, Math.max(0D, rules.size() - visibleRows));
        }

        @Override
        protected void buildUi() {
            resetScrollableWidgets();
            widgets.clear();

            startX = 10;
            listW = this.canvasWidth() - 35; // 预留右侧独立轨道给滚动条
            listStartY = 80;
            infoX = 15;

            int btnY = 15;
            int btnW_Save = 85;
            int btnW_Add = 60;
            int btnW_Back = 45;

            int saveBtnX = this.canvasWidth() - 15 - btnW_Save;
            int addBtnX = saveBtnX - btnW_Add - 5;
            int backBtnX = addBtnX - btnW_Back - 5;

            addButton(backBtnX, btnY, btnW_Back,
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.back"), null, () -> {
                        saveInputStates();
                        if (rules.isEmpty()) clientData.remove(itemId);
                        navigateBack();
                    });

            addButton(addBtnX, btnY, btnW_Add,
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.add_rule"), null, () -> {
                        saveInputStates();
                        rules.add(new TooltipManager.TooltipRule());
                        rebuildUi();
                    });

            addButton(saveBtnX, btnY, btnW_Save,
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.save"), null, () -> {
                        saveInputStates();
                        for (TooltipManager.TooltipRule r : rules) {
                            if (r.text == null || r.text.trim().isEmpty()) {
                                KineticOverlays.toast(Component.translatable("gui.contentstudio.tooltip.tooltipeditor.err.empty.colored"));
                                return;
                            }
                        }
                        sendSave(SaveIntent.EDIT);
                    });

            int availableH = this.canvasHeight() - listStartY - 15;
            visibleRows = availableH / ROW_HEIGHT;
            listH = visibleRows * ROW_HEIGHT;
            maxScroll = Math.max(0, rules.size() - visibleRows);

            for (int i = 0; i < rules.size(); i++) {
                RuleWidget w = new RuleWidget(rules.get(i), i, startX, listW);
                w.setY(listStartY + i * ROW_HEIGHT);
                widgets.add(w);
                this.addScrollableWidget(w.modeBtn, startX, listStartY, startX + listW, listStartY + listH, this::scrollPixelOffset);
                this.addScrollableWidget(w.lineBox, startX, listStartY, startX + listW, listStartY + listH, this::scrollPixelOffset);
                this.addScrollableWidget(w.keyBtn, startX, listStartY, startX + listW, listStartY + listH, this::scrollPixelOffset);
                this.addScrollableWidget(w.textBox, startX, listStartY, startX + listW, listStartY + listH, this::scrollPixelOffset);
                this.addScrollableWidget(w.delBtn, startX, listStartY, startX + listW, listStartY + listH, this::scrollPixelOffset);
            }

            int curX = backBtnX - (8 * 16) - 15;
            for (int i = 0; i < COLORS.length; i++) {
                final String c = "§" + CODES[i];
                int col = i % 8;
                int row = i / 8;
                addColorSwatchButton(
                        curX + col * 16,
                        btnY - 2 + row * 16,
                        COLORS[i],
                        Component.translatable("gui.contentstudio.tooltip.tooltipeditor.color.insert", c),
                        () -> insertCode(c)
                );
            }
            addButton(
                    curX + 8 * 16,
                    btnY + 4,
                    15,
                    Component.literal("R"),
                    Component.translatable("gui.contentstudio.tooltip.tooltipeditor.color.reset"),
                    () -> insertCode("§r")
            );

            updateWidgetPositions();
        }

        private void insertCode(String c) {
            if (focusedControl() instanceof KineticEditBox textBox) {
                int pos = textBox.getCursorPosition();
                String next = textBox.getValue().substring(0, pos) + c + textBox.getValue().substring(pos);
                textBox.setValue(next);
                textBox.setCursorPosition(pos + c.length());
            }
        }

        private void saveInputStates() {
            for (RuleWidget w : widgets) w.saveToRule();
        }

        private double scrollPixelOffset() {
            return scrollState.follow(
                    scrollOffset,
                    maxScroll,
                    isDraggingScrollbar
            ) * ROW_HEIGHT;
        }

        private void updateWidgetPositions() {
            for (RuleWidget widget : widgets) {
                widget.updateLineBoxVisibility();
            }
        }

        @Override
        protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
            GuiTheme.canvasBackground(g, this.canvasWidth(), this.canvasHeight());
            GuiTheme.panel(g, startX - 2, listStartY - 2, listW + 4, listH + 4);
            g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.drag_hint"), startX, listStartY - 30, 0xFFFFFF);
        }

        @Override
        protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
            updateWidgetPositions();
            boolean hoverIcon = GuiTheme.hovering(mx, my, infoX, 10, 24, 24);
            GuiTheme.itemSlot(g, infoX, 10, 24, 4, hoverIcon);

            g.pose().pushPose();
            g.pose().translate(infoX, 10, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(itemStack, 0, 0);
            g.pose().popPose();

            g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.title", itemStack.getHoverName()), infoX + 30, 12, 0xFFFFFF);
            g.drawString(font, itemId, infoX + 30, 24, 0xAAAAAA);

            int headerY = listStartY - 15;
            g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.header.mode"), startX + 2, headerY, 0xFFFF55);
            g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.header.line_short"), startX + 35, headerY, 0xFFFF55);
            g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.header.key"), startX + 70, headerY, 0xFFFF55);
            g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.header.text_content"), startX + 127, headerY, 0xFFFF55);

            if (maxScroll > 0) {
                int barX = startX + listW + 8;
                int thumbH = KineticScroll.stateThumbHeight(listH, visibleRows, rules.size(), 20);
                KineticScroll.renderScrollbarState(
                        g, mx, my, barX, listStartY, 4, listH, thumbH, maxScroll,
                        scrollState.follow(scrollOffset, maxScroll, isDraggingScrollbar),
                        isDraggingScrollbar
                );
            }

            if (draggingIndex != -1) {
                hoverTargetIndex = -1;
                if (my >= listStartY && my <= listStartY + listH) {
                    double smoothScroll = scrollState.follow(
                            scrollOffset, maxScroll, isDraggingScrollbar
                    );
                    int start = (int) Math.floor(smoothScroll + 1.0E-6D);
                    int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
                    int hoverRow = (my - listStartY + shift) / ROW_HEIGHT;
                    int actualIndex = hoverRow + start;
                    int rowTop = listStartY + hoverRow * ROW_HEIGHT - shift;
                    hoverTargetIndex = my < rowTop + ROW_HEIGHT / 2
                            ? actualIndex
                            : actualIndex + 1;
                }

                if (hoverTargetIndex == -1) {
                    if (my > listStartY + listH) hoverTargetIndex = rules.size();
                    else if (my < listStartY) hoverTargetIndex = 0;
                }

                if (hoverTargetIndex != -1 && hoverTargetIndex <= rules.size()) {
                    double smoothScroll = scrollState.follow(
                            scrollOffset, maxScroll, isDraggingScrollbar
                    );
                    int start = (int) Math.floor(smoothScroll + 1.0E-6D);
                    int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
                    int lineY = listStartY + (hoverTargetIndex - start) * ROW_HEIGHT - shift;
                    if (lineY >= listStartY - ROW_HEIGHT && lineY <= listStartY + listH) {
                        GuiTheme.indicatorFill(
                                g, startX, lineY - 1, listW, 2, GuiTheme.Indicator.SUCCESS
                        );
                    }
                }

                int ghostY = my - ROW_HEIGHT / 2;
                g.pose().pushPose();
                g.pose().translate(0, 0, 500);
                GuiTheme.surface(
                        g, startX - 1, ghostY - 1, listW + 2, ROW_HEIGHT, GuiTheme.Surface.PANEL
                );
                GuiTheme.surface(
                        g, startX, ghostY, listW, ROW_HEIGHT - 2, GuiTheme.Surface.PANEL_ALT
                );
                GuiTheme.indicatorOutline(
                        g, startX, ghostY, listW, ROW_HEIGHT - 2, GuiTheme.Indicator.SUCCESS
                );

                String displayTxt = rules.get(draggingIndex).text;
                if (font.width(displayTxt) > listW - 140) {
                    displayTxt = font.plainSubstrByWidth(displayTxt, listW - 150) + "...";
                }

                g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.moving_prefix"), startX + 10, ghostY + 4, 0xAAAAAA);
                g.drawString(font, Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.moving_item", displayTxt), startX + 10, ghostY + 13, 0xFFFFFF);
                g.pose().popPose();
            }
        }

        @Override
        protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
            if (GuiTheme.hovering(smx, smy, infoX, 10, 24, 24)) {
                showTooltipLine(Component.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.swap_item"));
            }
        }

        @Override
        public boolean canvasMouseClicked(double mx, double my, int btn) {
            if (KineticClientRuntime.controlModifierDown() && KineticMouseButtons.isPrimary(btn)) {
                if (mx >= startX && mx <= startX + listW && my >= listStartY && my <= listStartY + listH) {
                    double smoothScroll = scrollState.follow(
                            scrollOffset, maxScroll, isDraggingScrollbar
                    );
                    int start = (int) Math.floor(smoothScroll + 1.0E-6D);
                    int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
                    int clickedRow = start
                            + (int) Math.floor((my - listStartY + shift) / ROW_HEIGHT);
                    if (clickedRow >= 0 && clickedRow < rules.size()) {
                        saveInputStates();
                        draggingIndex = clickedRow;
                        return true;
                    }
                }
            }
            if (mx >= infoX && mx <= infoX + 24 && my >= 10 && my <= 34) {
                KineticSelectors.openItemSelector(this, selection -> {
                    if (!selection.isItem()) return;
                    String cleanId = selectedItemId(selection.stack());
                    if (cleanId.isEmpty()) return;
                    if (!cleanId.equals(this.itemId)) {
                        saveInputStates();
                        List<TooltipManager.TooltipRule> currentRules = clientData.remove(this.itemId);
                        clientData.put(cleanId, currentRules == null ? new ArrayList<>() : currentRules);
                        this.itemId = cleanId;
                        Item item = KineticRegistries.items().get(KineticResourceIds.parse(cleanId));
                        this.itemStack = item != null ? selection.stack().copy() : ItemStack.EMPTY;
                    }
                });
                return true;
            }
            if (maxScroll > 0 && mx >= startX + listW + 8 && mx <= startX + listW + 12 && my >= listStartY && my <= listStartY + listH) {
                isDraggingScrollbar = true;
                return true;
            }
            return super.canvasMouseClicked(mx, my, btn);
        }

        @Override
        public boolean canvasMouseReleased(double mx, double my, int btn) {
            if (draggingIndex != -1 && KineticMouseButtons.isPrimary(btn)) {
                if (hoverTargetIndex != -1 && hoverTargetIndex != draggingIndex && hoverTargetIndex != draggingIndex + 1) {
                    TooltipManager.TooltipRule temp = rules.remove(draggingIndex);
                    rules.add(hoverTargetIndex > draggingIndex ? hoverTargetIndex - 1 : hoverTargetIndex, temp);
                    rebuildUi();
                }
                draggingIndex = -1;
                hoverTargetIndex = -1;
                return true;
            }
            isDraggingScrollbar = false;
            return super.canvasMouseReleased(mx, my, btn);
        }

        @Override
        public boolean canvasMouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (draggingIndex != -1) {
                return true;
            }
            if (isDraggingScrollbar) {
                int thumbH = KineticScroll.stateThumbHeight(listH, visibleRows, rules.size(), 20);
                scrollOffset = KineticScroll.stateOffsetFromPointerPrecise(my, listStartY, listH, thumbH, maxScroll);
                scrollState.snap(scrollOffset, maxScroll);
                updateWidgetPositions();
                return true;
            }
            return super.canvasMouseDragged(mx, my, btn, dx, dy);
        }

        @Override
        public boolean canvasMouseScrolled(double mx, double my, double delta) {
            if (maxScroll > 0) {
                scrollOffset = scrollState.wheel(
                        scrollOffset,
                        delta,
                        1.0D,
                        maxScroll
                );
                updateWidgetPositions();
                return true;
            }
            return super.canvasMouseScrolled(mx, my, delta);
        }

        private void handleSuccessfulSave() {
            commitDraft();
            parent.commitServerBaseline();
            returnToParentAfterSave();
        }

        private void returnToParentAfterSave() {
            navigateBack();
        }

        private Style getStyleAtPos(String text, int index) {
            if (index <= 0 || text.isEmpty()) return Style.EMPTY;
            String sub = text.substring(0, Math.min(index, text.length()));
            Matcher m = COLOR_PATTERN.matcher(sub);
            Style style = Style.EMPTY;
            while (m.find()) {
                String code = m.group().toLowerCase(Locale.ROOT);
                if (code.matches("§[0-9a-f]")) style = Style.EMPTY.withColor(COLORS["0123456789abcdef".indexOf(code.charAt(1))]);
                else if (code.equals("§l")) style = style.withBold(true);
                else if (code.equals("§o")) style = style.withItalic(true);
                else if (code.equals("§n")) style = style.withUnderlined(true);
                else if (code.equals("§m")) style = style.withStrikethrough(true);
                else if (code.equals("§r")) style = Style.EMPTY;
            }
            return style;
        }

        private class RuleWidget {
            TooltipManager.TooltipRule rule;
            CycleButton modeBtn, keyBtn;
            StateButton delBtn;
            KineticEditBox lineBox, textBox;

            RuleWidget(TooltipManager.TooltipRule rule, int index, int startX, int listWidth) {
                this.rule = rule;
                modeBtn = KineticWidgets.createCycleButton(
                        startX, 0, 32, rule.mode,
                        List.of(
                                Component.translatable("gui.contentstudio.tooltip.tooltipeditor.mode.overwrite"),
                                Component.translatable("gui.contentstudio.tooltip.tooltipeditor.mode.append")
                        ),
                        Component.translatable("gui.contentstudio.tooltip.tooltipeditor.tooltip.mode"),
                        null,
                        value -> {
                            rule.mode = value;
                            updateLineBoxVisibility();
                        }
                );

                lineBox = KineticWidgets.createTextField(
                        font, startX + 34, 0, 24, Component.empty(), null, null, null
                );
                lineBox.setValue(String.valueOf(rule.line));
                lineBox.setMaxLength(2);
                lineBox.setFilter(value -> value.matches("\\d*"));
                lineBox.setResponder(value -> {
                    if (rule.mode == 0 && !value.isEmpty()) {
                        try { rule.line = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
                    }
                });

                keyBtn = KineticWidgets.createCycleButton(
                        startX + 60, 0, 65, rule.keyCond,
                        List.of(
                                Component.translatable("gui.contentstudio.tooltip.tooltipeditor.key.none"),
                                Component.translatable("gui.contentstudio.tooltip.tooltipeditor.key.shift"),
                                Component.translatable("gui.contentstudio.tooltip.tooltipeditor.key.alt"),
                                Component.translatable("gui.contentstudio.tooltip.tooltipeditor.key.shift_alt")
                        ),
                        Component.translatable("gui.contentstudio.tooltip.tooltipeditor.tooltip.key"),
                        null,
                        value -> rule.keyCond = value
                );

                int delBtnW = 20;
                int delBtnX = startX + listWidth - delBtnW - 2;

                textBox = KineticWidgets.createTextField(
                        font, startX + 127, 0, delBtnX - (startX + 127) - 4, Component.empty(), null, null, null
                );
                textBox.setMaxLength(256);
                textBox.setValue(rule.text);
                textBox.setResponder(value -> rule.text = value);
                textBox.setFormatter((string, idx) -> Component.literal(string).setStyle(getStyleAtPos(textBox.getValue(), idx)).getVisualOrderText());

                delBtn = KineticWidgets.createButton(
                        delBtnX, 0, delBtnW,
                        Component.translatable("gui.contentstudio.tooltip.tooltipeditor.rule.delete.btn"),
                        Component.translatable("gui.contentstudio.tooltip.tooltipeditor.rule.delete"),
                        () -> {
                            saveInputStates();
                            rules.remove(index);
                            TooltipEditScreen.this.rebuildUi();
                        }
                );
            }

            private void updateLineBoxVisibility() {
                boolean visible = modeBtn.isVisible() && rule.mode == 0;
                lineBox.setVisible(visible);
                lineBox.setEnabled(visible);
            }

            void setVisible(boolean visible) {
                modeBtn.setVisible(visible);
                modeBtn.setEnabled(visible);
                keyBtn.setVisible(visible);
                keyBtn.setEnabled(visible);
                textBox.setVisible(visible);
                textBox.setEnabled(visible);
                delBtn.setVisible(visible);
                delBtn.setEnabled(visible);
                updateLineBoxVisibility();
            }


            void saveToRule() {
                if (rule.mode == 0 && !lineBox.getValue().isEmpty()) {
                    try {
                        rule.line = Integer.parseInt(lineBox.getValue());
                    } catch (Exception e) {
                        lineBox.setValue(String.valueOf(rule.line));
                    }
                }
                rule.text = textBox.getValue();
            }

            void setY(int y) {
                modeBtn.setY(y);
                lineBox.setY(y);
                keyBtn.setY(y);
                textBox.setY(y);
                delBtn.setY(y);
            }
        }
    }

}
