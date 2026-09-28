package dev.xyat.contentstudio.tooltip;

import dev.xyat.contentstudio.client.gui.FractionalScrollJump;

import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollAnimator;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;
import dev.xyat.kineticcore.api.text.KineticI18n;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.contentstudio.tooltip.config.TooltipConfigGui;

import com.google.gson.reflect.TypeToken;

import dev.xyat.kineticcore.api.client.search.KineticSearch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
        KineticOverlays.toast(KineticI18n.translatable(
                reason == 0
                        ? "msg.contentstudio.tooltip.tooltipeditor.permission_denied.colored"
                        : "msg.contentstudio.tooltip.tooltipeditor.load_failed.colored"
        ));
    }

    public static void handleSaveResult(boolean success) {
        KineticPage currentScreen = KineticGui.currentPage();
        SaveIntent intent = pendingSaveIntent;

        if (success) {
            KTConfigApi.notifySaved(TooltipConfigGui.PAGE_ID);
            if (intent == SaveIntent.SAVE && currentScreen instanceof TooltipHubPage hubScreen) {
                hubScreen.commitServerBaseline();
            } else if (intent == SaveIntent.EDIT && currentScreen instanceof TooltipEditPage editScreen) {
                editScreen.handleSuccessfulSave();
            }
        } else {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.tooltip.tooltipeditor.save_failed.colored"));
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
            if (!TooltipManager.hasValidStructure(loaded)) {
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.tooltip.tooltipeditor.load_failed.colored"));
                return;
            }
            clientData = loaded;
            // 原代码无父界面打开中心页 / The original opened the hub without a parent screen.
            KineticGui.open(new TooltipHubPage());
        } catch (RuntimeException exception) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.tooltip.tooltipeditor.load_failed.colored"));
        }
    }

    public static class TooltipHubPage extends KineticPage {
        private static final int CELL_SIZE = 36;

        private final KineticSearch.Model<String> itemModel;
        private final KineticScrollController gridScroll =
                new KineticScrollController();

        private int gridX;
        private int gridY;
        private int gridW;
        private int gridH;
        private int columns;
        private int visibleRows;

        private KineticTextField searchBox;
        private String lastSearch = "";
        // 网格无选中概念：中键跳转目标为最近点击的物品（按 ID 跟踪以适应搜索筛选）/ The grid has no selection: the middle-click target is the last clicked item (tracked by id so search filtering keeps it valid).
        private String lastClickedItemId;

        public TooltipHubPage() {
            super(KineticI18n.translatable(
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
            gridScroll.bindSelection(
                    () -> lastClickedItemId == null ? -1 : itemModel.items().indexOf(lastClickedItemId),
                    index -> index / Math.max(1, columns) - visibleRows / 2
            );
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
            updateSearch(searchBox == null ? lastSearch : searchBox.textValue());
        }

        private void commitServerBaseline() {
            commitDraft();
        }

        @Override
        protected void build(KineticUi ui) {
            int padding = 20;
            int topY = 15;

            searchBox = ui().textField(padding, topY, 150).placeholder(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.hub.search_hint")).build();

            searchBox.setTextValue(lastSearch);
            searchBox.onTextChange(this::updateSearch);

            int btnW = 80;
            int backBtnX =
                    width() - padding - btnW;

            int addBtnX =
                    backBtnX - btnW - 5;

            int saveBtnX =
                    addBtnX - btnW - 5;

            ui().button(saveBtnX, topY, btnW).text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.save")).onClick(this::saveChanges).build();

            ui().button(addBtnX, topY, btnW).text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.hub.add")).onClick(this::openAddItemSelector).build();

            ui().button(backBtnX, topY, btnW).text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.hub.close")).onClick(this::close).build();

            gridY = 50;

            int availableWidth =
                    width() - padding * 2 - 10;

            columns = Math.max(
                    1,
                    availableWidth / CELL_SIZE
            );

            gridW =
                    columns * CELL_SIZE;

            gridX =
                    (width() - gridW) / 2;

            int availableHeight =
                    height() - gridY - 15;

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
            KineticSelectors.openItemSelector(selection -> {
                if (!selection.isItem()) return;
                String cleanId = selectedItemId(selection.stack());
                if (cleanId.isEmpty()) return;
                clientData.putIfAbsent(cleanId, new ArrayList<>());
                KineticClientRuntime.execute(() ->
                        openChild(new TooltipEditPage(this, cleanId))
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
                    : KineticI18n.string(item.getDescriptionId()).toLowerCase(Locale.ROOT);

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
                                KineticI18n.translatable(
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
        protected void renderBackground(KineticGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            KineticTheme.canvasBackground(graphics, width(), height());

            graphics.centeredText(title(), width() / 2, 5, 0xFFFFFF, true);

            KineticTheme.panel(graphics, gridX - 2, gridY - 2, gridW + 4, gridH + 4);
        }

        @Override
        protected void renderForeground(KineticGraphics graphics,
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

            graphics.scissor(gridX, gridY, gridX + gridW, gridY + gridH);

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

                KineticTheme.itemSlot(graphics, x, y, CELL_SIZE, 4, hovered);

                graphics.push();
                graphics.translate(
                        x + 6, y + 6);

                graphics.scale(
                        1.5f, 1.5f);

                graphics.item(
                        stack,
                        0,
                        0
                );

                graphics.pop();

                String countText =
                        String.valueOf(
                                clientData.get(itemId).size()
                        );

                graphics.push();
                graphics.translate(
                        x
                                + CELL_SIZE
                                - 2
                                - KineticText.width(countText) * 0.7f, y + CELL_SIZE - 8);

                graphics.scale(
                        0.7f, 0.7f);

                graphics.text(countText, 0, 0, 0xFFFF55, true);

                graphics.pop();

                boolean deleteHovered =
                        KineticTheme.hovering(
                                mouseX,
                                mouseY,
                                x + CELL_SIZE - 10,
                                y + 2,
                                8,
                                8
                        );

                graphics.push();
                graphics.translate(
                        x + CELL_SIZE - 8, y + 2);

                graphics.scale(
                        0.8f, 0.8f);

                graphics.text("x", 0, 0, deleteHovered
                                ? 0xFFFF5555
                                : 0xFF888888, false);

                graphics.pop();
                gridScroll.renderSelectionFlash(graphics, i, x, y, CELL_SIZE, CELL_SIZE);
            }

            graphics.endScissor();

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
                int scaledMouseX,
                int scaledMouseY
        ) {
            if (!KineticTheme.hovering(
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

            if (KineticTheme.hovering(
                    scaledMouseX,
                    scaledMouseY,
                    cellX + CELL_SIZE - 10,
                    cellY + 2,
                    8,
                    8
            )) {
                showTooltip(KineticI18n.translatable(
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
        protected boolean onMouseClickCapture(MouseInput input) {
            // 原逻辑在控件分发前清除搜索框以外的焦点 / Original logic cleared focus (outside the search box) before control dispatch.
            if (searchBox != null
                    && !searchBox.contains(
                            input.x(),
                            input.y()
                    )) {
                clearFocus();
            }
            return false;
        }

        @Override
        protected boolean onMouseClick(MouseInput input) {
            double mouseX = input.x();
            double mouseY = input.y();
            if (gridScroll.beginDrag(
                            mouseX,
                            mouseY,
                            input.button(),
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
                    lastClickedItemId = itemId;

                    int cellX =
                            gridX
                                    + column * CELL_SIZE;

                    int cellY =
                            gridY
                                    + row * CELL_SIZE
                                    - visualShift;

                    if (KineticTheme.hovering(
                            mouseX,
                            mouseY,
                            cellX + CELL_SIZE - 10,
                            cellY + 2,
                            8,
                            8
                    )) {
                        clientData.remove(itemId);

                        updateSearch(
                                searchBox.textValue()
                        );

                        return true;
                    }

                    openChild(new TooltipEditPage(this, itemId));

                    return true;
                }
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
        protected boolean onMouseDrag(MouseDragInput input) {
            if (gridScroll.drag(
                    input.y(),
                    gridY,
                    gridH,
                    20
            )) {
                return true;
            }

            return false;
        }

        @Override
        protected boolean onMouseScroll(ScrollInput input) {
            if (gridScroll.scroll(input.deltaY())) {
                return true;
            }

            return false;
        }
    }

    public static class TooltipEditPage extends KineticPage {
        private final TooltipHubPage parent;
        private String itemId;
        private ItemStack itemStack;
        private List<TooltipManager.TooltipRule> rules;
        private final List<RuleWidget> widgets = new ArrayList<>();

        private double scrollOffset = 0D;
        private int maxScroll = 0;
        private boolean isDraggingScrollbar = false;
        private final KineticScrollAnimator scrollState = new KineticScrollAnimator();
        private int draggingIndex = -1, hoverTargetIndex = -1;
        // 规则列表无选中概念：中键跳转目标为最近点击的规则行，-1 表示无 / The rule list has no selection: the middle-click target is the last clicked rule row; -1 means none.
        private int lastClickedRuleIndex = -1;
        private final FractionalScrollJump ruleJump = new FractionalScrollJump(
                () -> lastClickedRuleIndex < this.rules.size() ? lastClickedRuleIndex : -1,
                index -> index - this.visibleRows / 2);

        private final int ROW_HEIGHT = 28;
        private int listStartY, listH, visibleRows, startX, listW, infoX;

        public TooltipEditPage(TooltipHubPage parent, String itemId) {
            super(Component.empty());
            this.parent = parent;
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
            lastClickedRuleIndex = -1;
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(itemId));
            itemStack = item == null ? ItemStack.EMPTY : new ItemStack(item);
            scrollOffset = Math.min(scrollOffset, Math.max(0D, rules.size() - visibleRows));
        }

        @Override
        protected void build(KineticUi ui) {
            widgets.clear();

            startX = 10;
            listW = this.width() - 35; // 预留右侧独立轨道给滚动条
            listStartY = 80;
            infoX = 15;

            int btnY = 15;
            int btnW_Save = 85;
            int btnW_Add = 60;
            int btnW_Back = 45;

            int saveBtnX = this.width() - 15 - btnW_Save;
            int addBtnX = saveBtnX - btnW_Add - 5;
            int backBtnX = addBtnX - btnW_Back - 5;

            ui().button(backBtnX, btnY, btnW_Back).text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.back")).onClick(() -> {
                        saveInputStates();
                        if (rules.isEmpty()) clientData.remove(itemId);
                        navigateBack();
                    }).build();

            ui().button(addBtnX, btnY, btnW_Add).text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.add_rule")).onClick(() -> {
                        saveInputStates();
                        rules.add(new TooltipManager.TooltipRule());
                        rebuild();
                    }).build();

            ui().button(saveBtnX, btnY, btnW_Save).text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.save")).onClick(() -> {
                        saveInputStates();
                        for (TooltipManager.TooltipRule r : rules) {
                            if (r.text == null || r.text.trim().isEmpty()) {
                                KineticOverlays.toast(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.err.empty.colored"));
                                return;
                            }
                        }
                        sendSave(SaveIntent.EDIT);
                    }).build();

            int availableH = this.height() - listStartY - 15;
            visibleRows = availableH / ROW_HEIGHT;
            listH = visibleRows * ROW_HEIGHT;
            maxScroll = Math.max(0, rules.size() - visibleRows);

            // 规则行放入按像素偏移滚动的视口（原 addScrollableWidget）/ Rule rows live in a pixel-offset scroll viewport (formerly addScrollableWidget).
            KineticUi rowUi = ui.scrollViewport(startX, listStartY, startX + listW, listStartY + listH, this::scrollPixelOffset);
            for (int i = 0; i < rules.size(); i++) {
                RuleWidget w = new RuleWidget(rowUi, rules.get(i), i, startX, listStartY + i * ROW_HEIGHT, listW);
                widgets.add(w);
            }

            int curX = backBtnX - (8 * 16) - 15;
            for (int i = 0; i < COLORS.length; i++) {
                final String c = "§" + CODES[i];
                int col = i % 8;
                int row = i / 8;
                ui.colorSwatch(curX + col * 16, btnY - 2 + row * 16, COLORS[i])
                        .tooltip(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.color.insert", c))
                        .onClick(() -> insertCode(c))
                        .build();
            }
            ui().button(curX + 8 * 16, btnY + 4, 15).text(Component.literal("R")).tooltip(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.color.reset")).onClick(() -> insertCode("§r")).build();

            updateWidgetPositions();
        }

        private void insertCode(String c) {
            if (focusedControl() instanceof KineticTextField textBox) {
                int pos = textBox.cursorIndex();
                String next = textBox.textValue().substring(0, pos) + c + textBox.textValue().substring(pos);
                textBox.setTextValue(next);
                textBox.setCursorIndex(pos + c.length());
            }
        }

        private void saveInputStates() {
            for (RuleWidget w : widgets) w.saveToRule();
        }

        private double scrollPixelOffset() {
            return scrollState.update(
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
        protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
            // 在本帧计算平滑偏移之前应用中键跳转 / Apply the middle-click jump before this frame computes smooth offsets.
            double jump = ruleJump.takeJump();
            if (!Double.isNaN(jump)) {
                scrollOffset = jump;
                scrollState.snap(scrollOffset, maxScroll);
            }
            KineticTheme.canvasBackground(g, this.width(), this.height());
            KineticTheme.panel(g, startX - 2, listStartY - 2, listW + 4, listH + 4);
            g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.drag_hint"), startX, listStartY - 30, 0xFFFFFF, true);
        }

        @Override
        protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
            updateWidgetPositions();
            boolean hoverIcon = KineticTheme.hovering(mx, my, infoX, 10, 24, 24);
            KineticTheme.itemSlot(g, infoX, 10, 24, 4, hoverIcon);

            g.push();
            g.translate(infoX, 10);
            g.scale(1.5f, 1.5f);
            g.item(itemStack, 0, 0);
            g.pop();

            g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.title", itemStack.getHoverName()), infoX + 30, 12, 0xFFFFFF, true);
            g.text(itemId, infoX + 30, 24, 0xAAAAAA, true);

            int headerY = listStartY - 15;
            g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.header.mode"), startX + 2, headerY, 0xFFFF55, true);
            g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.header.line_short"), startX + 35, headerY, 0xFFFF55, true);
            g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.header.key"), startX + 70, headerY, 0xFFFF55, true);
            g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.header.text_content"), startX + 127, headerY, 0xFFFF55, true);

            // 行由控件组成，闪烁框包住整行控件并裁剪到列表区域 / Rows are made of controls; the flash frames the whole row, clipped to the list.
            if (lastClickedRuleIndex >= 0) {
                int rowShift = (int) Math.round(scrollPixelOffset());
                g.scissor(startX - 1, listStartY, startX + listW + 1, listStartY + listH);
                for (int i = 0; i < rules.size(); i++) {
                    ruleJump.flash(g, i, startX - 1, listStartY + i * ROW_HEIGHT - rowShift - 2, listW + 2, 20);
                }
                g.endScissor();
            }

            if (maxScroll > 0) {
                int barX = startX + listW + 8;
                int thumbH = thumbHeight(listH, visibleRows, rules.size(), 20);
                ruleJump.track(g, mx, my, barX, listStartY, 4, listH, 20,
                        scrollState.update(scrollOffset, maxScroll, isDraggingScrollbar), maxScroll,
                        rules.size(), visibleRows, isDraggingScrollbar);
                renderRawScrollbar(
                        g, mx, my, barX, listStartY, 4, listH, thumbH, maxScroll,
                        scrollState.update(scrollOffset, maxScroll, isDraggingScrollbar),
                        isDraggingScrollbar
                );
            }

            if (draggingIndex != -1) {
                hoverTargetIndex = -1;
                if (my >= listStartY && my <= listStartY + listH) {
                    double smoothScroll = scrollState.update(
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
                    double smoothScroll = scrollState.update(
                            scrollOffset, maxScroll, isDraggingScrollbar
                    );
                    int start = (int) Math.floor(smoothScroll + 1.0E-6D);
                    int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
                    int lineY = listStartY + (hoverTargetIndex - start) * ROW_HEIGHT - shift;
                    if (lineY >= listStartY - ROW_HEIGHT && lineY <= listStartY + listH) {
                        KineticTheme.indicatorFill(
                                g, startX, lineY - 1, listW, 2, KineticTheme.Indicator.SUCCESS
                        );
                    }
                }

                int ghostY = my - ROW_HEIGHT / 2;
                g.push();
                g.raise(2);
                KineticTheme.surface(
                        g, startX - 1, ghostY - 1, listW + 2, ROW_HEIGHT, KineticTheme.Surface.PANEL
                );
                KineticTheme.surface(
                        g, startX, ghostY, listW, ROW_HEIGHT - 2, KineticTheme.Surface.PANEL_ALT
                );
                KineticTheme.indicatorOutline(
                        g, startX, ghostY, listW, ROW_HEIGHT - 2, KineticTheme.Indicator.SUCCESS
                );

                String displayTxt = rules.get(draggingIndex).text;
                if (KineticText.width(displayTxt) > listW - 140) {
                    displayTxt = KineticText.trim(displayTxt, listW - 150) + "...";
                }

                g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.moving_prefix"), startX + 10, ghostY + 4, 0xAAAAAA, true);
                g.text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.moving_item", displayTxt), startX + 10, ghostY + 13, 0xFFFFFF, true);
                g.pop();
            }
        }

        @Override
        protected void renderTooltips(int smx, int smy) {
            if (KineticTheme.hovering(smx, smy, infoX, 10, 24, 24)) {
                showTooltip(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.edit.swap_item"));
            }
        }

        @Override
        protected boolean onMouseClickCapture(MouseInput input) {
            // 原逻辑全部在控件分发前执行 / The original logic all ran before control dispatch.
            double mx = input.x();
            double my = input.y();
            // 记录中键跳转目标（不消费点击，行内控件照常处理）/ Record the middle-click target (without consuming; row controls still handle the click).
            if (mx >= startX && mx <= startX + listW && my >= listStartY && my <= listStartY + listH) {
                int clickedRow = (int) Math.floor((my - listStartY + scrollPixelOffset()) / ROW_HEIGHT);
                if (clickedRow >= 0 && clickedRow < rules.size()) lastClickedRuleIndex = clickedRow;
            }
            if (KineticClientRuntime.controlModifierDown() && input.isLeft()) {
                if (mx >= startX && mx <= startX + listW && my >= listStartY && my <= listStartY + listH) {
                    double smoothScroll = scrollState.update(
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
                KineticSelectors.openItemSelector(selection -> {
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
            return false;
        }

        @Override
        protected boolean onMouseRelease(MouseInput input) {
            if (draggingIndex != -1 && input.isLeft()) {
                if (hoverTargetIndex != -1 && hoverTargetIndex != draggingIndex && hoverTargetIndex != draggingIndex + 1) {
                    TooltipManager.TooltipRule temp = rules.remove(draggingIndex);
                    int newIndex = hoverTargetIndex > draggingIndex ? hoverTargetIndex - 1 : hoverTargetIndex;
                    rules.add(newIndex, temp);
                    // 被拖动的行即最近点击的行，跟随到新位置 / The dragged row is the last clicked row; follow it to its new position.
                    lastClickedRuleIndex = newIndex;
                    rebuild();
                }
                draggingIndex = -1;
                hoverTargetIndex = -1;
                return true;
            }
            isDraggingScrollbar = false;
            return false;
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
            double my = input.y();
            if (draggingIndex != -1) {
                return true;
            }
            if (isDraggingScrollbar) {
                int thumbH = thumbHeight(listH, visibleRows, rules.size(), 20);
                scrollOffset = offsetFromPointer(my, listStartY, listH, thumbH, maxScroll);
                scrollState.snap(scrollOffset, maxScroll);
                updateWidgetPositions();
                return true;
            }
            return false;
        }

        @Override
        protected boolean onMouseScroll(ScrollInput input) {
            if (maxScroll > 0) {
                scrollOffset = scrollState.wheel(
                        scrollOffset,
                        input.deltaY(),
                        1.0D,
                        maxScroll
                );
                updateWidgetPositions();
                return true;
            }
            return false;
        }

        // 原 KineticScroll 裸滚动条状态工具的等价实现（核心 v2 未公开）/ Equivalent of the old raw KineticScroll state helpers (not exposed by core v2).
        private static int thumbHeight(int trackHeight, int visibleItems, int totalItems, int minHeight) {
            if (trackHeight <= 0) return 0;
            if (totalItems <= 0) return trackHeight;
            int minimum = Math.min(trackHeight, Math.max(1, minHeight));
            int calculated = (int) ((double) Math.max(0, visibleItems) / totalItems * trackHeight);
            return Math.min(trackHeight, Math.max(minimum, calculated));
        }

        private static double offsetFromPointer(double pointerY, int trackY, int trackHeight, int thumbHeight, int maxOffset) {
            if (!Double.isFinite(pointerY) || trackHeight <= 0 || maxOffset <= 0) return 0D;
            double relativeY = pointerY - trackY - thumbHeight / 2.0D;
            double scrollableHeight = (double) trackHeight - thumbHeight;
            if (scrollableHeight <= 0D) return 0D;
            return Math.max(0D, Math.min(maxOffset, (relativeY / scrollableHeight) * maxOffset));
        }

        private static void renderRawScrollbar(KineticGraphics g, int mouseX, int mouseY, int x, int y, int width, int height,
                                               int thumbHeight, int maxOffset, double offset, boolean dragging) {
            if (maxOffset <= 0 || height <= 0 || width <= 0) return;
            int thumbH = Math.max(1, Math.min(height, thumbHeight));
            double safeOffset = Math.max(0D, Math.min(maxOffset, offset));
            int thumbY = y + (int) Math.round(safeOffset / maxOffset * (height - thumbH));
            int visualWidth = Math.min(4, Math.max(1, width));
            int visualX = x + Math.max(0, width - visualWidth);
            boolean hovered = mouseX >= visualX && mouseX <= visualX + visualWidth
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            KineticTheme.Palette theme = KineticTheme.current();
            g.fill(visualX, y, visualX + visualWidth, y + height, theme.scrollTrack());
            g.fill(visualX, thumbY, visualX + visualWidth, thumbY + thumbH,
                    dragging || hovered ? theme.scrollThumbHover() : theme.scrollThumb());
        }

        private void handleSuccessfulSave() {
            commitDraft();
            parent.commitServerBaseline();
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
            KineticCycleButton modeBtn, keyBtn;
            KineticButton delBtn;
            KineticTextField lineBox, textBox;

            RuleWidget(KineticUi rowUi, TooltipManager.TooltipRule rule, int index, int startX, int y, int listWidth) {
                this.rule = rule;
                modeBtn = rowUi.cycleButton(
                                startX, y, 32,
                                List.of(
                                        KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.mode.overwrite"),
                                        KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.mode.append")
                                )
                        )
                        .index(rule.mode)
                        .tooltip(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.tooltip.mode"))
                        .onChange(value -> {
                            rule.mode = value;
                            updateLineBoxVisibility();
                        })
                        .build();

                lineBox = rowUi.textField(startX + 34, y, 24).build();
                lineBox.setTextValue(String.valueOf(rule.line));
                lineBox.limitTextLength(2);
                lineBox.filterText(value -> value.matches("\\d*"));
                lineBox.onTextChange(value -> {
                    if (rule.mode == 0 && !value.isEmpty()) {
                        try { rule.line = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
                    }
                });

                keyBtn = rowUi.cycleButton(
                                startX + 60, y, 65,
                                List.of(
                                        KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.key.none"),
                                        KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.key.shift"),
                                        KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.key.alt"),
                                        KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.key.shift_alt")
                                )
                        )
                        .index(rule.keyCond)
                        .tooltip(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.tooltip.key"))
                        .onChange(value -> rule.keyCond = value)
                        .build();

                int delBtnW = 20;
                int delBtnX = startX + listWidth - delBtnW - 2;

                textBox = rowUi.textField(startX + 127, y, delBtnX - (startX + 127) - 4).build();
                textBox.limitTextLength(256);
                textBox.setTextValue(rule.text);
                textBox.onTextChange(value -> rule.text = value);
                textBox.formatText((string, idx) -> Component.literal(string).setStyle(getStyleAtPos(textBox.textValue(), idx)).getVisualOrderText());

                delBtn = rowUi.button(delBtnX, y, delBtnW)
                        .text(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.rule.delete.btn"))
                        .tooltip(KineticI18n.translatable("gui.contentstudio.tooltip.tooltipeditor.rule.delete"))
                        .onClick(() -> {
                            saveInputStates();
                            rules.remove(index);
                            if (lastClickedRuleIndex == index) lastClickedRuleIndex = -1;
                            else if (lastClickedRuleIndex > index) lastClickedRuleIndex--;
                            TooltipEditPage.this.rebuild();
                        })
                        .build();
            }

            private void updateLineBoxVisibility() {
                boolean visible = modeBtn.controlVisible() && rule.mode == 0;
                lineBox.setControlVisible(visible);
                lineBox.setEnabled(visible);
            }

            void setVisible(boolean visible) {
                modeBtn.setControlVisible(visible);
                modeBtn.setEnabled(visible);
                keyBtn.setControlVisible(visible);
                keyBtn.setEnabled(visible);
                textBox.setControlVisible(visible);
                textBox.setEnabled(visible);
                delBtn.setControlVisible(visible);
                delBtn.setEnabled(visible);
                updateLineBoxVisibility();
            }


            void saveToRule() {
                if (rule.mode == 0 && !lineBox.textValue().isEmpty()) {
                    try {
                        rule.line = Integer.parseInt(lineBox.textValue());
                    } catch (Exception e) {
                        lineBox.setTextValue(String.valueOf(rule.line));
                    }
                }
                rule.text = textBox.textValue();
            }

            void setY(int y) {
                modeBtn.moveControlY(y);
                lineBox.moveControlY(y);
                keyBtn.moveControlY(y);
                textBox.moveControlY(y);
                delBtn.moveControlY(y);
            }
        }
    }

}
