package dev.xyat.contentstudio.villager.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.KeyInput;
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
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.client.VillagerClientActions;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Map;

public class VillagerTradeEditorPage extends KineticPage {
    private static final int BUY_A_LABEL = 0xFFFFFF55;
    private static final int BUY_B_LABEL = 0xFF55FFFF;
    private static final int SELL_LABEL = 0xFF55FF55;
    private static final int ROW_H = 41;
    private static final int LEVEL_ROW_H = 24;
    private static final int LEVEL_BUTTON_SIZE = 14;
    // Shared layout bounds keep labels 4px away from neighboring controls and panel edges.
    private static final int TEXT_GAP = 4;
    private static final int META_FIELD_GAP = 88;
    private static final int TRADE_SLOT_PANEL_WIDTH = 78;
    // The 36 px count field and buttons start here, keeping 2 px from the slot panel's right line.
    private static final int SLOT_CONTROL_OFFSET = 33;
    // Fixed menu widths keep translated labels scrolling inside the editor's outer frame.
    private static final int TRADE_MENU_WIDTH = 180;
    private static final int PROBLEM_MENU_WIDTH = 260;
    private static final int MENU_FRAME_INSET = 8;

    private static final int MAX_UNDO_STEPS = 10;

    private record TradeConfigState(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean enabled,
            boolean lateOverride
    ) {
    }

    private record UndoCheckpoint(
            TradeEditorState editorState,
            TradeConfigState configState
    ) {
    }

    private record TradeEditorState(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean enabled,
            boolean lateOverride,
            String selectedOwner,
            int selectedLevel,
            String selectedMode,
            int selectedOfferCount,
            String selectedKey,
            boolean editorActive,
            boolean levelSettingsActive,
            boolean rewardExp,
            boolean allowRestock,
            String rawRewardExp,
            String rawAllowRestock,
            boolean editingDefault,
            int editingVanillaIndex,
            int editingCustomIndex,
            MerchantOffer editingOriginalVanillaOffer,
            Map<String, String> loadedFieldValues,
            OfferFormState loadedOfferFormBaseline,
            Integer loadedLevelOfferCountBaseline,
            String buyAId,
            String buyBId,
            String sellId,
            String buyANbt,
            String buyBNbt,
            String sellNbt,
            String buyACount,
            String buyBCount,
            String sellCount,
            String weight,
            String maxUses,
            String xp,
            String price,
            String demand,
            String specialPrice,
            String uses
    ) {
    }

    private record OfferFormState(
            String buyAId, String buyBId, String sellId,
            String buyANbt, String buyBNbt, String sellNbt,
            String buyACount, String buyBCount, String sellCount,
            String weight, String maxUses, String xp, String price,
            String demand, String specialPrice, String uses,
            boolean rewardExp, boolean allowRestock, String rawRewardExp, String rawAllowRestock
    ) {
    }

    private static String lastProfessionText = "";
    private static String lastTradeSearchText = "";
    private static String lastSelectedOwner = "";
    private static int lastSelectedLevel = 1;
    private static int lastListScroll = 0;
    private static final boolean[] LEVEL_EXPANDED = new boolean[]{false, false, false, false, false};

    private final List<TradeEntry> entries = new ArrayList<>();
    private final List<TradeSearchEntry> tradeSearchSource = new ArrayList<>();
    private final KineticSearch.Model<TradeSearchEntry> tradeSearchModel =
            new KineticSearch.Model<>(List.of(), (entry, query) -> KineticSearch.match(entry.searchText(), query));
    private boolean tradeSearchIndexDirty = true;
    private final List<KineticControl> editorWidgets = new ArrayList<>();
    private final List<KineticControl> levelWidgets = new ArrayList<>();
    private final List<KineticButton> levelExpandButtons = new ArrayList<>();
    private final List<KineticControl> toolbarWidgets = new ArrayList<>();

    private KineticAutoCompleteField professionBox;
    private KineticTextField tradeSearchBox;
    private KineticNumberField buyACountBox;
    private KineticNumberField buyBCountBox;
    private KineticNumberField sellCountBox;
    private KineticNumberField weightBox;
    private KineticNumberField offerCountBox;
    private KineticNumberField maxUsesBox;
    private KineticNumberField xpBox;
    private KineticNumberField priceBox;
    private KineticNumberField demandBox;
    private KineticNumberField specialPriceBox;
    private KineticNumberField usesBox;
    private KineticToggle rewardButton;
    private KineticToggle restockButton;
    private KineticToggle lateOverrideButton;
    private KineticButton undoButton;
    private KineticButton tradeSourceButton;
    private KineticButton previousIssueButton;
    private KineticButton nextIssueButton;
    private List<VillagerConfig.TradeValidationIssue> validationIssues = List.of();
    private boolean validationActive;
    private final List<VillagerConfig.TradeValidationIssue> problemTargets = new ArrayList<>();
    private final Map<VillagerConfig.TradeValidationIssue, String> problemRawLines = new java.util.HashMap<>();
    private int problemCursor = -1;
    private boolean savePending;
    private boolean exitAfterSuccessfulSave;
    private int pendingSavedCount;
    private int pendingSkippedCount;
    private boolean pendingHasIssues;
    private TradeConfigState pendingSavedState;
    private TradeConfigState pendingDraftState;
    private TradeConfigState lastServerSavedState;
    private VillagerConfig.TradeSourceMode pendingTradeSourceMode;
    private TradeEditorState shortcutSuspendedState;
    private OfferFormState loadedOfferFormBaseline;
    private Integer loadedLevelOfferCountBaseline;
    private final Deque<TradeEditorState> undoHistory = new ArrayDeque<>();
    private boolean restoringUndo;

    private int rootX;
    private int rootY;
    private int rootW;
    private int rootH;
    private int leftX;
    private int leftY;
    private int leftW;
    private int leftPanelHeight;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int rightX;
    private int rightY;
    private int rightW;
    private int rightPanelHeight;
    private final KineticScrollController listScroll =
            new KineticScrollController();

    private String selectedOwner = lastSelectedOwner;
    private int selectedLevel = lastSelectedLevel;
    private String selectedMode = "replace_level";
    private int selectedOfferCount = 0;
    /**
     * 载入交易时的数值（原版交易为原版值，自定义交易为已保存值），输入框内容等于它时显示黑色，修改后显示绿色。
     * Values captured when a trade is loaded (vanilla values for vanilla trades, saved values for custom ones);
     * an input equal to its loaded value is drawn black, a modified one green.
     */
    private final java.util.Map<String, String> loadedFieldValues = new java.util.HashMap<>();
    private String selectedKey = "";
    private boolean editorActive = false;
    private boolean levelSettingsActive = false;
    private boolean rewardExp = true;
    private boolean allowRestock = true;
    private String rawRewardExp = "true";
    private String rawAllowRestock = "true";
    private boolean editingDefault = false;
    private int editingVanillaIndex = -1;
    private int editingCustomIndex = -1;
    private MerchantOffer editingOriginalVanillaOffer = null;
    private boolean suppressProfessionResponder = false;

    private String buyAId = "minecraft:emerald";
    private String buyBId = "minecraft:air";
    private String sellId = "minecraft:bread";
    private String buyANbt = "";
    private String buyBNbt = "";
    private String sellNbt = "";
    private String buyACount = "1";
    private String buyBCount = "0";
    private String sellCount = "1";
    private String weight = "1";
    private String maxUses = "16";
    private String xp = "1";
    private String price = "0.05";
    private String demand = "0";
    private String specialPrice = "0";
    private String uses = "0";

    public VillagerTradeEditorPage() {
        super(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.title"));
        lastServerSavedState = captureTradeConfigState();
        configureStandaloneDraft(this::captureTradeConfigState, state ->
                restoreTradeConfigState(lastServerSavedState == null ? state : lastServerSavedState));
        // 交易列表按条目滚动：跳回当前选中条目（selectedKey），并同步记忆偏移以免重建时跳回旧位置
        // The trade list scrolls by entry: jump back to the selected entry (selectedKey) and keep the remembered offset
        // in sync so a rebuild does not jump back to the old position.
        listScroll.bindSelection(this::selectedEntryIndex, index -> {
            int visible = Math.max(1, entries.size() - getMaxListScroll());
            int target = index - visible / 2;
            lastListScroll = Math.max(0, Math.min(target, listScroll.maxOffset()));
            return target;
        });
    }

    private int selectedEntryIndex() {
        if (selectedKey == null || selectedKey.isEmpty()) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (Objects.equals(selectedKey, entries.get(i).key())) return i;
        }
        return -1;
    }

    private TradeConfigState captureTradeConfigState() {
        return new TradeConfigState(
                List.copyOf(VillagerConfig.villagerTradeGroups),
                List.copyOf(VillagerConfig.villagerTradeOffers),
                List.copyOf(VillagerConfig.villagerDefaultTradeOverrides),
                VillagerConfig.enableCustomVillagerTrades,
                VillagerConfig.enableVillagerTradeLateOverride
        );
    }

    private void restoreTradeConfigState(TradeConfigState state) {
        if (state == null) return;
        VillagerConfig.replaceTradeLists(state.groups(), state.offers(), state.overrides());
        VillagerConfig.enableCustomVillagerTrades = state.enabled();
        VillagerConfig.enableVillagerTradeLateOverride = state.lateOverride();
        if (lateOverrideButton != null) {
            lateOverrideButton.setValue(state.lateOverride());
        }
        invalidateTradeSearchIndex();
        refreshEntries();
    }

    private TradeEditorState captureTradeEditorState() {
        syncFieldValues();
        return new TradeEditorState(
                List.copyOf(VillagerConfig.villagerTradeGroups),
                List.copyOf(VillagerConfig.villagerTradeOffers),
                List.copyOf(VillagerConfig.villagerDefaultTradeOverrides),
                VillagerConfig.enableCustomVillagerTrades,
                VillagerConfig.enableVillagerTradeLateOverride,
                selectedOwner,
                selectedLevel,
                selectedMode,
                selectedOfferCount,
                selectedKey,
                editorActive,
                levelSettingsActive,
                rewardExp,
                allowRestock,
                rawRewardExp,
                rawAllowRestock,
                editingDefault,
                editingVanillaIndex,
                editingCustomIndex,
                editingOriginalVanillaOffer,
                Map.copyOf(loadedFieldValues),
                loadedOfferFormBaseline,
                loadedLevelOfferCountBaseline,
                buyAId,
                buyBId,
                sellId,
                buyANbt,
                buyBNbt,
                sellNbt,
                buyACount,
                buyBCount,
                sellCount,
                weight,
                maxUses,
                xp,
                price,
                demand,
                specialPrice,
                uses
        );
    }

    private void restoreTradeEditorState(TradeEditorState state) {
        restoreTradeEditorState(state, true);
    }

    private void restoreTradeEditorState(TradeEditorState state, boolean updateUi) {
        if (state == null) return;

        VillagerConfig.replaceTradeLists(state.groups(), state.offers(), state.overrides());
        VillagerConfig.enableCustomVillagerTrades = state.enabled();
        VillagerConfig.enableVillagerTradeLateOverride = state.lateOverride();
        if (lateOverrideButton != null) {
            lateOverrideButton.setValue(state.lateOverride());
        }
        selectedOwner = state.selectedOwner();
        selectedLevel = state.selectedLevel();
        selectedMode = state.selectedMode();
        selectedOfferCount = state.selectedOfferCount();
        selectedKey = state.selectedKey();
        editorActive = state.editorActive();
        levelSettingsActive = state.levelSettingsActive();
        rewardExp = state.rewardExp();
        allowRestock = state.allowRestock();
        rawRewardExp = state.rawRewardExp();
        rawAllowRestock = state.rawAllowRestock();
        editingDefault = state.editingDefault();
        editingVanillaIndex = state.editingVanillaIndex();
        editingCustomIndex = state.editingCustomIndex();
        editingOriginalVanillaOffer = state.editingOriginalVanillaOffer();
        loadedFieldValues.clear();
        loadedFieldValues.putAll(state.loadedFieldValues());
        loadedOfferFormBaseline = state.loadedOfferFormBaseline();
        loadedLevelOfferCountBaseline = state.loadedLevelOfferCountBaseline();
        buyAId = state.buyAId();
        buyBId = state.buyBId();
        sellId = state.sellId();
        buyANbt = state.buyANbt();
        buyBNbt = state.buyBNbt();
        sellNbt = state.sellNbt();
        buyACount = state.buyACount();
        buyBCount = state.buyBCount();
        sellCount = state.sellCount();
        weight = state.weight();
        maxUses = state.maxUses();
        xp = state.xp();
        price = state.price();
        demand = state.demand();
        specialPrice = state.specialPrice();
        uses = state.uses();

        lastSelectedOwner = selectedOwner;
        lastSelectedLevel = selectedLevel;
        if (!updateUi) {
            invalidateTradeSearchIndex();
            return;
        }
        if (professionBox != null) {
            if (selectedOwner.isEmpty()) {
                suppressProfessionResponder = true;
                professionBox.setTextValue("");
                suppressProfessionResponder = false;
                lastProfessionText = "";
            } else {
                setProfessionBoxDisplay(selectedOwner);
            }
        }
        refreshEntries();
        writeFieldsToWidgets();
        applyFieldDefaults();
        setEditorWidgetsVisible(!selectedOwner.isEmpty() && editorActive);
        setLevelWidgetsVisible(!selectedOwner.isEmpty() && levelSettingsActive);
    }

