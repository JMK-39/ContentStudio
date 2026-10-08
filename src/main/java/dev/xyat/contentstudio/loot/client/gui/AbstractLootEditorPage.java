package dev.xyat.contentstudio.loot.client.gui;


import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.KeyInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractLootEditorPage extends KineticPage {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    protected static final int V_WIDTH = 640;
    protected static final int V_HEIGHT = 360;
    protected static final int LEFT_X = 10;
    protected static final int LEFT_Y = 42;
    protected static final int SEARCH_Y = 16;
    protected static final int TARGET_WIDTH = 192;
    protected static final int TARGET_HEIGHT = 288;
    protected static final int RIGHT_X = 220;
    protected static final int RIGHT_Y = 14;
    protected static final int RIGHT_W = 410;
    protected static final int RIGHT_H = 334;
    protected static final int HEADER_H = 32;
    protected static final int EDIT_Y = RIGHT_Y + HEADER_H + 8;
    protected static final int EDIT_H = 74;
    protected static final int DROP_Y = EDIT_Y + EDIT_H + 8;
    protected static final int DROP_H = RIGHT_Y + RIGHT_H - DROP_Y - 4;
    protected static final int DROP_ROW_H = 36;
    protected static final int ICON_CELL = 22;
    protected static final int EDIT_ICON = 20;
    protected static final int TEXT_GAP = 4;
    private static final int HEADER_POOL_BUTTON_X = RIGHT_X + RIGHT_W - 276;
    private static final int HEADER_RESET_BUTTON_X = RIGHT_X + RIGHT_W - 148;
    private static final int GROUP_POOL_ACTION_X = RIGHT_X + RIGHT_W - 141;
    private static final int GROUP_ENTRY_ACTION_X = RIGHT_X + RIGHT_W - 109;
    private static final int TARGET_SCROLLBAR_X = LEFT_X + TARGET_WIDTH + 2;
    private static final int TARGET_SCROLLBAR_WIDTH = 6;
    private static final int DROP_SCROLLBAR_X = RIGHT_X + RIGHT_W - 13;
    private static final int GROUP_Y = RIGHT_Y + HEADER_H + 4;
    private static final int GROUP_H = RIGHT_Y + RIGHT_H - GROUP_Y - 4;
    private static final int GROUP_POOL_H = 24;
    private static final int GROUP_ENTRY_H = 30;
    private static final int GROUP_ROW_GAP = 2;
    private static final int GROUP_BUTTON_SLOTS = 10;
    private static final int GROUP_SCROLLBAR_X = RIGHT_X + RIGHT_W - 13;

    protected final int mode;
    protected final List<LootEntryInfo> allEntries;
    protected List<LootEntryInfo> displayEntries;
    protected final List<DropVisual> dropVisuals = new ArrayList<>();
    protected List<Component> deferredTooltip = null;

    private KineticTextField searchBox;
    private KineticTextField itemBox;
    private KineticTextField chanceBox;
    private KineticTextField rollsBox;
    private KineticTextField countMinBox;
    private KineticTextField countMaxBox;
    private KineticTextField weightBox;
    private KineticTextField lootingMinBox;
    private KineticTextField lootingMaxBox;
    private KineticButton saveButton;
    private KineticButton resetButton;
    private KineticButton deleteButton;
    private KineticButton applyButton;
    private KineticButton addButton;
    private KineticToggle killedButton;
    private KineticToggle lootingButton;
    private KineticToggle fireButton;
    private KineticToggle overrideModeButton;
    private String searchQuery = "";

    protected LootEntryInfo selectedEntry;
    protected DropVisual selectedDrop;
    protected JsonObject currentRoot;
    private String selectedJson = "";
    private boolean selectedOverridden = false;
    protected boolean dirty = false;
    private boolean detailLoading;
    private boolean requirePlayerKill = false;
    private boolean enableLooting = false;
    private boolean enableFireSmelt = false;
    private boolean overrideMode = false;
    private JsonObject appendModeBackupRoot = null;
    private String pendingPickedItemId = null;
    private String pendingPickedItemStackId = null;
    private ItemStack pendingPickedItemStack = ItemStack.EMPTY;
    private JsonObject pendingPickedTargetEntry = null;
    protected final KineticScrollController targetScroller = new KineticScrollController()
            .bindSelection(this::selectedTargetIndex, index -> targetUnitOf(index) - targetVisibleRows() / 2);
    private final KineticScrollController dropScroller = new KineticScrollController()
            .bindSelection(() -> selectedDrop == null ? -1 : dropVisuals.indexOf(selectedDrop), index -> index - visibleDropRows() / 2);
    private KineticButton addPoolHeaderButton;
    private KineticButton[] groupArrowButtons;
    private KineticButton[] groupAddButtons;
    private KineticButton[] groupPoolEditButtons;
    private KineticButton[] groupPoolDeleteButtons;
    private KineticButton[] groupEntryDeleteButtons;
    private KineticButton[] groupEntryEditButtons;
    private final List<GroupRow> groupedRows = new ArrayList<>();
    private final List<GroupRow> visibleGroupRows = new ArrayList<>();
    private final Set<Integer> expandedPools = new HashSet<>();
    private final KineticScrollController groupScroller = new KineticScrollController()
            .bindSelection(this::lastClickedGroupRowIndex, index -> index - Math.max(1, visibleGroupRows.size()) / 2);
    private int lastClickedGroupPool = -1;
    private DropVisual lastClickedGroupVisual;
    private final Map<TableDraftKey, String> pendingTableDrafts = new LinkedHashMap<>();
    private final Set<TableDraftKey> pendingTableResets = new LinkedHashSet<>();
    private final Map<TableDraftKey, String> savingTableDrafts = new LinkedHashMap<>();
    private final Set<TableDraftKey> savingTableResets = new LinkedHashSet<>();
    private boolean saveBatchHadFailure = false;
    private String selectedDropEditorSnapshot = "";

    protected static class DropVisual {
        ItemStack stack;
        String displayId;
        Component name;
        Component chance;
        Component probability;
        Component count;
        int poolIndex;
        int entryIndex;
        int depth;
        JsonObject entry;
        JsonObject pool;
        JsonArray parentEntries;
        boolean loadError;
        final List<Component> details = new ArrayList<>();
    }

    private static class GroupRow {
        final int poolIndex;
        final DropVisual visual;
        int y;

        GroupRow(int poolIndex, DropVisual visual) {
            this.poolIndex = poolIndex;
            this.visual = visual;
        }

        boolean isPool() {
            return visual == null;
        }

        int height() {
            return isPool() ? GROUP_POOL_H : GROUP_ENTRY_H;
        }
    }

    private static class PoolContext {
        int poolIndex;
        String rolls;
        String bonusRolls;
        int totalWeight;
        JsonObject pool;
        final List<Component> conditions = new ArrayList<>();
        final List<Component> functions = new ArrayList<>();
    }

    private record RollRange(double min, double max) {
    }

    private record TableDraftKey(String targetId, String lootTableId) {
    }

    private record LootEditorSnapshot(
            Map<TableDraftKey, String> pendingDrafts,
            Set<TableDraftKey> pendingResets,
            String selectedTargetId,
            String selectedLootTableId,
            String selectedJson,
            String currentRootJson,
            boolean selectedOverridden,
            boolean dirty,
            boolean requirePlayerKill,
            boolean enableLooting,
            boolean enableFireSmelt,
            boolean overrideMode,
            String appendModeBackupJson,
            Set<Integer> expandedPools,
            Object specialState
    ) {
    }

    protected final void enableLootDraft() {
        configureStandaloneDraft(this::captureLootEditorSnapshot, this::restoreLootEditorSnapshot);
    }

    private LootEditorSnapshot captureLootEditorSnapshot() {
        Map<TableDraftKey, String> drafts = new LinkedHashMap<>(pendingTableDrafts);
        String currentJson = currentRoot == null ? "" : GSON.toJson(currentRoot);
        String backupJson = appendModeBackupRoot == null ? "" : GSON.toJson(appendModeBackupRoot);
        return new LootEditorSnapshot(
                drafts,
                new LinkedHashSet<>(pendingTableResets),
                selectedEntry == null ? "" : selectedEntry.targetId(),
                selectedEntry == null ? "" : selectedEntry.lootTableId(),
                selectedJson == null ? "" : selectedJson,
                currentJson,
                selectedOverridden,
                dirty,
                requirePlayerKill,
                enableLooting,
                enableFireSmelt,
                overrideMode,
                backupJson,
                new HashSet<>(expandedPools),
                captureSpecialEditState()
        );
    }

    private void restoreLootEditorSnapshot(LootEditorSnapshot snapshot) {
        if (snapshot == null) return;
        pendingTableDrafts.clear();
        pendingTableDrafts.putAll(snapshot.pendingDrafts());
        pendingTableResets.clear();
        pendingTableResets.addAll(snapshot.pendingResets());
        savingTableDrafts.clear();
        savingTableResets.clear();
        saveBatchHadFailure = false;

        if (!snapshot.selectedTargetId().isBlank() && !snapshot.selectedLootTableId().isBlank()) {
            selectedEntry = allEntries.stream()
                    .filter(entry -> entry.targetId().equals(snapshot.selectedTargetId())
                            && entry.lootTableId().equals(snapshot.selectedLootTableId()))
                    .findFirst()
                    .orElse(new LootEntryInfo(mode, snapshot.selectedTargetId(), snapshot.selectedLootTableId(), snapshot.selectedOverridden()));
        }
        selectedJson = snapshot.selectedJson();
        selectedOverridden = snapshot.selectedOverridden();
        dirty = snapshot.dirty();
        requirePlayerKill = snapshot.requirePlayerKill();
        enableLooting = snapshot.enableLooting();
        enableFireSmelt = snapshot.enableFireSmelt();
        overrideMode = snapshot.overrideMode();
        appendModeBackupRoot = parseSnapshotRoot(snapshot.appendModeBackupJson());
        currentRoot = parseSnapshotRoot(snapshot.currentRootJson());
        expandedPools.clear();
        expandedPools.addAll(snapshot.expandedPools());
        selectedDrop = null;
        selectedDropEditorSnapshot = "";
        restoreSpecialEditState(snapshot.specialState());
        if (currentRoot != null) rebuildVisualData();
        updateButtons();
    }

    private JsonObject parseSnapshotRoot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    protected Object captureSpecialEditState() {
        return null;
    }

    protected void restoreSpecialEditState(Object state) {
    }

    private enum NumericInputType {
        PROBABILITY,
        ROLL_RANGE,
        WHOLE_NUMBER
    }

    // 原 parentScreen 参数已移除：由 KineticGui.openChild 以当前界面为父 / The former parentScreen parameter was removed: KineticGui.openChild uses the current screen as parent.
    protected AbstractLootEditorPage(int mode, List<LootEntryInfo> entries, String titleKey) {
        super(KineticI18n.translatable(titleKey));
        this.mode = mode;
        this.allEntries = new ArrayList<>(entries);
        this.displayEntries = new ArrayList<>(this.allEntries);
    }

    @Override
    protected void build(KineticUi ui) {
        searchBox = ui().textField(LEFT_X + 1, SEARCH_Y, TARGET_WIDTH - 2).placeholder(KineticI18n.translatable("gui.contentstudio.loot.loots.search_hint")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.search")).build();
        searchBox.limitTextLength(256);
        searchBox.setTextValue(searchQuery);
        searchBox.onTextChange(this::onSearchChanged);

        ui().button(RIGHT_X + RIGHT_W - 48, RIGHT_Y + 10, 44).text(KineticI18n.translatable("gui.contentstudio.loot.loots.back")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.back")).onClick(this::close).build();

        initEditWidgets();
        if (usesGroupedLayout()) {
            initGroupedWidgets();
        }
        initSpecialWidgets();
        sortAllEntries();
        updateSearch(searchQuery, false);
        if (selectedEntry == null && !displayEntries.isEmpty()) {
            selectEntry(displayEntries.get(0));
        } else {
            if (selectedDrop != null) {
                loadSelectedDropIntoFields();
            } else {
                clearEditFields();
            }
            updateButtons();
        }
        if (pendingPickedItemId != null && itemBox != null) {
            itemBox.setTextValue(pendingPickedItemId);
            pendingPickedItemId = null;
        }
        if (usesGroupedLayout()) {
            refreshGroupedLayout();
        }
    }

    private void initEditWidgets() {
        int x0 = RIGHT_X + 10;
        itemBox = ui().textField(-1000, -1000, 1).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.item_id")).build();
        itemBox.limitTextLength(256);

        int fieldY = EDIT_Y + 26;
        chanceBox = numberBox(x0 + 48, fieldY, "gui.contentstudio.loot.loots.tip.chance", "1", NumericInputType.PROBABILITY);
        rollsBox = numberBox(x0 + 92, fieldY, rollsTooltipKey(), "1", NumericInputType.ROLL_RANGE);
        countMinBox = numberBox(x0 + 136, fieldY, "gui.contentstudio.loot.loots.tip.count_min", "1", NumericInputType.WHOLE_NUMBER);
        countMaxBox = numberBox(x0 + 180, fieldY, "gui.contentstudio.loot.loots.tip.count_max", "1", NumericInputType.WHOLE_NUMBER);
        weightBox = numberBox(x0 + 224, fieldY, "gui.contentstudio.loot.loots.tip.weight", "1", NumericInputType.WHOLE_NUMBER);
        lootingMinBox = numberBox(x0 + 268, fieldY, "gui.contentstudio.loot.loots.tip.looting_min", "0", NumericInputType.WHOLE_NUMBER);
        lootingMaxBox = numberBox(x0 + 312, fieldY, "gui.contentstudio.loot.loots.tip.looting_max", "0", NumericInputType.WHOLE_NUMBER);

        int by = EDIT_Y + 48;
        killedButton = ui().toggle(x0, by, 44).value(requirePlayerKill).labels(KineticI18n.translatable("gui.contentstudio.loot.loots.killed_on"), KineticI18n.translatable("gui.contentstudio.loot.loots.killed_off")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.killed")).onChange(value -> requirePlayerKill = value).build();
        lootingButton = ui().toggle(x0 + 48, by, 44).value(enableLooting).labels(KineticI18n.translatable("gui.contentstudio.loot.loots.looting_on"), KineticI18n.translatable("gui.contentstudio.loot.loots.looting_off")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.looting")).onChange(value -> enableLooting = value).build();
        fireButton = ui().toggle(x0 + 96, by, 44).value(enableFireSmelt).labels(KineticI18n.translatable("gui.contentstudio.loot.loots.fire_on"), KineticI18n.translatable("gui.contentstudio.loot.loots.fire_off")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.fire")).onChange(value -> enableFireSmelt = value).build();
        int modeButtonX = modeButtonX(x0);
        int actionButtonX = actionButtonX(x0);
        overrideModeButton = ui().toggle(modeButtonX, by, 58).value(overrideMode).labels(KineticI18n.translatable("gui.contentstudio.loot.loots.mode.override"), KineticI18n.translatable("gui.contentstudio.loot.loots.mode.append")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.override_mode")).validator(value -> selectedEntry != null).onChange(value -> toggleOverrideMode()).build();

        applyButton = ui().button(actionButtonX, by, 38).text(KineticI18n.translatable("gui.contentstudio.loot.loots.apply_drop")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.apply_drop")).onClick(this::applyEditToSelected).build();
        addButton = ui().button(actionButtonX + 42, by, 38).text(KineticI18n.translatable("gui.contentstudio.loot.loots.add_drop")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.add_drop")).onClick(this::addDrop).build();
        deleteButton = ui().button(actionButtonX + 84, by, 38).text(KineticI18n.translatable("gui.contentstudio.loot.loots.delete_drop")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.delete_drop")).onClick(this::deleteSelectedDrop).build();
        saveButton = ui().button(RIGHT_X + RIGHT_W - 97, RIGHT_Y + 10, 44).text(KineticI18n.translatable("gui.contentstudio.loot.loots.save")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.save")).onClick(this::saveCurrentJson).build();
        resetButton = ui().button(HEADER_RESET_BUTTON_X, RIGHT_Y + 10, 44).text(KineticI18n.translatable("gui.contentstudio.loot.loots.reset")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.reset")).onClick(this::openResetConfirmDialog).build();

        if (!showEntityEditControls()) {
            killedButton.setControlVisible(false);
            lootingButton.setControlVisible(false);
            fireButton.setControlVisible(false);
            lootingMinBox.setControlVisible(false);
            lootingMaxBox.setControlVisible(false);
        }
        if (usesGroupedLayout()) {
            hideInlineWidgets();
            overrideModeButton.setControlVisible(true);
            overrideModeButton.moveControlX(RIGHT_X + RIGHT_W - 212);
            overrideModeButton.moveControlY(RIGHT_Y + 10);
        }
    }

    protected boolean usesGroupedLayout() {
        return true;
    }

    private void hideInlineWidgets() {
        if (itemBox != null) itemBox.setControlVisible(false);
        if (chanceBox != null) chanceBox.setControlVisible(false);
        if (rollsBox != null) rollsBox.setControlVisible(false);
        if (countMinBox != null) countMinBox.setControlVisible(false);
        if (countMaxBox != null) countMaxBox.setControlVisible(false);
        if (weightBox != null) weightBox.setControlVisible(false);
        if (lootingMinBox != null) lootingMinBox.setControlVisible(false);
        if (lootingMaxBox != null) lootingMaxBox.setControlVisible(false);
        if (killedButton != null) killedButton.setControlVisible(false);
        if (lootingButton != null) lootingButton.setControlVisible(false);
        if (fireButton != null) fireButton.setControlVisible(false);
        if (applyButton != null) applyButton.setControlVisible(false);
        if (addButton != null) addButton.setControlVisible(false);
        if (deleteButton != null) deleteButton.setControlVisible(false);
    }

    private void initGroupedWidgets() {
        addPoolHeaderButton = ui().button(HEADER_POOL_BUTTON_X, RIGHT_Y + 10, 58).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool.add")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.add")).onClick(this::addGroupedPool).build();

        groupArrowButtons = new KineticButton[GROUP_BUTTON_SLOTS];
        groupAddButtons = new KineticButton[GROUP_BUTTON_SLOTS];
        groupPoolEditButtons = new KineticButton[GROUP_BUTTON_SLOTS];
        groupPoolDeleteButtons = new KineticButton[GROUP_BUTTON_SLOTS];
        groupEntryDeleteButtons = new KineticButton[GROUP_BUTTON_SLOTS];
        groupEntryEditButtons = new KineticButton[GROUP_BUTTON_SLOTS];
        for (int i = 0; i < GROUP_BUTTON_SLOTS; i++) {
            int slot = i;
            groupArrowButtons[i] = ui().button(RIGHT_X + 11, -1000, 18).text(KineticI18n.translatable("gui.contentstudio.common.expand_symbol")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.expand")).onClick(() -> toggleGroupedPool(slot)).build();
            groupAddButtons[i] = ui().button(GROUP_POOL_ACTION_X, -1000, 44).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool.add_reward")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.add_reward")).onClick(() -> addGroupedReward(slot)).build();
            groupPoolEditButtons[i] = ui().button(RIGHT_X + RIGHT_W - 93, -1000, 36).text(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.short")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.edit")).onClick(() -> editGroupedPool(slot)).build();
            groupPoolDeleteButtons[i] = ui().button(RIGHT_X + RIGHT_W - 53, -1000, 36).text(KineticI18n.translatable("gui.contentstudio.loot.loots.delete.short")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm")).onClick(() -> confirmGroupedPoolDelete(slot)).build();
            groupEntryEditButtons[i] = ui().button(RIGHT_X + RIGHT_W - 61, -1000, 44).text(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.short")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.drop.edit")).onClick(() -> editGroupedEntry(slot)).build();
            groupEntryDeleteButtons[i] = ui().button(GROUP_ENTRY_ACTION_X, -1000, 44).text(KineticI18n.translatable("gui.contentstudio.loot.loots.delete.short")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.delete")).onClick(() -> deleteGroupedEntry(slot)).build();
            hideGroupSlot(i);
        }
    }

    private void hideGroupSlot(int slot) {
        if (groupArrowButtons == null || slot < 0 || slot >= GROUP_BUTTON_SLOTS) {
            return;
        }
        groupArrowButtons[slot].setControlVisible(false);
        groupAddButtons[slot].setControlVisible(false);
        groupPoolEditButtons[slot].setControlVisible(false);
        groupPoolDeleteButtons[slot].setControlVisible(false);
        groupEntryDeleteButtons[slot].setControlVisible(false);
        groupEntryEditButtons[slot].setControlVisible(false);
    }

    protected boolean showEntityEditControls() {
        return true;
    }

    protected String rollsTooltipKey() {
        return "gui.contentstudio.loot.loots.tip.rolls";
    }

    protected int modeButtonX(int x0) {
        return x0 + 144;
    }

    protected int actionButtonX(int x0) {
        return x0 + 206;
    }

    private void setGroupedButtonsActive(boolean active) {
        if (groupArrowButtons == null) {
            return;
        }
        for (int i = 0; i < GROUP_BUTTON_SLOTS; i++) {
            groupArrowButtons[i].setEnabled(active);
            groupAddButtons[i].setEnabled(active);
            groupPoolEditButtons[i].setEnabled(active);
            groupPoolDeleteButtons[i].setEnabled(active && poolCountForGroupedLayout() > 0);
            groupEntryDeleteButtons[i].setEnabled(active);
            groupEntryEditButtons[i].setEnabled(active);
        }
    }

    private KineticTextField numberBox(int x, int y, String tooltipKey, String defaultValue, NumericInputType inputType) {
        KineticTextField box = ui().textField(x, y, 34).tooltip(KineticI18n.translatable(tooltipKey)).build();
        box.limitTextLength(16);
        box.filterText(value -> isAllowedNumericInput(value, inputType));
        box.setTextValue(defaultValue);
        // 等于默认值时黑色，修改后绿色 / Black while equal to the default, green once modified.
        box.setDefaultText(defaultValue);
        return box;
    }

    private boolean isAllowedNumericInput(String value, NumericInputType inputType) {
        if (value == null || value.isEmpty()) {
            showNumericInputToast("msg.contentstudio.loot.loots.number.empty");
            return false;
        }
        boolean valid = switch (inputType) {
            case PROBABILITY -> value.matches("0(?:\\.\\d*)?|1(?:\\.0*)?");
            case ROLL_RANGE -> value.matches("\\d+(?:\\.\\d*)?(?:-\\d*(?:\\.\\d*)?)?");
            case WHOLE_NUMBER -> value.matches("\\d+");
        };
        if (!valid) {
            String key = switch (inputType) {
                case PROBABILITY -> "msg.contentstudio.loot.loots.number.probability";
                case ROLL_RANGE -> "msg.contentstudio.loot.loots.number.rolls";
                case WHOLE_NUMBER -> "msg.contentstudio.loot.loots.number.integer";
            };
            showNumericInputToast(key);
        }
        return valid;
    }

    private void showNumericInputToast(String translationKey) {
        KineticOverlays.toast(
                "loots_number_input",
                KineticI18n.translatable(translationKey),
                KineticOverlays.Position.BOTTOM_CENTER,
                3000,
                0,
                -30
        );
    }

    private Component getKilledText() {
        return KineticI18n.translatable(requirePlayerKill ? "gui.contentstudio.loot.loots.killed_on" : "gui.contentstudio.loot.loots.killed_off");
    }

    private Component getLootingText() {
        return KineticI18n.translatable(enableLooting ? "gui.contentstudio.loot.loots.looting_on" : "gui.contentstudio.loot.loots.looting_off");
    }

    private Component getFireText() {
        return KineticI18n.translatable(enableFireSmelt ? "gui.contentstudio.loot.loots.fire_on" : "gui.contentstudio.loot.loots.fire_off");
    }

    private Component getOverrideModeText() {
        return KineticI18n.translatable(overrideMode ? "gui.contentstudio.loot.loots.mode.override" : "gui.contentstudio.loot.loots.mode.append");
    }

    private void onSearchChanged(String query) {
        String nextQuery = query == null ? "" : query;
        if (nextQuery.equals(searchQuery)) {
            return;
        }
        searchQuery = nextQuery;
        updateSearch(searchQuery, true);
    }

    private void updateSearch(String query, boolean resetScroll) {
        String clean = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            displayEntries = new ArrayList<>(allEntries);
        } else {
            displayEntries = allEntries.stream().filter(entry -> KineticSearch.match(searchText(entry), clean)).collect(Collectors.toList());
        }
        displayEntries = sortedEntries(displayEntries);
        updateTargetScrollLimit();
        if (resetScroll) {
            targetScroller.scrollTo(0);
        }
    }

    private String searchText(LootEntryInfo entry) {
        String name = getDisplayName(entry).toLowerCase(Locale.ROOT);
        return entry.targetId() + " " + entry.lootTableId() + " " + name + " " + KineticSearch.pinyin(name);
    }

    private List<LootEntryInfo> sortedEntries(List<LootEntryInfo> entries) {
        return entries.stream()
                .sorted(fullEntryComparator())
                .collect(Collectors.toList());
    }

    private void sortAllEntries() {
        allEntries.sort(fullEntryComparator());
    }

    private Comparator<LootEntryInfo> fullEntryComparator() {
        return Comparator.comparingInt(this::entryPriority)
                .thenComparing(Comparator.comparing(LootEntryInfo::overridden).reversed())
                .thenComparing(entryComparator());
    }

    protected int entryPriority(LootEntryInfo entry) {
        return 0;
    }

    protected Comparator<LootEntryInfo> entryComparator() {
        return Comparator.comparing(LootEntryInfo::targetId);
    }

    private void updateTargetScrollLimit() {
        targetScroller.update(targetTotalRows(), targetVisibleRows());
    }

    protected abstract int targetVisibleRows();

    protected abstract int targetTotalRows();

    protected final double smoothTargetScroll() {
        return targetScroller.smoothOffset();
    }

    protected abstract int targetStartIndex();

    /** 目标下标所在的滚动单位（行）；网格子类按列数换算 / Scroll unit (row) of a target index; grid subclasses divide by columns. */
    protected int targetUnitOf(int index) {
        return index;
    }

    private int selectedTargetIndex() {
        if (selectedEntry == null) return -1;
        for (int i = 0; i < displayEntries.size(); i++) {
            LootEntryInfo entry = displayEntries.get(i);
            if (selectedEntry.targetId().equals(entry.targetId())
                    && selectedEntry.lootTableId().equals(entry.lootTableId())) return i;
        }
        return -1;
    }

    /** 子类在绘制每个目标条目后调用 / Subclasses call this after drawing each target entry. */
    protected final void flashTarget(KineticGraphics g, int index, int x, int y, int width, int height) {
        targetScroller.renderSelectionFlash(g, index, x, y, width, height);
    }

    private int lastClickedGroupRowIndex() {
        if (lastClickedGroupPool < 0) return -1;
        for (int i = 0; i < groupedRows.size(); i++) {
            GroupRow row = groupedRows.get(i);
            if (row.poolIndex == lastClickedGroupPool && row.visual == lastClickedGroupVisual) return i;
        }
        return -1;
    }

    protected abstract int targetVisibleEntryCount();

    protected int targetAreaHeight() {
        return TARGET_HEIGHT;
    }

    protected int targetAreaY() {
        return LEFT_Y;
    }

    protected void renderTargetScrollbar(KineticGraphics g, int mx, int my) {
        targetScroller.render(g, mx, my, TARGET_SCROLLBAR_X, targetAreaY() + 1, TARGET_SCROLLBAR_WIDTH, targetAreaHeight() - 2, 24);
    }

    protected String lootTableType() {
        return mode == LootEntryInfo.MODE_ENTITY ? "minecraft:entity" : "minecraft:block";
    }

    protected boolean showMissingPlayerKillHint() {
        return true;
    }

    protected void onTargetSelected() {
    }

    protected void onVisualDataRebuilt() {
    }

    protected boolean handleSpecialTargetClick(LootEntryInfo entry) {
        return false;
    }

    protected void initSpecialWidgets() {
    }

    protected boolean isSpecialPanelActive() {
        return false;
    }

    protected void renderSpecialPanel(KineticGraphics g, int mx, int my) {
    }

    protected boolean handleSpecialPanelClick(double mx, double my, int btn) {
        return false;
    }

    protected boolean handleSpecialPanelDragged(double mx, double my, int btn, double dx, double dy) {
        return false;
    }

    protected boolean handleSpecialPanelScrolled(double mx, double my, double delta) {
        return false;
    }

    protected void handleSpecialPanelReleased(double mx, double my, int btn) {
    }

    protected void updateSpecialButtons() {
    }

    private TableDraftKey draftKey(LootEntryInfo entry) {
        return entry == null ? null : new TableDraftKey(entry.targetId(), entry.lootTableId());
    }

    private TableDraftKey draftKey(String targetId, String lootTableId) {
        return new TableDraftKey(targetId, lootTableId);
    }

    protected boolean hasPendingDraft(LootEntryInfo entry) {
        TableDraftKey key = draftKey(entry);
        return key != null && pendingTableDrafts.containsKey(key);
    }

    private void stashCurrentDraft() {
        TableDraftKey key = draftKey(selectedEntry);
        if (key == null || currentRoot == null || !dirty) {
            return;
        }
        selectedJson = GSON.toJson(currentRoot);
        pendingTableDrafts.put(key, selectedJson);
    }

    private String currentEditorSnapshot() {
        if (selectedDrop == null || itemBox == null) {
            return "";
        }
        return String.join("\u0001",
                itemBox.textValue(),
                chanceBox == null ? "" : chanceBox.textValue(),
                rollsBox == null ? "" : rollsBox.textValue(),
                countMinBox == null ? "" : countMinBox.textValue(),
                countMaxBox == null ? "" : countMaxBox.textValue(),
                weightBox == null ? "" : weightBox.textValue(),
                lootingMinBox == null ? "" : lootingMinBox.textValue(),
                lootingMaxBox == null ? "" : lootingMaxBox.textValue(),
                Boolean.toString(requirePlayerKill),
                Boolean.toString(enableLooting),
                Boolean.toString(enableFireSmelt));
    }

    private boolean hasNoUnappliedEditorChanges() {
        return selectedDrop == null || Objects.equals(selectedDropEditorSnapshot, currentEditorSnapshot());
    }

    private boolean failsToCommitSelectedDropEditorChanges() {
        if (hasNoUnappliedEditorChanges()) {
            return false;
        }
        if (selectedDrop == null || selectedDrop.entry == null || selectedDrop.pool == null) {
            return false;
        }
        if (hasEditorValueError(selectedDrop.entry, selectedDrop.pool)) {
            return true;
        }
        dirty = true;
        selectedJson = GSON.toJson(currentRoot);
        TableDraftKey key = draftKey(selectedEntry);
        if (key != null) {
            pendingTableResets.remove(key);
            pendingTableDrafts.put(key, selectedJson);
        }
        selectedDropEditorSnapshot = currentEditorSnapshot();
        updateButtons();
        return false;
    }

    protected void selectEntry(LootEntryInfo entry) {
        if (entry == null) {
            return;
        }
        TableDraftKey currentKey = draftKey(selectedEntry);
        TableDraftKey nextKey = draftKey(entry);
        if (currentKey != null && currentKey.equals(nextKey)) {
            return;
        }
        if (currentKey != null) {
            if (failsToCommitSelectedDropEditorChanges()) {
                return;
            }
            stashCurrentDraft();
        }
        // 切换战利品表时清除分组列表的跳转目标 / Switching loot tables clears the grouped-list jump target.
        lastClickedGroupPool = -1;
        lastClickedGroupVisual = null;
        selectedEntry = entry;
        selectedDrop = null;
        selectedDropEditorSnapshot = "";
        currentRoot = null;
        selectedJson = "";
        selectedOverridden = entry.overridden();
        dirty = false;
        overrideMode = false;
        appendModeBackupRoot = null;
        expandedPools.clear();
        expandedPools.add(0);
        groupScroller.scrollTo(0);
        onTargetSelected();
        detailLoading = !isSpecialPanelActive();
        dropVisuals.clear();
        dropScroller.update(0, visibleDropRows());
        refreshGroupedLayout();
        clearEditFields();
        requestSelectedDetail();
        updateButtons();
    }

    protected void requestSelectedDetail() {
        if (selectedEntry == null) {
            return;
        }
        LootNetwork.sendToServer(new LootNetwork.RequestDetailPacket(mode, selectedEntry.targetId(), selectedEntry.lootTableId()));
    }

    public void applyDetail(int packetMode, String targetId, String lootTableId, String json, boolean overridden) {
        if (packetMode != mode || selectedEntry == null || !selectedEntry.targetId().equals(targetId) || !selectedEntry.lootTableId().equals(lootTableId)) {
            return;
        }
        TableDraftKey key = draftKey(targetId, lootTableId);
        String pendingDraft = pendingTableDrafts.get(key);
        selectedJson = pendingDraft != null ? pendingDraft : json == null ? "" : json;
        selectedOverridden = overridden;
        dirty = pendingDraft != null;
        overrideMode = false;
        appendModeBackupRoot = null;
        selectedEntry = new LootEntryInfo(mode, targetId, lootTableId, overridden);
        detailLoading = false;
        replaceEntry(selectedEntry);
        parseCurrentRoot();
        rebuildVisualData();
        updateSearch(searchBox == null ? "" : searchBox.textValue(), false);
        updateButtons();
    }

    public void applySaveResult(int packetMode, String targetId, String lootTableId, String json, boolean overridden, boolean success, Component message) {
        if (packetMode != mode) {
            KineticOverlays.toast(message);
            return;
        }

        TableDraftKey key = draftKey(targetId, lootTableId);
        String submittedDraft = savingTableDrafts.remove(key);
        boolean submittedReset = savingTableResets.remove(key);
        boolean batchResult = submittedDraft != null || submittedReset;
        boolean currentSelection = selectedEntry != null && key.equals(draftKey(selectedEntry));

        if (!batchResult) {
            if (!currentSelection) {
                KineticOverlays.toast(message);
                return;
            }
            if (success && (json == null || json.isBlank())) {
                selectedOverridden = overridden;
                dirty = false;
                selectedJson = currentRoot == null ? selectedJson : GSON.toJson(currentRoot);
                overrideMode = false;
                appendModeBackupRoot = null;
                selectedEntry = new LootEntryInfo(mode, targetId, lootTableId, overridden);
                replaceEntry(selectedEntry);
                updateSearch(searchBox == null ? "" : searchBox.textValue(), false);
                updateButtons();
            } else {
                applyDetail(packetMode, targetId, lootTableId, json, overridden);
            }
            if (success) {
                commitDraft();
            }
            KineticOverlays.toast(message);
            return;
        }

        if (success) {
            if (submittedReset) {
                pendingTableResets.remove(key);
                pendingTableDrafts.remove(key);
            } else if (Objects.equals(pendingTableDrafts.get(key), submittedDraft)) {
                pendingTableDrafts.remove(key);
            }
            replaceEntry(new LootEntryInfo(mode, targetId, lootTableId, overridden));
            if (currentSelection) {
                selectedOverridden = overridden;
                selectedEntry = new LootEntryInfo(mode, targetId, lootTableId, overridden);
                if (submittedReset) {
                    selectedJson = json == null ? "" : json;
                    dirty = false;
                    overrideMode = false;
                    appendModeBackupRoot = null;
                    selectedDrop = null;
                    selectedDropEditorSnapshot = "";
                    parseCurrentRoot();
                    rebuildVisualData();
                } else {
                    dirty = pendingTableDrafts.containsKey(key) || pendingTableResets.contains(key);
                    selectedJson = currentRoot == null ? selectedJson : GSON.toJson(currentRoot);
                }
                updateSearch(searchBox == null ? "" : searchBox.textValue(), false);
            }
        } else {
            saveBatchHadFailure = true;
        }

        KineticOverlays.toast(message);
        if (!isSavingBatch()) {
            if (!saveBatchHadFailure && pendingTableDrafts.isEmpty() && pendingTableResets.isEmpty()) {
                commitDraft();
            }
            saveBatchHadFailure = false;
        }
        updateButtons();
    }

    private void replaceEntry(LootEntryInfo updated) {
        for (int i = 0; i < allEntries.size(); i++) {
            LootEntryInfo entry = allEntries.get(i);
            if (entry.targetId().equals(updated.targetId()) && entry.lootTableId().equals(updated.lootTableId())) {
                allEntries.set(i, updated);
                sortAllEntries();
                return;
            }
        }
    }

    protected void updateEntryOverrideState(String targetId, String lootTableId, boolean overridden) {
        replaceEntry(new LootEntryInfo(mode, targetId, lootTableId, overridden));
        updateSearch(searchBox == null ? searchQuery : searchBox.textValue(), false);
        updateButtons();
    }

    private void parseCurrentRoot() {
        try {
            JsonElement element = JsonParser.parseString(selectedJson == null ? "" : selectedJson);
            currentRoot = element.isJsonObject() ? element.getAsJsonObject() : createEmptyRoot();
        } catch (Exception ignored) {
            currentRoot = createEmptyRoot();
        }
    }

    private JsonObject createEmptyRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("type", lootTableType());
        JsonArray pools = new JsonArray();
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", new JsonArray());
        pools.add(pool);
        root.add("pools", pools);
        return root;
    }

    private void rebuildVisualData() {
        dropVisuals.clear();
        buildDropVisuals();
        dropVisuals.sort(Comparator.comparingInt(visual -> visual.loadError ? 0 : 1));
        onVisualDataRebuilt();
        dropScroller.update(dropVisuals.size(), visibleDropRows());
        dropScroller.scrollTo(0);
        selectedDrop = null;
        clearEditFields();
        if (usesGroupedLayout()) {
            refreshGroupedLayout();
        }
    }

    private void buildDropVisuals() {
        try {
            JsonArray pools = getPools();
            for (int i = 0; i < pools.size(); i++) {
                if (!pools.get(i).isJsonObject()) {
                    continue;
                }
                JsonObject pool = pools.get(i).getAsJsonObject();
                PoolContext context = new PoolContext();
                context.poolIndex = i + 1;
                context.pool = pool;
                context.rolls = readNumberRange(pool.get("rolls"));
                context.bonusRolls = readNumberRange(pool.get("bonus_rolls"));
                context.totalWeight = sumWeights(pool.get("entries"));
                appendReadableConditions(context.conditions, pool.get("conditions"));
                appendReadableFunctions(context.functions, pool.get("functions"));
                JsonArray entries = pool.has("entries") && pool.get("entries").isJsonArray() ? pool.getAsJsonArray("entries") : new JsonArray();
                appendVisualEntries(entries, context, new ArrayList<>(), new ArrayList<>(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    protected JsonArray getPools() {
        if (currentRoot == null) {
            currentRoot = createEmptyRoot();
        }
        if (!currentRoot.has("pools") || !currentRoot.get("pools").isJsonArray()) {
            currentRoot.add("pools", new JsonArray());
        }
        return currentRoot.getAsJsonArray("pools");
    }

    protected JsonObject poolForNewDrop() {
        JsonArray pools = getPools();
        if (pools.isEmpty()) {
            pools.add(createDefaultPool());
        }
        JsonElement element = pools.get(0);
        if (!element.isJsonObject()) {
            JsonObject replacement = new JsonObject();
            replacement.addProperty("rolls", 1);
            replacement.add("entries", new JsonArray());
            pools.set(0, replacement);
            return replacement;
        }
        JsonObject pool = element.getAsJsonObject();
        if (!pool.has("entries") || !pool.get("entries").isJsonArray()) {
            pool.add("entries", new JsonArray());
        }
        return pool;
    }

    private void appendVisualEntries(JsonArray entries, PoolContext context, List<Component> parentConditions, List<Component> parentFunctions, int depth) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).isJsonObject()) {
                continue;
            }
            JsonObject entry = entries.get(i).getAsJsonObject();
            List<Component> conditions = new ArrayList<>(parentConditions);
            List<Component> functions = new ArrayList<>(parentFunctions);
            appendReadableConditions(conditions, entry.get("conditions"));
            appendReadableFunctions(functions, entry.get("functions"));
            String type = safeString(entry.get("type"));
            if (isItemLikeEntry(entry, type)) {
                dropVisuals.add(createDropVisual(entry, entries, i, context, conditions, functions, depth));
            }
            if (entry.has("children") && entry.get("children").isJsonArray()) {
                appendVisualEntries(entry.getAsJsonArray("children"), context, conditions, functions, depth + 1);
            }
            if (entry.has("entries") && entry.get("entries").isJsonArray()) {
                appendVisualEntries(entry.getAsJsonArray("entries"), context, conditions, functions, depth + 1);
            }
        }
    }

    private boolean isItemLikeEntry(JsonObject entry, String type) {
        if (entry.has("name")) {
            return true;
        }
        return type.endsWith("dynamic") || type.endsWith("empty") || type.endsWith("loot_table");
    }

    private DropVisual createDropVisual(JsonObject entry, JsonArray parentEntries, int entryIndex, PoolContext context, List<Component> conditions, List<Component> functions, int depth) {
        DropVisual visual = new DropVisual();
        String type = safeString(entry.get("type"));
        String name = safeString(entry.get("name"));
        visual.displayId = name.isBlank() ? blankToDash(type) : name;
        visual.loadError = isInvalidItemEntry(type, name);
        visual.stack = stackForEntry(type, name, entry);
        visual.name = visual.loadError
                ? KineticI18n.translatable("gui.contentstudio.loot.loots.drop.load_error")
                : displayNameForEntry(type, name, visual.stack);
        visual.poolIndex = context.poolIndex;
        visual.entryIndex = entryIndex;
        visual.depth = depth;
        visual.entry = entry;
        visual.pool = context.pool;
        visual.parentEntries = parentEntries;
        int weight = readInt(entry.get("weight"));
        visual.chance = KineticI18n.translatable("gui.contentstudio.loot.loots.drop.chance",
                        numberComponent(formatChance(weight, context.totalWeight, entry)),
                        numberComponent(context.rolls));
        visual.probability = KineticI18n.translatable("gui.contentstudio.loot.loots.drop.probability_only",
                        numberComponent(formatChance(weight, context.totalWeight, entry)));
        List<Component> allFunctions = new ArrayList<>(context.functions);
        allFunctions.addAll(functions);
        visual.count = KineticI18n.translatable("gui.contentstudio.loot.loots.drop.count", numberComponent(countText(allFunctions)));
        visual.details.add(KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.pool", numberComponent(context.poolIndex))));
        visual.details.add(KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.weight",
                        numberComponent(weight), numberComponent(Math.max(context.totalWeight, weight)))));
        if (depth > 0) {
            visual.details.add(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.nested", numberComponent(depth)));
        }
        visual.details.add(KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.rolls", numberComponent(context.rolls))));
        if (!context.bonusRolls.equals("-")) {
            visual.details.add(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.bonus_rolls", numberComponent(context.bonusRolls)));
        }
        visual.details.addAll(context.conditions);
        visual.details.addAll(conditions);
        visual.details.addAll(context.functions);
        visual.details.addAll(functions);
        if (visual.loadError) {
            visual.details.add(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.load_error_replaced"));
        }
        if (showMissingPlayerKillHint() && lacksKilledByPlayer(context.conditions, conditions)) {
            visual.details.add(KineticI18n.translatable("gui.contentstudio.loot.loots.condition.no_player_kill"));
        }
        return visual;
    }

    private boolean isInvalidItemEntry(String type, String name) {
        if (!("minecraft:item".equals(type) || "item".equals(type))) {
            return false;
        }
        if (name == null || name.isBlank()) {
            return true;
        }
        try {
            ResourceLocation id = KineticResourceIds.parse(name);
            Item item = KineticRegistries.items().get(id);
            return item == null || item == Items.AIR;
        } catch (Exception ignored) {
            return true;
        }
    }

    private Component loadErrorIdComponent(DropVisual visual) {
        return KineticI18n.translatable("gui.contentstudio.loot.loots.drop.load_error_id", visual.displayId);
    }

    private boolean lacksKilledByPlayer(List<Component> firstLines, List<Component> secondLines) {
        String killed = KineticI18n.translatable("gui.contentstudio.loot.loots.condition.killed_by_player").getString();
        for (Component line : firstLines) {
            if (line.getString().equals(killed)) {
                return false;
            }
        }
        for (Component line : secondLines) {
            if (line.getString().equals(killed)) {
                return false;
            }
        }
        return true;
    }

    private Component displayNameForEntry(String type, String name, ItemStack stack) {
        if (type.endsWith("empty")) {
            return KineticI18n.translatable("gui.contentstudio.loot.loots.drop.empty");
        }
        if (type.endsWith("loot_table")) {
            return KineticI18n.translatable("gui.contentstudio.loot.loots.drop.sub_table", idComponent(blankToDash(name)));
        }
        if (type.endsWith("tag")) {
            return KineticI18n.translatable("gui.contentstudio.loot.loots.drop.tag", idComponent(blankToDash(name)));
        }
        if (!stack.isEmpty()) {
            return KineticI18n.styled("gui.contentstudio.loot.style.name", stack.getHoverName());
        }
        return nameComponent(blankToDash(name));
    }

    private ItemStack stackForEntry(String type, String name, JsonObject entry) {
        if (type.endsWith("empty")) {
            return new ItemStack(Items.BARRIER);
        }
        if (type.endsWith("tag")) {
            return new ItemStack(Items.NAME_TAG);
        }
        if (type.endsWith("loot_table") || type.endsWith("dynamic")) {
            return new ItemStack(Items.CHEST);
        }
        try {
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(name));
            if (item != null && item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                LootJsonEditUtil.applyItemNbt(entry, stack);
                return stack;
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(Items.BARRIER);
    }

    protected void clearEditFields() {
        if (itemBox != null) itemBox.setTextValue("");
        if (chanceBox != null) chanceBox.setTextValue("1");
        if (rollsBox != null) rollsBox.setTextValue(defaultRollsValue());
        if (countMinBox != null) countMinBox.setTextValue("1");
        if (countMaxBox != null) countMaxBox.setTextValue("1");
        if (weightBox != null) weightBox.setTextValue("1");
        if (lootingMinBox != null) lootingMinBox.setTextValue("0");
        if (lootingMaxBox != null) lootingMaxBox.setTextValue("0");
        requirePlayerKill = false;
        enableLooting = false;
        enableFireSmelt = false;
        if (killedButton != null) killedButton.setValue(false);
        if (lootingButton != null) lootingButton.setValue(enableLooting);
        if (fireButton != null) fireButton.setValue(enableFireSmelt);
        selectedDropEditorSnapshot = "";
    }

    protected String defaultRollsValue() {
        return "1";
    }

    protected void loadSelectedDropIntoFields() {
        if (selectedDrop == null || selectedDrop.entry == null) {
            clearEditFields();
            return;
        }
        itemBox.setTextValue(safeString(selectedDrop.entry.get("name")));
        chanceBox.setTextValue(trimNumber(readRandomChanceValue(selectedDrop.entry)));
        rollsBox.setTextValue(readRollsForEditor(selectedDrop.pool));
        weightBox.setTextValue(String.valueOf(readInt(selectedDrop.entry.get("weight"))));
        JsonObject setCount = findFunction(selectedDrop.entry, "set_count");
        if (setCount != null) {
            setRangeFields(setCount.get("count"), countMinBox, countMaxBox, "1");
            enforceAtLeastOne(countMinBox);
            enforceAtLeastOne(countMaxBox);
        } else {
            countMinBox.setTextValue("1");
            countMaxBox.setTextValue("1");
        }
        JsonObject looting = findFunction(selectedDrop.entry, "looting_enchant");
        enableLooting = looting != null;
        enableFireSmelt = entryRequiresFire(selectedDrop.entry);
        if (looting != null) {
            setRangeFields(looting.get("count"), lootingMinBox, lootingMaxBox, "0");
        } else {
            lootingMinBox.setTextValue("0");
            lootingMaxBox.setTextValue("0");
        }
        requirePlayerKill = entryHasKilledByPlayer(selectedDrop.entry);
        killedButton.setValue(requirePlayerKill);
        lootingButton.setValue(enableLooting);
        if (fireButton != null) fireButton.setValue(enableFireSmelt);
        selectedDropEditorSnapshot = currentEditorSnapshot();
        updateButtons();
    }

    private void setRangeFields(JsonElement element, KineticTextField minBox, KineticTextField maxBox, String fallback) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            minBox.setTextValue(object.has("min") ? trimNumber(readDouble(object.get("min"), 1.0D)) : fallback);
            maxBox.setTextValue(object.has("max") ? trimNumber(readDouble(object.get("max"), 1.0D)) : minBox.textValue());
        } else if (element != null && element.isJsonPrimitive()) {
            String value = trimNumber(readDouble(element, 1.0D));
            minBox.setTextValue(value);
            maxBox.setTextValue(value);
        } else {
            minBox.setTextValue(fallback);
            maxBox.setTextValue(fallback);
        }
    }

    private JsonObject findFunction(JsonObject entry, String suffix) {
        if (!entry.has("functions") || !entry.get("functions").isJsonArray()) {
            return null;
        }
        JsonArray functions = entry.getAsJsonArray("functions");
        for (JsonElement element : functions) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (safeString(object.get("function")).endsWith(suffix)) {
                    return object;
                }
            }
        }
        return null;
    }

    private boolean entryHasKilledByPlayer(JsonObject entry) {
        if (!entry.has("conditions") || !entry.get("conditions").isJsonArray()) {
            return false;
        }
        JsonArray conditions = entry.getAsJsonArray("conditions");
        for (JsonElement element : conditions) {
            if (element.isJsonObject() && safeString(element.getAsJsonObject().get("condition")).endsWith("killed_by_player")) {
                return true;
            }
        }
        return false;
    }

    private void applyEditToSelected() {
        if (selectedDrop == null || selectedDrop.entry == null) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.no_drop_selected"));
            return;
        }
        if (hasEditorValueError(selectedDrop.entry, selectedDrop.pool)) {
            return;
        }
        markDirtyAndRebuild(selectedDrop.entry);
    }

    private void toggleOverrideMode() {
        if (selectedEntry == null) {
            return;
        }

        TableDraftKey key = draftKey(selectedEntry);
        pendingTableResets.remove(key);

        if (!overrideMode) {
            if (currentRoot == null) {
                parseCurrentRoot();
            }
            appendModeBackupRoot = currentRoot == null ? createEmptyRoot() : currentRoot.deepCopy();
            currentRoot = createEmptyRoot();
            overrideMode = true;
            dirty = true;
        } else {
            currentRoot = appendModeBackupRoot == null ? createRootFromSelectedJson() : appendModeBackupRoot.deepCopy();
            appendModeBackupRoot = null;
            overrideMode = false;
            dirty = isCurrentRootDifferentFromSelectedJson();
        }

        selectedDrop = null;
        if (overrideModeButton != null) {
            overrideModeButton.setValue(overrideMode);
        }
        rebuildVisualData();
        updateButtons();
    }

    private JsonObject createRootFromSelectedJson() {
        try {
            JsonElement element = JsonParser.parseString(selectedJson == null ? "" : selectedJson);
            return element.isJsonObject() ? element.getAsJsonObject() : createEmptyRoot();
        } catch (Exception ignored) {
            return createEmptyRoot();
        }
    }

    private boolean isCurrentRootDifferentFromSelectedJson() {
        try {
            JsonElement element = JsonParser.parseString(selectedJson == null ? "" : selectedJson);
            return currentRoot == null || !element.isJsonObject() || !currentRoot.equals(element.getAsJsonObject());
        } catch (Exception ignored) {
            return true;
        }
    }

    private void addDrop() {
        if (selectedEntry == null) {
            return;
        }
        JsonObject entry = new JsonObject();
        JsonObject pool = poolForNewDrop();
        if (hasEditorValueError(entry, pool)) {
            return;
        }
        JsonArray entries = pool.getAsJsonArray("entries");
        entries.add(entry);
        markDirtyAndRebuild(entry);
    }

    private void deleteSelectedDrop() {
        if (selectedDrop == null || selectedDrop.parentEntries == null) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.no_drop_selected"));
            return;
        }
        selectedDrop.parentEntries.remove(selectedDrop.entryIndex);
        markDirtyAndRebuild(null);
    }

    private boolean hasEditorValueError(JsonObject entry, JsonObject pool) {
        String itemId = itemBox.textValue().trim();
        if (isInvalidItemId(itemId)) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.invalid_item"));
            return true;
        }
        if (hasEmptyNumericField()) {
            restoreEmptyNumericDefaults();
            showNumericInputToast("msg.contentstudio.loot.loots.number.empty");
            return true;
        }
        Double chanceValue = parseDoubleBox(chanceBox);
        RollRange rollsValue = parseRollRange(rollsBox);
        Integer countMinValue = parseIntegerBox(countMinBox);
        Integer countMaxValue = parseIntegerBox(countMaxBox);
        Integer weightValue = parseIntegerBox(weightBox);
        Integer lootMinValue = parseIntegerBox(lootingMinBox);
        Integer lootMaxValue = parseIntegerBox(lootingMaxBox);
        if (chanceValue == null) {
            showNumericInputToast("msg.contentstudio.loot.loots.number.probability");
            return true;
        }
        if (rollsValue == null) {
            showNumericInputToast("msg.contentstudio.loot.loots.number.rolls");
            return true;
        }
        if (countMinValue == null || countMaxValue == null || weightValue == null || lootMinValue == null || lootMaxValue == null) {
            showNumericInputToast("msg.contentstudio.loot.loots.number.integer");
            return true;
        }
        double chance = chanceValue;
        int countMin = countMinValue;
        int countMax = countMaxValue;
        int weight = weightValue;
        int lootMin = lootMinValue;
        int lootMax = lootMaxValue;
        if (chance < 0.01D || chance > 1.0D || rollsValue.min() <= 0.0D || countMin <= 0 || countMax <= 0 || weight <= 0 || lootMin < 0 || lootMax < 0) {
            showNumericInputToast("msg.contentstudio.loot.loots.number.out_of_range");
            return true;
        }
        if (rollsValue.max() < rollsValue.min() || countMax < countMin || lootMax < lootMin) {
            showNumericInputToast("msg.contentstudio.loot.loots.number.min_max");
            return true;
        }
        String previousItemId = safeString(entry.get("name"));
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", itemId);
        if (pendingPickedItemStackId != null
                && pendingPickedItemStackId.equals(itemId)
                && (pendingPickedTargetEntry == null || pendingPickedTargetEntry == entry)) {
            LootJsonEditUtil.setItemNbt(entry, pendingPickedItemStack);
            clearPendingPickedItem();
        } else if (!itemId.equals(previousItemId)) {
            LootJsonEditUtil.setItemNbt(entry, ItemStack.EMPTY);
        }
        entry.addProperty("weight", weight);
        rewriteCountFunctions(entry, countMin, countMax, lootMin, lootMax);
        rewriteKilledCondition(entry);
        rewriteFireCondition(entry);
        rewriteRandomChanceCondition(entry, chance);
        rewritePoolRolls(pool, rollsValue);
        return false;
    }

    private boolean hasEmptyNumericField() {
        return isEmpty(chanceBox)
                || isEmpty(rollsBox)
                || isEmpty(countMinBox)
                || isEmpty(countMaxBox)
                || isEmpty(weightBox)
                || isEmpty(lootingMinBox)
                || isEmpty(lootingMaxBox);
    }

    private boolean isEmpty(KineticTextField box) {
        return box == null || box.textValue().trim().isEmpty();
    }

    private void restoreEmptyNumericDefaults() {
        if (isEmpty(chanceBox)) chanceBox.setTextValue("1");
        if (isEmpty(rollsBox)) rollsBox.setTextValue(defaultRollsValue());
        if (isEmpty(countMinBox)) countMinBox.setTextValue("1");
        if (isEmpty(countMaxBox)) countMaxBox.setTextValue("1");
        if (isEmpty(weightBox)) weightBox.setTextValue("1");
        if (isEmpty(lootingMinBox)) lootingMinBox.setTextValue("0");
        if (isEmpty(lootingMaxBox)) lootingMaxBox.setTextValue("0");
    }

    private boolean isInvalidItemId(String itemId) {
        try {
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(itemId));
            return item == null || item == Items.AIR;
        } catch (Exception e) {
            return true;
        }
    }

    private Integer parseIntegerBox(KineticTextField box) {
        try {
            return Integer.parseInt(box.textValue().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDoubleBox(KineticTextField box) {
        try {
            return Double.parseDouble(box.textValue().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private RollRange parseRollRange(KineticTextField box) {
        try {
            String value = box.textValue().trim().replace(" ", "");
            int separator = value.indexOf('-');
            if (separator < 0) {
                double number = Double.parseDouble(value);
                return new RollRange(number, number);
            }
            double min = Double.parseDouble(value.substring(0, separator));
            double max = Double.parseDouble(value.substring(separator + 1));
            return new RollRange(min, max);
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceAtLeastOne(KineticTextField box) {
        Integer value = parseIntegerBox(box);
        if (value == null || value < 1) {
            box.setTextValue("1");
        }
    }

    private void rewriteCountFunctions(JsonObject entry, int countMin, int countMax, int lootMin, int lootMax) {
        JsonArray functions = entry.has("functions") && entry.get("functions").isJsonArray() ? entry.getAsJsonArray("functions") : new JsonArray();
        JsonArray kept = new JsonArray();
        for (JsonElement element : functions) {
            if (element.isJsonObject()) {
                String function = safeString(element.getAsJsonObject().get("function"));
                if (function.endsWith("set_count") || function.endsWith("looting_enchant") || function.endsWith("furnace_smelt")) {
                    continue;
                }
            }
            kept.add(element);
        }
        JsonObject setCount = new JsonObject();
        setCount.addProperty("function", "minecraft:set_count");
        setCount.add("count", numberRange(countMin, countMax));
        kept.add(setCount);
        if (enableLooting) {
            JsonObject looting = new JsonObject();
            looting.addProperty("function", "minecraft:looting_enchant");
            looting.add("count", numberRange(lootMin, lootMax));
            kept.add(looting);
        }
        if (enableFireSmelt) {
            kept.add(createFurnaceSmeltFunction());
        }
        entry.add("functions", kept);
    }

    private JsonElement numberRange(int min, int max) {
        if (min == max) {
            return GSON.toJsonTree(min);
        }
        JsonObject object = new JsonObject();
        object.addProperty("type", "minecraft:uniform");
        object.addProperty("min", min);
        object.addProperty("max", max);
        return object;
    }

    private JsonObject createFurnaceSmeltFunction() {
        JsonObject furnaceSmelt = new JsonObject();
        furnaceSmelt.addProperty("function", "minecraft:furnace_smelt");
        return furnaceSmelt;
    }

    private void rewriteFireCondition(JsonObject entry) {
        JsonArray conditions = entry.has("conditions") && entry.get("conditions").isJsonArray() ? entry.getAsJsonArray("conditions") : new JsonArray();
        JsonArray kept = new JsonArray();
        for (JsonElement element : conditions) {
            if (element.isJsonObject() && isFireRequirement(element.getAsJsonObject())) {
                continue;
            }
            kept.add(element);
        }
        if (enableFireSmelt) {
            kept.add(createFireRequirementCondition());
        }
        if (kept.isEmpty()) {
            entry.remove("conditions");
        } else {
            entry.add("conditions", kept);
        }
    }

    private boolean entryRequiresFire(JsonObject entry) {
        if (!entry.has("conditions") || !entry.get("conditions").isJsonArray()) {
            return false;
        }
        JsonArray conditions = entry.getAsJsonArray("conditions");
        for (JsonElement element : conditions) {
            if (element.isJsonObject() && isFireRequirement(element.getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFireRequirement(JsonObject condition) {
        if (!safeString(condition.get("condition")).endsWith("entity_properties")) {
            return false;
        }
        if (!"this".equals(safeString(condition.get("entity")))) {
            return false;
        }
        String compact = readCompact(condition);
        return compact.contains("is_on_fire") && compact.contains("true");
    }

    private JsonObject createFireRequirementCondition() {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:entity_properties");
        condition.addProperty("entity", "this");
        JsonObject predicate = new JsonObject();
        JsonObject flags = new JsonObject();
        flags.addProperty("is_on_fire", true);
        predicate.add("flags", flags);
        condition.add("predicate", predicate);
        return condition;
    }

    private void rewriteRandomChanceCondition(JsonObject entry, double chanceValue) {
        JsonArray conditions = entry.has("conditions") && entry.get("conditions").isJsonArray() ? entry.getAsJsonArray("conditions") : new JsonArray();
        JsonArray kept = new JsonArray();
        for (JsonElement element : conditions) {
            if (element.isJsonObject() && safeString(element.getAsJsonObject().get("condition")).endsWith("random_chance")) {
                continue;
            }
            kept.add(element);
        }
        if (chanceValue < 1.0D) {
            JsonObject chance = new JsonObject();
            chance.addProperty("condition", "minecraft:random_chance");
            chance.addProperty("chance", chanceValue);
            kept.add(chance);
        }
        if (kept.isEmpty()) {
            entry.remove("conditions");
        } else {
            entry.add("conditions", kept);
        }
    }

    private void rewritePoolRolls(JsonObject pool, RollRange rolls) {
        if (pool != null) {
            if (Math.abs(rolls.min() - rolls.max()) < 0.0001D) {
                pool.addProperty("rolls", rolls.min());
            } else {
                JsonObject range = new JsonObject();
                range.addProperty("type", "minecraft:uniform");
                range.addProperty("min", rolls.min());
                range.addProperty("max", rolls.max());
                pool.add("rolls", range);
            }
        }
    }

    private double readRandomChanceValue(JsonObject entry) {
        JsonObject condition = findRandomChanceCondition(entry);
        if (condition == null) {
            return 1.0D;
        }
        double value = readDouble(condition.get("chance"), 1.0D);
        return Math.max(0.01D, Math.min(1.0D, value));
    }

    protected String readRollsForEditor(JsonObject pool) {
        if (pool == null || !pool.has("rolls")) {
            return "1";
        }
        JsonElement rolls = pool.get("rolls");
        if (rolls.isJsonPrimitive()) {
            return trimNumber(readDouble(rolls, 1.0D));
        }
        if (rolls.isJsonObject()) {
            JsonObject object = rolls.getAsJsonObject();
            if (object.has("min") && object.has("max")) {
                return trimNumber(readDouble(object.get("min"), 1.0D)) + "-" + trimNumber(readDouble(object.get("max"), 1.0D));
            }
        }
        return "1";
    }

    private JsonObject findRandomChanceCondition(JsonObject entry) {
        if (!entry.has("conditions") || !entry.get("conditions").isJsonArray()) {
            return null;
        }
        JsonArray conditions = entry.getAsJsonArray("conditions");
        for (JsonElement element : conditions) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (safeString(object.get("condition")).endsWith("random_chance")) {
                    return object;
                }
            }
        }
        return null;
    }

    private void rewriteKilledCondition(JsonObject entry) {
        JsonArray conditions = entry.has("conditions") && entry.get("conditions").isJsonArray() ? entry.getAsJsonArray("conditions") : new JsonArray();
        JsonArray kept = new JsonArray();
        for (JsonElement element : conditions) {
            if (element.isJsonObject() && safeString(element.getAsJsonObject().get("condition")).endsWith("killed_by_player")) {
                continue;
            }
            kept.add(element);
        }
        if (requirePlayerKill) {
            JsonObject killed = new JsonObject();
            killed.addProperty("condition", "minecraft:killed_by_player");
            kept.add(killed);
        }
        if (kept.isEmpty()) {
            entry.remove("conditions");
        } else {
            entry.add("conditions", kept);
        }
    }

    protected void markDirtyAndRebuild(JsonObject preferredEntry) {
        dirty = true;
        selectedJson = GSON.toJson(currentRoot);
        TableDraftKey key = draftKey(selectedEntry);
        if (key != null) {
            pendingTableResets.remove(key);
            pendingTableDrafts.put(key, selectedJson);
        }
        rebuildVisualData();
        if (preferredEntry != null) {
            for (DropVisual visual : dropVisuals) {
                if (visual.entry == preferredEntry) {
                    selectedDrop = visual;
                    loadSelectedDropIntoFields();
                    break;
                }
            }
        }
        updateButtons();
    }

    private boolean isSavingBatch() {
        return !savingTableDrafts.isEmpty() || !savingTableResets.isEmpty();
    }

    private void saveCurrentJson() {
        if (selectedEntry == null || currentRoot == null || isSavingBatch()) {
            return;
        }
        if (failsToCommitSelectedDropEditorChanges()) {
            return;
        }
        stashCurrentDraft();
        saveAllPendingDrafts();
    }

    private void saveAllPendingDrafts() {
        if ((pendingTableDrafts.isEmpty() && pendingTableResets.isEmpty()) || isSavingBatch()) {
            updateButtons();
            return;
        }
        savingTableResets.addAll(pendingTableResets);
        for (Map.Entry<TableDraftKey, String> entry : pendingTableDrafts.entrySet()) {
            if (!pendingTableResets.contains(entry.getKey())) {
                savingTableDrafts.put(entry.getKey(), entry.getValue());
            }
        }
        saveBatchHadFailure = false;
        for (TableDraftKey key : new ArrayList<>(savingTableResets)) {
            LootNetwork.sendToServer(new LootNetwork.ResetPacket(mode, key.targetId(), key.lootTableId()));
        }
        for (Map.Entry<TableDraftKey, String> entry : new ArrayList<>(savingTableDrafts.entrySet())) {
            TableDraftKey key = entry.getKey();
            LootNetwork.sendToServer(new LootNetwork.SavePacket(mode, key.targetId(), key.lootTableId(), entry.getValue()));
        }
        updateButtons();
    }

    private void openResetConfirmDialog() {
        if (selectedEntry == null || isSavingBatch()) {
            return;
        }
        openDialog(
                KineticI18n.translatable("gui.contentstudio.loot.loots.reset.confirm.title"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.reset.confirm.desc"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.reset.confirm.yes"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.reset.confirm.cancel"),
                this::resetCurrentJson,
                () -> { }
        );
    }

    private void resetCurrentJson() {
        if (selectedEntry == null || isSavingBatch()) {
            return;
        }
        LootNetwork.sendToServer(new LootNetwork.RequestResetPreviewPacket(mode, selectedEntry.targetId(), selectedEntry.lootTableId()));
    }

    public void applyResetPreview(int packetMode, String targetId, String lootTableId, String json) {
        if (packetMode != mode || selectedEntry == null
                || !selectedEntry.targetId().equals(targetId)
                || !selectedEntry.lootTableId().equals(lootTableId)) {
            return;
        }
        TableDraftKey key = draftKey(targetId, lootTableId);
        String previewJson = json == null ? "" : json;
        pendingTableResets.add(key);
        pendingTableDrafts.put(key, previewJson);
        selectedJson = previewJson;
        dirty = true;
        overrideMode = false;
        appendModeBackupRoot = null;
        selectedDrop = null;
        selectedDropEditorSnapshot = "";
        parseCurrentRoot();
        rebuildVisualData();
        updateButtons();
    }

    private void openItemPicker() {
        if (!isAttached()) {
            return;
        }
        KineticSelectors.openItemSelector(selection -> {
            if (selection != null && selection.isItem()) {
                ResourceLocation id = KineticRegistries.items().id(selection.stack().getItem());
                if (id != null && itemBox != null) {
                    pendingPickedItemId = id.toString();
                    pendingPickedItemStackId = id.toString();
                    pendingPickedItemStack = selection.stack().copy();
                    pendingPickedTargetEntry = selectedDrop == null ? null : selectedDrop.entry;
                    itemBox.setTextValue(id.toString());
                }
            }
        });
    }

    private void appendReadableConditions(List<Component> lines, JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).isJsonObject()) {
                appendCondition(lines, array.get(i).getAsJsonObject());
            }
        }
    }

    private void appendCondition(List<Component> lines, JsonObject object) {
        String condition = safeString(object.get("condition"));
        String compact = readCompact(object);
        if (condition.endsWith("killed_by_player")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.killed_by_player"));
        } else if (condition.endsWith("random_chance")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.random",
                    numberComponent(formatPercent(readDouble(object.get("chance"), 0.0D)))));
        } else if (condition.endsWith("random_chance_with_looting")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.random_looting",
                    numberComponent(formatPercent(readDouble(object.get("chance"), 0.0D))),
                    numberComponent(formatPercent(readDouble(object.get("looting_multiplier"), 0.0D)))));
        } else if (condition.endsWith("survives_explosion")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.survives_explosion"));
        } else if (condition.endsWith("inverted")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.inverted", describeNestedCondition(object.get("term"))));
        } else if (condition.endsWith("match_tool")) {
            lines.add(describeToolCondition(object));
        } else if (condition.endsWith("table_bonus")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.table_bonus", idComponent(safeString(object.get("enchantment")))));
        } else if (condition.endsWith("entity_properties")) {
            lines.add(describeEntityCondition(object));
        } else if (condition.endsWith("block_state_property")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.block_state", idComponent(safeString(object.get("block")))));
        } else if (condition.endsWith("location_check")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.location"));
        } else if (condition.endsWith("weather_check")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.weather", valueComponent(readCompact(object))));
        } else if (condition.endsWith("time_check")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.time", numberComponent(readNumberRange(object.get("value")))));
        } else if (condition.endsWith("value_check")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.value", numberComponent(readNumberRange(object.get("range")))));
        } else if (condition.endsWith("reference")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.reference", idComponent(blankToDash(safeString(object.get("name"))))));
        } else if (condition.endsWith("alternative") || condition.endsWith("any_of") || condition.endsWith("all_of")) {
            int count = object.has("terms") && object.get("terms").isJsonArray() ? object.getAsJsonArray("terms").size() : 0;
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.combined", numberComponent(count)));
        } else if (condition.endsWith("damage_source_properties")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.damage_source"));
        } else if (condition.endsWith("entity_scores")) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.entity_scores"));
        } else if (!condition.isBlank()) {
            lines.add(conditionLine("gui.contentstudio.loot.loots.condition.generic", idComponent(condition), valueComponent(compact)));
        }
    }

    private Component describeToolCondition(JsonObject object) {
        String compact = readCompact(object);
        if (compact.contains("silk_touch")) {
            return conditionLine("gui.contentstudio.loot.loots.condition.silk_touch");
        }
        if (compact.contains("fortune")) {
            return conditionLine("gui.contentstudio.loot.loots.condition.fortune_tool");
        }
        return conditionLine("gui.contentstudio.loot.loots.condition.match_tool");
    }

    private Component describeEntityCondition(JsonObject object) {
        String compact = readCompact(object);
        if (compact.contains("is_on_fire") && compact.contains("true")) {
            return conditionLine("gui.contentstudio.loot.loots.condition.on_fire");
        }
        if (compact.contains("killer_player")) {
            return conditionLine("gui.contentstudio.loot.loots.condition.killer_player_property");
        }
        return conditionLine("gui.contentstudio.loot.loots.condition.entity", idComponent(safeString(object.get("entity"))));
    }

    private Component describeNestedCondition(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String compact = readCompact(object);
            if (compact.contains("silk_touch")) {
                return conditionLine("gui.contentstudio.loot.loots.condition.no_silk_touch");
            }
            String condition = safeString(object.get("condition"));
            return idComponent(blankToDash(condition));
        }
        return conditionLine("gui.contentstudio.loot.loots.condition.unknown");
    }

    private void appendReadableFunctions(List<Component> lines, JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).isJsonObject()) {
                appendFunction(lines, array.get(i).getAsJsonObject());
            }
        }
    }

    private void appendFunction(List<Component> lines, JsonObject object) {
        String function = safeString(object.get("function"));
        if (function.endsWith("set_count")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_count", numberComponent(readNumberRange(object.get("count")))));
        } else if (function.endsWith("set_damage")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_damage", numberComponent(readNumberRange(object.get("damage")))));
        } else if (function.endsWith("set_potion")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_potion",
                    idComponent(blankToDash(safeString(object.has("id") ? object.get("id") : object.get("potion"))))));
        } else if (function.endsWith("exploration_map")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.exploration_map",
                    idComponent(blankToDash(safeString(object.get("destination"))))));
        } else if (function.endsWith("set_stew_effect")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_stew_effect", valueComponent(readCompact(object.get("effects")))));
        } else if (function.endsWith("set_instrument")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_instrument",
                    idComponent(blankToDash(safeString(object.has("options") ? object.get("options") : object.get("instrument"))))));
        } else if (function.endsWith("set_name")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_name"));
        } else if (function.endsWith("set_lore")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_lore"));
//? if >=1.21 {
/*        } else if (function.endsWith("enchanted_count_increase")) {
*///?} else {
        } else if (function.endsWith("looting_enchant")) {
//?}

            lines.add(functionLine("gui.contentstudio.loot.loots.function.looting", numberComponent(readNumberRange(object.get("count")))));
        } else if (function.endsWith("apply_bonus")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.apply_bonus",
                    idComponent(safeString(object.get("enchantment"))), idComponent(safeString(object.get("formula")))));
        } else if (function.endsWith("furnace_smelt")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.furnace_smelt"));
        } else if (function.endsWith("explosion_decay")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.explosion_decay"));
//? if >=1.21 {
/*        } else if (function.endsWith("set_components")) {
*///?} else {
        } else if (function.endsWith("set_nbt")) {
//?}

            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_nbt"));
//? if >=1.21 {
/*        } else if (function.endsWith("copy_name") || function.endsWith("copy_custom_data") || function.endsWith("copy_components") || function.endsWith("copy_state")) {
*///?} else {
        } else if (function.endsWith("copy_name") || function.endsWith("copy_nbt") || function.endsWith("copy_state")) {
//?}

            lines.add(functionLine("gui.contentstudio.loot.loots.function.copy_data", idComponent(function)));
        } else if (function.endsWith("enchant_randomly")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.enchant_randomly"));
        } else if (function.endsWith("enchant_with_levels")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.enchant_with_levels", numberComponent(readNumberRange(object.get("levels")))));
        } else if (function.endsWith("set_enchantments")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_enchantments"));
        } else if (function.endsWith("limit_count")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.limit_count", numberComponent(readNumberRange(object.get("limit")))));
        } else if (function.endsWith("set_contents")) {
            int count = object.has("entries") && object.get("entries").isJsonArray() ? object.getAsJsonArray("entries").size() : 0;
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_contents", numberComponent(count)));
        } else if (!function.isBlank()) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.generic", idComponent(function)));
        }
    }

    private String countText(List<Component> functions) {
        String setCountPrefix = KineticI18n.translatable("gui.contentstudio.loot.loots.function.set_count.prefix").getString();
        String lootingPrefix = KineticI18n.translatable("gui.contentstudio.loot.loots.function.looting.prefix").getString();
        String base = "1";
        String looting = "";
        for (Component component : functions) {
            String text = component.getString();
            if (text.startsWith(setCountPrefix)) {
                base = text.substring(setCountPrefix.length()).trim();
            } else if (text.startsWith(lootingPrefix)) {
                looting = text.substring(lootingPrefix.length()).trim();
            }
        }
        if (!looting.isBlank()) {
            return KineticI18n.translatable("gui.contentstudio.loot.loots.drop.count_with_looting", base, looting).getString();
        }
        return base;
    }

    private int sumWeights(JsonElement entriesElement) {
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            return 0;
        }
        JsonArray entries = entriesElement.getAsJsonArray();
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).isJsonObject()) {
                JsonObject entry = entries.get(i).getAsJsonObject();
                if (isItemLikeEntry(entry, safeString(entry.get("type")))) {
                    total += Math.max(0, readInt(entry.get("weight")));
                }
            }
        }
        return total;
    }

    private String formatChance(int weight, int totalWeight, JsonObject entry) {
        if (totalWeight <= 0) {
            return KineticI18n.translatable("gui.contentstudio.loot.loots.drop.unknown_chance").getString();
        }
        double value = (double) weight * 100.0D / (double) totalWeight;
        value = value * readRandomChanceValue(entry);
        return trimNumber(value) + "%";
    }

    private String formatPercent(double chance) {
        return trimNumber(chance * 100.0D) + "%";
    }

    private String trimNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001D) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private int readInt(JsonElement element) {
        try {
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsInt();
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private double readDouble(JsonElement element, double fallback) {
        try {
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsDouble();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private String readNumberRange(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "-";
        }
        try {
            if (element.isJsonPrimitive()) {
                return trimNumber(element.getAsDouble());
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("min") && object.has("max")) {
                    return trimNumber(object.get("min").getAsDouble()) + " - " + trimNumber(object.get("max").getAsDouble());
                }
                if (object.has("n") && object.has("p")) {
                    return readCompact(object);
                }
            }
        } catch (Exception ignored) {
        }
        return readCompact(element);
    }

    private String safeString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return readCompact(element);
    }

    private String readCompact(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "-";
        }
        return GSON.toJson(element).replace('\n', ' ').replace("  ", " ");
    }

    private String blankToDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private int poolCountForGroupedLayout() {
        return currentRoot == null ? 0 : getPools().size();
    }

    private JsonObject groupedPool(int poolIndex) {
        JsonArray pools = getPools();
        int index = Math.max(0, Math.min(poolIndex, pools.size() - 1));
        JsonElement element = pools.get(index);
        if (!element.isJsonObject()) {
            JsonObject replacement = createDefaultPool();
            pools.set(index, replacement);
            return replacement;
        }
        JsonObject pool = element.getAsJsonObject();
        if (!pool.has("entries") || !pool.get("entries").isJsonArray()) {
            pool.add("entries", new JsonArray());
        }
        return pool;
    }

    private JsonObject createDefaultPool() {
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0.0D);
        pool.add("entries", new JsonArray());
        return pool;
    }

    private void refreshGroupedLayout() {
        if (!usesGroupedLayout()) {
            return;
        }
        groupedRows.clear();
        if (selectedEntry != null && currentRoot != null) {
            int pools = getPools().size();
            expandedPools.removeIf(index -> index < 0 || index >= pools);
            List<Integer> poolOrder = new ArrayList<>();
            for (int poolIndex = 0; poolIndex < pools; poolIndex++) {
                poolOrder.add(poolIndex);
                if (poolHasLoadError(poolIndex)) {
                    expandedPools.add(poolIndex);
                }
            }
            poolOrder.sort(Comparator
                    .comparingInt((Integer poolIndex) -> poolHasLoadError(poolIndex) ? 0 : 1)
                    .thenComparingInt(Integer::intValue));
            for (int poolIndex : poolOrder) {
                groupedRows.add(new GroupRow(poolIndex, null));
                if (expandedPools.contains(poolIndex)) {
                    for (DropVisual visual : dropVisuals) {
                        if (visual.poolIndex == poolIndex + 1) {
                            groupedRows.add(new GroupRow(poolIndex, visual));
                        }
                    }
                }
            }
        }
        groupScroller.updateRange(calculateMaxGroupScroll(), groupedRows.size(), Math.max(1, visibleGroupRows.size()));
        updateVisibleGroupedRows();
    }


    private boolean poolHasLoadError(int poolIndex) {
        for (DropVisual visual : dropVisuals) {
            if (visual.poolIndex == poolIndex + 1 && visual.loadError) {
                return true;
            }
        }
        return false;
    }

    private int calculateMaxGroupScroll() {
        if (groupedRows.isEmpty()) {
            return 0;
        }
        int available = GROUP_H - 12;
        int used = 0;
        int start = groupedRows.size() - 1;
        while (start >= 0) {
            int height = groupedRows.get(start).height();
            if (used > 0 && used + height > available) {
                break;
            }
            used += height;
            start--;
        }
        return Math.max(0, start + 1);
    }

    private void updateVisibleGroupedRows() {
        visibleGroupRows.clear();
        if (groupArrowButtons != null) {
            for (int i = 0; i < GROUP_BUTTON_SLOTS; i++) {
                hideGroupSlot(i);
            }
        }
        double visualScroll = groupScroller.smoothOffset();
        int firstIndex = Math.max(0, Math.min((int) Math.floor(visualScroll + 1.0E-6D), groupScroller.maxOffset()));
        double fraction = Math.max(0D, visualScroll - firstIndex);
        int top = GROUP_Y + 6;
        int bottom = GROUP_Y + GROUP_H - 6;
        int firstHeight = firstIndex < groupedRows.size() ? groupedRows.get(firstIndex).height() : 0;
        int y = top - (int) Math.round(fraction * firstHeight);
        for (int i = firstIndex; i < groupedRows.size() && visibleGroupRows.size() < GROUP_BUTTON_SLOTS; i++) {
            GroupRow row = groupedRows.get(i);
            if (y >= bottom) {
                break;
            }
            row.y = y;
            if (y + row.height() > top) {
                visibleGroupRows.add(row);
            }
            y += row.height();
        }
        groupScroller.updateRange(groupScroller.maxOffset(), groupedRows.size(), Math.max(1, visibleGroupRows.size()));
        if (groupArrowButtons == null) {
            return;
        }
        for (int slot = 0; slot < visibleGroupRows.size(); slot++) {
            GroupRow row = visibleGroupRows.get(slot);
            boolean fullyVisible = row.y >= top && row.y + row.height() <= bottom;
            if (!fullyVisible) {
                continue;
            }
            if (row.isPool()) {
                groupArrowButtons[slot].setControlVisible(true);
                // 3 px inside the pool row frame, keeping 2 px from its lines.
                groupArrowButtons[slot].moveControlY(row.y + 3);
                groupArrowButtons[slot].setText(KineticI18n.translatable(expandedPools.contains(row.poolIndex)
                        ? "gui.contentstudio.common.collapse_symbol"
                        : "gui.contentstudio.common.expand_symbol"));
                groupAddButtons[slot].setControlVisible(true);
                groupAddButtons[slot].moveControlY(row.y + 3);
                groupPoolEditButtons[slot].setControlVisible(true);
                groupPoolEditButtons[slot].moveControlY(row.y + 3);
                groupPoolDeleteButtons[slot].setControlVisible(true);
                groupPoolDeleteButtons[slot].setEnabled(poolCountForGroupedLayout() > 0);
                groupPoolDeleteButtons[slot].moveControlY(row.y + 3);
            } else {
                groupEntryDeleteButtons[slot].setControlVisible(true);
                groupEntryDeleteButtons[slot].moveControlY(row.y + 5);
                groupEntryEditButtons[slot].setControlVisible(true);
                groupEntryEditButtons[slot].moveControlY(row.y + 5);
            }
        }
    }

    private GroupRow visibleGroupRow(int slot) {
        return slot >= 0 && slot < visibleGroupRows.size() ? visibleGroupRows.get(slot) : null;
    }

    private void toggleGroupedPool(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row == null || !row.isPool()) {
            return;
        }
        if (!expandedPools.add(row.poolIndex)) {
            expandedPools.remove(row.poolIndex);
        }
        refreshGroupedLayout();
    }

    private void addGroupedReward(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row != null && row.isPool()) {
            openGroupedItemPicker(row.poolIndex);
        }
    }

    private void editGroupedPool(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row != null && row.isPool() && isAttached()) {
            openChild(new LootPoolEditPage(this, row.poolIndex, groupedPool(row.poolIndex).deepCopy()));
        }
    }

    private void confirmGroupedPoolDelete(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row == null || !row.isPool()) {
            return;
        }
        int poolIndex = row.poolIndex;
        openDialog(
                KineticI18n.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.title"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.desc"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.confirm.delete"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.confirm.cancel"),
                () -> {
                    if (deletePoolFromEditor(poolIndex)) {
                        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.pool.deleted"));
                    }
                },
                () -> { }
        );
    }

    private void editGroupedEntry(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row != null && !row.isPool() && row.visual != null && isAttached()) {
            openChild(new LootEntryEditPage(this, row.visual));
        }
    }

    private void deleteGroupedEntry(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row != null && !row.isPool() && row.visual != null) {
            deleteEntryFromEditor(row.visual);
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.entry.deleted"));
        }
    }

    private void addGroupedPool() {
        if (selectedEntry == null) {
            return;
        }
        JsonArray pools = getPools();
        pools.add(createDefaultPool());
        int poolIndex = pools.size() - 1;
        expandedPools.add(poolIndex);
        markDirtyAndRebuild(null);
        if (isAttached()) {
            openChild(new LootPoolEditPage(this, poolIndex, groupedPool(poolIndex).deepCopy()));
        }
    }

    private void openGroupedItemPicker(int poolIndex) {
        if (!isAttached()) {
            return;
        }
        KineticSelectors.openItemSelector(selection -> {
            if (selection == null || !selection.isItem()) {
                return;
            }
            ResourceLocation id = KineticRegistries.items().id(selection.stack().getItem());
            if (id == null) {
                return;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", id.toString());
            entry.addProperty("weight", 1);
            JsonObject setCount = new JsonObject();
            setCount.addProperty("function", "minecraft:set_count");
            setCount.addProperty("count", 1);
            JsonArray functions = new JsonArray();
            functions.add(setCount);
            entry.add("functions", functions);
            LootJsonEditUtil.setItemNbt(entry, selection.stack());
            groupedPool(poolIndex).getAsJsonArray("entries").add(entry);
            expandedPools.add(poolIndex);
            markDirtyAndRebuild(entry);
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.drop.added"));
        });
    }

    void applyEntryEdit(DropVisual original, JsonObject updated) {
        if (original == null || original.parentEntries == null || updated == null) {
            return;
        }
        original.parentEntries.set(original.entryIndex, updated);
        expandedPools.add(Math.max(0, original.poolIndex - 1));
        markDirtyAndRebuild(updated);
    }

    void deleteEntryFromEditor(DropVisual original) {
        if (original == null || original.parentEntries == null) {
            return;
        }
        original.parentEntries.remove(original.entryIndex);
        expandedPools.add(Math.max(0, original.poolIndex - 1));
        markDirtyAndRebuild(null);
    }

    void applyPoolEdit(int poolIndex, JsonObject updated) {
        JsonArray pools = getPools();
        if (updated == null || poolIndex < 0 || poolIndex >= pools.size()) {
            return;
        }
        pools.set(poolIndex, updated);
        expandedPools.add(poolIndex);
        markDirtyAndRebuild(null);
    }

    boolean deletePoolFromEditor(int poolIndex) {
        JsonArray pools = getPools();
        if (poolIndex < 0 || poolIndex >= pools.size()) {
            return false;
        }
        pools.remove(poolIndex);
        Set<Integer> adjusted = new HashSet<>();
        for (int index : expandedPools) {
            if (index < poolIndex) adjusted.add(index);
            else if (index > poolIndex) adjusted.add(index - 1);
        }
        expandedPools.clear();
        expandedPools.addAll(adjusted);
        if (!pools.isEmpty()) {
            expandedPools.add(Math.max(0, Math.min(poolIndex, pools.size() - 1)));
        }
        markDirtyAndRebuild(null);
        return true;
    }

    int editorMode() {
        return mode;
    }

    Component selectedTableName() {
        if (selectedEntry == null) {
            return Component.empty();
        }
        String display = getDisplayName(selectedEntry);
        return display.equals(selectedEntry.lootTableId()) ? idComponent(display) : nameComponent(display);
    }

    List<Component> groupedPoolTooltip(int poolIndex) {
        List<Component> tooltip = new ArrayList<>();
        JsonObject pool = groupedPool(poolIndex);
        tooltip.add(KineticI18n.translatable("gui.contentstudio.loot.loots.pool.number", numberComponent(poolIndex + 1)));
        tooltip.add(KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.rolls", numberComponent(readRollsForEditor(pool)))));
        String bonusRolls = readNumberRange(pool.get("bonus_rolls"));
        if (!bonusRolls.equals("-")) {
            tooltip.add(KineticI18n.translatable("gui.contentstudio.loot.loots.drop.bonus_rolls", numberComponent(bonusRolls)));
        }
        tooltip.add(KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.pool.entry_count", numberComponent(pool.getAsJsonArray("entries").size()))));
        appendReadableConditions(tooltip, pool.get("conditions"));
        appendReadableFunctions(tooltip, pool.get("functions"));
        return tooltip;
    }

    protected void updateButtons() {
        boolean hasEntry = selectedEntry != null;
        boolean hasDrop = selectedDrop != null;
        boolean specialPanel = isSpecialPanelActive();
        boolean saving = isSavingBatch();
        if (saveButton != null) {
            saveButton.setControlVisible(!specialPanel);
            saveButton.setEnabled(!specialPanel && hasEntry && !saving);
        }
        if (resetButton != null) {
            resetButton.setControlVisible(!specialPanel);
            resetButton.setEnabled(!specialPanel && hasEntry && !saving);
        }
        if (addPoolHeaderButton != null) {
            addPoolHeaderButton.setControlVisible(!specialPanel);
            addPoolHeaderButton.setEnabled(!specialPanel && hasEntry);
        }
        if (deleteButton != null) deleteButton.setEnabled(!specialPanel && hasDrop);
        if (applyButton != null) applyButton.setEnabled(!specialPanel && hasDrop);
        if (fireButton != null) fireButton.setEnabled(!specialPanel && hasEntry);
        if (overrideModeButton != null) {
            overrideModeButton.setControlVisible(!specialPanel);
            overrideModeButton.setEnabled(!specialPanel && hasEntry);
            overrideModeButton.setValue(overrideMode);
        }
        if (usesGroupedLayout()) {
            setGroupedButtonsActive(!specialPanel && hasEntry);
            if (specialPanel) {
                for (int i = 0; i < GROUP_BUTTON_SLOTS; i++) {
                    hideGroupSlot(i);
                }
            } else {
                updateVisibleGroupedRows();
            }
        }
        updateSpecialButtons();
    }

    private int visibleDropRows() {
        return DROP_H / DROP_ROW_H;
    }

    private int dropListStartY() {
        return DROP_Y + (DROP_H - visibleDropRows() * DROP_ROW_H) / 2;
    }

    private int dropListHeight() {
        return visibleDropRows() * DROP_ROW_H;
    }

    @Override
    protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        KineticTheme.panel(g, 0, 0, V_WIDTH, V_HEIGHT);
        drawPanel(g, LEFT_X - 2, RIGHT_Y - 2, TARGET_WIDTH + 14, RIGHT_H + 4);
        drawPanel(g, RIGHT_X - 2, RIGHT_Y - 2, RIGHT_W + 4, RIGHT_H + 4);
        if (isSpecialPanelActive()) {
            drawPanel(g, RIGHT_X + 4, GROUP_Y + 2, RIGHT_W - 8, GROUP_H - 4);
        } else if (usesGroupedLayout()) {
            drawPanel(g, RIGHT_X + 4, GROUP_Y + 2, RIGHT_W - 8, GROUP_H - 4);
            updateVisibleGroupedRows();
            renderGroupedRowBackgrounds(g, mx, my);
        } else {
            drawPanel(g, RIGHT_X + 4, EDIT_Y + 2, RIGHT_W - 8, EDIT_H);
            drawPanel(g, RIGHT_X + 4, DROP_Y + 2, RIGHT_W - 8, DROP_H - 4);
        }
    }

    private void drawPanel(KineticGraphics g, int x, int y, int w, int h) {
        KineticTheme.stateSurface(g, x, y, w, h, KineticTheme.Surface.PANEL_ALT, true, false, false);
    }

    @Override
    protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
        deferredTooltip = null;
        updateTargetScrollLimit();
        renderTargets(g, mx, my);
        renderTopInfo(g, mx, my);
        if (isSpecialPanelActive()) {
            renderSpecialPanel(g, mx, my);
        } else if (usesGroupedLayout()) {
            renderGroupedPanel(g, mx, my);
        } else {
            renderEditLabels(g, mx, my);
            renderEditorItemPreview(g, mx, my);
            renderDropPanel(g, mx, my);
        }
        if (selectedEntry == null) {
            int centerY = usesGroupedLayout() ? GROUP_Y + GROUP_H / 2 : DROP_Y + DROP_H / 2;
            g.scrollingTextCentered(KineticI18n.translatable("gui.contentstudio.loot.loots.no_selection"), RIGHT_X + RIGHT_W / 2, centerY, RIGHT_W - 24, 0xFFFFAA00, true);
        }
    }

    protected abstract void renderTargets(KineticGraphics g, int mx, int my);

    protected abstract LootEntryInfo targetEntryAt(double mx, double my);

    protected abstract String getDisplayName(LootEntryInfo entry);

    // The title/loading region stops before the first visible header action.
    protected int headerTextRight() {
        return usesGroupedLayout() ? HEADER_POOL_BUTTON_X : HEADER_RESET_BUTTON_X;
    }

    private void renderTopInfo(KineticGraphics g, int mx, int my) {
        int maxWidth = headerTextRight() - (RIGHT_X + 6) - TEXT_GAP;
        g.scrollingText(title(), RIGHT_X + 6, RIGHT_Y + 6, maxWidth, 0xFFFFAA00, false);
        if (detailLoading) {
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.loading"), RIGHT_X + 6, RIGHT_Y + 25, maxWidth, 0xFFFFD75F, false);
        }
    }

    private void renderEditLabels(KineticGraphics g, int mx, int my) {
        int x0 = RIGHT_X + 10;
        int y = EDIT_Y + 10;
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.item"), x0, y, 48 - TEXT_GAP, 0xFFFFAA00, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.chance"), x0 + 48, y, 44 - TEXT_GAP, 0xFFFFAA00, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.rolls"), x0 + 92, y, 44 - TEXT_GAP, 0xFFFFAA00, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.count_min"), x0 + 136, y, 44 - TEXT_GAP, 0xFFFFAA00, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.count_max"), x0 + 180, y, 44 - TEXT_GAP, 0xFFFFAA00, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.weight"), x0 + 224, y, 44 - TEXT_GAP, 0xFFFFAA00, false);
        renderModeEditLabels(g, x0, y);

        if (my >= y - 2 && my <= y + 11) {
            String tooltipKey;
            if (mx >= x0 && mx < x0 + 44) tooltipKey = "gui.contentstudio.loot.loots.tip.item_id";
            else if (mx >= x0 + 48 && mx < x0 + 88) tooltipKey = "gui.contentstudio.loot.loots.tip.chance";
            else if (mx >= x0 + 92 && mx < x0 + 132) tooltipKey = rollsTooltipKey();
            else if (mx >= x0 + 136 && mx < x0 + 176) tooltipKey = "gui.contentstudio.loot.loots.tip.count_min";
            else if (mx >= x0 + 180 && mx < x0 + 220) tooltipKey = "gui.contentstudio.loot.loots.tip.count_max";
            else if (mx >= x0 + 224 && mx < x0 + 264) tooltipKey = "gui.contentstudio.loot.loots.tip.weight";
            else tooltipKey = modeLabelTooltipKey(mx, x0);
            if (tooltipKey != null) {
                deferredTooltip = List.of(KineticI18n.translatable(tooltipKey));
            }
        }
    }

    protected void renderModeEditLabels(KineticGraphics g, int x0, int y) {
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.looting_min"), x0 + 268, y, 44 - TEXT_GAP, 0xFFFFAA00, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.loot.loots.edit.looting_max"), x0 + 312, y, RIGHT_X + RIGHT_W - 10 - (x0 + 312) - TEXT_GAP, 0xFFFFAA00, false);
    }

    protected String modeLabelTooltipKey(int mx, int x0) {
        if (mx >= x0 + 268 && mx < x0 + 308) {
            return "gui.contentstudio.loot.loots.tip.looting_min";
        }
        if (mx >= x0 + 312 && mx < x0 + 352) {
            return "gui.contentstudio.loot.loots.tip.looting_max";
        }
        return null;
    }

    private void renderEditorItemPreview(KineticGraphics g, int mx, int my) {
        int x = RIGHT_X + 18;
        int y = EDIT_Y + 25;
        ItemStack stack = editorPreviewStack();
        boolean hovered = mx >= x && mx <= x + EDIT_ICON && my >= y && my <= y + EDIT_ICON;
        drawCheckerboard(g, x, y, EDIT_ICON, EDIT_ICON);
        KineticTheme.stateOutline(g, x, y, EDIT_ICON, EDIT_ICON, true, hovered, false);
        if (!stack.isEmpty()) {
            KineticTheme.item(g, stack, x, y, EDIT_ICON, 0.875F, true);
        }
        if (hovered) {
            if (!stack.isEmpty()) {
                deferredTooltip = List.of(
                        KineticI18n.styled("gui.contentstudio.loot.style.name", stack.getHoverName()),
                        idComponent(itemBox == null ? "" : itemBox.textValue())
                );
            } else {
                deferredTooltip = List.of(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pick_item"));
            }
        }
    }

    private ItemStack editorPreviewStack() {
        if (itemBox == null) {
            return ItemStack.EMPTY;
        }
        String itemId = itemBox.textValue().trim();
        if (pendingPickedItemStackId != null
                && pendingPickedItemStackId.equals(itemId)
                && (pendingPickedTargetEntry == null
                || selectedDrop != null && pendingPickedTargetEntry == selectedDrop.entry)) {
            return pendingPickedItemStack.copy();
        }
        try {
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(itemId));
            if (item != null && item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (selectedDrop != null && itemId.equals(safeString(selectedDrop.entry.get("name")))) {
                    LootJsonEditUtil.applyItemNbt(selectedDrop.entry, stack);
                }
                return stack;
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    private void clearPendingPickedItem() {
        pendingPickedItemId = null;
        pendingPickedItemStackId = null;
        pendingPickedItemStack = ItemStack.EMPTY;
        pendingPickedTargetEntry = null;
    }

    private void renderGroupedPanel(KineticGraphics g, int mx, int my) {
        if (selectedEntry == null || currentRoot == null) {
            return;
        }
        if (groupedRows.isEmpty()) {
            g.scrollingTextCentered(KineticI18n.translatable("gui.contentstudio.loot.loots.no_drops"), RIGHT_X + RIGHT_W / 2, GROUP_Y + GROUP_H / 2, RIGHT_W - 24, 0xFFFFAA00, true);
            return;
        }
        g.scissor(RIGHT_X + 4, GROUP_Y + 6, RIGHT_X + RIGHT_W - 8, GROUP_Y + GROUP_H - 6);
        try {
            for (GroupRow row : visibleGroupRows) {
                if (row.isPool()) {
                    renderGroupedPoolRow(g, row, mx, my);
                    groupScroller.renderSelectionFlash(g, groupedRows.indexOf(row), RIGHT_X + 8, row.y, RIGHT_W - 22, GROUP_POOL_H - GROUP_ROW_GAP);
                } else {
                    renderGroupedEntryRow(g, row, mx, my);
                    groupScroller.renderSelectionFlash(g, groupedRows.indexOf(row), RIGHT_X + 10, row.y, RIGHT_W - 24, GROUP_ENTRY_H - GROUP_ROW_GAP);
                }
            }
        } finally {
            g.endScissor();
        }
        groupScroller.render(g, mx, my, GROUP_SCROLLBAR_X + 2, GROUP_Y + 6, 4, GROUP_H - 12, 18);
    }

    private void renderGroupedRowBackgrounds(KineticGraphics g, int mx, int my) {
        g.scissor(RIGHT_X + 4, GROUP_Y + 6, RIGHT_X + RIGHT_W - 8, GROUP_Y + GROUP_H - 6);
        try {
            for (GroupRow row : visibleGroupRows) {
                if (row.isPool()) {
                int x = RIGHT_X + 8;
                int width = RIGHT_W - 22;
                boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + GROUP_POOL_H;
                int drawHeight = GROUP_POOL_H - GROUP_ROW_GAP;
                KineticTheme.stateSurface(
                        g, x, row.y, width, drawHeight,
                        KineticTheme.Surface.PANEL_ALT, false, hover, false
                );
            } else {
                int x = RIGHT_X + 10;
                int width = RIGHT_W - 24;
                int drawHeight = GROUP_ENTRY_H - GROUP_ROW_GAP;
                boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + drawHeight;
                KineticTheme.stateSurface(
                        g, x, row.y, width, drawHeight,
                        KineticTheme.Surface.PANEL_ALT, false, hover, row.visual.loadError
                );
                }
            }
        } finally {
            g.endScissor();
        }
    }

    private void renderGroupedPoolRow(KineticGraphics g, GroupRow row, int mx, int my) {
        int x = RIGHT_X + 8;
        int width = RIGHT_W - 22;
        boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + GROUP_POOL_H;
        JsonObject pool = groupedPool(row.poolIndex);
        int entryCount = 0;
        for (DropVisual visual : dropVisuals) {
            if (visual.poolIndex == row.poolIndex + 1) entryCount++;
        }
        Component line = KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.pool.header",
                        numberComponent(row.poolIndex + 1),
                        numberComponent(readRollsForEditor(pool)),
                        numberComponent(entryCount)));
        g.scissor(RIGHT_X + 34,
                row.y + 1,
                GROUP_POOL_ACTION_X - TEXT_GAP,
                row.y + GROUP_POOL_H - 2
        );
        g.scrollingText(line, RIGHT_X + 34, row.y + 8, GROUP_POOL_ACTION_X - TEXT_GAP - (RIGHT_X + 34), 0xFFFFFFFF, false);
        g.endScissor();
        if (hover && mx >= RIGHT_X + 32 && mx < RIGHT_X + RIGHT_W - 145) {
            deferredTooltip = groupedPoolTooltip(row.poolIndex);
        }
    }

    private void renderGroupedEntryRow(KineticGraphics g, GroupRow row, int mx, int my) {
        DropVisual visual = row.visual;
        int x = RIGHT_X + 10;
        int width = RIGHT_W - 24;
        int drawHeight = GROUP_ENTRY_H - GROUP_ROW_GAP;
        boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + drawHeight;
        renderDropIcon(g, visual, row.y, hover);
        int textX = RIGHT_X + 39;
        int textRight = GROUP_ENTRY_ACTION_X - TEXT_GAP;
        Component firstLine = visual.loadError
                ? visual.name
                : Component.empty()
                .append(visual.probability == null ? visual.chance : visual.probability)
                .append(Component.literal("    "))
                .append(visual.count);
        Component secondLine = visual.loadError ? loadErrorIdComponent(visual) : buildGroupedSecondLine(visual);
        g.scissor(textX,
                row.y + 1,
                textRight,
                row.y + drawHeight - 1
        );
        g.scrollingText(firstLine, textX, row.y + 4, textRight - textX, 0xFFFFFFFF, false);
        g.scrollingText(secondLine, textX, row.y + 16, textRight - textX, 0xFFFFFFFF, false);
        g.endScissor();
        if (hover && mx < RIGHT_X + RIGHT_W - 112) {
            deferredTooltip = buildGroupedDropTooltip(visual);
        }
    }

    private Component buildGroupedSecondLine(DropVisual visual) {
        List<Component> visibleDetails = new ArrayList<>();
        for (Component detail : visual.details) {
            if (isEntryOwnedDetail(detail) && !detail.getString().isBlank()) {
                visibleDetails.add(detail);
            }
        }
        MutableComponent line = Component.empty();
        for (int i = 0; i < Math.min(3, visibleDetails.size()); i++) {
            if (i > 0) line.append(Component.literal("    "));
            line.append(visibleDetails.get(i));
        }
        return line;
    }

    private List<Component> buildGroupedDropTooltip(DropVisual visual) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(visual.name);
        tooltip.add(visual.loadError ? loadErrorIdComponent(visual) : idComponent(visual.displayId));
        tooltip.add(visual.probability == null ? visual.chance : visual.probability);
        tooltip.add(visual.count);
        for (Component detail : visual.details) {
            if (isEntryOwnedDetail(detail)) {
                tooltip.add(detail);
            }
        }
        return tooltip;
    }

    private boolean isEntryOwnedDetail(Component detail) {
        if (detail.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            return !key.equals("gui.contentstudio.loot.loots.drop.pool")
                    && !key.equals("gui.contentstudio.loot.loots.drop.rolls")
                    && !key.equals("gui.contentstudio.loot.loots.drop.bonus_rolls")
                    && !key.equals("gui.contentstudio.loot.loots.function.set_count");
        }
        return true;
    }

    private void renderDropPanel(KineticGraphics g, int mx, int my) {
        if (dropVisuals.isEmpty()) {
            g.scrollingTextCentered(KineticI18n.translatable("gui.contentstudio.loot.loots.no_drops"), RIGHT_X + RIGHT_W / 2, DROP_Y + DROP_H / 2, RIGHT_W - 24, 0xFFFFAA00, true);
            return;
        }
        int startY = dropListStartY();
        int listH = dropListHeight();
        int visible = visibleDropRows();
        double smoothDropScroll = dropScroller.smoothOffset();
        int smoothDropRow = (int) Math.floor(smoothDropScroll + 1.0E-6D);
        int dropShift = (int) Math.round(
                (smoothDropScroll - smoothDropRow) * DROP_ROW_H
        );
        int end = Math.min(dropVisuals.size(), smoothDropRow + visible + 1);
        g.scissor(RIGHT_X + 8,
                startY,
                RIGHT_X + RIGHT_W - 8,
                startY + listH
        );
        for (int i = smoothDropRow; i < end; i++) {
            DropVisual visual = dropVisuals.get(i);
            int rowY = startY + (i - smoothDropRow) * DROP_ROW_H - dropShift;
            boolean hover = mx >= RIGHT_X + 10 && mx <= RIGHT_X + RIGHT_W - 16 && my >= rowY && my <= rowY + DROP_ROW_H - 4;
            boolean selected = selectedDrop == visual;
            KineticTheme.stateSurface(
                    g,
                    RIGHT_X + 10,
                    rowY,
                    RIGHT_W - 26,
                    DROP_ROW_H - 4,
                    KineticTheme.Surface.PANEL_ALT,
                    selected,
                    hover,
                    visual.loadError
            );
            renderDropIcon(g, visual, rowY, hover);
            int textX = RIGHT_X + 44;
            int lineOneY = rowY + 7;
            int lineTwoY = rowY + 20;
            Component firstLine = visual.loadError
                    ? visual.name
                    : Component.empty()
                    .append(visual.chance)
                    .append(Component.literal("    "))
                    .append(visual.count);
            Component secondLine = visual.loadError ? loadErrorIdComponent(visual) : buildDropSecondLine(visual);
            g.scissor(textX,
                    rowY + 2,
                    DROP_SCROLLBAR_X - TEXT_GAP,
                    rowY + DROP_ROW_H - 5
            );
            g.scrollingText(firstLine, textX, lineOneY, DROP_SCROLLBAR_X - TEXT_GAP - textX, 0xFFFFFFFF, false);
            g.scrollingText(secondLine, textX, lineTwoY, DROP_SCROLLBAR_X - TEXT_GAP - textX, 0xFFFFFFFF, false);
            g.endScissor();
            if (hover) {
                deferredTooltip = buildDropTooltip(visual);
            }
            dropScroller.renderSelectionFlash(g, i, RIGHT_X + 10, rowY, RIGHT_W - 26, DROP_ROW_H - 4);
        }
        g.endScissor();
        dropScroller.render(g, mx, my, DROP_SCROLLBAR_X, startY + 1, 4, listH - 2, 18);
    }

    private void renderDropIcon(KineticGraphics g, DropVisual visual, int rowY, boolean hovered) {
        int x = RIGHT_X + 13;
        int y = rowY + (GROUP_ENTRY_H - GROUP_ROW_GAP - ICON_CELL) / 2;
        ItemStack stack = visual.stack == null ? ItemStack.EMPTY : visual.stack;
        LootCheckerboard.draw(g, x, y, ICON_CELL, ICON_CELL);
        KineticTheme.stateOutline(g, x, y, ICON_CELL, ICON_CELL, false, hovered, visual.loadError);
        if (!stack.isEmpty()) {
            renderLargeItem(g, stack, x + 3, y + 3, ICON_CELL - 6);
        }
    }

    protected void drawCheckerboard(KineticGraphics g, int x, int y, int w, int h) {
        KineticTheme.itemGrid(g, x, y, w, h);
    }

    protected void renderLargeItem(KineticGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float scale = size / 16.0f;
        g.push();
        g.translate(x, y);
        g.scale(scale, scale);
        g.item(stack, 0, 0);
        g.itemDecorations(stack, 0, 0);
        g.pop();
    }

    private Component buildDropSecondLine(DropVisual visual) {
        List<Component> important = new ArrayList<>();
        List<Component> normal = new ArrayList<>();
        for (Component detail : visual.details) {
            if (detail.getString().isBlank()) {
                continue;
            }
            if (isImportantDropDetail(detail)) {
                important.add(detail);
            } else {
                normal.add(detail);
            }
        }

        List<Component> result = new ArrayList<>();
        appendLimited(result, important);
        appendLimited(result, normal);
        MutableComponent line = Component.empty();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                line.append(Component.literal("    "));
            }
            line.append(result.get(i));
        }
        return line;
    }

    private void appendLimited(List<Component> result, List<Component> source) {
        for (Component value : source) {
            if (result.size() >= 3) {
                return;
            }
            result.add(value);
        }
    }

    private boolean isImportantDropDetail(Component detail) {
        if (detail.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            return key.startsWith("gui.contentstudio.loot.loots.condition.")
                    || key.startsWith("gui.contentstudio.loot.loots.function.");
        }
        return false;
    }

    private List<Component> buildDropTooltip(DropVisual visual) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(visual.name);
        tooltip.add(visual.loadError ? loadErrorIdComponent(visual) : idComponent(visual.displayId));
        tooltip.add(visual.chance);
        tooltip.add(visual.count);
        tooltip.addAll(visual.details);
        return tooltip;
    }

    protected MutableComponent nameComponent(String text) {
        return KineticI18n.styled("gui.contentstudio.loot.style.name", text == null ? "" : text);
    }

    protected MutableComponent idComponent(String text) {
        return KineticI18n.styled("gui.contentstudio.loot.style.id", text == null ? "" : text);
    }

    protected MutableComponent numberComponent(Object value) {
        return KineticI18n.styled("gui.contentstudio.loot.style.number", value);
    }

    private MutableComponent valueComponent(String value) {
        return KineticI18n.styled("gui.contentstudio.loot.style.value", value == null ? "" : value);
    }

    private MutableComponent conditionLine(String key, Object... args) {
        return KineticI18n.styled("gui.contentstudio.loot.style.condition", KineticI18n.translatable(key, args));
    }

    private MutableComponent functionLine(String key, Object... args) {
        return KineticI18n.styled("gui.contentstudio.loot.style.function", KineticI18n.translatable(key, args));
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原逻辑在控件分发前执行：保存中吞掉点击并刷新目标滚动上限 / Ran before control dispatch originally: swallow clicks while saving and refresh the target scroll limit.
        if (isSavingBatch()) {
            return true;
        }
        updateTargetScrollLimit();
        // 分组行上的点击多由行内按钮处理，这里只记录中键跳转目标，不消费点击 / Grouped-row clicks are mostly handled by row buttons; only record the middle-click target here without consuming the click.
        if (usesGroupedLayout() && !isSpecialPanelActive()
                && input.x() >= RIGHT_X + 4 && input.x() < RIGHT_X + RIGHT_W - 8
                && input.y() >= GROUP_Y + 6 && input.y() < GROUP_Y + GROUP_H - 6) {
            for (GroupRow row : visibleGroupRows) {
                if (input.y() >= row.y && input.y() < row.y + row.height()) {
                    lastClickedGroupPool = row.poolIndex;
                    lastClickedGroupVisual = row.visual;
                    break;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mx = input.x();
        double my = input.y();
        int btn = input.rawButton();
        if (!KineticMouseButtons.isPrimary(btn) && isSpecialPanelActive() && handleSpecialPanelClick(mx, my, btn)) {
            return true;
        }
        if (KineticMouseButtons.isPrimary(btn)) {
            LootEntryInfo targetEntry = targetEntryAt(mx, my);
            if (targetEntry != null) {
                if (handleSpecialTargetClick(targetEntry)) {
                    return true;
                }
                selectEntry(targetEntry);
                return true;
            }
            if (targetScroller.beginDrag(mx, my, input.button(), TARGET_SCROLLBAR_X, targetAreaY() + 1, TARGET_SCROLLBAR_WIDTH, targetAreaHeight() - 2, 24)) {
                return true;
            }
            if (isSpecialPanelActive()) {
                return handleSpecialPanelClick(mx, my, btn);
            }
            if (usesGroupedLayout()) {
                if (groupScroller.beginDrag(mx, my, input.button(), GROUP_SCROLLBAR_X + 2, GROUP_Y + 6, 4, GROUP_H - 12, 18, 2)) {
                    updateVisibleGroupedRows();
                    return true;
                }
                return false;
            }
            int editIconX = RIGHT_X + 18;
            int editIconY = EDIT_Y + 25;
            if (mx >= editIconX && mx <= editIconX + EDIT_ICON && my >= editIconY && my <= editIconY + EDIT_ICON) {
                openItemPicker();
                return true;
            }
            int dropStartY = dropListStartY();
            int dropListH = dropListHeight();
            if (dropScroller.beginDrag(mx, my, input.button(), DROP_SCROLLBAR_X, dropStartY + 1, 4, dropListH - 2, 18)) {
                return true;
            }
            int visible = visibleDropRows();
            double smoothDropScroll = dropScroller.smoothOffset();
            int smoothDropRow = (int) Math.floor(smoothDropScroll + 1.0E-6D);
            int dropShift = (int) Math.round(
                    (smoothDropScroll - smoothDropRow) * DROP_ROW_H
            );
            int endDrop = Math.min(dropVisuals.size(), smoothDropRow + visible + 1);
            for (int i = smoothDropRow; i < endDrop; i++) {
                int rowY = dropStartY + (i - smoothDropRow) * DROP_ROW_H - dropShift;
                if (mx >= RIGHT_X + 10 && mx <= RIGHT_X + RIGHT_W - 16 && my >= rowY && my <= rowY + DROP_ROW_H - 4) {
                    if (failsToCommitSelectedDropEditorChanges()) {
                        return true;
                    }
                    selectedDrop = dropVisuals.get(i);
                    loadSelectedDropIntoFields();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mx = input.x();
        double my = input.y();
        int btn = input.rawButton();
        double dx = input.deltaX();
        double dy = input.deltaY();
        if (targetScroller.drag(my, targetAreaY() + 1, targetAreaHeight() - 2, 24)) {
            return true;
        }
        if (isSpecialPanelActive() && handleSpecialPanelDragged(mx, my, btn, dx, dy)) {
            return true;
        }
        if (dropScroller.drag(my, dropListStartY() + 1, dropListHeight() - 2, 18)) {
            return true;
        }
        if (groupScroller.drag(my, GROUP_Y + 6, GROUP_H - 12, 18)) {
            updateVisibleGroupedRows();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        double mx = input.x();
        double my = input.y();
        int btn = input.rawButton();
        targetScroller.release(input.button());
        dropScroller.release(input.button());
        groupScroller.release(input.button());
        if (isSpecialPanelActive()) {
            handleSpecialPanelReleased(mx, my, btn);
        }
        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mx = input.x();
        double my = input.y();
        double delta = input.deltaY();
        updateTargetScrollLimit();
        if (mx >= LEFT_X && mx <= LEFT_X + TARGET_WIDTH + 10 && my >= targetAreaY() && my <= targetAreaY() + targetAreaHeight() && targetScroller.canScroll()) {
            targetScroller.scroll(delta);
            return true;
        }
        if (isSpecialPanelActive() && handleSpecialPanelScrolled(mx, my, delta)) {
            return true;
        }
        if (!isSpecialPanelActive() && usesGroupedLayout() && mx >= RIGHT_X && mx <= RIGHT_X + RIGHT_W
                && my >= GROUP_Y && my <= GROUP_Y + GROUP_H && groupScroller.canScroll()) {
            groupScroller.scroll(delta);
            updateVisibleGroupedRows();
            return true;
        }
        if (!usesGroupedLayout() && mx >= RIGHT_X && mx <= RIGHT_X + RIGHT_W && my >= DROP_Y && my <= DROP_Y + DROP_H && dropScroller.canScroll()) {
            dropScroller.scroll(delta);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCloseRequested() {
        return isSavingBatch();
    }

    @Override
    protected boolean onKeyPress(KeyInput input) {
        if (isSavingBatch()) {
            return true;
        }
        if (input.is(KineticKeyBindings.Key.ESCAPE)) {
            commitDraft();
            close();
            return true;
        }
        return false;
    }

    @Override
    protected void renderTooltips(int smx, int smy) {
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) {
            showTooltip(deferredTooltip);
        }
    }
}
