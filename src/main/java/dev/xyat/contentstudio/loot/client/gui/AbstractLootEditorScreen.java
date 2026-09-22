package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.ToggleButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
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

public abstract class AbstractLootEditorScreen extends KineticScreen {
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
    protected static final int HEADER_H = 40;
    protected static final int EDIT_Y = RIGHT_Y + HEADER_H + 8;
    protected static final int EDIT_H = 74;
    protected static final int DROP_Y = EDIT_Y + EDIT_H + 8;
    protected static final int DROP_H = RIGHT_Y + RIGHT_H - DROP_Y - 4;
    protected static final int DROP_ROW_H = 36;
    protected static final int ICON_CELL = 22;
    protected static final int EDIT_ICON = 20;
    private static final int DROP_SCROLLBAR_X = RIGHT_X + RIGHT_W - 13;
    private static final int GROUP_Y = RIGHT_Y + HEADER_H + 4;
    private static final int GROUP_H = RIGHT_Y + RIGHT_H - GROUP_Y - 4;
    private static final int GROUP_POOL_H = 24;
    private static final int GROUP_ENTRY_H = 30;
    private static final int GROUP_ROW_GAP = 2;
    private static final int GROUP_BUTTON_SLOTS = 10;
    private static final int GROUP_SCROLLBAR_X = RIGHT_X + RIGHT_W - 13;

    protected final int mode;
    protected final Screen parentScreen;
    protected final List<LootEntryInfo> allEntries;
    protected List<LootEntryInfo> displayEntries;
    protected final List<DropVisual> dropVisuals = new ArrayList<>();
    private List<Component> infoLines = new ArrayList<>();
    protected List<Component> deferredTooltip = null;

    private KineticEditBox searchBox;
    private KineticEditBox itemBox;
    private KineticEditBox chanceBox;
    private KineticEditBox rollsBox;
    private KineticEditBox countMinBox;
    private KineticEditBox countMaxBox;
    private KineticEditBox weightBox;
    private KineticEditBox lootingMinBox;
    private KineticEditBox lootingMaxBox;
    private StateButton saveButton;
    private StateButton resetButton;
    private StateButton deleteButton;
    private StateButton applyButton;
    private StateButton addButton;
    private ToggleButton killedButton;
    private ToggleButton lootingButton;
    private ToggleButton fireButton;
    private ToggleButton overrideModeButton;
    private String searchQuery = "";

    protected LootEntryInfo selectedEntry;
    protected DropVisual selectedDrop;
    protected JsonObject currentRoot;
    private String selectedJson = "";
    private boolean selectedOverridden = false;
    protected boolean dirty = false;
    private boolean requirePlayerKill = false;
    private boolean enableLooting = false;
    private boolean enableFireSmelt = false;
    private boolean overrideMode = false;
    private JsonObject appendModeBackupRoot = null;
    private String pendingPickedItemId = null;
    private String pendingPickedItemStackId = null;
    private ItemStack pendingPickedItemStack = ItemStack.EMPTY;
    private JsonObject pendingPickedTargetEntry = null;
    protected double targetScroll = 0D;
    protected int maxTargetScroll = 0;
    protected double dropScroll = 0D;
    protected int maxDropScroll = 0;
    protected boolean draggingTargetScroll = false;
    private boolean draggingDropScroll = false;
    protected final KineticScroll.State targetScrollState = new KineticScroll.State();
    protected final KineticScroll.State dropScrollState = new KineticScroll.State();
    private StateButton addPoolHeaderButton;
    private StateButton[] groupArrowButtons;
    private StateButton[] groupAddButtons;
    private StateButton[] groupPoolEditButtons;
    private StateButton[] groupPoolDeleteButtons;
    private StateButton[] groupEntryDeleteButtons;
    private StateButton[] groupEntryEditButtons;
    private final List<GroupRow> groupedRows = new ArrayList<>();
    private final List<GroupRow> visibleGroupRows = new ArrayList<>();
    private final Set<Integer> expandedPools = new HashSet<>();
    private double groupScroll = 0D;
    private int maxGroupScroll = 0;
    private boolean draggingGroupScroll = false;
    private final KineticScroll.State groupScrollState = new KineticScroll.State();
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

    protected AbstractLootEditorScreen(int mode, List<LootEntryInfo> entries, String titleKey, Screen parentScreen) {
        super(Component.translatable(titleKey));
        setParentScreen(parentScreen);
        this.mode = mode;
        this.parentScreen = parentScreen;
        this.allEntries = new ArrayList<>(entries);
        this.displayEntries = new ArrayList<>(this.allEntries);
    }