    @Override
    protected void build(KineticUi ui) {
        editorWidgets.clear();
        levelWidgets.clear();
        levelExpandButtons.clear();
        toolbarWidgets.clear();
        setupLayout();
        listScroll.setOffset(lastListScroll);

        int topY = rootY + 8;
        int searchLeftPadding = 6;
        int searchRightPadding = 6;
        int clearButtonWidth = 40;
        int searchClearGap = 4;
        int searchFieldWidth = leftW - searchLeftPadding - searchRightPadding - clearButtonWidth - searchClearGap;
        int clearButtonX = leftX + searchLeftPadding + searchFieldWidth + searchClearGap;

        this.professionBox = ui().autoComplete(leftX + searchLeftPadding, topY, searchFieldWidth, VillagerTradeEditorPage::getProfessionDictionary).label(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.profession.search")).placeholder(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.profession.placeholder")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.profession.search.tooltip")).build();
        this.professionBox.setTextValue(lastProfessionText);
        this.professionBox.onTextChange(value -> {
            if (!suppressProfessionResponder) {
                lastProfessionText = value;
                trySelectOwnerFromText(value);
            }
        });

        toolbarWidgets.add(ui().button(clearButtonX, topY, clearButtonWidth).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.search.clear.short")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.search.clear.tooltip")).onClick(this::clearProfessionSearch).build());

        this.tradeSearchBox = ui().textField(leftX + searchLeftPadding, leftY + 6, searchFieldWidth).label(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search")).placeholder(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.placeholder")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.tooltip")).build();
        this.tradeSearchBox.limitTextLength(128);
        this.tradeSearchBox.setTextValue(lastTradeSearchText);
        this.tradeSearchBox.onTextChange(value -> {
            lastTradeSearchText = value;
            listScroll.reset();
            lastListScroll = 0;
            refreshEntries();
        });

        toolbarWidgets.add(ui().button(clearButtonX, leftY + 6, clearButtonWidth).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.search.clear.short")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.clear.tooltip")).onClick(this::clearTradeSearch).build());

        int gap = 6;
        int backW = 58;
        int saveW = 58;
        int undoW = 78;
        int previewW = 94;
        int backX = rootX + rootW - 8 - backW;
        int saveX = backX - gap - saveW;
        int undoX = saveX - gap - undoW;
        int previewX = undoX - gap - previewW;
        // 76 px keeps 4 px between this toggle and the search Clear button to its left.
        int lateOverrideW = 76;
        int lateOverrideX = previewX - gap - lateOverrideW;

        this.lateOverrideButton = ui().toggle(lateOverrideX, topY, lateOverrideW)
                .compact()
                .value(VillagerConfig.enableVillagerTradeLateOverride)
                .labels(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.late_override.on"), KineticI18n.translatable("gui.contentstudio.villager.villager.trade.late_override.off"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.late_override.tooltip"))
                .onChange(value -> VillagerConfig.enableVillagerTradeLateOverride = value)
                .build();

        toolbarWidgets.add(ui().button(previewX, topY, previewW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed_preview")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed_preview.tooltip")).onClick(this::openRemovedDefaultTradesScreen).build());

        this.undoButton = ui().button(undoX, topY, undoW).text(undoButtonText()).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.undo.tooltip")).onClick(this::undoLastChange).build();
        updateUndoButtonState();

        toolbarWidgets.add(ui().button(saveX, topY, saveW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.save")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.save.tooltip")).onClick(this::saveValidTradeConfig).build());

        toolbarWidgets.add(ui().button(backX, topY, backW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.back")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.back.tooltip")).onClick(this::close).build());

        // The footer already has room after the reward/restock controls; keep the toolbar and trade slots fixed.
        int sourceX = rightX + 246;
        int sourceY = rightY + 268;
        tradeSourceButton = ui().button(sourceX, sourceY, 110).compact()
                .text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.source_mode.button"))
                .onClick(() -> openTradeSourceMenu(sourceX, sourceY + CONTROL_HEIGHT)).build();
        previousIssueButton = ui().button(rightX + rightW - 54, rightY + 65, 22).compact()
                .text(Component.literal("<"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.error.previous.tooltip"))
                .onClick(() -> navigateProblem(-1)).build();
        nextIssueButton = ui().button(rightX + rightW - 28, rightY + 65, 22).compact()
                .text(Component.literal(">"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.error.next.tooltip"))
                .onClick(() -> navigateProblem(1)).build();

        addLevelExpandButtons();
        addLevelFields();
        addEditorFields();
        addEditorButtons();

        Integer pendingLevelCount = levelSettingsActive && loadedLevelOfferCountBaseline != null ? selectedOfferCount : null;
        if (!selectedOwner.isEmpty() && isValidOwner(selectedOwner)) {
            selectedLevel = VillagerConfig.clampTradeLevel(selectedOwner, selectedLevel);
            loadGroupState();
        } else {
            selectedOwner = "";
            lastSelectedOwner = "";
        }
        if (pendingLevelCount != null) selectedOfferCount = pendingLevelCount;
        refreshEntries();
        writeFieldsToWidgets();
        applyFieldDefaults();
        setEditorWidgetsVisible(!selectedOwner.isEmpty() && editorActive);
        setLevelWidgetsVisible(!selectedOwner.isEmpty() && levelSettingsActive);
        updatePendingControls();
    }

    private void openTradeSourceMenu(int x, int y) {
        if (savePending || pendingTradeSourceMode != null) return;
        openTradeMenuInFrame(x, y - 64, List.of(
                KineticOverlays.MenuItem.choice(tradeSourceModeLabel(VillagerConfig.TradeSourceMode.MERGE_ALL), null,
                        VillagerConfig.getTradeSourceMode() == VillagerConfig.TradeSourceMode.MERGE_ALL,
                        () -> requestTradeSourceMode(VillagerConfig.TradeSourceMode.MERGE_ALL)),
                KineticOverlays.MenuItem.choice(tradeSourceModeLabel(VillagerConfig.TradeSourceMode.LOCAL_ONLY), null,
                        VillagerConfig.getTradeSourceMode() == VillagerConfig.TradeSourceMode.LOCAL_ONLY,
                        () -> requestTradeSourceMode(VillagerConfig.TradeSourceMode.LOCAL_ONLY))
        ), TRADE_MENU_WIDTH);
    }

    private void openTradeMenuInFrame(int x, int y, List<KineticOverlays.MenuItem> items, int preferredWidth) {
        int menuWidth = Math.min(preferredWidth, rootW - MENU_FRAME_INSET * 2);
        int menuX = Math.max(rootX + MENU_FRAME_INSET,
                Math.min(x, rootX + rootW - MENU_FRAME_INSET - menuWidth));
        openContextMenu(menuX, Math.max(rootY + MENU_FRAME_INSET, y), items, menuWidth);
    }

    private Component tradeSourceModeLabel(VillagerConfig.TradeSourceMode mode) {
        return KineticI18n.translatable("gui.contentstudio.villager.villager.trade.source_mode." + mode.configValue());
    }

    private void requestTradeSourceMode(VillagerConfig.TradeSourceMode mode) {
        if (savePending || pendingTradeSourceMode != null || mode == VillagerConfig.getTradeSourceMode()) return;
        pendingTradeSourceMode = mode;
        updatePendingControls();
        VillagerNetwork.saveTradeSourceMode(mode.configValue());
    }

    private void updateTradeSourceButton() {
        if (tradeSourceButton == null) return;
        tradeSourceButton.setEnabled(!isSavePending() && pendingTradeSourceMode == null);
        tradeSourceButton.setTooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.source_mode.tooltip",
                tradeSourceModeLabel(VillagerConfig.getTradeSourceMode())));
    }

    public void handleTradeSourceModeResult(boolean success, String mode, String failureCode) {
        pendingTradeSourceMode = null;
        if (success) {
            VillagerConfig.setTradeSourceMode(mode);
            invalidateTradeSearchIndex();
            refreshEntries();
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.source.changed",
                    tradeSourceModeLabel(VillagerConfig.getTradeSourceMode())));
        } else {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.source.save_failed." + failureCode(failureCode)));
        }
        updatePendingControls();
    }

    private static String failureCode(String code) {
        return code == null || code.isBlank() ? "unknown" : code;
    }

    private void saveValidTradeConfig() {
        if (savePending || pendingTradeSourceMode != null) return;
        syncFieldValues();
        if (levelSettingsActive) applyCurrentLevelSettings();
        VillagerConfig.enableCustomVillagerTrades = true;
        VillagerConfig.TradeValidationResult result = VillagerConfig.validateTradeListsDetailed(
                VillagerConfig.villagerTradeGroups, VillagerConfig.villagerTradeOffers,
                VillagerConfig.villagerDefaultTradeOverrides);
        validationIssues = List.copyOf(result.issues());
        validationActive = true;
        rebuildProblemTargets();
        pendingSavedCount = result.validOffers().size();
        pendingSkippedCount = result.skippedOfferCount();
        pendingHasIssues = !validationIssues.isEmpty();
        pendingDraftState = captureTradeConfigState();
        // The server normalizes accepted records (including supported 19-field offers). Compare its ACK against
        // that exact pure canonical form, while keeping the submitted raw draft intact for a partial save.
        VillagerConfig.TradeValidationResult canonical = VillagerConfig.canonicalizeValidTradeLists(
                result.validGroups(), result.validOffers(), result.validOverrides());
        pendingSavedState = new TradeConfigState(List.copyOf(canonical.validGroups()), List.copyOf(canonical.validOffers()),
                List.copyOf(canonical.validOverrides()), true, VillagerConfig.enableVillagerTradeLateOverride);
        savePending = true;
        updatePendingControls();
        VillagerNetwork.saveTradeConfig(result.validGroups(), result.validOffers(), result.validOverrides(),
                VillagerConfig.enableVillagerTradeLateOverride, true);
    }

    public void handleTradeSaveResult(boolean success, String failureCode) {
        if (!savePending) return;
        TradeConfigState currentState = captureTradeConfigState();
        boolean draftUnchanged = Objects.equals(pendingDraftState, currentState);
        boolean acceptedSubsetVisible = Objects.equals(pendingSavedState, currentState);
        savePending = false;
        if (!success) {
            exitAfterSuccessfulSave = false;
            updatePendingControls();
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.save_failed." + failureCode(failureCode)));
            return;
        }
        lastServerSavedState = pendingSavedState;
        if (pendingHasIssues) {
            exitAfterSuccessfulSave = false;
            // The integrated server shares these statics with the client and has just applied the valid subset.
            // Restore only a recognized response state, so a newer external draft is never overwritten.
            if (draftUnchanged || acceptedSubsetVisible) restoreTradeConfigState(pendingDraftState);
            updatePendingControls();
            refreshEntries();
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.saved_partial",
                    pendingSavedCount, pendingSkippedCount));
            focusCurrentProblem();
            return;
        }
        if (draftUnchanged || acceptedSubsetVisible) {
            restoreTradeConfigState(pendingSavedState);
            commitDraft();
            undoHistory.clear();
        } else {
            exitAfterSuccessfulSave = false;
        }
        updatePendingControls();
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.saved"));
        if (exitAfterSuccessfulSave) closeEditorSession(false);
    }

    private void updatePendingControls() {
        for (KineticControl widget : toolbarWidgets) widget.setEnabled(!isSavePending());
        if (professionBox != null) professionBox.setEnabled(!isSavePending());
        if (tradeSearchBox != null) tradeSearchBox.setEnabled(!isSavePending());
        if (lateOverrideButton != null) lateOverrideButton.setEnabled(!isSavePending());
        for (KineticControl widget : editorWidgets) widget.setEnabled(!isSavePending() && widget.controlVisible());
        for (KineticControl widget : levelWidgets) widget.setEnabled(!isSavePending() && widget.controlVisible());
        updateUndoButtonState();
        updateTradeSourceButton();
        updateProblemButtons();
    }

    private void rebuildProblemTargets() {
        problemTargets.clear();
        problemRawLines.clear();
        for (VillagerConfig.TradeValidationIssue issue : validationIssues) {
            boolean duplicate = problemTargets.stream().anyMatch(target -> target.kind() == issue.kind() && target.index() == issue.index());
            if (!duplicate) {
                problemTargets.add(issue);
                List<String> raw = rawProblemList(issue.kind());
                if (issue.index() >= 0 && issue.index() < raw.size()) problemRawLines.put(issue, raw.get(issue.index()));
            }
        }
        if (problemTargets.isEmpty()) problemCursor = -1;
        else if (problemCursor < 0 || problemCursor >= problemTargets.size()) problemCursor = 0;
        updateProblemButtons();
    }

    private void navigateProblem(int direction) {
        if (isSavePending() || problemTargets.isEmpty()) return;
        problemCursor = Math.floorMod(problemCursor + direction, problemTargets.size());
        focusCurrentProblem();
    }

    private void focusCurrentProblem() {
        if (problemCursor < 0 || problemCursor >= problemTargets.size()) return;
        VillagerConfig.TradeValidationIssue issue = problemTargets.get(problemCursor);
        clearTradeSearch();
        if (isValidOwner(issue.owner())) {
            selectedOwner = issue.owner();
            lastSelectedOwner = selectedOwner;
            selectedLevel = VillagerConfig.clampTradeLevel(selectedOwner, issue.level());
            lastSelectedLevel = selectedLevel;
            LEVEL_EXPANDED[selectedLevel - 1] = true;
            setProfessionBoxDisplay(selectedOwner);
            refreshEntries();
            boolean selected = false;
            if (issue.kind() == VillagerConfig.TradeIssueKind.OFFER) {
                for (TradeEntry entry : entries) {
                    if (entry.custom() && entry.customIndex() == issue.index()) {
                        selectEntry(entry);
                        selected = true;
                        break;
                    }
                }
            }
            if (!selected) selectLevelSettings(selectedLevel);
            int visible = Math.max(1, entries.size() - getMaxListScroll());
            listScroll.setOffset(selectedEntryIndex() - visible / 2);
            lastListScroll = listScroll.offset();
        }
        showWarningToast(issueComponent(issue));
        updateProblemButtons();
    }

    private boolean isProblemCustomIndex(int index) {
        return validationIssues.stream().anyMatch(issue -> issue.kind() == VillagerConfig.TradeIssueKind.OFFER && issue.index() == index);
    }

    private VillagerConfig.TradeValidationIssue firstCurrentSlotProblem(int slot) {
        if (editingCustomIndex < 0) return null;
        return validationIssues.stream().filter(issue -> issue.kind() == VillagerConfig.TradeIssueKind.OFFER
                && issue.index() == editingCustomIndex && issue.slot() == slot).findFirst().orElse(null);
    }

    private Component issueComponent(VillagerConfig.TradeValidationIssue issue) {
        String value = issue.value() == null ? "" : issue.value();
        return KineticI18n.translatable(issue.reasonKey(), value);
    }

    private List<String> rawProblemList(VillagerConfig.TradeIssueKind kind) {
        return switch (kind) {
            case GROUP -> VillagerConfig.villagerTradeGroups;
            case OFFER -> VillagerConfig.villagerTradeOffers;
            case OVERRIDE -> VillagerConfig.villagerDefaultTradeOverrides;
        };
    }

    private List<Component> problemDetails(VillagerConfig.TradeValidationIssue target) {
        return validationIssues.stream().filter(issue -> issue.kind() == target.kind() && issue.index() == target.index())
                .map(this::issueComponent).toList();
    }

    private boolean isProblemStatusHovered(double x, double y) {
        return !problemTargets.isEmpty() && x >= rightX + 8 && x < rightX + rightW - 62
                && y >= rightY + 64 && y < rightY + 82;
    }

    private boolean matchesProblemRawLine(VillagerConfig.TradeValidationIssue issue, String rawLine) {
        List<String> raw = rawProblemList(issue.kind());
        return rawLine != null && issue.index() >= 0 && issue.index() < raw.size()
                && Objects.equals(rawLine, raw.get(issue.index()));
    }

    private void openProblemMenu(int x, int y) {
        if (isSavePending() || problemCursor < 0 || problemCursor >= problemTargets.size()) return;
        VillagerConfig.TradeValidationIssue target = problemTargets.get(problemCursor);
        String rawLine = problemRawLines.get(target);
        List<KineticOverlays.MenuItem> menu = new ArrayList<>();
        for (Component detail : problemDetails(target)) {
            menu.add(KineticOverlays.MenuItem.disabled(detail, detail));
        }
        if (rawLine != null) menu.add(KineticOverlays.MenuItem.disabled(Component.literal(rawLine)));
        menu.add(KineticOverlays.MenuItem.separator());
        Component remove = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.error.remove");
        Component tooltip = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.error.remove.tooltip");
        menu.add(matchesProblemRawLine(target, rawLine)
                ? KineticOverlays.MenuItem.danger(remove, tooltip, () -> removeInvalidEntry(target, rawLine))
                : KineticOverlays.MenuItem.disabled(remove, tooltip));
        openTradeMenuInFrame(x, rootY + 30, menu, PROBLEM_MENU_WIDTH);
    }

    private void removeInvalidEntry(VillagerConfig.TradeValidationIssue issue, String rawLine) {
        if (isSavePending() || !matchesProblemRawLine(issue, rawLine)) return;
        // A stale popup cannot delete an entry which has since been repaired at the same raw index.
        boolean stillInvalid = VillagerConfig.validateTradeListsDetailed(VillagerConfig.villagerTradeGroups,
                VillagerConfig.villagerTradeOffers, VillagerConfig.villagerDefaultTradeOverrides).issues().stream()
                .anyMatch(current -> current.kind() == issue.kind() && current.index() == issue.index());
        if (!stillInvalid) return;
        UndoCheckpoint checkpoint = beginUndoableChange();
        List<String> groups = new ArrayList<>(VillagerConfig.villagerTradeGroups);
        List<String> offers = new ArrayList<>(VillagerConfig.villagerTradeOffers);
        List<String> overrides = new ArrayList<>(VillagerConfig.villagerDefaultTradeOverrides);
        switch (issue.kind()) {
            case GROUP -> groups.remove(issue.index());
            case OFFER -> offers.remove(issue.index());
            case OVERRIDE -> overrides.remove(issue.index());
        }
        VillagerConfig.replaceTradeLists(groups, offers, overrides);
        validationActive = true;
        selectedKey = "";
        editorActive = false;
        levelSettingsActive = false;
        editingCustomIndex = -1;
        editingVanillaIndex = -1;
        editingOriginalVanillaOffer = null;
        loadedOfferFormBaseline = null;
        loadedLevelOfferCountBaseline = null;
        setEditorWidgetsVisible(false);
        setLevelWidgetsVisible(false);
        invalidateTradeSearchIndex();
        refreshEntries();
        finishUndoableChange(checkpoint);
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.invalid_entry_removed"));
    }

    private void updateProblemButtons() {
        boolean visible = !problemTargets.isEmpty();
        if (previousIssueButton != null) {
            previousIssueButton.setControlVisible(visible);
            previousIssueButton.setEnabled(visible && !isSavePending() && problemTargets.size() > 1);
        }
        if (nextIssueButton != null) {
            nextIssueButton.setControlVisible(visible);
            nextIssueButton.setEnabled(visible && !isSavePending() && problemTargets.size() > 1);
        }
    }

    private void renderProblemStatus(KineticGraphics g) {
        if (problemTargets.isEmpty()) return;
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.error.navigation",
                problemCursor + 1, problemTargets.size()), rightX + 14, rightY + 69, rightW - 84, 0xFFFF5555, false);
    }

    private OfferFormState captureOfferFormState() {
        syncFieldValues();
        return new OfferFormState(buyAId, buyBId, sellId, buyANbt, buyBNbt, sellNbt, buyACount, buyBCount, sellCount,
                weight, maxUses, xp, price, demand, specialPrice, uses, rewardExp, allowRestock, rawRewardExp, rawAllowRestock);
    }

    private void markOfferFormBaseline() {
        loadedOfferFormBaseline = editorActive ? captureOfferFormState() : null;
        loadedLevelOfferCountBaseline = null;
    }

    private void markLevelSettingsBaseline() {
        loadedOfferFormBaseline = null;
        loadedLevelOfferCountBaseline = levelSettingsActive ? currentOfferCountValue() : null;
    }

    private boolean hasPendingOfferFormEdits() {
        return editorActive && loadedOfferFormBaseline != null && !Objects.equals(loadedOfferFormBaseline, captureOfferFormState());
    }

    private boolean hasPendingLevelSettingsEdits() {
        return levelSettingsActive && loadedLevelOfferCountBaseline != null && loadedLevelOfferCountBaseline != currentOfferCountValue();
    }

    private boolean hasPendingTradeChanges() {
        return hasUnsavedEdits() || hasPendingOfferFormEdits() || hasPendingLevelSettingsEdits() || !validationIssues.isEmpty();
    }

    @Override
    protected boolean onCloseRequested() {
        if (savePending || pendingTradeSourceMode != null) return true;
        if (!hasPendingTradeChanges()) {
            closeEditorSession(false);
            return true;
        }
        openTradeMenuInFrame(rootX + rootW - MENU_FRAME_INSET - TRADE_MENU_WIDTH, rootY + 30, List.of(
                KineticOverlays.MenuItem.action(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.exit.save"), this::saveAndExit),
                KineticOverlays.MenuItem.action(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.exit.discard"), () -> closeEditorSession(true)),
                KineticOverlays.MenuItem.action(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.exit.cancel"), () -> { })
        ), TRADE_MENU_WIDTH);
        return true;
    }

    private void saveAndExit() {
        if (savePending || pendingTradeSourceMode != null) return;
        if (hasPendingOfferFormEdits()) {
            saveCurrentOffer();
            if (hasPendingOfferFormEdits()) return;
        }
        if (hasPendingLevelSettingsEdits()) applyCurrentLevelSettings();
        exitAfterSuccessfulSave = true;
        saveValidTradeConfig();
    }

    private void closeEditorSession(boolean discardChanges) {
        exitAfterSuccessfulSave = false;
        shortcutSuspendedState = null;
        if (discardChanges) {
            discardDraft();
            // After a partial ACK the retained raw draft can equal Core's original baseline, so Core may skip
            // its restore callback. Discard still returns to the latest snapshot actually accepted by the server.
            restoreTradeConfigState(lastServerSavedState);
        }
        undoHistory.clear();
        VillagerClientActions.onTradeEditorSessionClosed(this);
        navigateBack();
    }

    public boolean isSavePending() {
        return savePending || pendingTradeSourceMode != null;
    }

    public void suspendForShortcut() {
        if (!isSavePending()) {
            shortcutSuspendedState = captureTradeEditorState();
            // Keep rejected/unsaved rows only in this suspended session, not in the shared client configuration.
            restoreTradeConfigState(lastServerSavedState);
        }
    }

    public void resumeFromShortcut() {
        if (shortcutSuspendedState != null) {
            // Core releases a standalone draft on navigateBack, even when this page's host is reused.
            // Its first capture must be the accepted baseline; subsequent captures see the suspended edits.
            boolean[] firstCapture = {true};
            configureStandaloneDraft(() -> {
                if (firstCapture[0]) {
                    firstCapture[0] = false;
                    return lastServerSavedState;
                }
                return captureTradeConfigState();
            }, state -> restoreTradeConfigState(lastServerSavedState == null ? state : lastServerSavedState));
            restoreTradeEditorState(shortcutSuspendedState, false);
            shortcutSuspendedState = null;
        }
    }

    private void setupLayout() {
        this.rootW = Math.min(this.width() - 8, 632);
        this.rootH = Math.min(this.height() - 8, 352);
        this.rootX = (this.width() - rootW) / 2;
        this.rootY = (this.height() - rootH) / 2;

        this.leftX = rootX + 8;
        this.leftY = rootY + 36;
        this.leftW = 230;
        this.leftPanelHeight = rootH - 44;

        this.listX = leftX + 6;
        this.listY = leftY + 32;
        this.listW = leftW - 12;
        this.listH = leftPanelHeight - 38;

        this.rightX = leftX + leftW + 8;
        this.rightY = leftY;
        this.rightW = rootX + rootW - rightX - 8;
        this.rightPanelHeight = leftPanelHeight;
    }

    private void addLevelExpandButtons() {
        levelExpandButtons.clear();
        for (int level = 1; level <= LEVEL_EXPANDED.length; level++) {
            int targetLevel = level;
            KineticButton button = ui().button(-10000, -10000, LEVEL_BUTTON_SIZE).text(levelExpandText(level)).compact().onClick(() -> {
                        selectedLevel = targetLevel;
                        lastSelectedLevel = targetLevel;
                        loadGroupState();
                        toggleLevelExpanded(targetLevel);
                        refreshEntries();
                    }).build();
            button.setControlVisible(false);
            button.setEnabled(false);
            levelExpandButtons.add(button);
        }
    }

    private Component levelExpandText(int level) {
        return Component.literal(isLevelExpanded(level) ? "▼" : "▶");
    }

    private void addLevelFields() {
        int buttonW = 92;
        int buttonX = rightX + rightW - 110;
        int buttonY = rightY + 8;
        int countX = buttonX - 70;

        this.offerCountBox = addLevelSmallNumberBox(countX, buttonY + 15, selectedOfferCount);

        KineticButton addTradeButton = ui().button(buttonX, buttonY, buttonW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.add_trade")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.add_trade.tooltip")).onClick(this::beginNewTradeFromLevel).build();
        addLevelWidget(addTradeButton);

        KineticButton clearLevelButton = ui().button(buttonX, buttonY + 24, buttonW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.clear")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.clear.tooltip")).onClick(() -> clearCurrentLevel(selectedLevel)).build();
        addLevelWidget(clearLevelButton);
    }

    private void addEditorFields() {
        int previewY = rightY + 90;
        int contentX = rightX + 14;
        int bX = contentX + 112;
        int sellX = contentX + 270;

        buyACountBox = addSmallNumberBox(contentX + SLOT_CONTROL_OFFSET, previewY + 18, buyACount, "gui.contentstudio.villager.villager.trade.count.buy_a.tooltip", 1);
        addNbtButton(0, contentX + SLOT_CONTROL_OFFSET, previewY + 40);
        addClearSlotButton(0, contentX + SLOT_CONTROL_OFFSET, previewY + 61);
        buyBCountBox = addSmallNumberBox(bX + SLOT_CONTROL_OFFSET, previewY + 18, buyBCount, "gui.contentstudio.villager.villager.trade.count.buy_b.tooltip", 0);
        addNbtButton(1, bX + SLOT_CONTROL_OFFSET, previewY + 40);
        addClearSlotButton(1, bX + SLOT_CONTROL_OFFSET, previewY + 61);
        sellCountBox = addSmallNumberBox(sellX + SLOT_CONTROL_OFFSET, previewY + 18, sellCount, "gui.contentstudio.villager.villager.trade.count.sell.tooltip", 1);
        addNbtButton(2, sellX + SLOT_CONTROL_OFFSET, previewY + 40);
        addClearSlotButton(2, sellX + SLOT_CONTROL_OFFSET, previewY + 61);

        int metaX = rightX + 14;
        int metaY = rightY + 196;
        int fieldW = 62;
        int fieldGap = META_FIELD_GAP;
        weightBox = addIntegerBox(metaX, metaY, fieldW, weight, "gui.contentstudio.villager.villager.trade.weight.tooltip", 0);
        maxUsesBox = addIntegerBox(metaX + fieldGap, metaY, fieldW, maxUses, "gui.contentstudio.villager.villager.trade.max_uses.tooltip", 0);
        xpBox = addIntegerBox(metaX + fieldGap * 2, metaY, fieldW, xp, "gui.contentstudio.villager.villager.trade.xp.tooltip", 0);
        priceBox = addDecimalBox(metaX + fieldGap * 3, metaY, fieldW, price);

        int metaY2 = metaY + 38;
        demandBox = addIntegerBox(metaX, metaY2, fieldW, demand, "gui.contentstudio.villager.villager.trade.demand.tooltip", -999999);
        specialPriceBox = addIntegerBox(metaX + fieldGap, metaY2, fieldW, specialPrice, "gui.contentstudio.villager.villager.trade.special_price.tooltip", -999999);
        usesBox = addIntegerBox(metaX + fieldGap * 2, metaY2, fieldW, uses, "gui.contentstudio.villager.villager.trade.uses.tooltip", 0);

        int toggleY = metaY2 + 34;
        this.rewardButton = ui().toggle(metaX, toggleY, 110)
                .compact()
                .value(rewardExp)
                .labels(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.reward.on"), KineticI18n.translatable("gui.contentstudio.villager.villager.trade.reward.off"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.reward.tooltip"))
                .onChange(value -> {
                    rewardExp = value;
                    rawRewardExp = Boolean.toString(value);
                    updateBoolButtons();
                })
                .build();
        addEditorWidget(this.rewardButton);

        this.restockButton = ui().toggle(metaX + 116, toggleY, 110)
                .compact()
                .value(allowRestock)
                .labels(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.on"), KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.off"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.tooltip"))
                .onChange(value -> {
                    allowRestock = value;
                    rawAllowRestock = Boolean.toString(value);
                    updateBoolButtons();
                })
                .build();
        addEditorWidget(this.restockButton);
    }

    private void addClearSlotButton(int slot, int x, int y) {
        KineticButton clear = ui().button(x, y, 36).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.slot.clear")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.slot.clear.tooltip")).compact().onClick(() -> clearTradeItemSlot(slot)).build();
        addEditorWidget(clear);
    }

    private void clearTradeItemSlot(int slot) {
        if (isSavePending()) return;
        if (slot == 0) {
            buyAId = "minecraft:air";
            buyACount = "1";
            buyANbt = "";
        } else if (slot == 1) {
            buyBId = "minecraft:air";
            buyBCount = "0";
            buyBNbt = "";
        } else {
            sellId = "minecraft:air";
            sellCount = "1";
            sellNbt = "";
        }
        writeFieldsToWidgets();
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.slot_cleared"));
    }

    private KineticNumberField addSmallNumberBox(
            int x,
            int y,
            String value,
            String tooltipKey,
            int minValue
    ) {
        KineticNumberField box = ui().numberField(x, y, 36, NumberType.INT).range(minValue, 64).tooltip(KineticI18n.translatable(tooltipKey)).build();

        box.limitTextLength(3);
        box.setTextValue(value);
        addEditorWidget(box);
        return box;
    }

    private KineticNumberField addLevelSmallNumberBox(
            int x,
            int y,
            int value
    ) {
        KineticNumberField box = ui().numberField(x, y, 50, NumberType.INT).range(0, 64).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer_count.tooltip")).build();

        box.limitTextLength(3);
        box.setIntValue(value);
        addLevelWidget(box);
        return box;
    }

    private KineticNumberField addIntegerBox(
            int x,
            int y,
            int width,
            String value,
            String tooltipKey,
            int minValue
    ) {
        KineticNumberField box = ui().numberField(x, y, width, NumberType.INT).range(minValue, 999999).tooltip(KineticI18n.translatable(tooltipKey)).build();

        box.limitTextLength(10);
        box.setTextValue(value);
        addEditorWidget(box);
        return box;
    }

    private KineticNumberField addDecimalBox(
            int x,
            int y,
            int width,
            String value
    ) {
        KineticNumberField box = ui().numberField(x, y, width, NumberType.DECIMAL).allowNegative(false).range(0.0, 1000.0).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.price.tooltip")).build();

        box.limitTextLength(10);
        box.setTextValue(value);
        addEditorWidget(box);
        return box;
    }

    private void addNbtButton(int slot, int x, int y) {
        KineticButton button = ui().button(x, y, 36).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.nbt.button")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.nbt.tooltip")).compact().onClick(() -> openNbtEditor(slot)).build();
        addEditorWidget(button);
    }

    private void addEditorButtons() {
        int y = rightY + 40;
        int x = rightX + 8;
        int saveW = 84;
        int deleteW = 84;
        int clearW = 96;
        int gap = 8;

        KineticButton saveButton = ui().button(x, y, saveW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer.save")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.save.tooltip")).onClick(this::saveCurrentOffer).build();
        addEditorWidget(saveButton);

        KineticButton deleteButton = ui().button(x + saveW + gap, y, deleteW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer.delete")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.delete.tooltip")).onClick(this::deleteSelectedEntry).build();
        addEditorWidget(deleteButton);

        KineticButton clearCurrentButton = ui().button(x + saveW + gap + deleteW + gap, y, clearW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.clear_current")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.clear_current.tooltip")).onClick(this::clearCurrentTarget).build();
        addEditorWidget(clearCurrentButton);
    }

    private void addEditorWidget(KineticControl widget) {
        this.editorWidgets.add(widget);
    }

    private void addLevelWidget(KineticControl widget) {
        this.levelWidgets.add(widget);
    }

    private void setEditorWidgetsVisible(boolean visible) {
        for (KineticControl widget : editorWidgets) {
            widget.setControlVisible(visible);
            widget.setEnabled(visible && !isSavePending());
        }
    }

    private void setLevelWidgetsVisible(boolean visible) {
        for (KineticControl widget : levelWidgets) {
            widget.setControlVisible(visible);
            widget.setEnabled(visible && !isSavePending());
        }
    }

    private void closeProfessionBoxFocus() {
        // 原 clearSuggestions()：失焦时自动完成框会自行清空候选 / Former clearSuggestions(): the field clears its suggestions when blurred.
        clearFocus();
    }

    @Override
    protected void onTick() {
        if (isSavePending()) return;
        syncFieldValues();
        syncSelectedOwnerFromBox();
        updateUndoButtonState();
    }

    @Override
    protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        updateLevelExpandButtons();
        KineticTheme.panel(g, rootX, rootY, rootW, rootH);
        KineticTheme.panelAlt(g, leftX, leftY, leftW, leftPanelHeight);
        KineticTheme.panelAlt(g, rightX, rightY, rightW, rightPanelHeight);
        if (!problemTargets.isEmpty()) {
            KineticTheme.panelAlt(g, rightX + 8, rightY + 64, rightW - 16, 18);
        }
        if (!selectedOwner.isEmpty() && editorActive && !levelSettingsActive) {
            renderTradeSlotPanels(g);
        }
        if (!selectedOwner.isEmpty() && !editorActive && !levelSettingsActive) {
            int textWidth = rightW - 36;
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.right.empty.title"), rightX + 18, rightY + 24, textWidth, 0xFFFFFF55, false);
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.right.empty.line1"), rightX + 18, rightY + 48, textWidth, 0xFFFFFFFF, false);
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.right.empty.line2"), rightX + 18, rightY + 66, textWidth, 0xFFFFFFFF, false);
        }
    }

    @Override
    protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
        renderLeftPanel(g, mx, my);
        renderRightPanel(g, mx, my);
        renderProfessionSearchHint(g);
        renderProblemStatus(g);
    }

    private void renderProfessionSearchHint(KineticGraphics g) {
        if (professionBox == null || !selectedOwner.isEmpty() || isTradeSearching()) {
            return;
        }
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.profession.hint"), listX + 4, listY + 8, listW - 8, 0xFFFFAA00, false);
    }


    private int smoothListStart() {
        return listScroll.smoothIndexOffset();
    }

    private int smoothListShift() {
        int start = smoothListStart();
        if (start < 0 || start >= entries.size()) {
            return 0;
        }
        TradeEntry entry = entries.get(start);
        int height = entry.header() ? LEVEL_ROW_H : ROW_H;
        return (int) Math.round(listScroll.fractionalOffset() * height);
    }

    private void renderLeftPanel(KineticGraphics g, int mx, int my) {
        if (canUseTradeList()) {
            KineticTheme.panelAlt(g, listX, listY, listW, listH);
            return;
        }

        KineticTheme.panelAlt(g, listX, listY, listW, listH);

        if (isTradeSearching() && entries.isEmpty()) {
            g.scrollingTextCentered(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.empty"), listX + listW / 2, listY + 10, listW - 8, 0xFFFFFF55, true);
            return;
        }

        int y = listY + 3 - smoothListShift();
        int start = smoothListStart();
        g.scissor(listX, listY + 3, listX + listW - 10, listY + listH - 3);
        for (int i = start; i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int h = entry.header() ? LEVEL_ROW_H : ROW_H;
            if (y >= listY + listH - 3) {
                break;
            }
            renderEntry(g, mx, my, entry, y, h);
            listScroll.renderSelectionFlash(g, i, listX + 4, y, listW - 16, h - 2);
            y += h;
        }
        g.endScissor();
        renderListScrollbar(g, mx, my);
    }

    private void updateLevelExpandButtons() {
        for (KineticButton button : levelExpandButtons) {
            button.setControlVisible(false);
            button.setEnabled(false);
            button.moveControlX(-10000);
            button.moveControlY(-10000);
        }
        if (selectedOwner.isEmpty() || !tradeSearchText().isEmpty()) {
            return;
        }

        int y = listY + 3 - smoothListShift();
        for (int i = smoothListStart(); i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int h = entry.header() ? LEVEL_ROW_H : ROW_H;
            if (y >= listY + listH - 3) {
                break;
            }
            if (entry.header() && y >= listY + 3 && y + h <= listY + listH - 3) {
                int index = entry.level() - 1;
                if (index >= 0 && index < levelExpandButtons.size()) {
                    KineticButton button = levelExpandButtons.get(index);
                    button.moveControlX(listX + 8);
                    // 3 px inside the 22 px row frame, 2 px clear of its lines.
                    button.moveControlY(y + 3);
                    button.setText(levelExpandText(entry.level()));
                    button.setControlVisible(true);
                    button.setEnabled(!isSavePending());
                }
            }
            y += h;
        }
    }

    private KineticButton hoveredLevelExpandButton(double mx, double my) {
        for (KineticButton button : levelExpandButtons) {
            if (button.controlVisible() && button.contains(mx, my)) {
                return button;
            }
        }
        return null;
    }

    private void renderEntry(KineticGraphics g, int mx, int my, TradeEntry entry, int y, int h) {
        boolean hover = mx >= listX + 4 && mx <= listX + listW - 10 && my >= y && my < y + h;
        boolean selected = Objects.equals(selectedKey, entry.key());
        KineticTheme.stateSurface(
                g,
                listX + 4,
                y,
                listW - 16,
                h - 2,
                entry.header() ? KineticTheme.Surface.PANEL : KineticTheme.Surface.PANEL_ALT,
                selected,
                hover,
                false
        );
        if (entry.custom() && isProblemCustomIndex(entry.customIndex())) {
            g.outline(listX + 4, y, listW - 16, h - 2, 0xFFFF5555);
        }

        if (entry.header()) {
            Component left = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.trade_list.level", entry.level(), modeName(entry.mode()));
            Component right = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.trade_list.level_summary", entry.levelCustomCount(), entry.levelOfferCount());

            int leftTextX = listX + 29;
            int rightPadding = 22;
            int minGap = 10;

            int rightTextEnd = listX + listW - rightPadding;
            // Fixed column shares prevent either translation from moving into its neighbor.
            int leftWidth = (rightTextEnd - leftTextX - minGap) / 2;
            int rightWidth = rightTextEnd - leftTextX - minGap - leftWidth;
            g.scrollingText(left, leftTextX, y + 7, leftWidth, 0xFFFFFF55, false);
            g.scrollingTextRight(right, rightTextEnd, y + 7, rightWidth, 0xFFFFFFFF, false);
            //? if >=26.1 {
            /*// On 26.1 the row surface covers the expand button, so the row draws its arrow; the button only takes the
            // click. Earlier versions show the button label, so drawing it here too would double the arrow.
            if (!isTradeSearching()) {
                g.scrollingTextCentered(levelExpandText(entry.level()), listX + 8 + LEVEL_BUTTON_SIZE / 2, y + 7,
                        LEVEL_BUTTON_SIZE - 4, 0xFFFFFFFF, true);
            }
            *///?}
            return;
        }

        Component source = KineticI18n.translatable(entry.custom() ? "gui.contentstudio.villager.villager.trade.source.custom" : "gui.contentstudio.villager.villager.trade.source.default");
        String offerMeta = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer.meta.weight", entry.weight(), entry.maxUses(), entry.xp(), entry.restock() ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.yes").getString() : KineticI18n.translatable("gui.contentstudio.villager.villager.trade.no").getString()).getString();
        String meta = isTradeSearching()
                ? KineticI18n.translatable(
                "gui.contentstudio.villager.villager.trade.content_search.result_meta",
                getProfessionName(entry.owner()),
                entry.level(),
                offerMeta
        ).getString()
                : offerMeta;

        int sourceColor = entry.custom() ? 0xFFFF55FF : 0xFF55FF55;
        int iconX = listX + 56;
        g.scrollingText(source, listX + 9, y + 4, iconX - (listX + 9) - TEXT_GAP, sourceColor, false);
        renderOfferIconLine(g, entry.offer(), iconX, y + 2);
        g.scrollingText(Component.literal(meta), listX + 9, y + 27, listW - 28, 0xFFFFFFFF, false);
    }

    private void renderOfferIconLine(KineticGraphics g, MerchantOffer offer, int x, int y) {
        if (offer == null) {
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer.invalid"), x, y + 5, listX + listW - 16 - x, 0xFFFF5555, false);
            return;
        }

        int slotY = y + 1;
        int plusX = x + 22;
        int bX = plusX + 12;
        int arrowX = bX + 22;
        int outX = arrowX + 16;

        renderMiniStack(g, offer.getBaseCostA(), x, slotY);
        g.text("+", plusX + 2, slotY + 6, 0xFFFFFF55, false);
        ItemStack buyB = offer.getCostB();
        renderMiniStack(g, buyB, bX, slotY);
        g.text("→", arrowX + 1, slotY + 6, 0xFF55FF55, false);
        renderMiniStack(g, offer.getResult(), outX, slotY);
    }

    private void renderMiniStack(KineticGraphics g, ItemStack stack, int x, int y) {
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        renderListFlatSlot(g, x, y);
        if (!safeStack.isEmpty()) {
            KineticTheme.item(g, safeStack, x, y, 18, 1.0F, false);
            renderGreenItemCount(g, safeStack, x, y);
        }
    }

    private void renderListFlatSlot(
            KineticGraphics graphics,
            int x,
            int y
    ) {
        KineticTheme.itemSlot(
                graphics,
                x,
                y,
                18,
                4,
                false
        );
    }

    private void renderGreenItemCount(KineticGraphics g, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }
        String countText = String.valueOf(stack.getCount());
        float scale = Math.min(1.0F, 16.0F / KineticText.width(countText));
        float textX = x + 17 - KineticText.width(countText) * scale;
        float textY = y + 17 - KineticText.lineHeight() * scale;
        // 原 flush + translate(z=300)：抬高一层盖过物品 / Former flush + translate(z=300): raise one layer above items.
        g.push();
        g.raise(1);
        g.translate(textX, textY);
        g.scale(scale, scale);
        g.text(countText, 0, 0, 0xFFFFFFFF, true);
        g.pop();
    }

    private void renderListScrollbar(
            KineticGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        updateMainListScrollRange();

        listScroll.render(
                graphics,
                mouseX,
                mouseY,
                listX + listW - 8,
                listY + 3,
                4,
                listH - 6,
                24
        );
    }

    private int getMaxListScroll() {
        int availableHeight = Math.max(
                1,
                listH - 6
        );

        int usedHeight = 0;
        int startIndex = entries.size();

        while (startIndex > 0) {
            TradeEntry entry =
                    entries.get(startIndex - 1);

            int entryHeight =
                    entry.header()
                            ? LEVEL_ROW_H
                            : ROW_H;

            if (usedHeight + entryHeight > availableHeight) {
                break;
            }

            usedHeight += entryHeight;
            startIndex--;
        }

        return Math.max(
                0,
                startIndex
        );
    }

    private void updateMainListScrollRange() {
        int maxScroll =
                getMaxListScroll();

        int visibleItems =
                Math.max(
                        1,
                        entries.size() - maxScroll
                );

        listScroll.updateRange(
                maxScroll,
                entries.size(),
                visibleItems
        );
    }

    private void renderRightPanel(KineticGraphics g, int mx, int my) {
        if (selectedOwner.isEmpty()) {
            return;
        }

        if (levelSettingsActive) {
            renderLevelSettingsPanel(g);
            return;
        }

        if (!editorActive) {
            return;
        }

        String ownerName = getProfessionName(selectedOwner);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.selected_profession", ownerName), rightX + 8, rightY + 8, rightW - 20, 0xFFFFFF55, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.selected_state", selectedLevel, modeName(selectedMode), countCustomOffers(selectedOwner, selectedLevel)), rightX + 8, rightY + 24, rightW - 20, 0xFFFFFFFF, false);

        int noticeX = rightX + 8;
        int noticeY = rightY + 64;
        KineticTheme.panelAlt(g, noticeX, noticeY, rightW - (problemTargets.isEmpty() ? 16 : 76), 18);
        Component state = editingDefault ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.editing_default") : (editingCustomIndex >= 0 ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.editing_custom") : KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.creating"));
        if (problemTargets.isEmpty()) {
            g.scrollingText(state, noticeX + 6, noticeY + 5, rightW - 28, 0xFFFF55FF, false);
        }

        renderTradeSlots(g, mx, my);

        int metaX = rightX + 14;
        int metaY = rightY + 196;
        int metaY2 = metaY + 38;
        int fieldGap = META_FIELD_GAP;
        int labelWidth = fieldGap - TEXT_GAP;
        int lastLabelWidth = rightX + rightW - 8 - (metaX + fieldGap * 3) - TEXT_GAP;
        int labelY = metaY - 14;
        int labelY2 = metaY2 - 14;
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.weight"), metaX, labelY, labelWidth, 0xFFFFFF55, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.max_uses"), metaX + fieldGap, labelY, labelWidth, 0xFFFFFF55, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.xp"), metaX + fieldGap * 2, labelY, labelWidth, 0xFFFFFF55, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.price"), metaX + fieldGap * 3, labelY, lastLabelWidth, 0xFFFFFF55, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.demand"), metaX, labelY2, labelWidth, 0xFFFFFFFF, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.special_price"), metaX + fieldGap, labelY2, labelWidth, 0xFFFFFFFF, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.uses"), metaX + fieldGap * 2, labelY2, labelWidth, 0xFFFFFFFF, false);
        if (!isRawBoolean(rawRewardExp)) g.outline(metaX - 1, metaY2 + 33, 112, CONTROL_HEIGHT + 2, 0xFFFF5555);
        if (!isRawBoolean(rawAllowRestock)) g.outline(metaX + 115, metaY2 + 33, 112, CONTROL_HEIGHT + 2, 0xFFFF5555);
    }

    private void renderLevelSettingsPanel(KineticGraphics g) {
        String ownerName = getProfessionName(selectedOwner);
        int defaultCount = VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, selectedLevel);
        int controlRightX = rightX + rightW - 190;

        int summaryWidth = controlRightX - (rightX + 8) - TEXT_GAP;
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.selected_profession", ownerName), rightX + 8, rightY + 8, summaryWidth, 0xFFFFFF55, false);
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.settings_state", selectedLevel, modeName(selectedMode), selectedOfferCount, defaultCount), rightX + 8, rightY + 24, summaryWidth, 0xFFFFFFFF, false);

        int buttonW = 92;
        int buttonX = rightX + rightW - 110;
        int buttonY = rightY + 8;
        int countX = buttonX - 70;

        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer_count.label"), countX, buttonY, buttonX - countX - TEXT_GAP, 0xFFFFFF55, false);
        // The settings title shares its row with the bottom of the action controls.
        g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.settings.title"), rightX + 8, rightY + 48, summaryWidth, 0xFFFFFF55, false);
        if (problemTargets.isEmpty()) {
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.settings.tip"), rightX + 8, rightY + 64, rightW - 20, 0xFFFFFFFF, false);
        }

        KineticTheme.stateOutline(g, countX - 8, buttonY - 4, buttonW + 82, 52, false, false, false);
    }

    private void renderTradeSlotPanels(KineticGraphics g) {
        int previewY = rightY + 90;
        int contentX = rightX + 14;
        int bX = contentX + 112;
        int sellX = contentX + 270;

        renderTradeSlotSection(g, contentX, previewY);
        renderTradeSlotSection(g, bX, previewY);
        renderTradeSlotSection(g, sellX, previewY);
    }

    private void renderTradeSlots(KineticGraphics g, int mx, int my) {
        int previewY = rightY + 90;
        int contentX = rightX + 14;
        int bX = contentX + 112;
        int sellX = contentX + 270;
        int arrow1X = contentX + 88;
        int arrow2X = contentX + 230;

        renderSlotCell(g, mx, my, 0, contentX, previewY, buyAId, buyACount, buyANbt);
        renderLargeTradeSymbol(g, "+", arrow1X, previewY + 32);
        renderSlotCell(g, mx, my, 1, bX, previewY, buyBId, buyBCount, buyBNbt);
        renderLargeTradeSymbol(g, "→", arrow2X, previewY + 36);
        renderSlotCell(g, mx, my, 2, sellX, previewY, sellId, sellCount, sellNbt);
    }

    private void renderTradeSlotSection(KineticGraphics g, int x, int y) {
        KineticTheme.panelAlt(g, x - 6, y - 6, TRADE_SLOT_PANEL_WIDTH, 91);
    }

    private void renderLargeTradeSymbol(KineticGraphics g, String text, int centerX, int centerY) {
        float scale = 5.0F;
        g.push();
        float drawX = centerX - KineticText.width(text) * scale / 2.0F;
        float drawY = centerY - KineticText.lineHeight() * scale / 2.0F;
        g.translate(drawX, drawY);
        g.scale(scale, scale);
        g.text(text, 0, 0, 0xFFFFFF55, false);
        g.pop();
    }

    private void renderSlotCell(KineticGraphics g, int mx, int my, int slot, int x, int y, String id, String count, String nbt) {
        Component label = KineticI18n.translatable(slot == 0 ? "gui.contentstudio.villager.villager.trade.slot.buy_a" : slot == 1 ? "gui.contentstudio.villager.villager.trade.slot.buy_b" : "gui.contentstudio.villager.villager.trade.slot.sell");
        ItemStack stack = createStack(id, VillagerConfig.parseIntValue(count, slot == 1 ? 0 : 1, slot == 1 ? 0 : 1, 64), nbt);
        int slotY = y + 20;

        int labelColor = slot == 0 ? BUY_A_LABEL : slot == 1 ? BUY_B_LABEL : SELL_LABEL;
        g.scrollingText(label, x, y, TRADE_SLOT_PANEL_WIDTH - 6 - TEXT_GAP, labelColor, false);
        renderInsetSlot(g, x, slotY, isHoverSlot(mx, my, slot));
        if (firstCurrentSlotProblem(slot) != null) g.outline(x, slotY, 24, 24, 0xFFFF5555);
        if (!stack.isEmpty()) {
            g.item(stack, x + 4, slotY + 4);
            g.itemDecorations(stack, x + 4, slotY + 4);
        }
    }

    private void renderInsetSlot(
            KineticGraphics graphics,
            int x,
            int y,
            boolean hovered
    ) {
        KineticTheme.itemSlot(
                graphics,
                x,
                y,
                24,
                4,
                hovered
        );
    }

    @Override
    protected void renderTooltips(int smx, int smy) {
        if (isProblemStatusHovered(smx, smy) && problemCursor >= 0 && problemCursor < problemTargets.size()) {
            List<Component> details = new ArrayList<>(problemDetails(problemTargets.get(problemCursor)));
            details.add(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.error.details.tooltip"));
            showTooltip(details);
            return;
        }
        if (hoveredLevelExpandButton(smx, smy) != null) {
            showTooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.arrow.tooltip"));
            return;
        }
        int hoverSlot = hoveredSlot(smx, smy);
        if (hoverSlot >= 0) {
            List<Component> issues = validationIssues.stream().filter(issue -> issue.kind() == VillagerConfig.TradeIssueKind.OFFER
                    && issue.index() == editingCustomIndex && issue.slot() == hoverSlot).map(this::issueComponent).toList();
            if (!issues.isEmpty()) {
                showTooltip(issues);
                return;
            }
            String key = hoverSlot == 0 ? "gui.contentstudio.villager.villager.trade.slot.buy_a.tooltip" : hoverSlot == 1 ? "gui.contentstudio.villager.villager.trade.slot.buy_b.tooltip" : "gui.contentstudio.villager.villager.trade.slot.sell.tooltip";
            showTooltip(KineticI18n.translatable(key));
            return;
        }
        TradeEntry hoveredEntry = findEntryAt(smx, smy);
        if (hoveredEntry != null && hoveredEntry.custom()) {
            List<Component> issues = validationIssues.stream().filter(issue -> issue.kind() == VillagerConfig.TradeIssueKind.OFFER
                    && issue.index() == hoveredEntry.customIndex()).map(this::issueComponent).toList();
            if (!issues.isEmpty()) {
                showTooltip(issues);
                return;
            }
        }
        ItemStack hoveredListStack = findHoveredLeftListStack(smx, smy);
        if (!hoveredListStack.isEmpty()) {
            showItemTooltip(hoveredListStack);
            return;
        }
        if (hoveredEntry != null && hoveredEntry.header()) {
            showTooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.row.tooltip"));
        }
    }

    private ItemStack findHoveredLeftListStack(double mx, double my) {
        if (canUseTradeList() || mx < listX || mx > listX + listW || my < listY || my > listY + listH) {
            return ItemStack.EMPTY;
        }

        int y = listY + 3 - smoothListShift();
        int start = smoothListStart();
        for (int i = start; i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int h = entry.header() ? LEVEL_ROW_H : ROW_H;
            if (y >= listY + listH - 3) {
                break;
            }

            if (!entry.header() && entry.offer() != null) {
                int iconX = listX + 56;
                int iconY = y + 3;
                MerchantOffer offer = entry.offer();

                ItemStack hovered = stackAtMiniSlot(mx, my, iconX, iconY, offer.getBaseCostA());
                if (!hovered.isEmpty()) {
                    return hovered;
                }

                hovered = stackAtMiniSlot(mx, my, iconX + 34, iconY, offer.getCostB());
                if (!hovered.isEmpty()) {
                    return hovered;
                }

                hovered = stackAtMiniSlot(mx, my, iconX + 72, iconY, offer.getResult());
                if (!hovered.isEmpty()) {
                    return hovered;
                }
            }
            y += h;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack stackAtMiniSlot(double mx, double my, int x, int y, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return mx >= x && mx < x + 18 && my >= y && my < y + 18 ? stack : ItemStack.EMPTY;
    }

    private TradeEntry findEntryAt(double mx, double my) {
        if (canUseTradeList() || mx < listX || mx > listX + listW || my < listY || my > listY + listH) {
            return null;
        }
        int y = listY + 3 - smoothListShift();
        for (int i = smoothListStart(); i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int h = entry.header() ? LEVEL_ROW_H : ROW_H;
            if (y >= listY + listH - 3) {
                break;
            }
            if (my >= y && my < y + h) {
                return entry;
            }
            y += h;
        }
        return null;
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        if (isSavePending()) return true;
        // 原 canvasMouseClicked 全部在控件之前处理 / The old canvasMouseClicked handled all of this before controls.
        double mx = input.x();
        double my = input.y();
        if ((input.isLeft() || input.isRight()) && isProblemStatusHovered(mx, my)) {
            openProblemMenu((int) mx, (int) my);
            return true;
        }
        updateMainListScrollRange();

        if (listScroll.beginDrag(
                mx,
                my,
                input.button(),
                listX + listW - 8,
                listY + 3,
                4,
                listH - 6,
                24,
                2
        )) {
            lastListScroll =
                    listScroll.offset();
            return true;
        }

        // 原先手动转发给展开按钮；现在左键交给该按钮控件本身处理（其它键仍按原样落到列表）
        // Formerly forwarded by hand to the expand button; a left click now falls through to the button control
        // itself (other buttons still reach the list as before).
        if (input.isLeft() && hoveredLevelExpandButton(mx, my) != null) {
            return false;
        }
        if (handleListClick(mx, my, input)) {
            return true;
        }
        if (input.isRight()) {
            int slot = hoveredSlot((int) mx, (int) my);
            if (slot >= 0) {
                clearTradeItemSlot(slot);
                return true;
            }
        }
        return input.isLeft() && handleSlotClick(mx, my);
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        return listScroll.release(input.button());
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        if (isSavePending()) return true;
        double my = input.y();
        if (listScroll.drag(
                my,
                listY + 3,
                listH - 6,
                24
        )) {
            lastListScroll =
                    listScroll.offset();
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        if (isSavePending()) return true;
        double mx = input.x();
        double my = input.y();
        double delta = input.deltaY();
        if (mx >= listX
                && mx <= listX + listW
                && my >= listY
                && my <= listY + listH) {
            updateMainListScrollRange();

            if (listScroll.scroll(delta)) {
                lastListScroll =
                        listScroll.offset();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onKeyPress(KeyInput input) {
        if (isSavePending()) return true;
        if (input.is(KineticKeyBindings.Key.Z) && KineticClientRuntime.controlModifierDown()) {
            if (!undoHistory.isEmpty()) {
                undoLastChange();
            }
            return true;
        }
        if (isFocused(professionBox)
                && (input.is(KineticKeyBindings.Key.ENTER)
                || input.is(KineticKeyBindings.Key.KP_ENTER))) {
            syncSelectedOwnerFromBox();
            closeProfessionBoxFocus();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCharTyped(dev.xyat.kineticcore.api.client.gui.input.CharInput input) {
        return isSavePending();
    }

    private boolean handleListClick(double mx, double my, MouseInput input) {
        if (canUseTradeList() || mx < listX || mx > listX + listW || my < listY || my > listY + listH) {
            return false;
        }
        int y = listY + 3 - smoothListShift();
        for (int i = smoothListStart(); i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int h = entry.header() ? LEVEL_ROW_H : ROW_H;
            if (y >= listY + listH - 3) {
                break;
            }
            if (my >= y && my < y + h) {
                if (entry.header()) {
                    selectedLevel = entry.level();
                    lastSelectedLevel = selectedLevel;
                    loadGroupState();

                    selectedKey = entry.key();

                    if (input.isRight()) {
                        clearCurrentLevel(entry.level());
                        return true;
                    }

                    if (input.isLeft()) {
                        selectLevelSettings(entry.level());
                        refreshEntries();
                        return true;
                    }
                }
                if (input.isRight()) {
                    deleteEntry(entry);
                    return true;
                }
                if (input.isLeft()) {
                    selectEntry(entry);
                    return true;
                }
            }
            y += h;
        }
        return false;
    }

    private boolean handleSlotClick(double mx, double my) {
        int slot = hoveredSlot((int) mx, (int) my);
        if (slot >= 0) {
            openItemSelector(slot);
            return true;
        }
        return false;
    }

    private int hoveredSlot(int mx, int my) {
        if (selectedOwner.isEmpty() || !editorActive) {
            return -1;
        }
        int previewY = rightY + 90;
        int contentX = rightX + 14;
        int[] xs = {contentX, contentX + 112, contentX + 270};
        int slotY = previewY + 20;
        for (int i = 0; i < xs.length; i++) {
            if (mx >= xs[i] && mx <= xs[i] + 24 && my >= slotY && my <= slotY + 24) {
                return i;
            }
        }
        return -1;
    }

    private boolean isHoverSlot(int mx, int my, int slot) {
        return hoveredSlot(mx, my) == slot;
    }

    private void syncSelectedOwnerFromBox() {
        if (professionBox == null || suppressProfessionResponder) {
            return;
        }
        String value = professionBox.textValue();
        if (!Objects.equals(lastProfessionText, value)) {
            lastProfessionText = value;
            trySelectOwnerFromText(value);
        }
    }

    private void trySelectOwnerFromText(String text) {
        String owner = resolveOwner(text);
        if (!isValidOwner(owner)) {
            return;
        }
        if (!owner.equals(selectedOwner)) {
            selectedOwner = owner;
            lastSelectedOwner = owner;
            selectedLevel = VillagerConfig.clampTradeLevel(owner, selectedLevel);
            lastSelectedLevel = selectedLevel;
            listScroll.reset();
            lastListScroll = 0;
            editingCustomIndex = -1;
            editingVanillaIndex = -1;
            loadGroupState();
            loadGroupState();
            refreshEntries();
            editorActive = false;
            levelSettingsActive = false;
            setEditorWidgetsVisible(false);
            setLevelWidgetsVisible(false);
        }
        setProfessionBoxDisplay(owner);
    }

    private String resolveOwner(String text) {
        String raw = text == null ? "" : text.trim();
        String clean = VillagerConfig.clean(raw);
        if (isValidOwner(clean)) {
            return clean;
        }
        for (KineticSuggestion option : getProfessionDictionary()) {
            String id = option.value();
            String name = option.translation().getString();
            if ((!name.isBlank() && name.equalsIgnoreCase(raw)) || id.equalsIgnoreCase(raw)) {
                return id;
            }
        }
        return clean;
    }

    private void setProfessionBoxDisplay(String owner) {
        if (professionBox == null) {
            return;
        }
        suppressProfessionResponder = true;
        String display = VillagerConfig.clean(owner);
        professionBox.setTextValue(display);
        professionBox.setCursorIndex(display.length());
        closeProfessionBoxFocus();
        lastProfessionText = display;
        suppressProfessionResponder = false;
    }

    private void clearProfessionSearch() {
        selectedOwner = "";
        lastSelectedOwner = "";
        selectedKey = "";
        editorActive = false;
        levelSettingsActive = false;
        listScroll.reset();
        lastListScroll = 0;
        entries.clear();
        loadBlankOffer();
        setEditorWidgetsVisible(false);
        setLevelWidgetsVisible(false);
        if (professionBox != null) {
            suppressProfessionResponder = true;
            professionBox.setTextValue("");
            closeProfessionBoxFocus();
            suppressProfessionResponder = false;
        }
        lastProfessionText = "";
        refreshEntries();
    }

    private void clearTradeSearch() {
        lastTradeSearchText = "";
        listScroll.reset();
        lastListScroll = 0;
        if (tradeSearchBox != null && !tradeSearchBox.textValue().isEmpty()) {
            tradeSearchBox.setTextValue("");
        } else {
            refreshEntries();
        }
    }

    private boolean isValidOwner(String owner) {
        String clean = VillagerConfig.clean(owner);
        if (clean.equals(VillagerConfig.WANDERING_TRADER_ID)) {
            return true;
        }
        ResourceLocation id = KineticResourceIds.tryParse(clean);
        return id != null && KineticRegistries.villagerProfessions().contains(id);
    }

    private void loadGroupState() {
        if (selectedOwner.isEmpty()) {
            selectedMode = "replace_level";
            selectedOfferCount = 0;
            return;
        }
        selectedLevel = VillagerConfig.clampTradeLevel(selectedOwner, selectedLevel);
        VillagerConfig.TradeGroup group = VillagerConfig.getTradeGroup(selectedOwner, selectedLevel);
        selectedMode = group == null ? "replace_level" : group.mode;
        selectedOfferCount = group == null ? VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, selectedLevel) : group.offerCount;
        if (offerCountBox != null) {
            offerCountBox.setTextValue(String.valueOf(selectedOfferCount));
        }
        applyFieldDefaults();
    }

    private int currentOfferCountValue() {
        if (offerCountBox != null) {
            Integer value =
                    offerCountBox.getIntValue();

            if (value != null) {
                return value;
            }
        }

        return VillagerTradeRuntimeUtil.getDefaultOfferCount(
                selectedOwner,
                selectedLevel
        );
    }

    private boolean isLevelExpanded(int level) {
        int index = level - 1;
        return index >= 0 && index < LEVEL_EXPANDED.length && LEVEL_EXPANDED[index];
    }

    private void toggleLevelExpanded(int level) {
        int index = level - 1;
        if (index >= 0 && index < LEVEL_EXPANDED.length) {
            LEVEL_EXPANDED[index] = !LEVEL_EXPANDED[index];
        }
    }

    private void refreshEntries() {
        if (!isSavePending() && validationActive) {
            validationIssues = List.copyOf(VillagerConfig.validateTradeListsDetailed(VillagerConfig.villagerTradeGroups,
                    VillagerConfig.villagerTradeOffers, VillagerConfig.villagerDefaultTradeOverrides).issues());
            rebuildProblemTargets();
        }
        String keepKey = selectedKey;
        entries.clear();

        String search = tradeSearchText();
        if (!search.isEmpty()) {
            if (tradeSearchIndexDirty) {
                rebuildTradeSearchIndex();
            }
            tradeSearchModel.refresh(search);
            for (TradeSearchEntry result : tradeSearchModel.items()) {
                entries.add(result.entry());
            }
            restoreSelectedKey(keepKey);
            updateMainListScrollRange();
            lastListScroll = listScroll.offset();
            return;
        }

        if (selectedOwner.isEmpty()) {
            selectedKey = "";
            updateMainListScrollRange();
            lastListScroll = listScroll.offset();
            return;
        }

        // Invalid offers stay visible above collapsed levels so a partial save can be repaired in place.
        int ownerMaxLevel = VillagerConfig.isWanderingTrader(selectedOwner) ? 2 : 5;
        for (int level = 1; level <= ownerMaxLevel; level++) {
            for (VillagerConfig.TradeOfferData data : getEditorOffers(selectedOwner, level)) {
                if (isProblemCustomIndex(data.index())) entries.add(TradeEntry.custom(selectedOwner, data));
            }
        }

        int maxLevel = VillagerConfig.isWanderingTrader(selectedOwner) ? 2 : 5;
        for (int level = 1; level <= maxLevel; level++) {
            VillagerConfig.TradeGroup group = VillagerConfig.getTradeGroup(selectedOwner, level);
            String mode = group == null ? "replace_level" : group.mode;
            int levelOfferCount = group == null
                    ? VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, level)
                    : group.offerCount;
            int customCount = countCustomOffers(selectedOwner, level);
            entries.add(TradeEntry.header(selectedOwner, level, mode, levelOfferCount, customCount));

            if (!isLevelExpanded(level)) {
                continue;
            }

            int vanillaCount = VillagerTradeRuntimeUtil.vanillaTradeCount(selectedOwner, level);
            if (!VillagerConfig.isLocalCustomTradesOnly() && vanillaCount > 0) {
                for (int i = 0; i < vanillaCount; i++) {
                    MerchantOffer offer = VillagerTradeRuntimeUtil.createPreviewOffer(selectedOwner, level, i);
                    if (offer != null && VillagerConfig.isVanillaTradeEnabled(selectedOwner, level, i)) {
                        entries.add(TradeEntry.vanilla(
                                selectedOwner,
                                level,
                                i,
                                offer,
                                VillagerConfig.getVanillaTradeWeight(selectedOwner, level, i)
                        ));
                    }
                }
            }

            for (VillagerConfig.TradeOfferData data : getEditorOffers(selectedOwner, level)) {
                if (!isProblemCustomIndex(data.index())) entries.add(TradeEntry.custom(selectedOwner, data));
            }
        }

        restoreSelectedKey(keepKey);
        updateMainListScrollRange();
        lastListScroll = listScroll.offset();
    }

    private void rebuildTradeSearchIndex() {
        tradeSearchSource.clear();

        for (KineticSuggestion option : getProfessionDictionary()) {
            String owner = option.value();
            if (!isValidOwner(owner)) {
                continue;
            }

            int maxLevel = VillagerConfig.isWanderingTrader(owner) ? 2 : 5;
            for (int level = 1; level <= maxLevel; level++) {
                int vanillaCount = VillagerTradeRuntimeUtil.vanillaTradeCount(owner, level);
                if (!VillagerConfig.isLocalCustomTradesOnly() && vanillaCount > 0) {
                    for (int i = 0; i < vanillaCount; i++) {
                        if (!VillagerConfig.isVanillaTradeEnabled(owner, level, i)) {
                            continue;
                        }
                        MerchantOffer offer = VillagerTradeRuntimeUtil.createPreviewOffer(owner, level, i);
                        if (offer == null) {
                            continue;
                        }
                        TradeEntry entry = TradeEntry.vanilla(
                                owner,
                                level,
                                i,
                                offer,
                                VillagerConfig.getVanillaTradeWeight(owner, level, i)
                        );
                        tradeSearchSource.add(new TradeSearchEntry(entry, buildTradeSearchText(entry)));
                    }
                }

                for (VillagerConfig.TradeOfferData data : getEditorOffers(owner, level)) {
                    TradeEntry entry = TradeEntry.custom(owner, data);
                    tradeSearchSource.add(new TradeSearchEntry(entry, buildTradeSearchText(entry)));
                }
            }
        }

        tradeSearchModel.setSource(tradeSearchSource);
        tradeSearchIndexDirty = false;
    }

    private String buildTradeSearchText(TradeEntry entry) {
        StringBuilder text = new StringBuilder();
        if (entry.customIndex() >= 0 && entry.customIndex() < VillagerConfig.villagerTradeOffers.size()) {
            text.append(VillagerConfig.villagerTradeOffers.get(entry.customIndex())).append(' ');
        }
        String ownerName = getProfessionName(entry.owner());
        text.append(entry.owner()).append(' ')
                .append(ownerName).append(' ')
                .append(KineticSearch.pinyin(ownerName)).append(' ')
                .append(entry.level()).append(' ')
                .append(entry.weight()).append(' ')
                .append(entry.maxUses()).append(' ')
                .append(entry.xp()).append(' ')
                .append(entry.restock()).append(' ')
                .append(KineticI18n.translatable(
                        entry.custom()
                                ? "gui.contentstudio.villager.villager.trade.source.custom"
                                : "gui.contentstudio.villager.villager.trade.source.default"
                ).getString()).append(' ');

        appendOfferSearchText(text, entry.offer());

        VillagerConfig.TradeOfferData data = entry.data();
        if (data != null) {
            text.append(data.buyAItem()).append(' ')
                    .append(data.buyACount()).append(' ')
                    .append(data.buyANbt()).append(' ')
                    .append(data.buyBItem()).append(' ')
                    .append(data.buyBCount()).append(' ')
                    .append(data.buyBNbt()).append(' ')
                    .append(data.sellItem()).append(' ')
                    .append(data.sellCount()).append(' ')
                    .append(data.sellNbt()).append(' ')
                    .append(data.maxUses()).append(' ')
                    .append(data.xp()).append(' ')
                    .append(data.priceMultiplier()).append(' ')
                    .append(data.demand()).append(' ')
                    .append(data.specialPrice()).append(' ')
                    .append(data.rewardExp()).append(' ')
                    .append(data.uses()).append(' ')
                    .append(data.allowRestock()).append(' ')
                    .append(data.weight()).append(' ');
        }

        return text.toString();
    }

    private void appendOfferSearchText(StringBuilder text, MerchantOffer offer) {
        if (offer == null) {
            return;
        }
        appendStackSearchText(text, offer.getBaseCostA());
        appendStackSearchText(text, offer.getCostB());
        appendStackSearchText(text, offer.getResult());
    }

    private void appendStackSearchText(StringBuilder text, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String hoverName = stack.getHoverName().getString();
        text.append(VillagerConfig.itemId(stack)).append(' ')
                .append(hoverName).append(' ')
                .append(KineticSearch.pinyin(hoverName)).append(' ')
                .append(stack.getCount()).append(' ');
//? if >=1.21 {
/*        text.append(dev.xyat.contentstudio.item.ItemData.format(stack)).append(' ');
*///?} else {
        if (stack.getTag() != null && !stack.getTag().isEmpty()) {
            text.append(stack.getTag()).append(' ');
        }
//?}
    }

    private void restoreSelectedKey(String keepKey) {
        for (TradeEntry entry : entries) {
            if (Objects.equals(entry.key(), keepKey)) {
                selectedKey = keepKey;
                return;
            }
        }
        selectedKey = "";
    }

    private void invalidateTradeSearchIndex() {
        tradeSearchIndexDirty = true;
    }

    private boolean isTradeSearching() {
        return !tradeSearchText().isEmpty();
    }

    private boolean canUseTradeList() {
        return selectedOwner.isEmpty() && !isTradeSearching();
    }

    private String tradeSearchText() {
        String value = tradeSearchBox == null ? lastTradeSearchText : tradeSearchBox.textValue();
        return value == null ? "" : value.trim();
    }


    private int countCustomOffers(String owner, int level) {
        return getEditorOffers(owner, level).size();
    }

    private List<VillagerConfig.TradeOfferData> getEditorOffers(String owner, int level) {
        List<VillagerConfig.TradeOfferData> offers = new ArrayList<>();
        List<String> rawOffers = new ArrayList<>(VillagerConfig.villagerTradeOffers);
        for (int rawIndex = 0; rawIndex < rawOffers.size(); rawIndex++) {
            // Runtime caches deliberately reject invalid rows. The editor needs their tolerant form and raw index
            // so issue navigation, in-place repair and deletion still refer to the original config record.
            VillagerConfig.TradeOfferData data = VillagerConfig.TradeOfferData.parse(rawOffers.get(rawIndex), rawIndex);
            if (data != null && data.matches(owner, level)) offers.add(data);
        }
        return offers;
    }

    private void selectLevelSettings(int level) {
        selectedLevel = VillagerConfig.clampTradeLevel(selectedOwner, level);
        lastSelectedLevel = selectedLevel;
        loadGroupState();
        selectedKey = selectedOwner + "|level:" + selectedLevel;
        editorActive = false;
        levelSettingsActive = true;
        setEditorWidgetsVisible(false);
        setLevelWidgetsVisible(true);
        writeFieldsToWidgets();
        markLevelSettingsBaseline();
    }

    private void applyCurrentLevelSettings() {
        if (selectedOwner.isEmpty()) {
            return;
        }
        selectedOfferCount = currentOfferCountValue();
        selectedMode = "replace_level";
        VillagerConfig.setTradeGroup(new VillagerConfig.TradeGroup(selectedOwner, selectedLevel, selectedMode, selectedOfferCount));
        refreshEntries();
        writeFieldsToWidgets();
        markLevelSettingsBaseline();
    }

    private void beginNewTradeFromLevel() {
        if (selectedOwner.isEmpty()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        UndoCheckpoint checkpoint = beginUndoableChange();
        applyCurrentLevelSettings();
        clearSelectionToNewOffer();
        refreshEntries();
        finishUndoableChange(checkpoint);
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.new_draft"));
    }

    private void selectEntry(TradeEntry entry) {
        if (entry == null || entry.header()) {
            return;
        }
        syncFieldValues();
        selectEntryOwner(entry);
        editorActive = true;
        levelSettingsActive = false;
        setLevelWidgetsVisible(false);
        setEditorWidgetsVisible(true);
        selectedLevel = entry.level();
        lastSelectedLevel = selectedLevel;
        loadGroupState();
        selectedKey = entry.key();
        if (entry.custom() && entry.data() != null) {
            loadOfferData(entry.data());
            editingDefault = false;
            editingVanillaIndex = -1;
            editingCustomIndex = entry.customIndex();
            editingOriginalVanillaOffer = null;
            updateBoolButtons();
            markOfferFormBaseline();
            return;
        }
        if (!entry.custom() && entry.offer() != null) {
            loadOffer(entry.offer(), VillagerConfig.getVanillaTradeWeight(selectedOwner, entry.level(), entry.vanillaIndex()));
            editingDefault = true;
            editingVanillaIndex = entry.vanillaIndex();
            editingCustomIndex = -1;
            editingOriginalVanillaOffer = entry.offer();
            updateBoolButtons();
            markOfferFormBaseline();
        }
    }

    private void selectEntryOwner(TradeEntry entry) {
        String owner = entry.owner();
        if (owner.equals(selectedOwner)) {
            return;
        }
        selectedOwner = owner;
        lastSelectedOwner = owner;
        selectedLevel = VillagerConfig.clampTradeLevel(owner, entry.level());
        lastSelectedLevel = selectedLevel;
        setProfessionBoxDisplay(owner);
    }

    private void loadOfferData(VillagerConfig.TradeOfferData data) {
        buyAId = data.buyAItem();
        buyACount = String.valueOf(data.buyACount());
        buyANbt = data.buyANbt();
        buyBId = data.buyBItem();
        buyBCount = String.valueOf(data.buyBCount());
        buyBNbt = data.buyBNbt();
        sellId = data.sellItem();
        sellCount = String.valueOf(data.sellCount());
        sellNbt = data.sellNbt();
        weight = String.valueOf(data.weight());
        maxUses = String.valueOf(data.maxUses());
        xp = String.valueOf(data.xp());
        price = String.valueOf(data.priceMultiplier());
        demand = String.valueOf(data.demand());
        specialPrice = String.valueOf(data.specialPrice());
        uses = String.valueOf(data.uses());
        rewardExp = data.rewardExp();
        allowRestock = data.allowRestock();
        rawRewardExp = Boolean.toString(rewardExp);
        rawAllowRestock = Boolean.toString(allowRestock);
        List<String> rawOffers = VillagerConfig.villagerTradeOffers;
        if (data.index() >= 0 && data.index() < rawOffers.size()) {
            List<String> parts = VillagerConfig.splitConfigLine(rawOffers.get(data.index()));
            if (parts.size() >= 19) {
                // The tolerant parser supplies a safe preview. Editing must display the actual saved text,
                // including out-of-range counts and malformed booleans, until the user explicitly repairs it.
                buyACount = parts.get(3);
                buyBCount = parts.get(6);
                sellCount = parts.get(9);
                maxUses = parts.get(11);
                xp = parts.get(12);
                price = parts.get(13);
                demand = parts.get(14);
                specialPrice = parts.get(15);
                rawRewardExp = parts.get(16);
                uses = parts.get(17);
                rawAllowRestock = parts.get(18);
                weight = parts.size() > 19 ? parts.get(19) : "1";
            }
        }
        writeFieldsToWidgets();
        captureLoadedFieldValues();
        markOfferFormBaseline();
    }

    private void loadOffer(MerchantOffer offer, int entryWeight) {
        if (offer == null) {
            loadBlankOffer();
            return;
        }
        ItemStack buyA = offer.getBaseCostA();
        ItemStack buyB = offer.getCostB();
        ItemStack sell = offer.getResult();
        buyAId = VillagerConfig.itemId(buyA);
        buyACount = String.valueOf(Math.max(1, buyA.getCount()));
        buyANbt = VillagerConfig.stackNbt(buyA);
        buyBId = buyB.isEmpty() ? "minecraft:air" : VillagerConfig.itemId(buyB);
        buyBCount = buyB.isEmpty() ? "0" : String.valueOf(Math.max(1, buyB.getCount()));
        buyBNbt = buyB.isEmpty() ? "" : VillagerConfig.stackNbt(buyB);
        sellId = VillagerConfig.itemId(sell);
        sellCount = String.valueOf(Math.max(1, sell.getCount()));
        sellNbt = VillagerConfig.stackNbt(sell);
        weight = String.valueOf(entryWeight);
        maxUses = String.valueOf(offer.getMaxUses());
        xp = String.valueOf(offer.getXp());
        price = String.valueOf(offer.getPriceMultiplier());
        demand = String.valueOf(offer.getDemand());
        specialPrice = String.valueOf(offer.getSpecialPriceDiff());
        uses = String.valueOf(offer.getUses());
        rewardExp = offer.shouldRewardExp();
        allowRestock = true;
        rawRewardExp = Boolean.toString(rewardExp);
        rawAllowRestock = "true";
        editingDefault = true;
        writeFieldsToWidgets();
        captureLoadedFieldValues();
    }

    private void loadBlankOffer() {
        editingDefault = false;
        editingVanillaIndex = -1;
        editingCustomIndex = -1;
        editingOriginalVanillaOffer = null;
        selectedKey = selectedKey.startsWith(selectedOwner + "|level:") ? selectedKey : "";
        buyAId = "minecraft:air";
        buyACount = "0";
        buyANbt = "";
        buyBId = "minecraft:air";
        buyBCount = "0";
        buyBNbt = "";
        sellId = "minecraft:air";
        sellCount = "0";
        sellNbt = "";
        weight = "1";
        maxUses = "16";
        xp = "1";
        price = "0.05";
        demand = "0";
        specialPrice = "0";
        uses = "0";
        rewardExp = true;
        allowRestock = true;
        rawRewardExp = "true";
        rawAllowRestock = "true";
        writeFieldsToWidgets();
        captureLoadedFieldValues();
        if (levelSettingsActive) markLevelSettingsBaseline();
        else markOfferFormBaseline();
    }

    private void clearSelectionToNewOffer() {
        editorActive = true;
        levelSettingsActive = false;
        setLevelWidgetsVisible(false);
        setEditorWidgetsVisible(true);
        editingCustomIndex = -1;
        editingVanillaIndex = -1;
        editingOriginalVanillaOffer = null;
        selectedKey = selectedOwner + "|level:" + selectedLevel;
        loadBlankOffer();
    }

    private UndoCheckpoint beginUndoableChange() {
        if (restoringUndo) return null;
        syncFieldValues();
        return new UndoCheckpoint(captureTradeEditorState(), captureTradeConfigState());
    }

    private void finishUndoableChange(UndoCheckpoint checkpoint) {
        if (checkpoint == null || restoringUndo) return;
        TradeConfigState after = captureTradeConfigState();
        if (Objects.equals(checkpoint.configState(), after)) {
            updateUndoButtonState();
            return;
        }
        if (undoHistory.size() >= MAX_UNDO_STEPS) {
            undoHistory.removeFirst();
        }
        undoHistory.addLast(checkpoint.editorState());
        updateUndoButtonState();
    }

    private void undoLastChange() {
        if (undoHistory.isEmpty()) {
            updateUndoButtonState();
            return;
        }
        TradeEditorState state = undoHistory.removeLast();
        restoringUndo = true;
        try {
            restoreTradeEditorState(state);
            invalidateTradeSearchIndex();
            refreshEntries();
        } finally {
            restoringUndo = false;
        }
        updateUndoButtonState();
    }

    private Component undoButtonText() {
        return KineticI18n.translatable(
                "gui.contentstudio.villager.villager.trade.undo",
                undoHistory.size(),
                MAX_UNDO_STEPS
        );
    }

    private void updateUndoButtonState() {
        if (undoButton == null) return;
        undoButton.setText(undoButtonText());
        undoButton.setEnabled(!isSavePending() && !undoHistory.isEmpty());
    }

    private void syncFieldValues() {
        if (buyACountBox != null) {
            buyACount = buyACountBox.textValue();
        }
        if (buyBCountBox != null) {
            buyBCount = buyBCountBox.textValue();
        }
        if (sellCountBox != null) {
            sellCount = sellCountBox.textValue();
        }
        if (weightBox != null) {
            weight = weightBox.textValue();
        }
        if (offerCountBox != null) {
            selectedOfferCount = currentOfferCountValue();
        }
        if (maxUsesBox != null) {
            maxUses = maxUsesBox.textValue();
        }
        if (xpBox != null) {
            xp = xpBox.textValue();
        }
        if (priceBox != null) {
            price = priceBox.textValue();
        }
        if (demandBox != null) {
            demand = demandBox.textValue();
        }
        if (specialPriceBox != null) {
            specialPrice = specialPriceBox.textValue();
        }
        if (usesBox != null) {
            uses = usesBox.textValue();
        }
    }

    /** 记录载入时的数值并应用到输入框 / Captures the loaded values and applies them as input defaults. */
    private void captureLoadedFieldValues() {
        loadedFieldValues.put("buyA", buyACount);
        loadedFieldValues.put("buyB", buyBCount);
        loadedFieldValues.put("sell", sellCount);
        loadedFieldValues.put("weight", weight);
        loadedFieldValues.put("maxUses", maxUses);
        loadedFieldValues.put("xp", xp);
        loadedFieldValues.put("price", price);
        loadedFieldValues.put("demand", demand);
        loadedFieldValues.put("specialPrice", specialPrice);
        loadedFieldValues.put("uses", uses);
        applyFieldDefaults();
    }

    /** 把载入值设为各输入框的默认值 / Applies the loaded values as each input's default. */
    private void applyFieldDefaults() {
        if (buyACountBox != null) buyACountBox.setDefaultText(loadedFieldValues.get("buyA"));
        if (buyBCountBox != null) buyBCountBox.setDefaultText(loadedFieldValues.get("buyB"));
        if (sellCountBox != null) sellCountBox.setDefaultText(loadedFieldValues.get("sell"));
        if (weightBox != null) weightBox.setDefaultText(loadedFieldValues.get("weight"));
        if (maxUsesBox != null) maxUsesBox.setDefaultText(loadedFieldValues.get("maxUses"));
        if (xpBox != null) xpBox.setDefaultText(loadedFieldValues.get("xp"));
        if (priceBox != null) priceBox.setDefaultText(loadedFieldValues.get("price"));
        if (demandBox != null) demandBox.setDefaultText(loadedFieldValues.get("demand"));
        if (specialPriceBox != null) specialPriceBox.setDefaultText(loadedFieldValues.get("specialPrice"));
        if (usesBox != null) usesBox.setDefaultText(loadedFieldValues.get("uses"));
        if (offerCountBox != null) {
            // 每级交易数的默认值为原版默认数量 / The per-level offer count defaults to the vanilla default count.
            offerCountBox.setDefaultText(selectedOwner.isEmpty() ? null
                    : String.valueOf(VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, selectedLevel)));
        }
    }

    private void writeFieldsToWidgets() {
        writeNumberField(buyACountBox, buyACount, false, false, 3);
        writeNumberField(buyBCountBox, buyBCount, false, false, 3);
        writeNumberField(sellCountBox, sellCount, false, false, 3);
        writeNumberField(weightBox, weight, false, false, 10);
        if (offerCountBox != null) {
            offerCountBox.setTextValue(String.valueOf(selectedOfferCount));
        }
        writeNumberField(maxUsesBox, maxUses, false, false, 10);
        writeNumberField(xpBox, xp, false, false, 10);
        writeNumberField(priceBox, price, true, false, 10);
        writeNumberField(demandBox, demand, false, true, 10);
        writeNumberField(specialPriceBox, specialPrice, false, true, 10);
        writeNumberField(usesBox, uses, false, false, 10);
        updateBoolButtons();
    }

    private void writeNumberField(KineticNumberField field, String value, boolean decimal, boolean negative, int limit) {
        if (field == null) return;
        String raw = value == null ? "" : value;
        // Public setters obey the numeric syntax filter too. Temporarily allow the saved invalid text to be
        // displayed, then restore the same integer/decimal typing policy while retaining Core range validation.
        field.limitTextLength(Math.max(limit, raw.length()));
        field.filterText(ignored -> true);
        field.setTextValue(raw);
        String pattern = decimal ? (negative ? "-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)" : "(?:\\d+(?:\\.\\d*)?|\\.\\d*)")
                : (negative ? "-?\\d*" : "\\d*");
        field.filterText(text -> text != null && (text.isEmpty() || text.matches(pattern)));
    }

    private static boolean isRawBoolean(String value) {
        return value != null && (value.trim().equalsIgnoreCase("true") || value.trim().equalsIgnoreCase("false"));
    }

    private List<VillagerConfig.TradeValidationIssue> currentOfferFormIssues() {
        List<String> parts = List.of(selectedOwner, String.valueOf(selectedLevel), buyAId, buyACount, buyANbt,
                buyBId, buyBCount, buyBNbt, sellId, sellCount, sellNbt, maxUses, xp, price, demand, specialPrice,
                rawRewardExp, uses, rawAllowRestock, weight);
        String line = String.join("|", parts.stream().map(VillagerConfig::escapeConfigPart).toList());
        return VillagerConfig.validateTradeListsDetailed(List.of(), List.of(line), List.of()).issues();
    }

    private void saveCurrentOffer() {
        if (isSavePending()) return;
        syncFieldValues();
        if (selectedOwner.isEmpty() || !editorActive) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        if (hasInvalidNbt()) {
//? if >=1.21 {
/*            showWarningToast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.invalid_components"));
*///?} else {
            showWarningToast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.invalid_nbt"));
//?}
            return;
        }

        List<VillagerConfig.TradeValidationIssue> formIssues = currentOfferFormIssues();
        if (!formIssues.isEmpty()) {
            showWarningToast(issueComponent(formIssues.get(0)));
            return;
        }

        UndoCheckpoint checkpoint = beginUndoableChange();
        invalidateTradeSearchIndex();
        int currentWeight = VillagerConfig.parseIntValue(weight, 1, 0, 999999);
        if (editingDefault && editingVanillaIndex >= 0) {
            if (currentWeight <= 0) {
                VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, selectedLevel, editingVanillaIndex, false, currentWeight));
                ensureControlledGroup();
                refreshEntries();
                finishUndoableChange(checkpoint);
                markOfferFormBaseline();
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.default_disabled"));
                return;
            }

            VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, selectedLevel, editingVanillaIndex, true, currentWeight));
            if (isSameAsOriginalVanilla()) {
                ensureControlledGroup();
                refreshEntries();
                finishUndoableChange(checkpoint);
                markOfferFormBaseline();
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.default_saved"));
                return;
            }

            VillagerConfig.setVanillaTradeEnabled(selectedOwner, selectedLevel, editingVanillaIndex, false);
            VillagerConfig.TradeOfferData converted = buildOfferData(-1);
            if (converted == null) {
                finishUndoableChange(checkpoint);
                showWarningToast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.invalid_items"));
                return;
            }
            editingCustomIndex = VillagerConfig.addTradeOffer(converted);
            editingDefault = false;
            editingVanillaIndex = -1;
            selectedKey = selectedOwner + "|custom:" + editingCustomIndex;
            ensureControlledGroup();
            refreshEntries();
            finishUndoableChange(checkpoint);
            markOfferFormBaseline();
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.default_converted"));
            return;
        }

        VillagerConfig.TradeOfferData data = buildOfferData(editingCustomIndex);
        if (data == null) {
            finishUndoableChange(checkpoint);
            showWarningToast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.invalid_items"));
            return;
        }
        if (editingCustomIndex >= 0) {
            VillagerConfig.setTradeOfferAt(editingCustomIndex, data);
        } else {
            editingCustomIndex = VillagerConfig.addTradeOffer(data);
        }
        selectedKey = selectedOwner + "|custom:" + editingCustomIndex;
        ensureControlledGroup();
        refreshEntries();
        finishUndoableChange(checkpoint);
        markOfferFormBaseline();
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.offer_saved"));
    }

    private VillagerConfig.TradeOfferData buildOfferData(int index) {
        String buyA = VillagerConfig.clean(buyAId);
        String sell = VillagerConfig.clean(sellId);
        if (buyA.isEmpty() || buyA.equals("minecraft:air") || sell.isEmpty() || sell.equals("minecraft:air")) {
            return null;
        }
        String buyB = VillagerConfig.clean(buyBId);
        int buyBValue = VillagerConfig.parseIntValue(buyBCount, 0, 0, 64);
        if (buyB.isEmpty() || buyB.equals("minecraft:air") || buyBValue <= 0) {
            buyB = "minecraft:air";
            buyBValue = 0;
            buyBNbt = "";
        }
        return new VillagerConfig.TradeOfferData(
                selectedOwner,
                selectedLevel,
                buyA,
                VillagerConfig.parseIntValue(buyACount, 1, 1, 64),
                buyANbt,
                buyB,
                buyBValue,
                buyBNbt,
                sell,
                VillagerConfig.parseIntValue(sellCount, 1, 1, 64),
                sellNbt,
                VillagerConfig.parseIntValue(maxUses, 16, 0, 999999),
                VillagerConfig.parseIntValue(xp, 1, 0, 999999),
                VillagerConfig.parseFloatValue(price, 0.05F, 0.0F, 1000.0F),
                VillagerConfig.parseIntValue(demand, 0, -999999, 999999),
                VillagerConfig.parseIntValue(specialPrice, 0, -999999, 999999),
                rewardExp,
                VillagerConfig.parseIntValue(uses, 0, 0, 999999),
                allowRestock,
                VillagerConfig.parseIntValue(weight, 1, 0, 999999),
                Math.max(0, index)
        );
    }

    private boolean hasInvalidNbt() {
//? if >=1.21 {
/*        return isInvalidComponents(buyAId, buyANbt, true)
                || isInvalidComponents(buyBId, buyBNbt, true)
                || isInvalidComponents(sellId, sellNbt, false);
*///?} else {
        return isInvalidNbt(buyANbt) || isInvalidNbt(buyBNbt) || isInvalidNbt(sellNbt);
//?}
    }

//? if >=1.21 {
/*    private boolean isInvalidComponents(String id, String data, boolean payment) {
        String text = data == null ? "" : data.trim();
        if (id == null || id.isBlank() || id.equals("minecraft:air") || id.equals("air")) {
            return !(text.isEmpty() || text.equals("[]"));
        }
        if (!dev.xyat.contentstudio.item.ItemData.validConstraint(id, text)) return true;
        if (payment && dev.xyat.contentstudio.item.ItemData.hasWorldContext()) {
            try {
                return dev.xyat.contentstudio.villager.util.TradeItemData.cost(
                        dev.xyat.contentstudio.item.ItemData.compile(id, text)) == null;
            } catch (RuntimeException invalid) {
                return true;
            }
        }
        return false;
    }
*///?} else {
    private boolean isInvalidNbt(String nbt) {
        String text = nbt == null ? "" : nbt.trim();
        if (text.isEmpty() || text.equals("{}")) {
            return false;
        }
        try {
            TagParser.parseTag(text);
            return false;
        } catch (Exception ignored) {
            return true;
        }
    }
//?}

    private void showWarningToast(Component message) {
        KineticOverlays.toast("villager_trade_warning", message, KineticOverlays.Position.BOTTOM_CENTER, 5000, 0, -30);
    }

    private void ensureControlledGroup() {
        selectedOfferCount = currentOfferCountValue();
        selectedMode = "replace_level";
        VillagerConfig.setTradeGroup(new VillagerConfig.TradeGroup(selectedOwner, selectedLevel, selectedMode, selectedOfferCount));
    }

    private boolean isSameAsOriginalVanilla() {
        if (editingOriginalVanillaOffer == null) {
            return false;
        }
        VillagerConfig.TradeOfferData data = buildOfferData(-1);
        if (data == null) {
            return false;
        }
        return equalsStack(editingOriginalVanillaOffer.getBaseCostA(), data.buyAItem(), data.buyACount(), data.buyANbt())
                && equalsStack(editingOriginalVanillaOffer.getCostB(), data.buyBItem(), data.buyBCount(), data.buyBNbt())
                && equalsStack(editingOriginalVanillaOffer.getResult(), data.sellItem(), data.sellCount(), data.sellNbt())
                && editingOriginalVanillaOffer.getMaxUses() == data.maxUses()
                && editingOriginalVanillaOffer.getXp() == data.xp()
                && Math.abs(editingOriginalVanillaOffer.getPriceMultiplier() - data.priceMultiplier()) < 0.0001F
                && editingOriginalVanillaOffer.getDemand() == data.demand()
                && editingOriginalVanillaOffer.getSpecialPriceDiff() == data.specialPrice()
                && editingOriginalVanillaOffer.getUses() == data.uses()
                && editingOriginalVanillaOffer.shouldRewardExp() == data.rewardExp()
                && data.allowRestock();
    }

    private boolean equalsStack(ItemStack stack, String id, int count, String nbt) {
        if ((stack == null || stack.isEmpty()) && (id == null || id.isBlank() || id.equals("minecraft:air") || count <= 0)) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return VillagerConfig.itemId(stack).equals(VillagerConfig.clean(id)) && stack.getCount() == count && VillagerConfig.stackNbt(stack).equals(nbt == null ? "" : nbt.trim());
    }

    private void deleteSelectedEntry() {
        TradeEntry entry = findSelectedEntry();
        if (entry == null || entry.header()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_offer_selected"));
            return;
        }
        deleteEntry(entry);
    }

    private TradeEntry findSelectedEntry() {
        for (TradeEntry entry : entries) {
            if (Objects.equals(selectedKey, entry.key())) {
                return entry;
            }
        }
        return null;
    }

    private void deleteEntry(TradeEntry entry) {
        if (entry == null || entry.header()) {
            return;
        }
        UndoCheckpoint checkpoint = beginUndoableChange();
        selectEntryOwner(entry);
        selectedLevel = entry.level();
        lastSelectedLevel = selectedLevel;
        loadGroupState();
        if (entry.custom()) {
            VillagerConfig.removeTradeOfferAt(entry.customIndex());
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.offer_deleted"));
        } else {
            VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, entry.level(), entry.vanillaIndex(), false, 0));
            selectedLevel = entry.level();
            ensureControlledGroup();
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.default_disabled"));
        }
        selectedKey = "";
        editingCustomIndex = -1;
        editingVanillaIndex = -1;
        invalidateTradeSearchIndex();
        refreshEntries();
        loadBlankOffer();
        finishUndoableChange(checkpoint);
    }

    private void clearCurrentTarget() {
        if (selectedOwner.isEmpty()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }

        TradeEntry entry = findSelectedEntry();
        if (entry != null && entry.header()) {
            clearCurrentLevel(entry.level());
            return;
        }

        if (editorActive) {
            loadBlankOffer();
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.current_content_cleared"));
            return;
        }

        clearCurrentLevel(selectedLevel);
    }

    private void clearCurrentLevel(int level) {
        if (selectedOwner.isEmpty()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }

        UndoCheckpoint checkpoint = beginUndoableChange();
        int safeLevel = VillagerConfig.clampTradeLevel(selectedOwner, level);
        selectedLevel = safeLevel;
        lastSelectedLevel = safeLevel;

        VillagerConfig.villagerTradeOffers.removeIf(line -> {
            VillagerConfig.TradeOfferData data = VillagerConfig.TradeOfferData.parse(line, 0);
            return data != null && data.matches(selectedOwner, safeLevel);
        });

        int vanillaCount = VillagerTradeRuntimeUtil.vanillaTradeCount(selectedOwner, safeLevel);
        if (vanillaCount > 0) {
            for (int i = 0; i < vanillaCount; i++) {
                VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, safeLevel, i, false, 0));
            }
        }

        int defaultOfferCount = VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, safeLevel);
        VillagerConfig.setTradeGroup(new VillagerConfig.TradeGroup(selectedOwner, safeLevel, "replace_level", defaultOfferCount));

        selectedMode = "replace_level";
        selectedOfferCount = defaultOfferCount;
        selectedKey = selectedOwner + "|level:" + safeLevel;
        editorActive = false;
        levelSettingsActive = true;
        setEditorWidgetsVisible(false);
        setLevelWidgetsVisible(true);
        editingCustomIndex = -1;
        editingVanillaIndex = -1;
        editingOriginalVanillaOffer = null;
        invalidateTradeSearchIndex();
        refreshEntries();
        loadBlankOffer();
        setEditorWidgetsVisible(false);
        finishUndoableChange(checkpoint);
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.current_level_cleared"));
    }

    private void openRemovedDefaultTradesScreen() {
        if (!isAttached() || isSavePending()) {
            return;
        }
        if (selectedOwner.isEmpty()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        openChild(new RemovedDefaultTradesPage(this, selectedOwner));
    }

    private void openItemSelector(int slot) {
        if (!isAttached() || isSavePending()) {
            return;
        }
        syncFieldValues();
        KineticSelectors.openItemSelector(selection -> {
            if (selection != null && selection.isItem()) {
                ItemStack stack = selection.stack().copy();
                setSlotStack(slot, stack);
            }
        });
    }

    private void setSlotStack(int slot, ItemStack stack) {
        String id = VillagerConfig.itemId(stack);
        String nbt = VillagerConfig.stackNbt(stack);
        String count = String.valueOf(stack == null || stack.isEmpty() ? 0 : Math.max(1, stack.getCount()));
        if (slot == 0) {
            boolean replacingExistingItem = buyAId != null && !buyAId.isBlank() && !buyAId.equals("minecraft:air");
            buyAId = id;
            if (!replacingExistingItem) buyACount = count.equals("0") ? "1" : count;
            buyANbt = nbt;
        } else if (slot == 1) {
            boolean replacingExistingItem = buyBId != null && !buyBId.isBlank() && !buyBId.equals("minecraft:air");
            buyBId = id;
            if (!replacingExistingItem) buyBCount = count;
            buyBNbt = nbt;
        } else {
            boolean replacingExistingItem = sellId != null && !sellId.isBlank() && !sellId.equals("minecraft:air");
            sellId = id;
            if (!replacingExistingItem) sellCount = count.equals("0") ? "1" : count;
            sellNbt = nbt;
        }
        writeFieldsToWidgets();
    }

    private void openNbtEditor(int slot) {
        if (isSavePending()) return;
        syncFieldValues();
        String initial = slot == 0 ? buyANbt : slot == 1 ? buyBNbt : sellNbt;
        {
//? if >=1.21 {
/*            dev.xyat.contentstudio.item.ItemData.edit(slot == 0 ? buyAId : slot == 1 ? buyBId : sellId, initial, value -> {
                if (slot == 0) {
                    buyANbt = value;
                } else if (slot == 1) {
                    buyBNbt = value;
                } else {
                    sellNbt = value;
                }
            });
*///?} else {
            KineticSelectors.openNbtEditor(initial, value -> {
                if (slot == 0) {
                    buyANbt = value;
                } else if (slot == 1) {
                    buyBNbt = value;
                } else {
                    sellNbt = value;
                }
            });
//?}
        }
    }

//? if >=1.21 {
/*    private ItemStack createStack(String itemId, int count, String nbt) {
        String id=VillagerConfig.clean(itemId);
        if(id.isEmpty() || id.equals("minecraft:air") || id.equals("air") || count<=0) return ItemStack.EMPTY;
        try { return dev.xyat.contentstudio.item.ItemData.compile(id,nbt).copyWithCount(VillagerConfig.clampInt(count,1,64)); }
        catch(RuntimeException invalid) { return ItemStack.EMPTY; }
    }
*///?} else {
    private ItemStack createStack(String itemId, int count, String nbt) {
        String id = VillagerConfig.clean(itemId);
        if (id.isEmpty() || id.equals("minecraft:air") || id.equals("air") || count <= 0) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        Item item = KineticRegistries.items().get(location);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, VillagerConfig.clampInt(count, 1, 64));
        String text = nbt == null ? "" : nbt.trim();
        if (!text.isEmpty() && !text.equals("{}")) {
            try {
                CompoundTag tag = TagParser.parseTag(text);
                stack.setTag(tag);
            } catch (Exception ignored) {
            }
        }
        return stack;
    }


