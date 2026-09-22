package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;

import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChestLootEditorScreen extends AbstractLootEditorScreen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_HEIGHT = ROW_HEIGHT * 13;
    private static final int VISIBLE_ROWS = 13;
    private static final int LIST_Y = SEARCH_Y + 22;

    private static final int SPECIAL_X = RIGHT_X + 10;
    private static final int SPECIAL_Y = RIGHT_Y + 50;
    private static final int SPECIAL_W = RIGHT_W - 20;
    private static final int SPECIAL_H = RIGHT_Y + RIGHT_H - SPECIAL_Y - 10;

    private static final int REMOVE_CELL = 18;
    private static final int REMOVE_GAP = 1;
    private static final int REMOVE_PITCH = REMOVE_CELL + REMOVE_GAP;
    private static final int REMOVE_PADDING = 2;
    private static final int REMOVE_COLS = 20;
    private static final int REMOVE_ROWS = 14;
    private static final int REMOVE_VISIBLE_ITEMS = REMOVE_COLS * REMOVE_ROWS;

    private static final int EXCLUDE_ROW_H = 24;
    private static final int EXCLUDE_VISIBLE_ROWS = SPECIAL_H / EXCLUDE_ROW_H;
    private static final int GLOBAL_REMOVE_SAVE_X = RIGHT_X + RIGHT_W - 50;
    private static final int GLOBAL_REMOVE_NBT_X = GLOBAL_REMOVE_SAVE_X - 76;
    private static final int GLOBAL_REMOVE_MODE_X = GLOBAL_REMOVE_NBT_X - 106;
    private static final int GLOBAL_REMOVE_ADD_X = GLOBAL_REMOVE_MODE_X - 84;
    private static final int GLOBAL_REMOVE_BUTTON_Y = RIGHT_Y + 10;
    private static final int GLOBAL_REMOVE_MODE_W = 100;


    private final List<GlobalRemoveRule> globalRemoveRules = new ArrayList<>();
    private int globalRemoveSelectedIndex = -1;
    private double globalRemoveScrollRow;
    private int globalRemoveMaxScrollRow;
    private boolean draggingGlobalRemoveScroll;
    private final KineticScroll.State globalRemoveScrollState = new KineticScroll.State();
    private boolean globalRemoveDirty;
    private StateButton globalRemoveAddButton;
    private StateButton globalRemoveModeButton;
    private StateButton globalRemoveNbtButton;
    private StateButton globalRemoveSaveButton;

    private final List<String> globalExcludedLootTableIds = new ArrayList<>();
    private int globalExcludeSelectedIndex = -1;
    private double globalExcludeScroll;
    private int globalExcludeMaxScroll;
    private boolean draggingGlobalExcludeScroll;
    private final KineticScroll.State globalExcludeScrollState = new KineticScroll.State();
    private boolean globalExcludeDirty;
    private StateButton globalExcludeSaveButton;

    private record ChestSpecialSnapshot(
            List<GlobalRemoveRule> removeRules,
            int removeSelectedIndex,
            boolean removeDirty,
            List<String> excludedLootTableIds,
            int excludeSelectedIndex,
            boolean excludeDirty
    ) {
    }

    @Override
    protected Object captureSpecialEditState() {
        return new ChestSpecialSnapshot(
                List.copyOf(globalRemoveRules),
                globalRemoveSelectedIndex,
                globalRemoveDirty,
                List.copyOf(globalExcludedLootTableIds),
                globalExcludeSelectedIndex,
                globalExcludeDirty
        );
    }

    @Override
    protected void restoreSpecialEditState(Object state) {
        if (!(state instanceof ChestSpecialSnapshot snapshot)) return;
        globalRemoveRules.clear();
        globalRemoveRules.addAll(snapshot.removeRules());
        globalRemoveSelectedIndex = snapshot.removeSelectedIndex();
        globalRemoveDirty = snapshot.removeDirty();
        globalExcludedLootTableIds.clear();
        globalExcludedLootTableIds.addAll(snapshot.excludedLootTableIds());
        globalExcludeSelectedIndex = snapshot.excludeSelectedIndex();
        globalExcludeDirty = snapshot.excludeDirty();
        updateGlobalRemoveScroll();
        updateGlobalExcludeScroll();
    }

    public ChestLootEditorScreen(List<LootEntryInfo> entries) {
        this(entries, null);
    }

    public ChestLootEditorScreen(List<LootEntryInfo> entries, Screen parentScreen) {
        super(LootEntryInfo.MODE_CHEST, entries, "gui.contentstudio.loot.loots.chest.title", parentScreen);
        enableLootDraft();
    }

    @Override
    protected String lootTableType() {
        return "minecraft:chest";
    }

    @Override
    protected boolean showMissingPlayerKillHint() {
        return false;
    }

    @Override
    protected int targetVisibleRows() {
        return VISIBLE_ROWS;
    }

    @Override
    protected int targetTotalRows() {
        return displayEntries.size();
    }

    @Override
    protected int targetStartIndex() {
        return (int) Math.floor(smoothTargetScroll() + 1.0E-6D);
    }

    @Override
    protected int targetVisibleEntryCount() {
        return VISIBLE_ROWS;
    }

    @Override
    protected int targetAreaHeight() {
        return LIST_HEIGHT;
    }

    @Override
    protected int targetAreaY() {
        return LIST_Y;
    }

    @Override
    protected int entryPriority(LootEntryInfo entry) {
        return specialRank(entry);
    }

    @Override
    protected Comparator<LootEntryInfo> entryComparator() {
        return Comparator.comparing(this::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LootEntryInfo::lootTableId);
    }

    private int specialRank(LootEntryInfo entry) {
        if (entry.isGlobalChestAppend()) {
            return 0;
        }
        if (entry.isGlobalChestRemove()) {
            return 1;
        }
        if (entry.isGlobalChestExclude()) {
            return 2;
        }
        return 3;
    }

    @Override
    protected void initSpecialWidgets() {
        closeContextMenu();
        globalRemoveAddButton = addButton(
                GLOBAL_REMOVE_ADD_X, GLOBAL_REMOVE_BUTTON_Y, 78,
                Component.translatable("gui.contentstudio.loot.loots.global_remove.add"),
                Component.translatable("gui.contentstudio.loot.loots.global_remove.tip.add"),
                () -> {
                    closeContextMenu();
                    openGlobalRemoveItemPicker();
                }
        );
        globalRemoveModeButton = addButton(
                GLOBAL_REMOVE_MODE_X, GLOBAL_REMOVE_BUTTON_Y, GLOBAL_REMOVE_MODE_W,
                Component.translatable("gui.contentstudio.loot.loots.global_remove.match_mode.none"),
                Component.translatable("gui.contentstudio.loot.loots.global_remove.tip.match_mode"),
                this::toggleGlobalRemoveModeMenu
        );
        globalRemoveNbtButton = addButton(
                GLOBAL_REMOVE_NBT_X, GLOBAL_REMOVE_BUTTON_Y, 70,
                Component.translatable("gui.contentstudio.loot.loots.global_remove.edit_nbt"),
                Component.translatable("gui.contentstudio.loot.loots.global_remove.tip.edit_nbt"),
                () -> {
                    closeContextMenu();
                    openGlobalRemoveNbtEditor();
                }
        );
        globalRemoveSaveButton = addButton(
                GLOBAL_REMOVE_SAVE_X, GLOBAL_REMOVE_BUTTON_Y, 44,
                Component.translatable("gui.contentstudio.loot.loots.global_remove.save"),
                Component.translatable("gui.contentstudio.loot.loots.global_remove.tip.save"),
                () -> {
                    closeContextMenu();
                    saveGlobalRemove();
                }
        );

        globalExcludeSaveButton = addButton(
                RIGHT_X + RIGHT_W - 97, RIGHT_Y + 10, 44,
                Component.translatable("gui.contentstudio.loot.loots.global_exclude.save"),
                Component.translatable("gui.contentstudio.loot.loots.global_exclude.tip.save"),
                this::saveGlobalExclude
        );
        updateSpecialButtons();
    }

    @Override
    protected boolean isSpecialPanelActive() {
        return selectedEntry != null && (selectedEntry.isGlobalChestRemove() || selectedEntry.isGlobalChestExclude());
    }

    private boolean isGlobalRemovePanel() {
        return selectedEntry != null && selectedEntry.isGlobalChestRemove();
    }

    private boolean isGlobalExcludePanel() {
        return selectedEntry != null && selectedEntry.isGlobalChestExclude();
    }

    @Override
    protected void requestSelectedDetail() {
        if (isGlobalRemovePanel()) {
            LootNetwork.sendToServer(new LootNetwork.RequestGlobalRemovePacket());
            return;
        }
        if (isGlobalExcludePanel()) {
            LootNetwork.sendToServer(new LootNetwork.RequestGlobalExcludePacket());
            return;
        }
        super.requestSelectedDetail();
    }

    @Override
    protected boolean handleSpecialTargetClick(LootEntryInfo entry) {
        if (!isGlobalExcludePanel() || entry == null || entry.isGlobalChestEntry()) {
            return false;
        }
        String id = entry.lootTableId();
        int existing = globalExcludedLootTableIds.indexOf(id);
        if (existing >= 0) {
            globalExcludeSelectedIndex = existing;
            ensureGlobalExcludeSelectedVisible();
            updateButtons();
            return true;
        }
        globalExcludedLootTableIds.add(id);
        globalExcludedLootTableIds.sort(String.CASE_INSENSITIVE_ORDER);
        globalExcludeSelectedIndex = globalExcludedLootTableIds.indexOf(id);
        globalExcludeDirty = true;
        updateGlobalExcludeScroll();
        ensureGlobalExcludeSelectedVisible();
        updateButtons();
        return true;
    }

    public void applyGlobalRemoveDetail(List<GlobalRemoveRule> rules) {
        if (!isGlobalRemovePanel()) {
            return;
        }
        globalRemoveRules.clear();
        if (rules != null) {
            globalRemoveRules.addAll(rules);
        }
        globalRemoveSelectedIndex = -1;
        globalRemoveDirty = false;
        closeContextMenu();
        updateGlobalRemoveScroll();
        updateButtons();
    }

    public void applyGlobalRemoveSaveResult(List<GlobalRemoveRule> rules, boolean success, Component message) {
        if (success) {
            commitDraft();
            globalRemoveRules.clear();
            if (rules != null) {
                globalRemoveRules.addAll(rules);
            }
            globalRemoveSelectedIndex = -1;
            globalRemoveDirty = false;
            closeContextMenu();
            updateGlobalRemoveScroll();
            updateGlobalRemoveState(!globalRemoveRules.isEmpty());
        }
        updateButtons();
        KineticOverlays.toast(message);
    }

    public void applyGlobalExcludeDetail(List<String> lootTableIds) {
        if (!isGlobalExcludePanel()) {
            return;
        }
        globalExcludedLootTableIds.clear();
        if (lootTableIds != null) {
            globalExcludedLootTableIds.addAll(lootTableIds);
        }
        globalExcludedLootTableIds.sort(String.CASE_INSENSITIVE_ORDER);
        globalExcludeSelectedIndex = -1;
        globalExcludeDirty = false;
        updateGlobalExcludeScroll();
        updateButtons();
    }

    public void applyGlobalExcludeSaveResult(List<String> lootTableIds, boolean success, Component message) {
        if (success) {
            commitDraft();
            globalExcludedLootTableIds.clear();
            if (lootTableIds != null) {
                globalExcludedLootTableIds.addAll(lootTableIds);
            }
            globalExcludedLootTableIds.sort(String.CASE_INSENSITIVE_ORDER);
            globalExcludeSelectedIndex = -1;
            globalExcludeDirty = false;
            updateGlobalExcludeScroll();
            updateGlobalExcludeState(!globalExcludedLootTableIds.isEmpty());
        }
        updateButtons();
        KineticOverlays.toast(message);
    }

    void updateGlobalRemoveState(boolean active) {
        updateEntryOverrideState(
                LootEntryInfo.GLOBAL_CHEST_REMOVE_ID,
                LootEntryInfo.GLOBAL_CHEST_REMOVE_ID,
                active
        );
    }

    void updateGlobalExcludeState(boolean active) {
        updateEntryOverrideState(
                LootEntryInfo.GLOBAL_CHEST_EXCLUDE_ID,
                LootEntryInfo.GLOBAL_CHEST_EXCLUDE_ID,
                active
        );
    }

    @Override
    protected void updateSpecialButtons() {
        boolean removeVisible = isGlobalRemovePanel();
        boolean hasSelection = removeVisible
                && globalRemoveSelectedIndex >= 0
                && globalRemoveSelectedIndex < globalRemoveRules.size();
        if (globalRemoveAddButton != null) {
            globalRemoveAddButton.setVisible(removeVisible);
            globalRemoveAddButton.setEnabled(removeVisible);
        }
        if (globalRemoveModeButton != null) {
            globalRemoveModeButton.setVisible(removeVisible);
            globalRemoveModeButton.setEnabled(hasSelection);
            globalRemoveModeButton.setText(hasSelection
                    ? globalRemoveModeComponent(globalRemoveRules.get(globalRemoveSelectedIndex).mode(), true)
                    : Component.translatable("gui.contentstudio.loot.loots.global_remove.match_mode.none"));
        }
        if (globalRemoveNbtButton != null) {
            globalRemoveNbtButton.setVisible(removeVisible);
            globalRemoveNbtButton.setEnabled(hasSelection);
        }
        if (globalRemoveSaveButton != null) {
            globalRemoveSaveButton.setVisible(removeVisible);
            globalRemoveSaveButton.setEnabled(removeVisible && globalRemoveDirty);
        }

        boolean excludeVisible = isGlobalExcludePanel();
        if (globalExcludeSaveButton != null) {
            globalExcludeSaveButton.setVisible(excludeVisible);
            globalExcludeSaveButton.setEnabled(excludeVisible && globalExcludeDirty);
        }
    }

    @Override
    protected void renderSpecialPanel(GuiGraphics g, int mx, int my) {
        if (isGlobalRemovePanel()) {
            renderGlobalRemovePanel(g, mx, my);
        } else if (isGlobalExcludePanel()) {
            renderGlobalExcludePanel(g, mx, my);
        }
    }

    private void renderGlobalRemovePanel(GuiGraphics g, int mx, int my) {
        Component count = Component.translatable(
                "gui.contentstudio.loot.loots.global_remove.count",
                Component.literal(Integer.toString(globalRemoveRules.size())).withStyle(ChatFormatting.AQUA)
        );
        g.drawString(font, count, RIGHT_X + RIGHT_W - 10 - font.width(count), RIGHT_Y + 28, 0xFFFFFFFF, false);

        if (globalRemoveRules.isEmpty()) {
            g.drawCenteredString(
                    font,
                    Component.translatable("gui.contentstudio.loot.loots.global_remove.empty"),
                    SPECIAL_X + SPECIAL_W / 2,
                    SPECIAL_Y + SPECIAL_H / 2 - 4,
                    0xFFFFAA00
            );
            return;
        }

        double smoothRemoveScroll = globalRemoveScrollState.follow(
                globalRemoveScrollRow,
                globalRemoveMaxScrollRow,
                draggingGlobalRemoveScroll
        );
        int smoothRemoveRow = (int) Math.floor(smoothRemoveScroll + 1.0E-6D);
        int removeShift = (int) Math.round(
                (smoothRemoveScroll - smoothRemoveRow) * REMOVE_PITCH
        );
        int start = smoothRemoveRow * REMOVE_COLS;
        int end = Math.min(
                globalRemoveRules.size(),
                start + REMOVE_VISIBLE_ITEMS + REMOVE_COLS
        );
        enableUiScissor(
                g,
                SPECIAL_X,
                SPECIAL_Y,
                SPECIAL_X + SPECIAL_W - 10,
                SPECIAL_Y + SPECIAL_H
        );
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % REMOVE_COLS;
            int row = local / REMOVE_COLS;
            int x = SPECIAL_X + REMOVE_PADDING + col * REMOVE_PITCH;
            int y = SPECIAL_Y + REMOVE_PADDING + row * REMOVE_PITCH - removeShift;
            renderGlobalRemoveItem(g, index, x, y, mx, my);
        }

        disableUiScissor(g);
        if (globalRemoveMaxScrollRow > 0) {
            int thumb = KineticScroll.stateThumbHeight(SPECIAL_H - 4, REMOVE_ROWS, totalGlobalRemoveRows(), 18);
            KineticScroll.renderScrollbarState(
                    g,
                    mx,
                    my,
                    SPECIAL_X + SPECIAL_W - 6,
                    SPECIAL_Y + 2,
                    4,
                    SPECIAL_H - 4,
                    thumb,
                    globalRemoveMaxScrollRow,
                    smoothRemoveScroll,
                    draggingGlobalRemoveScroll
            );
        }
    }

    private void renderGlobalRemoveItem(GuiGraphics g, int index, int x, int y, int mx, int my) {
        GlobalRemoveRule rule = globalRemoveRules.get(index);
        ItemStack stack = itemStack(rule);
        boolean hovered = mx >= x && mx < x + REMOVE_CELL && my >= y && my < y + REMOVE_CELL;
        boolean selected = index == globalRemoveSelectedIndex;

        LootCheckerboard.draw(g, stack, x, y, REMOVE_CELL, REMOVE_CELL, hovered);
        GuiTheme.stateOutline(g, x, y, REMOVE_CELL, REMOVE_CELL, selected, hovered, false);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
        }

        if (hovered) {
            List<Component> tooltip = new ArrayList<>();
            if (!stack.isEmpty()) {
                tooltip.add(stack.getHoverName().copy().withStyle(ChatFormatting.GOLD));
            }
            tooltip.add(idComponent(rule.itemId()));
            tooltip.add(Component.translatable(
                    "gui.contentstudio.loot.loots.global_remove.rule.mode_line",
                    globalRemoveModeComponent(rule.mode(), false)
            ));
            if (rule.hasConfiguredNbt()) {
                tooltip.add(Component.translatable("gui.contentstudio.loot.loots.global_remove.rule.nbt_set"));
            }
            tooltip.add(Component.translatable("gui.contentstudio.loot.loots.global_remove.tip.right_click"));
            deferredTooltip = tooltip;
        }
    }

    private void renderGlobalExcludePanel(GuiGraphics g, int mx, int my) {
        Component count = Component.translatable(
                "gui.contentstudio.loot.loots.global_exclude.count",
                Component.literal(Integer.toString(globalExcludedLootTableIds.size())).withStyle(ChatFormatting.AQUA)
        );
        g.drawString(font, count, RIGHT_X + RIGHT_W - 10 - font.width(count), RIGHT_Y + 28, 0xFFFFFFFF, false);
        g.drawString(
                font,
                Component.translatable("gui.contentstudio.loot.loots.global_exclude.desc"),
                SPECIAL_X + 4,
                SPECIAL_Y + 4,
                0xFFFFFF55,
                false
        );

        int listY = SPECIAL_Y + 18;
        int listH = SPECIAL_H - 18;
        if (globalExcludedLootTableIds.isEmpty()) {
            g.drawCenteredString(
                    font,
                    Component.translatable("gui.contentstudio.loot.loots.global_exclude.empty"),
                    SPECIAL_X + SPECIAL_W / 2,
                    listY + listH / 2 - 4,
                    0xFFFFAA00
            );
        } else {
            double smoothExcludeScroll = globalExcludeScrollState.follow(
                    globalExcludeScroll,
                    globalExcludeMaxScroll,
                    draggingGlobalExcludeScroll
            );
            int smoothExcludeRow = (int) Math.floor(smoothExcludeScroll + 1.0E-6D);
            int excludeShift = (int) Math.round(
                    (smoothExcludeScroll - smoothExcludeRow) * EXCLUDE_ROW_H
            );
            int end = Math.min(
                    globalExcludedLootTableIds.size(),
                    smoothExcludeRow + EXCLUDE_VISIBLE_ROWS + 1
            );
            enableUiScissor(
                    g,
                    SPECIAL_X + 2,
                    listY,
                    SPECIAL_X + SPECIAL_W - 10,
                    listY + listH
            );
            for (int i = smoothExcludeRow; i < end; i++) {
                int y = listY + (i - smoothExcludeRow) * EXCLUDE_ROW_H - excludeShift;
                renderGlobalExcludeRow(g, i, y, mx, my);
            }
            disableUiScissor(g);
        }

        if (globalExcludeMaxScroll > 0) {
            int visibleRows = EXCLUDE_VISIBLE_ROWS - 1;
            int thumb = KineticScroll.stateThumbHeight(listH - 4, visibleRows, globalExcludedLootTableIds.size(), 18);
            KineticScroll.renderScrollbarState(
                    g,
                    mx,
                    my,
                    SPECIAL_X + SPECIAL_W - 6,
                    listY + 2,
                    4,
                    listH - 4,
                    thumb,
                    globalExcludeMaxScroll,
                    globalExcludeScrollState.follow(
                            globalExcludeScroll,
                            globalExcludeMaxScroll,
                            draggingGlobalExcludeScroll
                    ),
                    draggingGlobalExcludeScroll
            );
        }
    }

    private void renderGlobalExcludeRow(GuiGraphics g, int index, int y, int mx, int my) {
        String id = globalExcludedLootTableIds.get(index);
        boolean hovered = mx >= SPECIAL_X + 2
                && mx < SPECIAL_X + SPECIAL_W - 10
                && my >= y
                && my < y + EXCLUDE_ROW_H;
        boolean selected = index == globalExcludeSelectedIndex;
        GuiTheme.surface(
                g,
                SPECIAL_X + 2,
                y,
                SPECIAL_W - 12,
                EXCLUDE_ROW_H - 2,
                GuiTheme.Surface.PANEL_ALT
        );
        GuiTheme.stateOutline(
                g,
                SPECIAL_X + 2,
                y,
                SPECIAL_W - 12,
                EXCLUDE_ROW_H - 2,
                selected,
                hovered,
                false
        );

        String name = lootTableDisplayName(id);
        if (!name.equals(id)) {
            g.drawString(font, trim(font, name, SPECIAL_W - 24), SPECIAL_X + 7, y + 2, 0xFFFFD75F, false);
            g.drawString(font, trim(font, id, SPECIAL_W - 24), SPECIAL_X + 7, y + 12, 0xFF55FFFF, false);
        } else {
            g.drawString(font, trim(font, id, SPECIAL_W - 24), SPECIAL_X + 7, y + 6, 0xFF55FFFF, false);
        }

        if (hovered) {
            deferredTooltip = name.equals(id)
                    ? List.of(idComponent(id))
                    : List.of(nameComponent(name), idComponent(id));
        }
    }

    @Override
    protected boolean handleSpecialPanelClick(double mx, double my, int btn) {
        if (!KineticMouseButtons.isPrimary(btn) && !KineticMouseButtons.isSecondary(btn)) {
            return false;
        }
        if (isGlobalRemovePanel()) {
            return handleGlobalRemoveClick(mx, my, btn);
        }
        if (isGlobalExcludePanel()) {
            return handleGlobalExcludeClick(mx, my, btn);
        }
        return false;
    }

    private boolean handleGlobalRemoveClick(double mx, double my, int btn) {
        if (KineticMouseButtons.isPrimary(btn) && globalRemoveMaxScrollRow > 0
                && mx >= SPECIAL_X + SPECIAL_W - 9
                && mx <= SPECIAL_X + SPECIAL_W
                && my >= SPECIAL_Y + 2
                && my <= SPECIAL_Y + SPECIAL_H - 2) {
            draggingGlobalRemoveScroll = true;
            int thumb = KineticScroll.stateThumbHeight(SPECIAL_H - 4, REMOVE_ROWS, totalGlobalRemoveRows(), 18);
            globalRemoveScrollRow = KineticScroll.stateOffsetFromPointerPrecise(
                    my,
                    SPECIAL_Y + 2,
                    SPECIAL_H - 4,
                    thumb,
                    globalRemoveMaxScrollRow
            );
            globalRemoveScrollState.snap(
                    globalRemoveScrollRow,
                    globalRemoveMaxScrollRow
            );
            return true;
        }

        int gridX = (int) mx - (SPECIAL_X + REMOVE_PADDING);
        int gridY = (int) my - (SPECIAL_Y + REMOVE_PADDING);
        if (gridX < 0 || gridY < 0) {
            return false;
        }
        int col = gridX / REMOVE_PITCH;
        int row = gridY / REMOVE_PITCH;
        if (col < 0 || col >= REMOVE_COLS || row < 0 || row >= REMOVE_ROWS
                || gridX % REMOVE_PITCH >= REMOVE_CELL
                || gridY % REMOVE_PITCH >= REMOVE_CELL) {
            return false;
        }
        double smoothRemoveScroll = globalRemoveScrollState.follow(
                globalRemoveScrollRow,
                globalRemoveMaxScrollRow,
                draggingGlobalRemoveScroll
        );
        int smoothRemoveRow = (int) Math.floor(smoothRemoveScroll + 1.0E-6D);
        int removeShift = (int) Math.round(
                (smoothRemoveScroll - smoothRemoveRow) * REMOVE_PITCH
        );
        row = (gridY + removeShift) / REMOVE_PITCH;
        int index = smoothRemoveRow * REMOVE_COLS + row * REMOVE_COLS + col;
        if (index >= 0 && index < globalRemoveRules.size()) {
            globalRemoveSelectedIndex = index;
            if (KineticMouseButtons.isSecondary(btn)) {
                removeSelectedGlobalItem();
            } else {
                updateButtons();
            }
            return true;
        }
        return false;
    }

    private boolean handleGlobalExcludeClick(double mx, double my, int btn) {
        if (KineticMouseButtons.isSecondary(btn)) {
            LootEntryInfo entry = targetEntryAt(mx, my);
            if (entry != null && !entry.isGlobalChestEntry()) {
                int existing = globalExcludedLootTableIds.indexOf(entry.lootTableId());
                if (existing >= 0) {
                    globalExcludeSelectedIndex = existing;
                    removeSelectedGlobalExclude();
                    return true;
                }
            }
        }
        int listY = SPECIAL_Y + 18;
        int listH = SPECIAL_H - 18;
        if (KineticMouseButtons.isPrimary(btn) && globalExcludeMaxScroll > 0
                && mx >= SPECIAL_X + SPECIAL_W - 9
                && mx <= SPECIAL_X + SPECIAL_W
                && my >= listY + 2
                && my <= listY + listH - 2) {
            draggingGlobalExcludeScroll = true;
            int visibleRows = EXCLUDE_VISIBLE_ROWS - 1;
            int thumb = KineticScroll.stateThumbHeight(listH - 4, visibleRows, globalExcludedLootTableIds.size(), 18);
            globalExcludeScroll = KineticScroll.stateOffsetFromPointerPrecise(
                    my,
                    listY + 2,
                    listH - 4,
                    thumb,
                    globalExcludeMaxScroll
            );
            globalExcludeScrollState.snap(
                    globalExcludeScroll,
                    globalExcludeMaxScroll
            );
            return true;
        }
        if (mx >= SPECIAL_X + 2
                && mx < SPECIAL_X + SPECIAL_W - 10
                && my >= listY
                && my < listY + listH) {
            double smoothExcludeScroll = globalExcludeScrollState.follow(
                    globalExcludeScroll,
                    globalExcludeMaxScroll,
                    draggingGlobalExcludeScroll
            );
            int smoothExcludeRow = (int) Math.floor(smoothExcludeScroll + 1.0E-6D);
            int excludeShift = (int) Math.round(
                    (smoothExcludeScroll - smoothExcludeRow) * EXCLUDE_ROW_H
            );
            int index = smoothExcludeRow
                    + (int) Math.floor((my - listY + excludeShift) / EXCLUDE_ROW_H);
            if (index >= 0 && index < globalExcludedLootTableIds.size()) {
                globalExcludeSelectedIndex = index;
                if (KineticMouseButtons.isSecondary(btn)) {
                    removeSelectedGlobalExclude();
                } else {
                    updateButtons();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleSpecialPanelDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingGlobalRemoveScroll) {
            int thumb = KineticScroll.stateThumbHeight(SPECIAL_H - 4, REMOVE_ROWS, totalGlobalRemoveRows(), 18);
            globalRemoveScrollRow = KineticScroll.stateOffsetFromPointerPrecise(
                    my,
                    SPECIAL_Y + 2,
                    SPECIAL_H - 4,
                    thumb,
                    globalRemoveMaxScrollRow
            );
            globalRemoveScrollState.snap(
                    globalRemoveScrollRow,
                    globalRemoveMaxScrollRow
            );
            return true;
        }
        if (draggingGlobalExcludeScroll) {
            int listY = SPECIAL_Y + 18;
            int listH = SPECIAL_H - 18;
            int visibleRows = EXCLUDE_VISIBLE_ROWS - 1;
            int thumb = KineticScroll.stateThumbHeight(listH - 4, visibleRows, globalExcludedLootTableIds.size(), 18);
            globalExcludeScroll = KineticScroll.stateOffsetFromPointerPrecise(
                    my,
                    listY + 2,
                    listH - 4,
                    thumb,
                    globalExcludeMaxScroll
            );
            globalExcludeScrollState.snap(
                    globalExcludeScroll,
                    globalExcludeMaxScroll
            );
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleSpecialPanelScrolled(double mx, double my, double delta) {
        if (mx < SPECIAL_X || mx > SPECIAL_X + SPECIAL_W || my < SPECIAL_Y || my > SPECIAL_Y + SPECIAL_H) {
            return false;
        }
        if (isGlobalRemovePanel() && globalRemoveMaxScrollRow > 0) {
            globalRemoveScrollRow = globalRemoveScrollState.wheel(
                    globalRemoveScrollRow,
                    delta,
                    1.0D,
                    globalRemoveMaxScrollRow
            );
            return true;
        }
        if (isGlobalExcludePanel() && globalExcludeMaxScroll > 0) {
            globalExcludeScroll = globalExcludeScrollState.wheel(
                    globalExcludeScroll,
                    delta,
                    1.0D,
                    globalExcludeMaxScroll
            );
            return true;
        }
        return false;
    }

    @Override
    protected void handleSpecialPanelReleased(double mx, double my, int btn) {
        draggingGlobalRemoveScroll = false;
        draggingGlobalExcludeScroll = false;
    }

    private void openGlobalRemoveItemPicker() {
        if (minecraft == null) {
            return;
        }
        KineticSelectors.openItemSelector(this, selection -> {
            if (selection == null || !selection.isItem()) {
                return;
            }
            GlobalRemoveRule nextRule = GlobalRemoveRule.fromStack(selection.stack());
            if (nextRule.itemId().isBlank()) {
                return;
            }
            int existing = findEquivalentGlobalRemoveRule(nextRule);
            if (existing >= 0) {
                globalRemoveSelectedIndex = existing;
                ensureGlobalRemoveSelectedVisible();
                updateButtons();
                KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.global_remove.duplicate"));
                return;
            }
            globalRemoveRules.add(nextRule);
            globalRemoveSelectedIndex = globalRemoveRules.size() - 1;
            globalRemoveDirty = true;
            updateGlobalRemoveScroll();
            ensureGlobalRemoveSelectedVisible();
            updateButtons();
        });
    }

    private int findEquivalentGlobalRemoveRule(GlobalRemoveRule rule) {
        for (int i = 0; i < globalRemoveRules.size(); i++) {
            GlobalRemoveRule existing = globalRemoveRules.get(i);
            if (existing.itemId().equals(rule.itemId()) && existing.mode() == rule.mode()) {
                if (rule.mode() == GlobalRemoveRule.MatchMode.ITEM
                        || rule.mode() == GlobalRemoveRule.MatchMode.NBT_PRESENT
                        || existing.nbt().equals(rule.nbt())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void toggleGlobalRemoveModeMenu() {
        if (globalRemoveSelectedIndex < 0 || globalRemoveSelectedIndex >= globalRemoveRules.size()) {
            closeContextMenu();
            return;
        }
        GlobalRemoveRule.MatchMode selectedMode = globalRemoveRules.get(globalRemoveSelectedIndex).mode();
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (GlobalRemoveRule.MatchMode mode : GlobalRemoveRule.MatchMode.values()) {
            items.add(KineticOverlays.MenuItem.create(
                    globalRemoveModeComponent(mode, false),
                    Component.empty(),
                    globalRemoveModeTooltip(mode),
                    mode == selectedMode,
                    () -> setSelectedGlobalRemoveMode(mode),
                    true,
                    KineticOverlays.MenuItemStyle.NORMAL
            ));
        }
        openContextMenu(GLOBAL_REMOVE_MODE_X, GLOBAL_REMOVE_BUTTON_Y + 20, items);
    }

    private void setSelectedGlobalRemoveMode(GlobalRemoveRule.MatchMode mode) {
        if (globalRemoveSelectedIndex < 0 || globalRemoveSelectedIndex >= globalRemoveRules.size()) {
            closeContextMenu();
            return;
        }
        GlobalRemoveRule current = globalRemoveRules.get(globalRemoveSelectedIndex);
        GlobalRemoveRule updated = current.withMode(mode);
        int duplicate = findEquivalentGlobalRemoveRule(updated);
        if (duplicate >= 0 && duplicate != globalRemoveSelectedIndex) {
            globalRemoveSelectedIndex = duplicate;
            closeContextMenu();
            ensureGlobalRemoveSelectedVisible();
            updateButtons();
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.global_remove.duplicate"));
            return;
        }
        globalRemoveRules.set(globalRemoveSelectedIndex, updated);
        globalRemoveDirty = true;
        closeContextMenu();
        updateButtons();
    }

    private void openGlobalRemoveNbtEditor() {
        if (minecraft == null
                || globalRemoveSelectedIndex < 0
                || globalRemoveSelectedIndex >= globalRemoveRules.size()) {
            return;
        }
        int editingIndex = globalRemoveSelectedIndex;
        GlobalRemoveRule original = globalRemoveRules.get(editingIndex);
        KineticSelectors.openNbtEditor(this, original.nbt(), value -> {
            if (editingIndex < 0 || editingIndex >= globalRemoveRules.size()) {
                return;
            }
            GlobalRemoveRule current = globalRemoveRules.get(editingIndex);
            if (!current.itemId().equals(original.itemId())) {
                return;
            }
            GlobalRemoveRule updated = current.withNbt(value);
            int duplicate = findEquivalentGlobalRemoveRule(updated);
            if (duplicate >= 0 && duplicate != editingIndex) {
                globalRemoveSelectedIndex = duplicate;
                KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.global_remove.duplicate"));
                return;
            }
            globalRemoveRules.set(editingIndex, updated);
            globalRemoveSelectedIndex = editingIndex;
            globalRemoveDirty = true;
            updateButtons();
        });
    }

    private Component globalRemoveModeTooltip(GlobalRemoveRule.MatchMode mode) {
        String suffix = switch (mode) {
            case ITEM -> "item";
            case NBT_PRESENT -> "nbt_present";
            case NBT_FUZZY -> "nbt_fuzzy";
            case NBT_EXACT -> "nbt_exact";
        };
        return Component.translatable("gui.contentstudio.loot.loots.global_remove.tip.mode." + suffix);
    }

    private Component globalRemoveModeComponent(GlobalRemoveRule.MatchMode mode, boolean compact) {
        String suffix = switch (mode) {
            case ITEM -> "item";
            case NBT_PRESENT -> "nbt_present";
            case NBT_FUZZY -> "nbt_fuzzy";
            case NBT_EXACT -> "nbt_exact";
        };
        String key = compact
                ? "gui.contentstudio.loot.loots.global_remove.match_mode.compact." + suffix
                : "gui.contentstudio.loot.loots.global_remove.match_mode." + suffix;
        return Component.translatable(key);
    }

    private void removeSelectedGlobalItem() {
        if (globalRemoveSelectedIndex < 0 || globalRemoveSelectedIndex >= globalRemoveRules.size()) {
            return;
        }
        globalRemoveRules.remove(globalRemoveSelectedIndex);
        if (globalRemoveRules.isEmpty()) {
            globalRemoveSelectedIndex = -1;
        } else if (globalRemoveSelectedIndex >= globalRemoveRules.size()) {
            globalRemoveSelectedIndex = globalRemoveRules.size() - 1;
        }
        globalRemoveDirty = true;
        closeContextMenu();
        updateGlobalRemoveScroll();
        updateButtons();
    }

    private void saveGlobalRemove() {
        LootNetwork.sendToServer(new LootNetwork.SaveGlobalRemovePacket(List.copyOf(globalRemoveRules)));
    }

    private void removeSelectedGlobalExclude() {
        if (globalExcludeSelectedIndex < 0 || globalExcludeSelectedIndex >= globalExcludedLootTableIds.size()) {
            return;
        }
        globalExcludedLootTableIds.remove(globalExcludeSelectedIndex);
        if (globalExcludedLootTableIds.isEmpty()) {
            globalExcludeSelectedIndex = -1;
        } else if (globalExcludeSelectedIndex >= globalExcludedLootTableIds.size()) {
            globalExcludeSelectedIndex = globalExcludedLootTableIds.size() - 1;
        }
        globalExcludeDirty = true;
        updateGlobalExcludeScroll();
        updateButtons();
    }

    private void saveGlobalExclude() {
        LootNetwork.sendToServer(new LootNetwork.SaveGlobalExcludePacket(List.copyOf(globalExcludedLootTableIds)));
    }

    private int totalGlobalRemoveRows() {
        return Math.max(1, (globalRemoveRules.size() + REMOVE_COLS - 1) / REMOVE_COLS);
    }

    private void updateGlobalRemoveScroll() {
        globalRemoveMaxScrollRow = Math.max(0, totalGlobalRemoveRows() - REMOVE_ROWS);
        globalRemoveScrollRow = Math.max(0D, Math.min(globalRemoveScrollRow, globalRemoveMaxScrollRow));
    }

    private void ensureGlobalRemoveSelectedVisible() {
        if (globalRemoveSelectedIndex < 0) {
            return;
        }
        int selectedRow = globalRemoveSelectedIndex / REMOVE_COLS;
        if (selectedRow < globalRemoveScrollRow) {
            globalRemoveScrollRow = selectedRow;
        } else if (selectedRow >= globalRemoveScrollRow + REMOVE_ROWS) {
            globalRemoveScrollRow = selectedRow - REMOVE_ROWS + 1;
        }
        globalRemoveScrollRow = Math.max(0D, Math.min(globalRemoveScrollRow, globalRemoveMaxScrollRow));
    }

    private void updateGlobalExcludeScroll() {
        int visibleRows = EXCLUDE_VISIBLE_ROWS - 1;
        globalExcludeMaxScroll = Math.max(0, globalExcludedLootTableIds.size() - visibleRows);
        globalExcludeScroll = Math.max(0D, Math.min(globalExcludeScroll, globalExcludeMaxScroll));
    }

    private void ensureGlobalExcludeSelectedVisible() {
        if (globalExcludeSelectedIndex < 0) {
            return;
        }
        int visibleRows = EXCLUDE_VISIBLE_ROWS - 1;
        if (globalExcludeSelectedIndex < globalExcludeScroll) {
            globalExcludeScroll = globalExcludeSelectedIndex;
        } else if (globalExcludeSelectedIndex >= globalExcludeScroll + visibleRows) {
            globalExcludeScroll = globalExcludeSelectedIndex - visibleRows + 1;
        }
        globalExcludeScroll = Math.max(0D, Math.min(globalExcludeScroll, globalExcludeMaxScroll));
    }

    private ItemStack itemStack(GlobalRemoveRule rule) {
        ResourceLocation id = KineticResourceIds.tryParse(rule.itemId());
        Item item = id == null ? Items.AIR : KineticRegistries.items().get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = item.getDefaultInstance();
        if (rule.hasConfiguredNbt()) {
            try {
                stack.setTag(TagParser.parseTag(rule.nbt()));
            } catch (Exception ignored) {
                return stack;
            }
        }
        return stack;
    }

    @Override
    protected void renderTargets(GuiGraphics g, int mx, int my) {
        double smoothScroll = smoothTargetScroll();
        int start = (int) Math.floor(smoothScroll + 1.0E-6D);
        int scrollShift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
        int end = Math.min(displayEntries.size(), start + targetVisibleEntryCount() + 1);
        enableUiScissor(
                g,
                LEFT_X,
                targetAreaY(),
                LEFT_X + TARGET_WIDTH,
                targetAreaY() + targetAreaHeight()
        );
        for (int i = start; i < end; i++) {
            LootEntryInfo entry = displayEntries.get(i);
            int y = targetAreaY() + (i - start) * ROW_HEIGHT - scrollShift;
            boolean hover = mx >= LEFT_X && mx < LEFT_X + TARGET_WIDTH && my >= y && my < y + ROW_HEIGHT;
            boolean selected = selectedEntry != null
                    && selectedEntry.targetId().equals(entry.targetId())
                    && selectedEntry.lootTableId().equals(entry.lootTableId());
            boolean changed = entry.overridden() || hasPendingDraft(entry) || (selected && dirty);
            boolean excluded = !entry.isGlobalChestEntry() && globalExcludedLootTableIds.contains(entry.lootTableId());
            GuiTheme.surface(g, LEFT_X, y, TARGET_WIDTH, ROW_HEIGHT, GuiTheme.Surface.PANEL_ALT);
            if (selected) {
                GuiTheme.stateOutline(g, LEFT_X, y, TARGET_WIDTH, ROW_HEIGHT, true, false, false, 2);
            } else if (excluded) {
                GuiTheme.indicatorOutline(g, LEFT_X, y, TARGET_WIDTH, ROW_HEIGHT, GuiTheme.Indicator.DANGER);
            } else if (changed) {
                GuiTheme.indicatorOutline(g, LEFT_X, y, TARGET_WIDTH, ROW_HEIGHT, GuiTheme.Indicator.WARNING);
            } else {
                GuiTheme.stateOutline(g, LEFT_X, y, TARGET_WIDTH, ROW_HEIGHT, false, hover, false);
            }

            String name = getDisplayName(entry);
            String id = entry.lootTableId();
            if (!name.equals(id)) {
                g.drawString(font, trim(font, name, TARGET_WIDTH - 10), LEFT_X + 5, y + 2, 0xFFFFD75F, false);
                g.drawString(font, trim(font, id, TARGET_WIDTH - 10), LEFT_X + 5, y + 12, 0xFF55FFFF, false);
            } else {
                g.drawString(font, trim(font, id, TARGET_WIDTH - 10), LEFT_X + 5, y + 6, 0xFF55FFFF, false);
            }

            if (hover) {
                if (excluded) {
                    deferredTooltip = List.<Component>of(
                            name.equals(id) ? idComponent(id) : nameComponent(name),
                            name.equals(id) ? Component.empty() : idComponent(id),
                            Component.translatable("gui.contentstudio.loot.loots.global_exclude.marked")
                    ).stream().filter(component -> !component.getString().isEmpty()).toList();
                } else {
                    deferredTooltip = name.equals(id)
                            ? List.of(idComponent(id))
                            : List.of(nameComponent(name), idComponent(id));
                }
            }
        }

        disableUiScissor(g);
        renderTargetScrollbar(g, mx, my);
    }

    @Override
    protected LootEntryInfo targetEntryAt(double mx, double my) {
        if (mx < LEFT_X || mx >= LEFT_X + TARGET_WIDTH || my < targetAreaY() || my >= targetAreaY() + LIST_HEIGHT) {
            return null;
        }
        double smoothScroll = smoothTargetScroll();
        int start = (int) Math.floor(smoothScroll + 1.0E-6D);
        int shift = (int) Math.round((smoothScroll - start) * ROW_HEIGHT);
        int index = start + (int) Math.floor((my - targetAreaY() + shift) / ROW_HEIGHT);
        return index >= 0 && index < displayEntries.size() ? displayEntries.get(index) : null;
    }

    @Override
    protected String getDisplayName(LootEntryInfo entry) {
        if (entry.isGlobalChestAppend()) {
            return Component.translatable("gui.contentstudio.loot.loots.chest.global_append").getString();
        }
        if (entry.isGlobalChestRemove()) {
            return Component.translatable("gui.contentstudio.loot.loots.chest.global_remove").getString();
        }
        if (entry.isGlobalChestExclude()) {
            return Component.translatable("gui.contentstudio.loot.loots.chest.global_exclude").getString();
        }
        return lootTableDisplayName(entry.lootTableId());
    }

    private String lootTableDisplayName(String lootTableId) {
        try {
            ResourceLocation id = KineticResourceIds.parse(lootTableId);
            String key = "gui.contentstudio.loot.loots.chest."
                    + id.getNamespace()
                    + "."
                    + id.getPath().replace('/', '.');
            String translated = Component.translatable(key).getString();
            return translated.equals(key) ? lootTableId : translated;
        } catch (Exception ignored) {
            return lootTableId;
        }
    }
}