    @Override
    protected void buildUi() {
        searchBox = addTextField(
                LEFT_X + 1, SEARCH_Y, TARGET_WIDTH - 2, Component.empty(),
                Component.translatable("gui.contentstudio.loot.loots.search_hint"), null,
                Component.translatable("gui.contentstudio.loot.loots.tip.search")
        );
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(this::onSearchChanged);

        addButton(
                RIGHT_X + RIGHT_W - 48, RIGHT_Y + 10, 44,
                Component.translatable("gui.contentstudio.loot.loots.back"),
                Component.translatable("gui.contentstudio.loot.loots.tip.back"),
                this::onClose
        );

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
            itemBox.setValue(pendingPickedItemId);
            pendingPickedItemId = null;
        }
        if (usesGroupedLayout()) {
            refreshGroupedLayout();
        }
    }

    private void initEditWidgets() {
        int x0 = RIGHT_X + 10;
        itemBox = addTextField(
                -1000, -1000, 1, Component.empty(), null, null,
                Component.translatable("gui.contentstudio.loot.loots.tip.item_id")
        );
        itemBox.setMaxLength(256);

        int fieldY = EDIT_Y + 26;
        chanceBox = numberBox(x0 + 48, fieldY, "gui.contentstudio.loot.loots.tip.chance", "1", NumericInputType.PROBABILITY);
        rollsBox = numberBox(x0 + 92, fieldY, rollsTooltipKey(), "1", NumericInputType.ROLL_RANGE);
        countMinBox = numberBox(x0 + 136, fieldY, "gui.contentstudio.loot.loots.tip.count_min", "1", NumericInputType.WHOLE_NUMBER);
        countMaxBox = numberBox(x0 + 180, fieldY, "gui.contentstudio.loot.loots.tip.count_max", "1", NumericInputType.WHOLE_NUMBER);
        weightBox = numberBox(x0 + 224, fieldY, "gui.contentstudio.loot.loots.tip.weight", "1", NumericInputType.WHOLE_NUMBER);
        lootingMinBox = numberBox(x0 + 268, fieldY, "gui.contentstudio.loot.loots.tip.looting_min", "0", NumericInputType.WHOLE_NUMBER);
        lootingMaxBox = numberBox(x0 + 312, fieldY, "gui.contentstudio.loot.loots.tip.looting_max", "0", NumericInputType.WHOLE_NUMBER);

        int by = EDIT_Y + 48;
        killedButton = addToggleButton(
                x0, by, 44, requirePlayerKill,
                Component.translatable("gui.contentstudio.loot.loots.killed_on"),
                Component.translatable("gui.contentstudio.loot.loots.killed_off"),
                Component.translatable("gui.contentstudio.loot.loots.tip.killed"),
                null, value -> requirePlayerKill = value
        );
        lootingButton = addToggleButton(
                x0 + 48, by, 44, enableLooting,
                Component.translatable("gui.contentstudio.loot.loots.looting_on"),
                Component.translatable("gui.contentstudio.loot.loots.looting_off"),
                Component.translatable("gui.contentstudio.loot.loots.tip.looting"),
                null, value -> enableLooting = value
        );
        fireButton = addToggleButton(
                x0 + 96, by, 44, enableFireSmelt,
                Component.translatable("gui.contentstudio.loot.loots.fire_on"),
                Component.translatable("gui.contentstudio.loot.loots.fire_off"),
                Component.translatable("gui.contentstudio.loot.loots.tip.fire"),
                null, value -> enableFireSmelt = value
        );
        int modeButtonX = modeButtonX(x0);
        int actionButtonX = actionButtonX(x0);
        overrideModeButton = addToggleButton(
                modeButtonX, by, 58, overrideMode,
                Component.translatable("gui.contentstudio.loot.loots.mode.override"),
                Component.translatable("gui.contentstudio.loot.loots.mode.append"),
                Component.translatable("gui.contentstudio.loot.loots.tip.override_mode"),
                value -> selectedEntry != null, value -> toggleOverrideMode()
        );

        applyButton = addButton(
                actionButtonX, by, 38, Component.translatable("gui.contentstudio.loot.loots.apply_drop"),
                Component.translatable("gui.contentstudio.loot.loots.tip.apply_drop"), this::applyEditToSelected
        );
        addButton = addButton(
                actionButtonX + 42, by, 38, Component.translatable("gui.contentstudio.loot.loots.add_drop"),
                Component.translatable("gui.contentstudio.loot.loots.tip.add_drop"), this::addDrop
        );
        deleteButton = addButton(
                actionButtonX + 84, by, 38, Component.translatable("gui.contentstudio.loot.loots.delete_drop"),
                Component.translatable("gui.contentstudio.loot.loots.tip.delete_drop"), this::deleteSelectedDrop
        );
        saveButton = addButton(
                RIGHT_X + RIGHT_W - 97, RIGHT_Y + 10, 44, Component.translatable("gui.contentstudio.loot.loots.save"),
                Component.translatable("gui.contentstudio.loot.loots.tip.save"), this::saveCurrentJson
        );
        resetButton = addButton(
                RIGHT_X + RIGHT_W - 148, RIGHT_Y + 10, 44, Component.translatable("gui.contentstudio.loot.loots.reset"),
                Component.translatable("gui.contentstudio.loot.loots.tip.reset"), this::openResetConfirmDialog
        );

        if (!showEntityEditControls()) {
            killedButton.setVisible(false);
            lootingButton.setVisible(false);
            fireButton.setVisible(false);
            lootingMinBox.setVisible(false);
            lootingMaxBox.setVisible(false);
        }
        if (usesGroupedLayout()) {
            hideInlineWidgets();
            overrideModeButton.setVisible(true);
            overrideModeButton.setX(RIGHT_X + RIGHT_W - 212);
            overrideModeButton.setY(RIGHT_Y + 10);
        }
    }

    protected boolean usesGroupedLayout() {
        return true;
    }

    private void hideInlineWidgets() {
        if (itemBox != null) itemBox.setVisible(false);
        if (chanceBox != null) chanceBox.setVisible(false);
        if (rollsBox != null) rollsBox.setVisible(false);
        if (countMinBox != null) countMinBox.setVisible(false);
        if (countMaxBox != null) countMaxBox.setVisible(false);
        if (weightBox != null) weightBox.setVisible(false);
        if (lootingMinBox != null) lootingMinBox.setVisible(false);
        if (lootingMaxBox != null) lootingMaxBox.setVisible(false);
        if (killedButton != null) killedButton.setVisible(false);
        if (lootingButton != null) lootingButton.setVisible(false);
        if (fireButton != null) fireButton.setVisible(false);
        if (applyButton != null) applyButton.setVisible(false);
        if (addButton != null) addButton.setVisible(false);
        if (deleteButton != null) deleteButton.setVisible(false);
    }

    private void initGroupedWidgets() {
        addPoolHeaderButton = addButton(
                RIGHT_X + RIGHT_W - 276, RIGHT_Y + 10, 58,
                Component.translatable("gui.contentstudio.loot.loots.pool.add"),
                Component.translatable("gui.contentstudio.loot.loots.tip.pool.add"),
                this::addGroupedPool
        );

        groupArrowButtons = new StateButton[GROUP_BUTTON_SLOTS];
        groupAddButtons = new StateButton[GROUP_BUTTON_SLOTS];
        groupPoolEditButtons = new StateButton[GROUP_BUTTON_SLOTS];
        groupPoolDeleteButtons = new StateButton[GROUP_BUTTON_SLOTS];
        groupEntryDeleteButtons = new StateButton[GROUP_BUTTON_SLOTS];
        groupEntryEditButtons = new StateButton[GROUP_BUTTON_SLOTS];
        for (int i = 0; i < GROUP_BUTTON_SLOTS; i++) {
            int slot = i;
            groupArrowButtons[i] = addButton(
                    RIGHT_X + 10, -1000, 18, Component.translatable("gui.contentstudio.common.expand_symbol"),
                    Component.translatable("gui.contentstudio.loot.loots.tip.pool.expand"), () -> toggleGroupedPool(slot)
            );
            groupAddButtons[i] = addButton(
                    RIGHT_X + RIGHT_W - 141, -1000, 44, Component.translatable("gui.contentstudio.loot.loots.pool.add_reward"),
                    Component.translatable("gui.contentstudio.loot.loots.tip.pool.add_reward"), () -> addGroupedReward(slot)
            );
            groupPoolEditButtons[i] = addButton(
                    RIGHT_X + RIGHT_W - 93, -1000, 36, Component.translatable("gui.contentstudio.loot.loots.edit.short"),
                    Component.translatable("gui.contentstudio.loot.loots.tip.pool.edit"), () -> editGroupedPool(slot)
            );
            groupPoolDeleteButtons[i] = addButton(
                    RIGHT_X + RIGHT_W - 53, -1000, 36, Component.translatable("gui.contentstudio.loot.loots.delete.short"),
                    Component.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm"), () -> confirmGroupedPoolDelete(slot)
            );
            groupEntryEditButtons[i] = addButton(
                    RIGHT_X + RIGHT_W - 61, -1000, 44, Component.translatable("gui.contentstudio.loot.loots.edit.short"),
                    Component.translatable("gui.contentstudio.loot.loots.tip.drop.edit"), () -> editGroupedEntry(slot)
            );
            groupEntryDeleteButtons[i] = addButton(
                    RIGHT_X + RIGHT_W - 109, -1000, 44, Component.translatable("gui.contentstudio.loot.loots.delete.short"),
                    Component.translatable("gui.contentstudio.loot.loots.tip.entry.delete"), () -> deleteGroupedEntry(slot)
            );
            hideGroupSlot(i);
        }
    }

    private void hideGroupSlot(int slot) {
        if (groupArrowButtons == null || slot < 0 || slot >= GROUP_BUTTON_SLOTS) {
            return;
        }
        groupArrowButtons[slot].setVisible(false);
        groupAddButtons[slot].setVisible(false);
        groupPoolEditButtons[slot].setVisible(false);
        groupPoolDeleteButtons[slot].setVisible(false);
        groupEntryDeleteButtons[slot].setVisible(false);
        groupEntryEditButtons[slot].setVisible(false);
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
            groupPoolDeleteButtons[i].setEnabled(active && poolCountForGroupedLayout() > 1);
            groupEntryDeleteButtons[i].setEnabled(active);
            groupEntryEditButtons[i].setEnabled(active);
        }
    }

    private KineticEditBox numberBox(int x, int y, String tooltipKey, String defaultValue, NumericInputType inputType) {
        KineticEditBox box = addTextField(
                x, y, 34, Component.empty(), null, null, Component.translatable(tooltipKey)
        );
        box.setMaxLength(16);
        box.setFilter(value -> isAllowedNumericInput(value, inputType));
        box.setValue(defaultValue);
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
                Component.translatable(translationKey),
                KineticOverlays.Position.BOTTOM_CENTER,
                3000,
                0,
                -30
        );
    }

    private Component getKilledText() {
        return Component.translatable(requirePlayerKill ? "gui.contentstudio.loot.loots.killed_on" : "gui.contentstudio.loot.loots.killed_off");
    }

    private Component getLootingText() {
        return Component.translatable(enableLooting ? "gui.contentstudio.loot.loots.looting_on" : "gui.contentstudio.loot.loots.looting_off");
    }

    private Component getFireText() {
        return Component.translatable(enableFireSmelt ? "gui.contentstudio.loot.loots.fire_on" : "gui.contentstudio.loot.loots.fire_off");
    }

    private Component getOverrideModeText() {
        return Component.translatable(overrideMode ? "gui.contentstudio.loot.loots.mode.override" : "gui.contentstudio.loot.loots.mode.append");
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
        double oldScroll = targetScroll;
        String clean = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            displayEntries = new ArrayList<>(allEntries);
        } else {
            displayEntries = allEntries.stream().filter(entry -> KineticSearch.match(searchText(entry), clean)).collect(Collectors.toList());
        }
        displayEntries = sortedEntries(displayEntries);
        targetScroll = resetScroll ? 0D : oldScroll;
        updateTargetScrollLimit();
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
        int rows = targetTotalRows();
        maxTargetScroll = Math.max(0, rows - targetVisibleRows());
        targetScroll = Math.max(0D, Math.min(targetScroll, maxTargetScroll));
    }

    protected abstract int targetVisibleRows();

    protected abstract int targetTotalRows();

    protected final double smoothTargetScroll() {
        return targetScrollState.follow(
                targetScroll,
                maxTargetScroll,
                draggingTargetScroll
        );
    }

    protected abstract int targetStartIndex();

    protected abstract int targetVisibleEntryCount();

    protected int targetAreaHeight() {
        return TARGET_HEIGHT;
    }

    protected int targetAreaY() {
        return LEFT_Y;
    }

    protected int targetScrollbarThumbHeight() {
        return KineticScroll.stateThumbHeight(targetAreaHeight(), targetVisibleRows(), targetTotalRows(), 24);
    }

    protected void renderTargetScrollbar(GuiGraphics g, int mx, int my) {
        if (maxTargetScroll <= 0) {
            return;
        }
        int x = LEFT_X + TARGET_WIDTH + 4;
        int y = targetAreaY() + 1;
        int width = 4;
        int height = targetAreaHeight() - 2;
        int thumbHeight = Math.min(height, targetScrollbarThumbHeight());
        double smoothTarget = smoothTargetScroll();
        GuiTheme.scrollbar(
                g,
                mx,
                my,
                x,
                y,
                width,
                height,
                thumbHeight,
                maxTargetScroll,
                smoothTarget,
                draggingTargetScroll
        );
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

    protected void renderSpecialPanel(GuiGraphics g, int mx, int my) {
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
                itemBox.getValue(),
                chanceBox == null ? "" : chanceBox.getValue(),
                rollsBox == null ? "" : rollsBox.getValue(),
                countMinBox == null ? "" : countMinBox.getValue(),
                countMaxBox == null ? "" : countMaxBox.getValue(),
                weightBox == null ? "" : weightBox.getValue(),
                lootingMinBox == null ? "" : lootingMinBox.getValue(),
                lootingMaxBox == null ? "" : lootingMaxBox.getValue(),
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
        infoLines = buildInfoLines();
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
        groupScroll = 0D;
        onTargetSelected();
        infoLines = Collections.singletonList(Component.translatable("gui.contentstudio.loot.loots.loading"));
        dropVisuals.clear();
        dropScroll = 0D;
        maxDropScroll = 0;
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
        replaceEntry(selectedEntry);
        parseCurrentRoot();
        rebuildVisualData();
        updateSearch(searchBox == null ? "" : searchBox.getValue(), false);
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
                updateSearch(searchBox == null ? "" : searchBox.getValue(), false);
                infoLines = buildInfoLines();
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
                updateSearch(searchBox == null ? "" : searchBox.getValue(), false);
                infoLines = buildInfoLines();
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
        updateSearch(searchBox == null ? searchQuery : searchBox.getValue(), false);
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
        infoLines = buildInfoLines();
        dropScroll = 0D;
        maxDropScroll = Math.max(0, dropVisuals.size() - visibleDropRows());
        selectedDrop = null;
        clearEditFields();
        if (usesGroupedLayout()) {
            refreshGroupedLayout();
        }
    }

    private List<Component> buildInfoLines() {
        List<Component> lines = new ArrayList<>();
        if (selectedEntry != null) {
            String displayName = getDisplayName(selectedEntry);
            Component tableName = displayName.equals(selectedEntry.lootTableId())
                    ? idComponent(selectedEntry.lootTableId())
                    : nameComponent(displayName);
            lines.add(Component.translatable("gui.contentstudio.loot.loots.summary.table", tableName)
                    .withStyle(ChatFormatting.GRAY));
            boolean changed = selectedOverridden || dirty;
            lines.add(Component.translatable("gui.contentstudio.loot.loots.summary.override",
                            Component.translatable(changed ? "gui.contentstudio.loot.loots.yes" : "gui.contentstudio.loot.loots.no")
                                    .withStyle(changed ? ChatFormatting.GOLD : ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY));
            if (overrideMode) {
                lines.add(Component.translatable("gui.contentstudio.loot.loots.summary.override_mode").withStyle(ChatFormatting.GOLD));
            }
        }
        try {
            JsonArray pools = getPools();
            lines.add(Component.translatable("gui.contentstudio.loot.loots.summary.pool_count", numberComponent(pools.size()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.contentstudio.loot.loots.summary.drop_count", numberComponent(dropVisuals.size()))
                    .withStyle(ChatFormatting.GRAY));
        } catch (Exception e) {
            lines.add(Component.translatable("gui.contentstudio.loot.loots.summary.invalid_json").withStyle(ChatFormatting.RED));
        }
        return lines;
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
        JsonArray pools = currentRoot.getAsJsonArray("pools");
        if (pools.isEmpty()) {
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", new JsonArray());
            pools.add(pool);
        }
        return pools;
    }

    protected JsonObject poolForNewDrop() {
        JsonArray pools = getPools();
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
                ? Component.translatable("gui.contentstudio.loot.loots.drop.load_error")
                : displayNameForEntry(type, name, visual.stack);
        visual.poolIndex = context.poolIndex;
        visual.entryIndex = entryIndex;
        visual.depth = depth;
        visual.entry = entry;
        visual.pool = context.pool;
        visual.parentEntries = parentEntries;
        int weight = readInt(entry.get("weight"));
        visual.chance = Component.translatable("gui.contentstudio.loot.loots.drop.chance",
                        numberComponent(formatChance(weight, context.totalWeight, entry)),
                        numberComponent(context.rolls))
                .withStyle(ChatFormatting.GREEN);
        visual.probability = Component.translatable("gui.contentstudio.loot.loots.drop.probability_only",
                        numberComponent(formatChance(weight, context.totalWeight, entry)))
                .withStyle(ChatFormatting.GREEN);
        List<Component> allFunctions = new ArrayList<>(context.functions);
        allFunctions.addAll(functions);
        visual.count = Component.translatable("gui.contentstudio.loot.loots.drop.count", numberComponent(countText(allFunctions)))
                .withStyle(ChatFormatting.GREEN);
        visual.details.add(Component.translatable("gui.contentstudio.loot.loots.drop.pool", numberComponent(context.poolIndex))
                .withStyle(ChatFormatting.GRAY));
        visual.details.add(Component.translatable("gui.contentstudio.loot.loots.drop.weight",
                        numberComponent(weight), numberComponent(Math.max(context.totalWeight, weight)))
                .withStyle(ChatFormatting.GRAY));
        if (depth > 0) {
            visual.details.add(Component.translatable("gui.contentstudio.loot.loots.drop.nested", numberComponent(depth))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        visual.details.add(Component.translatable("gui.contentstudio.loot.loots.drop.rolls", numberComponent(context.rolls))
                .withStyle(ChatFormatting.GRAY));
        if (!context.bonusRolls.equals("-")) {
            visual.details.add(Component.translatable("gui.contentstudio.loot.loots.drop.bonus_rolls", numberComponent(context.bonusRolls))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        visual.details.addAll(context.conditions);
        visual.details.addAll(conditions);
        visual.details.addAll(context.functions);
        visual.details.addAll(functions);
        if (visual.loadError) {
            visual.details.add(Component.translatable("gui.contentstudio.loot.loots.drop.load_error_replaced"));
        }
        if (showMissingPlayerKillHint() && lacksKilledByPlayer(context.conditions, conditions)) {
            visual.details.add(Component.translatable("gui.contentstudio.loot.loots.condition.no_player_kill").withStyle(ChatFormatting.RED));
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
        return Component.translatable("gui.contentstudio.loot.loots.drop.load_error_id", visual.displayId);
    }

    private boolean lacksKilledByPlayer(List<Component> firstLines, List<Component> secondLines) {
        String killed = Component.translatable("gui.contentstudio.loot.loots.condition.killed_by_player").getString();
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
            return Component.translatable("gui.contentstudio.loot.loots.drop.empty").withStyle(ChatFormatting.RED);
        }
        if (type.endsWith("loot_table")) {
            return Component.translatable("gui.contentstudio.loot.loots.drop.sub_table", idComponent(blankToDash(name)))
                    .withStyle(ChatFormatting.GOLD);
        }
        if (type.endsWith("tag")) {
            return Component.translatable("gui.contentstudio.loot.loots.drop.tag", idComponent(blankToDash(name)))
                    .withStyle(ChatFormatting.GOLD);
        }
        if (!stack.isEmpty()) {
            return stack.getHoverName().copy().withStyle(ChatFormatting.GOLD);
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
        if (itemBox != null) itemBox.setValue("");
        if (chanceBox != null) chanceBox.setValue("1");
        if (rollsBox != null) rollsBox.setValue(defaultRollsValue());
        if (countMinBox != null) countMinBox.setValue("1");
        if (countMaxBox != null) countMaxBox.setValue("1");
        if (weightBox != null) weightBox.setValue("1");
        if (lootingMinBox != null) lootingMinBox.setValue("0");
        if (lootingMaxBox != null) lootingMaxBox.setValue("0");
        requirePlayerKill = false;
        enableLooting = false;
        enableFireSmelt = false;
        if (killedButton != null) killedButton.setValue(requirePlayerKill);
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
        itemBox.setValue(safeString(selectedDrop.entry.get("name")));
        chanceBox.setValue(trimNumber(readRandomChanceValue(selectedDrop.entry)));
        rollsBox.setValue(readRollsForEditor(selectedDrop.pool));
        weightBox.setValue(String.valueOf(readInt(selectedDrop.entry.get("weight"))));
        JsonObject setCount = findFunction(selectedDrop.entry, "set_count");
        if (setCount != null) {
            setRangeFields(setCount.get("count"), countMinBox, countMaxBox, "1");
            enforceAtLeastOne(countMinBox);
            enforceAtLeastOne(countMaxBox);
        } else {
            countMinBox.setValue("1");
            countMaxBox.setValue("1");
        }
        JsonObject looting = findFunction(selectedDrop.entry, "looting_enchant");
        enableLooting = looting != null;
        enableFireSmelt = entryRequiresFire(selectedDrop.entry);
        if (looting != null) {
            setRangeFields(looting.get("count"), lootingMinBox, lootingMaxBox, "0");
        } else {
            lootingMinBox.setValue("0");
            lootingMaxBox.setValue("0");
        }
        requirePlayerKill = entryHasKilledByPlayer(selectedDrop.entry);
        killedButton.setValue(requirePlayerKill);
        lootingButton.setValue(enableLooting);
        if (fireButton != null) fireButton.setValue(enableFireSmelt);
        selectedDropEditorSnapshot = currentEditorSnapshot();
        updateButtons();
    }

    private void setRangeFields(JsonElement element, KineticEditBox minBox, KineticEditBox maxBox, String fallback) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            minBox.setValue(object.has("min") ? trimNumber(readDouble(object.get("min"), 1.0D)) : fallback);
            maxBox.setValue(object.has("max") ? trimNumber(readDouble(object.get("max"), 1.0D)) : minBox.getValue());
        } else if (element != null && element.isJsonPrimitive()) {
            String value = trimNumber(readDouble(element, 1.0D));
            minBox.setValue(value);
            maxBox.setValue(value);
        } else {
            minBox.setValue(fallback);
            maxBox.setValue(fallback);
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
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.no_drop_selected"));
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
        if (key != null) {
            pendingTableResets.remove(key);
        }

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
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.no_drop_selected"));
            return;
        }
        selectedDrop.parentEntries.remove(selectedDrop.entryIndex);
        markDirtyAndRebuild(null);
    }

    private boolean hasEditorValueError(JsonObject entry, JsonObject pool) {
        String itemId = itemBox.getValue().trim();
        if (isInvalidItemId(itemId)) {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.invalid_item"));
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

    private boolean isEmpty(KineticEditBox box) {
        return box == null || box.getValue().trim().isEmpty();
    }

    private void restoreEmptyNumericDefaults() {
        if (isEmpty(chanceBox)) chanceBox.setValue("1");
        if (isEmpty(rollsBox)) rollsBox.setValue(defaultRollsValue());
        if (isEmpty(countMinBox)) countMinBox.setValue("1");
        if (isEmpty(countMaxBox)) countMaxBox.setValue("1");
        if (isEmpty(weightBox)) weightBox.setValue("1");
        if (isEmpty(lootingMinBox)) lootingMinBox.setValue("0");
        if (isEmpty(lootingMaxBox)) lootingMaxBox.setValue("0");
    }

    private boolean isInvalidItemId(String itemId) {
        try {
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(itemId));
            return item == null || item == Items.AIR;
        } catch (Exception e) {
            return true;
        }
    }

    private Integer parseIntegerBox(KineticEditBox box) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDoubleBox(KineticEditBox box) {
        try {
            return Double.parseDouble(box.getValue().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private RollRange parseRollRange(KineticEditBox box) {
        try {
            String value = box.getValue().trim().replace(" ", "");
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

    private void enforceAtLeastOne(KineticEditBox box) {
        Integer value = parseIntegerBox(box);
        if (value == null || value < 1) {
            box.setValue("1");
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
        infoLines = buildInfoLines();
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
                Component.translatable("gui.contentstudio.loot.loots.reset.confirm.title"),
                Component.translatable("gui.contentstudio.loot.loots.reset.confirm.desc"),
                Component.translatable("gui.contentstudio.loot.loots.reset.confirm.yes"),
                Component.translatable("gui.contentstudio.loot.loots.reset.confirm.cancel"),
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
        infoLines = buildInfoLines();
        updateButtons();
    }

    private void openItemPicker() {
        if (this.minecraft == null) {
            return;
        }
        KineticSelectors.openItemSelector(this, selection -> {
            if (selection != null && selection.isItem()) {
                ResourceLocation id = KineticRegistries.items().id(selection.stack().getItem());
                if (id != null && itemBox != null) {
                    pendingPickedItemId = id.toString();
                    pendingPickedItemStackId = id.toString();
                    pendingPickedItemStack = selection.stack().copy();
                    pendingPickedTargetEntry = selectedDrop == null ? null : selectedDrop.entry;
                    itemBox.setValue(id.toString());
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
        } else if (function.endsWith("looting_enchant")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.looting", numberComponent(readNumberRange(object.get("count")))));
        } else if (function.endsWith("apply_bonus")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.apply_bonus",
                    idComponent(safeString(object.get("enchantment"))), idComponent(safeString(object.get("formula")))));
        } else if (function.endsWith("furnace_smelt")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.furnace_smelt"));
        } else if (function.endsWith("explosion_decay")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.explosion_decay"));
        } else if (function.endsWith("set_nbt")) {
            lines.add(functionLine("gui.contentstudio.loot.loots.function.set_nbt"));
        } else if (function.endsWith("copy_name") || function.endsWith("copy_nbt") || function.endsWith("copy_state")) {
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
        String setCountPrefix = Component.translatable("gui.contentstudio.loot.loots.function.set_count.prefix").getString();
        String lootingPrefix = Component.translatable("gui.contentstudio.loot.loots.function.looting.prefix").getString();
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
            return Component.translatable("gui.contentstudio.loot.loots.drop.count_with_looting", base, looting).getString();
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
            return Component.translatable("gui.contentstudio.loot.loots.drop.unknown_chance").getString();
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
        maxGroupScroll = calculateMaxGroupScroll();
        groupScroll = Math.max(0D, Math.min(groupScroll, maxGroupScroll));
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
        double visualScroll = groupScrollState.follow(groupScroll, maxGroupScroll, draggingGroupScroll);
        int firstIndex = Math.max(0, Math.min((int) Math.floor(visualScroll + 1.0E-6D), maxGroupScroll));
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
                groupArrowButtons[slot].setVisible(true);
                groupArrowButtons[slot].setY(row.y + 2);
                groupArrowButtons[slot].setText(Component.translatable(expandedPools.contains(row.poolIndex)
                        ? "gui.contentstudio.common.collapse_symbol"
                        : "gui.contentstudio.common.expand_symbol"));
                groupAddButtons[slot].setVisible(true);
                groupAddButtons[slot].setY(row.y + 2);
                groupPoolEditButtons[slot].setVisible(true);
                groupPoolEditButtons[slot].setY(row.y + 2);
                groupPoolDeleteButtons[slot].setVisible(true);
                groupPoolDeleteButtons[slot].setEnabled(poolCountForGroupedLayout() > 1);
                groupPoolDeleteButtons[slot].setY(row.y + 2);
            } else {
                groupEntryDeleteButtons[slot].setVisible(true);
                groupEntryDeleteButtons[slot].setY(row.y + 5);
                groupEntryEditButtons[slot].setVisible(true);
                groupEntryEditButtons[slot].setY(row.y + 5);
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
        if (row != null && row.isPool() && minecraft != null) {
            KineticClientRuntime.openScreen(new LootPoolEditScreen(this, row.poolIndex, groupedPool(row.poolIndex).deepCopy()));
        }
    }

    private void confirmGroupedPoolDelete(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row == null || !row.isPool()) {
            return;
        }
        int poolIndex = row.poolIndex;
        openDialog(
                Component.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.title"),
                Component.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.desc"),
                Component.translatable("gui.contentstudio.loot.loots.confirm.delete"),
                Component.translatable("gui.contentstudio.loot.loots.confirm.cancel"),
                () -> {
                    if (deletePoolFromEditor(poolIndex)) {
                        KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.pool.deleted"));
                    }
                },
                () -> { }
        );
    }

    private void editGroupedEntry(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row != null && !row.isPool() && row.visual != null && minecraft != null) {
            KineticClientRuntime.openScreen(new LootEntryEditScreen(this, row.visual));
        }
    }

    private void deleteGroupedEntry(int slot) {
        GroupRow row = visibleGroupRow(slot);
        if (row != null && !row.isPool() && row.visual != null) {
            deleteEntryFromEditor(row.visual);
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.entry.deleted"));
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
        if (minecraft != null) {
            KineticClientRuntime.openScreen(new LootPoolEditScreen(this, poolIndex, groupedPool(poolIndex).deepCopy()));
        }
    }

    private void openGroupedItemPicker(int poolIndex) {
        if (minecraft == null) {
            return;
        }
        KineticSelectors.openItemSelector(this, selection -> {
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
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.drop.added"));
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
        if (pools.size() <= 1) {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.pool.keep_one"));
            return false;
        }
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
        expandedPools.add(Math.max(0, Math.min(poolIndex, pools.size() - 1)));
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
        tooltip.add(Component.translatable("gui.contentstudio.loot.loots.pool.number", numberComponent(poolIndex + 1)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("gui.contentstudio.loot.loots.drop.rolls", numberComponent(readRollsForEditor(pool))).withStyle(ChatFormatting.GRAY));
        String bonusRolls = readNumberRange(pool.get("bonus_rolls"));
        if (!bonusRolls.equals("-")) {
            tooltip.add(Component.translatable("gui.contentstudio.loot.loots.drop.bonus_rolls", numberComponent(bonusRolls)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("gui.contentstudio.loot.loots.pool.entry_count", numberComponent(pool.getAsJsonArray("entries").size())).withStyle(ChatFormatting.GRAY));
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
            saveButton.setVisible(!specialPanel);
            saveButton.setEnabled(!specialPanel && hasEntry && !saving);
        }
        if (resetButton != null) {
            resetButton.setVisible(!specialPanel);
            resetButton.setEnabled(!specialPanel && hasEntry && !saving);
        }
        if (addPoolHeaderButton != null) {
            addPoolHeaderButton.setVisible(!specialPanel);
            addPoolHeaderButton.setEnabled(!specialPanel && hasEntry);
        }
        if (deleteButton != null) deleteButton.setEnabled(!specialPanel && hasDrop);
        if (applyButton != null) applyButton.setEnabled(!specialPanel && hasDrop);
        if (fireButton != null) fireButton.setEnabled(!specialPanel && hasEntry);
        if (overrideModeButton != null) {
            overrideModeButton.setVisible(!specialPanel);
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
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.panel(g, 0, 0, V_WIDTH, V_HEIGHT);
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

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        GuiTheme.stateSurface(g, x, y, w, h, GuiTheme.Surface.PANEL_ALT, true, false, false);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        deferredTooltip = null;
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
            g.drawCenteredString(font, Component.translatable("gui.contentstudio.loot.loots.no_selection"), RIGHT_X + RIGHT_W / 2, centerY, 0xFFFFAA00);
        }
    }

    protected abstract void renderTargets(GuiGraphics g, int mx, int my);

    protected abstract LootEntryInfo targetEntryAt(double mx, double my);

    protected abstract String getDisplayName(LootEntryInfo entry);

    private void renderTopInfo(GuiGraphics g, int mx, int my) {
        g.drawString(font, getTitle(), RIGHT_X + 6, RIGHT_Y + 6, 0xFFFFAA00, false);
        if (isSpecialPanelActive() && selectedEntry != null) {
            String display = getDisplayName(selectedEntry);
            g.drawString(font, trim(font, display, RIGHT_W - 62), RIGHT_X + 6, RIGHT_Y + 28, 0xFFFFD75F, false);
            if (mx >= RIGHT_X + 6 && mx <= RIGHT_X + RIGHT_W - 56 && my >= RIGHT_Y + 26 && my <= RIGHT_Y + 38) {
                deferredTooltip = List.of(nameComponent(display), idComponent(selectedEntry.lootTableId()));
            }
            return;
        }
        int textX = RIGHT_X + 6;
        int textY = usesGroupedLayout() ? RIGHT_Y + 28 : RIGHT_Y + 20;
        int textWidth = usesGroupedLayout() ? RIGHT_W - 12 : RIGHT_W - 160;
        if (!infoLines.isEmpty()) {
            boolean translatedName = selectedEntry != null
                    && !getDisplayName(selectedEntry).equals(selectedEntry.lootTableId());
            int tableColor = translatedName ? 0xFFFFD75F : 0xFF55FFFF;
            g.drawString(font, trim(font, infoLines.get(0).getString(), textWidth), textX, textY, tableColor, false);
        }
        if (selectedEntry != null && !usesGroupedLayout()) {
            g.drawString(font, trim(font, Component.translatable("gui.contentstudio.loot.loots.tip.compact_header").getString(), textWidth), textX, textY + 12, 0xFFE6E6E6, false);
        }
        int hoverHeight = usesGroupedLayout() ? 10 : 22;
        if (mx >= textX && mx <= textX + textWidth && my >= textY && my <= textY + hoverHeight && !infoLines.isEmpty()) {
            deferredTooltip = new ArrayList<>();
            if (selectedEntry != null) {
                deferredTooltip.add(idComponent(selectedEntry.lootTableId()));
            }
            if (infoLines.size() > 1) {
                deferredTooltip.addAll(infoLines.subList(1, infoLines.size()));
            }
        }
    }

    private void renderEditLabels(GuiGraphics g, int mx, int my) {
        int x0 = RIGHT_X + 10;
        int y = EDIT_Y + 10;
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.item"), x0, y, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.chance"), x0 + 48, y, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.rolls"), x0 + 92, y, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.count_min"), x0 + 136, y, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.count_max"), x0 + 180, y, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.weight"), x0 + 224, y, 0xFFFFAA00, false);
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
                deferredTooltip = List.of(Component.translatable(tooltipKey));
            }
        }
    }

    protected void renderModeEditLabels(GuiGraphics g, int x0, int y) {
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.looting_min"), x0 + 268, y, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.edit.looting_max"), x0 + 312, y, 0xFFFFAA00, false);
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

    private void renderEditorItemPreview(GuiGraphics g, int mx, int my) {
        int x = RIGHT_X + 18;
        int y = EDIT_Y + 25;
        ItemStack stack = editorPreviewStack();
        boolean hovered = mx >= x && mx <= x + EDIT_ICON && my >= y && my <= y + EDIT_ICON;
        drawCheckerboard(g, stack, x, y, EDIT_ICON, EDIT_ICON, 5, hovered);
        GuiTheme.stateOutline(g, x, y, EDIT_ICON, EDIT_ICON, true, hovered, false);
        if (!stack.isEmpty()) {
            int itemOffset = (EDIT_ICON - 16) / 2;
            g.pose().pushPose();
            g.pose().translate(x + itemOffset, y + itemOffset, 80.0F);
            g.renderItem(stack, 0, 0);
            g.renderItemDecorations(font, stack, 0, 0);
            g.pose().popPose();
        }
        if (hovered) {
            if (!stack.isEmpty()) {
                deferredTooltip = List.of(
                        stack.getHoverName().copy().withStyle(ChatFormatting.GOLD),
                        idComponent(itemBox == null ? "" : itemBox.getValue())
                );
            } else {
                deferredTooltip = List.of(Component.translatable("gui.contentstudio.loot.loots.tip.pick_item"));
            }
        }
    }

    private ItemStack editorPreviewStack() {
        if (itemBox == null) {
            return ItemStack.EMPTY;
        }
        String itemId = itemBox.getValue().trim();
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

    private void renderGroupedPanel(GuiGraphics g, int mx, int my) {
        if (selectedEntry == null || currentRoot == null) {
            return;
        }
        if (groupedRows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.contentstudio.loot.loots.no_drops"), RIGHT_X + RIGHT_W / 2, GROUP_Y + GROUP_H / 2, 0xFFFFAA00);
            return;
        }
        enableUiScissor(g, RIGHT_X + 4, GROUP_Y + 6, RIGHT_X + RIGHT_W - 8, GROUP_Y + GROUP_H - 6);
        try {
            for (GroupRow row : visibleGroupRows) {
                if (row.isPool()) {
                    renderGroupedPoolRow(g, row, mx, my);
                } else {
                    renderGroupedEntryRow(g, row, mx, my);
                }
            }
        } finally {
            disableUiScissor(g);
        }
        if (maxGroupScroll > 0) {
            int trackH = GROUP_H - 12;
            int thumbH = KineticScroll.stateThumbHeight(trackH,
                    Math.max(1, visibleGroupRows.size()), groupedRows.size(), 18);
            KineticScroll.renderScrollbarState(g, mx, my, GROUP_SCROLLBAR_X + 2, GROUP_Y + 6, 4, trackH,
                    thumbH, maxGroupScroll,
                    groupScrollState.follow(groupScroll, maxGroupScroll, draggingGroupScroll),
                    draggingGroupScroll);
        }
    }

    private void renderGroupedRowBackgrounds(GuiGraphics g, int mx, int my) {
        enableUiScissor(g, RIGHT_X + 4, GROUP_Y + 6, RIGHT_X + RIGHT_W - 8, GROUP_Y + GROUP_H - 6);
        try {
            for (GroupRow row : visibleGroupRows) {
                if (row.isPool()) {
                int x = RIGHT_X + 8;
                int width = RIGHT_W - 24;
                boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + GROUP_POOL_H;
                int drawHeight = GROUP_POOL_H - GROUP_ROW_GAP;
                GuiTheme.stateSurface(
                        g, x, row.y, width, drawHeight,
                        GuiTheme.Surface.PANEL_ALT, false, hover, false
                );
            } else {
                int x = RIGHT_X + 10;
                int width = RIGHT_W - 26;
                int drawHeight = GROUP_ENTRY_H - GROUP_ROW_GAP;
                boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + drawHeight;
                GuiTheme.stateSurface(
                        g, x, row.y, width, drawHeight,
                        GuiTheme.Surface.PANEL_ALT, false, hover, row.visual.loadError
                );
                }
            }
        } finally {
            disableUiScissor(g);
        }
    }

    private void renderGroupedPoolRow(GuiGraphics g, GroupRow row, int mx, int my) {
        int x = RIGHT_X + 8;
        int width = RIGHT_W - 24;
        boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + GROUP_POOL_H;
        JsonObject pool = groupedPool(row.poolIndex);
        int entryCount = 0;
        for (DropVisual visual : dropVisuals) {
            if (visual.poolIndex == row.poolIndex + 1) entryCount++;
        }
        Component line = Component.translatable("gui.contentstudio.loot.loots.pool.header",
                        numberComponent(row.poolIndex + 1),
                        numberComponent(readRollsForEditor(pool)),
                        numberComponent(entryCount))
                .withStyle(ChatFormatting.GRAY);
        enableUiScissor(
                g,
                RIGHT_X + 34,
                row.y + 1,
                RIGHT_X + RIGHT_W - 146,
                row.y + GROUP_POOL_H - 2
        );
        g.drawString(font, line, RIGHT_X + 34, row.y + 8, 0xFFFFFFFF, false);
        disableUiScissor(g);
        if (hover && mx >= RIGHT_X + 32 && mx < RIGHT_X + RIGHT_W - 145) {
            deferredTooltip = groupedPoolTooltip(row.poolIndex);
        }
    }

    private void renderGroupedEntryRow(GuiGraphics g, GroupRow row, int mx, int my) {
        DropVisual visual = row.visual;
        int x = RIGHT_X + 10;
        int width = RIGHT_W - 26;
        int drawHeight = GROUP_ENTRY_H - GROUP_ROW_GAP;
        boolean hover = mx >= x && mx < x + width && my >= row.y && my < row.y + drawHeight;
        renderDropIcon(g, visual, row.y, hover);
        int textX = RIGHT_X + 39;
        int textRight = RIGHT_X + RIGHT_W - 114;
        Component firstLine = visual.loadError
                ? visual.name
                : Component.empty()
                .append(visual.probability == null ? visual.chance : visual.probability)
                .append(Component.literal("    "))
                .append(visual.count);
        Component secondLine = visual.loadError ? loadErrorIdComponent(visual) : buildGroupedSecondLine(visual);
        enableUiScissor(
                g,
                textX,
                row.y + 1,
                textRight,
                row.y + drawHeight - 1
        );
        g.drawString(font, firstLine, textX, row.y + 4, 0xFFFFFFFF, false);
        if (visual.loadError) {
            drawFittedComponent(g, secondLine, textX, row.y + 16, textRight - textX);
        } else {
            g.drawString(font, secondLine, textX, row.y + 16, 0xFFFFFFFF, false);
        }
        disableUiScissor(g);
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

    private void renderDropPanel(GuiGraphics g, int mx, int my) {
        if (dropVisuals.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.contentstudio.loot.loots.no_drops"), RIGHT_X + RIGHT_W / 2, DROP_Y + DROP_H / 2, 0xFFFFAA00);
            return;
        }
        int startY = dropListStartY();
        int listH = dropListHeight();
        int visible = visibleDropRows();
        double smoothDropScroll = dropScrollState.follow(
                dropScroll,
                maxDropScroll,
                draggingDropScroll
        );
        int smoothDropRow = (int) Math.floor(smoothDropScroll + 1.0E-6D);
        int dropShift = (int) Math.round(
                (smoothDropScroll - smoothDropRow) * DROP_ROW_H
        );
        int end = Math.min(dropVisuals.size(), smoothDropRow + visible + 1);
        enableUiScissor(
                g,
                RIGHT_X + 8,
                startY,
                RIGHT_X + RIGHT_W - 8,
                startY + listH
        );
        for (int i = smoothDropRow; i < end; i++) {
            DropVisual visual = dropVisuals.get(i);
            int rowY = startY + (i - smoothDropRow) * DROP_ROW_H - dropShift;
            boolean hover = mx >= RIGHT_X + 10 && mx <= RIGHT_X + RIGHT_W - 16 && my >= rowY && my <= rowY + DROP_ROW_H - 4;
            boolean selected = selectedDrop == visual;
            GuiTheme.stateSurface(
                    g,
                    RIGHT_X + 10,
                    rowY,
                    RIGHT_W - 26,
                    DROP_ROW_H - 4,
                    GuiTheme.Surface.PANEL_ALT,
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
            enableUiScissor(
                    g,
                    textX,
                    rowY + 2,
                    RIGHT_X + RIGHT_W - 17,
                    rowY + DROP_ROW_H - 5
            );
            g.drawString(font, firstLine, textX, lineOneY, 0xFFFFFFFF, false);
            if (visual.loadError) {
                drawFittedComponent(g, secondLine, textX, lineTwoY, RIGHT_X + RIGHT_W - 17 - textX);
            } else {
                g.drawString(font, secondLine, textX, lineTwoY, 0xFFFFFFFF, false);
            }
            disableUiScissor(g);
            if (hover) {
                deferredTooltip = buildDropTooltip(visual);
            }
        }
        disableUiScissor(g);
        if (maxDropScroll > 0) {
            int thumbH = KineticScroll.stateThumbHeight(listH, visible, dropVisuals.size(), 18);
            KineticScroll.renderScrollbarState(
                    g, mx, my, DROP_SCROLLBAR_X, startY + 1, 4, listH - 2,
                    thumbH, maxDropScroll,
                    dropScrollState.follow(dropScroll, maxDropScroll, draggingDropScroll),
                    draggingDropScroll
            );
        }
    }

    private void renderDropIcon(GuiGraphics g, DropVisual visual, int rowY, boolean hovered) {
        int x = RIGHT_X + 13;
        int y = rowY + (GROUP_ENTRY_H - GROUP_ROW_GAP - ICON_CELL) / 2;
        ItemStack stack = visual.stack == null ? ItemStack.EMPTY : visual.stack;
        LootCheckerboard.draw(g, stack, x, y, ICON_CELL, ICON_CELL, hovered);
        GuiTheme.stateOutline(g, x, y, ICON_CELL, ICON_CELL, false, hovered, visual.loadError);
        if (!stack.isEmpty()) {
            renderLargeItem(g, stack, x + 3, y + 3, ICON_CELL - 6);
        }
    }

    protected void drawCheckerboard(GuiGraphics g, ItemStack stack, int x, int y, int w, int h, int cell, boolean hovered) {
        GuiTheme.itemSlot(g, x, y, w, h, cell, false, hovered, false);
    }

    protected void renderLargeItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float scale = size / 16.0f;
        g.pose().pushPose();
        g.pose().translate(x, y, 120);
        g.pose().scale(scale, scale, scale);
        g.renderItem(stack, 0, 0);
        g.renderItemDecorations(font, stack, 0, 0);
        g.pose().popPose();
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
        return Component.literal(text == null ? "" : text).withStyle(ChatFormatting.GOLD);
    }

    protected MutableComponent idComponent(String text) {
        return Component.literal(text == null ? "" : text).withStyle(ChatFormatting.AQUA);
    }

    protected MutableComponent numberComponent(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
    }

    private MutableComponent valueComponent(String value) {
        return Component.literal(value == null ? "" : value).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private MutableComponent conditionLine(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.AQUA);
    }

    private MutableComponent functionLine(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private void drawFittedComponent(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth || textWidth <= 0) {
            g.drawString(font, text, x, y, 0xFFFFFFFF, false);
            return;
        }
        float scale = (float) maxWidth / textWidth;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    protected String trim(Font font, String text, int width) {
        if (text == null) {
            return "";
        }
        return font.width(text) > width ? font.plainSubstrByWidth(text, Math.max(4, width - font.width("..."))) + "..." : text;
    }

    @Override
    protected boolean canvasMouseClicked(double mx, double my, int btn) {
        if (isSavingBatch()) {
            return true;
        }
        boolean handled = super.canvasMouseClicked(mx, my, btn);
        if (handled) {
            return true;
        }
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
            if (maxTargetScroll > 0 && mx >= LEFT_X + TARGET_WIDTH + 4 && mx <= LEFT_X + TARGET_WIDTH + 10 && my >= targetAreaY() + 1 && my <= targetAreaY() + targetAreaHeight() - 1) {
                draggingTargetScroll = true;
                targetScroll = KineticScroll.stateOffsetFromPointerPrecise(my, targetAreaY() + 1, targetAreaHeight() - 2, targetScrollbarThumbHeight(), maxTargetScroll);
                targetScrollState.snap(targetScroll, maxTargetScroll);
                return true;
            }
            if (isSpecialPanelActive()) {
                return handleSpecialPanelClick(mx, my, btn);
            }
            if (usesGroupedLayout()) {
                if (maxGroupScroll > 0 && mx >= GROUP_SCROLLBAR_X && mx <= GROUP_SCROLLBAR_X + 6
                        && my >= GROUP_Y + 6 && my <= GROUP_Y + GROUP_H - 6) {
                    draggingGroupScroll = true;
                    int trackH = GROUP_H - 12;
                    int thumbH = KineticScroll.stateThumbHeight(trackH,
                            Math.max(1, visibleGroupRows.size()), groupedRows.size(), 18);
                    groupScroll = KineticScroll.stateOffsetFromPointerPrecise(my, GROUP_Y + 6, trackH, thumbH, maxGroupScroll);
                    groupScrollState.snap(groupScroll, maxGroupScroll);
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
            if (maxDropScroll > 0 && mx >= DROP_SCROLLBAR_X && mx <= DROP_SCROLLBAR_X + 4 && my >= dropStartY + 1 && my <= dropStartY + dropListH - 1) {
                draggingDropScroll = true;
                int thumbH = KineticScroll.stateThumbHeight(dropListH, visibleDropRows(), dropVisuals.size(), 24);
                dropScroll = KineticScroll.stateOffsetFromPointerPrecise(my, dropStartY + 1, dropListH - 2, thumbH, maxDropScroll);
                dropScrollState.snap(dropScroll, maxDropScroll);
                return true;
            }
            int visible = visibleDropRows();
            double smoothDropScroll = dropScrollState.follow(
                    dropScroll,
                    maxDropScroll,
                    draggingDropScroll
            );
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
    protected boolean canvasMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingTargetScroll) {
            targetScroll = KineticScroll.stateOffsetFromPointerPrecise(my, targetAreaY() + 1, targetAreaHeight() - 2, targetScrollbarThumbHeight(), maxTargetScroll);
            targetScrollState.snap(targetScroll, maxTargetScroll);
            return true;
        }
        if (isSpecialPanelActive() && handleSpecialPanelDragged(mx, my, btn, dx, dy)) {
            return true;
        }
        if (draggingDropScroll) {
            int dropStartY = dropListStartY();
            int dropListH = dropListHeight();
            int thumbH = KineticScroll.stateThumbHeight(dropListH, visibleDropRows(), dropVisuals.size(), 24);
            dropScroll = KineticScroll.stateOffsetFromPointerPrecise(my, dropStartY + 1, dropListH - 2, thumbH, maxDropScroll);
            dropScrollState.snap(dropScroll, maxDropScroll);
            return true;
        }
        if (draggingGroupScroll) {
            int trackH = GROUP_H - 12;
            int thumbH = KineticScroll.stateThumbHeight(trackH,
                    Math.max(1, visibleGroupRows.size()), groupedRows.size(), 18);
            groupScroll = KineticScroll.stateOffsetFromPointerPrecise(my, GROUP_Y + 6, trackH, thumbH, maxGroupScroll);
            groupScrollState.snap(groupScroll, maxGroupScroll);
            updateVisibleGroupedRows();
            return true;
        }
        return super.canvasMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean canvasMouseReleased(double mx, double my, int btn) {
        draggingTargetScroll = false;
        draggingDropScroll = false;
        draggingGroupScroll = false;
        if (isSpecialPanelActive()) {
            handleSpecialPanelReleased(mx, my, btn);
        }
        return super.canvasMouseReleased(mx, my, btn);
    }

    @Override
    protected boolean canvasMouseScrolled(double mx, double my, double delta) {
        if (mx >= LEFT_X && mx <= LEFT_X + TARGET_WIDTH + 10 && my >= targetAreaY() && my <= targetAreaY() + targetAreaHeight() && maxTargetScroll > 0) {
            targetScroll = targetScrollState.wheel(
                    targetScroll, delta, 1.0D, maxTargetScroll
            );
            return true;
        }
        if (isSpecialPanelActive() && handleSpecialPanelScrolled(mx, my, delta)) {
            return true;
        }
        if (!isSpecialPanelActive() && usesGroupedLayout() && mx >= RIGHT_X && mx <= RIGHT_X + RIGHT_W
                && my >= GROUP_Y && my <= GROUP_Y + GROUP_H && maxGroupScroll > 0) {
            groupScroll = groupScrollState.wheel(
                    groupScroll, delta, 1.0D, maxGroupScroll
            );
            updateVisibleGroupedRows();
            return true;
        }
        if (!usesGroupedLayout() && mx >= RIGHT_X && mx <= RIGHT_X + RIGHT_W && my >= DROP_Y && my <= DROP_Y + DROP_H && maxDropScroll > 0) {
            dropScroll = dropScrollState.wheel(
                    dropScroll, delta, 1.0D, maxDropScroll
            );
            return true;
        }
        return super.canvasMouseScrolled(mx, my, delta);
    }

    @Override
    protected boolean handleCloseRequest() {
        return isSavingBatch();
    }

    @Override
    protected boolean canvasKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (isSavingBatch()) {
            return true;
        }
        if (KineticKeyBindings.matchesKeyCode(KineticKeyBindings.Key.ESCAPE, keyCode)) {
            commitDraft();
            onClose();
            return true;
        }
        return false;
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) {
            KineticOverlays.requestTooltip(deferredTooltip, mx, my);
        }
    }
}