//?}

    private void updateBoolButtons() {
        if (rewardButton != null) {
            rewardButton.setValue(rewardExp);
            rewardButton.setTooltip(isRawBoolean(rawRewardExp)
                    ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.reward.tooltip")
                    : KineticI18n.translatable("msg.contentstudio.villager.villager.trade.error.reward", rawRewardExp));
        }
        if (restockButton != null) {
            restockButton.setValue(allowRestock);
            restockButton.setTooltip(isRawBoolean(rawAllowRestock)
                    ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.tooltip")
                    : KineticI18n.translatable("msg.contentstudio.villager.villager.trade.error.restock", rawAllowRestock));
        }
    }

    private Component rewardText() {
        return KineticI18n.translatable(rewardExp ? "gui.contentstudio.villager.villager.trade.reward.on" : "gui.contentstudio.villager.villager.trade.reward.off");
    }

    private Component restockText() {
        return KineticI18n.translatable(allowRestock ? "gui.contentstudio.villager.villager.trade.restock.on" : "gui.contentstudio.villager.villager.trade.restock.off");
    }

    public static List<KineticSuggestion> getProfessionDictionary() {
        List<KineticSuggestion> result = new ArrayList<>();
        String wanderingName = KineticSearch.resolveTranslation("entity.minecraft.wandering_trader");
        result.add(new KineticSuggestion(
                VillagerConfig.WANDERING_TRADER_ID,
                wanderingName == null ? Component.empty() : Component.literal(wanderingName)
        ));
        KineticRegistries.villagerProfessions().ids().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(id -> {
                    ResourceLocation location = KineticResourceIds.tryParse(id);
                    String translated = location == null ? null : KineticSearch.resolveTranslation(
                            "entity.minecraft.villager." + location.getPath()
                    );
                    result.add(new KineticSuggestion(
                            id,
                            translated == null ? Component.empty() : Component.literal(translated)
                    ));
                });
        return List.copyOf(result);
    }

    public static String getProfessionName(String profession) {
        String id = VillagerConfig.clean(profession);
        if (id.equals(VillagerConfig.WANDERING_TRADER_ID)) {
            return KineticI18n.translatable("entity.minecraft.wandering_trader").getString();
        }
        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (location == null) {
            return id;
        }
        String key = "entity.minecraft.villager." + location.getPath();
        String translated = KineticI18n.translatable(key).getString();
        return translated.equals(key) ? id : translated;
    }

    public static String modeName(String mode) {
        return switch (VillagerConfig.TradeGroup.normalizeMode(mode)) {
            case "add" -> KineticI18n.translatable("gui.contentstudio.villager.villager.trade.mode.short.add").getString();
            case "disable_level" -> KineticI18n.translatable("gui.contentstudio.villager.villager.trade.mode.short.disable").getString();
            default -> KineticI18n.translatable("gui.contentstudio.villager.villager.trade.mode.short.replace").getString();
        };
    }

    private static class RemovedDefaultTradesPage extends KineticPage {
        private static final int ROW_HEIGHT = 42;

        private final VillagerTradeEditorPage parentScreen;
        private final String owner;
        private final List<RemovedDefaultEntry> removedEntries = new ArrayList<>();

        private int rootX;
        private int rootY;
        private int rootW;
        private int rootH;
        private int listX;
        private int listY;
        private int listW;
        private int listH;
        private final KineticScrollController listScroll =
                new KineticScrollController();
        // 本页无选中概念：中键跳转目标为最近左键点击的行，列表刷新时清除 / No selection here: the middle-click target is the last left-clicked row, cleared when the list refreshes.
        private int lastClickedIndex = -1;

        RemovedDefaultTradesPage(VillagerTradeEditorPage parentScreen, String owner) {
            super(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.title"));
            this.parentScreen = parentScreen;
            this.owner = VillagerConfig.clean(owner);
            listScroll.bindSelection(() -> lastClickedIndex < removedEntries.size() ? lastClickedIndex : -1);
        }

        @Override
        protected void build(KineticUi ui) {
            this.rootW = Math.min(this.width() - 8, 512);
            this.rootH = Math.min(this.height() - 8, 312);
            this.rootX = (this.width() - rootW) / 2;
            this.rootY = (this.height() - rootH) / 2;
            this.listX = rootX + 10;
            this.listY = rootY + 32;
            this.listW = rootW - 20;
            this.listH = rootH - 44;

            int buttonY = rootY + 8;
            ui().button(rootX + 10, buttonY, 50).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.restore_all")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.restore_all.tooltip")).onClick(this::restoreAllRemovedTrades).build();
            ui().button(rootX + 68, buttonY, 50).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset.tooltip")).onClick(this::openResetConfirmPopup).build();
            ui().button(rootX + rootW - 60, buttonY, 50).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.back")).onClick(this::returnToParent).build();

            refreshRemovedEntries();
        }

        private void refreshRemovedEntries() {
            removedEntries.clear();
            lastClickedIndex = -1;
            int maxLevel = VillagerConfig.isWanderingTrader(owner) ? 2 : 5;
            for (int level = 1; level <= maxLevel; level++) {
                int vanillaCount = VillagerTradeRuntimeUtil.vanillaTradeCount(owner, level);
                if (vanillaCount <= 0) {
                    continue;
                }
                for (int i = 0; i < vanillaCount; i++) {
                    if (VillagerConfig.isVanillaTradeEnabled(owner, level, i)) {
                        continue;
                    }
                    MerchantOffer offer = VillagerTradeRuntimeUtil.createPreviewOffer(owner, level, i);
                    if (offer != null) {
                        removedEntries.add(new RemovedDefaultEntry(level, i, offer));
                    }
                }
            }
            listScroll.update(
                    removedEntries.size(),
                    getVisibleRows()
            );
        }

        @Override
        protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
            KineticTheme.panel(g, rootX, rootY, rootW, rootH);
            int titleCenter = rootX + rootW / 2;
            int titleLeft = rootX + 118 + TEXT_GAP;
            int titleRight = rootX + rootW - 60 - TEXT_GAP;
            int titleWidth = Math.max(1, 2 * Math.min(titleCenter - titleLeft, titleRight - titleCenter));
            g.scrollingTextCentered(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.title"), titleCenter, rootY + 12, titleWidth, 0xFFFFFF55, true);
            KineticTheme.panelAlt(g, listX, listY, listW, listH);
        }

        @Override
        protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
            if (removedEntries.isEmpty()) {
                g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.empty"), listX + 10, listY + 12, listW - 20, 0xFF55FF55, false);
            } else {
                int visualShift = listScroll.visualShift(ROW_HEIGHT);
                int y = listY + 4 - visualShift;
                g.scissor(listX, listY + 4, listX + listW - 10, listY + listH - 4);
                for (int i = listScroll.smoothIndexOffset(); i < removedEntries.size(); i++) {
                    RemovedDefaultEntry entry = removedEntries.get(i);
                    if (y >= listY + listH - 4) {
                        break;
                    }
                    renderRemovedEntry(g, mx, my, entry, y);
                    listScroll.renderSelectionFlash(g, i, listX + 4, y, listW - 16, ROW_HEIGHT - 2);
                    y += ROW_HEIGHT;
                }
                g.endScissor();
                renderRemovedScrollbar(g, mx, my);
            }

        }


        private void openResetConfirmPopup() {
            openDialog(
                    KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.title"),
                    KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.line1")
                            .append("\n")
                            .append(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.line2")),
                    KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.yes"),
                    KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.no"),
                    this::resetCurrentProfessionToDefault,
                    () -> { }
            );
        }

        private void renderRemovedEntry(KineticGraphics g, int mx, int my, RemovedDefaultEntry entry, int y) {
            boolean hover = mx >= listX + 4 && mx <= listX + listW - 12 && my >= y && my < y + ROW_HEIGHT - 2;
            KineticTheme.stateSurface(
                    g,
                    listX + 4,
                    y,
                    listW - 16,
                    ROW_HEIGHT - 2,
                    KineticTheme.Surface.PANEL_ALT,
                    false,
                    hover,
                    false
            );
            int iconX = listX + 126;
            g.scrollingText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.entry", entry.level(), entry.index()), listX + 10, y + 5, iconX - (listX + 10) - TEXT_GAP, 0xFFFFFF55, false);
            int iconY = y + 13;
            renderRemovedMiniStack(g, entry.offer().getBaseCostA(), iconX, iconY);
            g.text("+", iconX + 24, iconY + 6, 0xFFFFFF55, false);
            renderRemovedMiniStack(g, entry.offer().getCostB(), iconX + 42, iconY);
            g.text("→", iconX + 68, iconY + 6, 0xFF55FF55, false);
            renderRemovedMiniStack(g, entry.offer().getResult(), iconX + 90, iconY);
        }

        private void renderRemovedMiniStack(KineticGraphics g, ItemStack stack, int x, int y) {
            ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
            renderRemovedFlatSlot(g, x, y);
            if (!safeStack.isEmpty()) {
                KineticTheme.item(g, safeStack, x, y, 18, 1.0F, true);
            }
        }

        private void renderRemovedFlatSlot(
                KineticGraphics graphics,
                int x,
                int y
        ) {
            KineticTheme.itemSlot(
                    graphics,
                    x,
                    y,
                    18,
                    4,
                    false
            );
        }

        private void renderRemovedScrollbar(
                KineticGraphics graphics,
                int mouseX,
                int mouseY
        ) {
            listScroll.update(
                    removedEntries.size(),
                    getVisibleRows()
            );

            listScroll.render(
                    graphics,
                    mouseX,
                    mouseY,
                    listX + listW - 8,
                    listY + 4,
                    4,
                    listH - 8,
                    24
        );
        }

        @Override
        protected boolean onMouseClickCapture(MouseInput input) {
            // 原 canvasMouseClicked 在控件之前处理 / The old canvasMouseClicked handled this before controls.
            double mx = input.x();
            double my = input.y();
            listScroll.update(
                    removedEntries.size(),
                    getVisibleRows()
            );

            if (listScroll.beginDrag(
                    mx,
                    my,
                    input.button(),
                    listX + listW - 8,
                    listY + 4,
                    4,
                    listH - 8,
                    24,
                    2
            )) {
                return true;
            }
            if (input.isRight()) {
                RemovedDefaultEntry entry = findEntryAt(mx, my);
                if (entry != null) {
                    restoreOneRemovedTrade(entry);
                    return true;
                }
            }
            if (input.isLeft()) {
                RemovedDefaultEntry entry = findEntryAt(mx, my);
                if (entry != null) lastClickedIndex = removedEntries.indexOf(entry);
            }
            return false;
        }

        @Override
        protected boolean onMouseRelease(MouseInput input) {
            return listScroll.release(input.button());
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
        double my = input.y();
            return listScroll.drag(
                    my,
                    listY + 4,
                    listH - 8,
                    24
            );
        }

        @Override
        protected boolean onMouseScroll(ScrollInput input) {
        double mx = input.x();
        double my = input.y();
        double delta = input.deltaY();
            if (mx >= listX
                    && mx <= listX + listW
                    && my >= listY
                    && my <= listY + listH) {
                listScroll.update(
                        removedEntries.size(),
                        getVisibleRows()
                );

                return listScroll.scroll(delta);
            }

            return false;
        }

        private RemovedDefaultEntry findEntryAt(double mx, double my) {
            if (mx < listX || mx > listX + listW || my < listY || my > listY + listH) {
                return null;
            }
            int y = listY + 4 - listScroll.visualShift(ROW_HEIGHT);
            for (int i = listScroll.smoothIndexOffset(); i < removedEntries.size(); i++) {
                RemovedDefaultEntry entry = removedEntries.get(i);
                if (my >= y && my < y + ROW_HEIGHT - 2) {
                    return entry;
                }
                y += ROW_HEIGHT;
                if (y > listY + listH) {
                    break;
                }
            }
            return null;
        }

        private int getVisibleRows() {
            return Math.max(1, listH / ROW_HEIGHT);
        }

        private void restoreOneRemovedTrade(RemovedDefaultEntry entry) {
            UndoCheckpoint checkpoint = parentScreen.beginUndoableChange();
            VillagerConfig.setVanillaTradeEnabled(owner, entry.level(), entry.index(), true);
            restoreLevelModeIfDisabled(entry.level());

            VillagerConfig.enableCustomVillagerTrades = true;

            refreshRemovedEntries();
            parentScreen.loadGroupState();
            parentScreen.invalidateTradeSearchIndex();
            parentScreen.refreshEntries();
            parentScreen.finishUndoableChange(checkpoint);
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.removed_restored"));
        }

        private void restoreAllRemovedTrades() {
            UndoCheckpoint checkpoint = parentScreen.beginUndoableChange();
            int before = VillagerConfig.villagerDefaultTradeOverrides.size();
            VillagerConfig.villagerDefaultTradeOverrides.removeIf(line -> {
                VillagerConfig.VanillaTradeOverride override = VillagerConfig.VanillaTradeOverride.parse(line);
                return override != null && owner.equals(override.profession()) && !override.enabled();
            });
            int removed = before - VillagerConfig.villagerDefaultTradeOverrides.size();
            int fixedLevels = restoreAllDisabledLevelModes();

            if (removed <= 0 && fixedLevels <= 0) {
                parentScreen.finishUndoableChange(checkpoint);
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.removed_empty"));
                return;
            }

            VillagerConfig.enableCustomVillagerTrades = true;

            refreshRemovedEntries();
            parentScreen.loadGroupState();
            parentScreen.invalidateTradeSearchIndex();
            parentScreen.refreshEntries();
            parentScreen.finishUndoableChange(checkpoint);
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.removed_restored_all"));
        }

        private int restoreAllDisabledLevelModes() {
            int changed = 0;
            int maxLevel = VillagerConfig.isWanderingTrader(owner) ? 2 : 5;
            for (int level = 1; level <= maxLevel; level++) {
                if (restoreLevelModeIfDisabled(level)) {
                    changed++;
                }
            }
            return changed;
        }

        private boolean restoreLevelModeIfDisabled(int level) {
            VillagerConfig.TradeGroup group = VillagerConfig.getTradeGroup(owner, level);
            if (group == null || !"disable_level".equals(group.mode)) {
                return false;
            }

            int defaultCount = VillagerTradeRuntimeUtil.getDefaultOfferCount(owner, level);
            int restoredCount = group.offerCount > 0 ? group.offerCount : defaultCount;
            VillagerConfig.setTradeGroup(new VillagerConfig.TradeGroup(owner, level, "replace_level", restoredCount));
            return true;
        }

        private void resetCurrentProfessionToDefault() {
            UndoCheckpoint checkpoint = parentScreen.beginUndoableChange();
            VillagerConfig.villagerDefaultTradeOverrides.removeIf(line -> {
                VillagerConfig.VanillaTradeOverride override = VillagerConfig.VanillaTradeOverride.parse(line);
                return override != null && owner.equals(override.profession());
            });
            VillagerConfig.villagerTradeGroups.removeIf(line -> {
                VillagerConfig.TradeGroup group = VillagerConfig.TradeGroup.parse(line);
                return group != null && owner.equals(group.profession);
            });
            VillagerConfig.villagerTradeOffers.removeIf(line -> {
                VillagerConfig.TradeOfferData data = VillagerConfig.TradeOfferData.parse(line, 0);
                return data != null && owner.equals(data.profession());
            });

            VillagerConfig.enableCustomVillagerTrades = true;

            listScroll.reset();
            refreshRemovedEntries();

            parentScreen.selectedKey = "";
            parentScreen.editorActive = false;
            parentScreen.levelSettingsActive = false;
            parentScreen.setEditorWidgetsVisible(false);
            parentScreen.setLevelWidgetsVisible(false);
            parentScreen.invalidateTradeSearchIndex();
            parentScreen.refreshEntries();
            parentScreen.finishUndoableChange(checkpoint);

            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.removed_reset_done"));
        }

        private void returnToParent() {
            parentScreen.refreshEntries();
            navigateBack();
        }

        private record RemovedDefaultEntry(int level, int index, MerchantOffer offer) {
        }
    }

    private record TradeSearchEntry(TradeEntry entry, String searchText) {
    }

    private record TradeEntry(
            String owner,
            boolean header,
            boolean custom,
            int level,
            String mode,
            int levelOfferCount,
            int levelCustomCount,
            int vanillaIndex,
            int customIndex,
            int weight,
            int maxUses,
            int xp,
            boolean restock,
            MerchantOffer offer,
            VillagerConfig.TradeOfferData data
    ) {
        String key() {
            if (header) {
                return owner + "|level:" + level;
            }
            if (custom) {
                return owner + "|custom:" + customIndex;
            }
            return owner + "|vanilla:" + level + ":" + vanillaIndex;
        }

        static TradeEntry header(String owner, int level, String mode, int offerCount, int customCount) {
            return new TradeEntry(owner, true, false, level, mode, offerCount, customCount, -1, -1, 0, 0, 0, true, null, null);
        }

        static TradeEntry vanilla(String owner, int level, int vanillaIndex, MerchantOffer offer, int weight) {
            return new TradeEntry(owner, false, false, level, "", 0, 0, vanillaIndex, -1, weight, offer.getMaxUses(), offer.getXp(), true, offer, null);
        }

        static TradeEntry custom(String owner, VillagerConfig.TradeOfferData data) {
            MerchantOffer preview;
            try {
                preview = data.createOffer();
            } catch (RuntimeException invalidPreview) {
                preview = null;
            }
            return new TradeEntry(owner, false, true, data.level(), "", 0, 0, -1, data.index(), data.weight(), data.maxUses(), data.xp(), data.allowRestock(), preview, data);
        }
    }
}
