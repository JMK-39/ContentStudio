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
import dev.xyat.kineticcore.api.client.gui.widget.list.*;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class VillagerTradeEditorPage extends KineticPage {
    private static final int BUY_A_LABEL = 0xFFFFFF55;
    private static final int BUY_B_LABEL = 0xFF55FFFF;
    private static final int SELL_LABEL = 0xFF55FF55;
    private static final int ROW_H = 41;
    private static final int LEVEL_ROW_H = 24;
    private static final int LEVEL_BUTTON_SIZE = 14;

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
            boolean editingDefault,
            int editingVanillaIndex,
            int editingCustomIndex,
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
    private final Deque<TradeEditorState> undoHistory = new ArrayDeque<>();
    private boolean restoringUndo;

    private int rootX;
    private int rootY;
    private int rootW;
    private int rootH;
    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;
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
        configureDraft(this::captureTradeConfigState, this::restoreTradeConfigState);
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
                editingDefault,
                editingVanillaIndex,
                editingCustomIndex,
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
        editingDefault = state.editingDefault();
        editingVanillaIndex = state.editingVanillaIndex();
        editingCustomIndex = state.editingCustomIndex();
        editingOriginalVanillaOffer = null;
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

        ui().button(clearButtonX, topY, clearButtonWidth).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.search.clear.short")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.search.clear.tooltip")).onClick(this::clearProfessionSearch).build();

        this.tradeSearchBox = ui().textField(leftX + searchLeftPadding, leftY + 6, searchFieldWidth).label(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search")).placeholder(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.placeholder")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.tooltip")).build();
        this.tradeSearchBox.limitTextLength(128);
        this.tradeSearchBox.setTextValue(lastTradeSearchText);
        this.tradeSearchBox.onTextChange(value -> {
            lastTradeSearchText = value;
            listScroll.reset();
            lastListScroll = 0;
            refreshEntries();
        });

        ui().button(clearButtonX, leftY + 6, clearButtonWidth).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.search.clear.short")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.clear.tooltip")).onClick(this::clearTradeSearch).build();

        int gap = 6;
        int backW = 58;
        int saveW = 58;
        int undoW = 78;
        int previewW = 94;
        int backX = rootX + rootW - 8 - backW;
        int saveX = backX - gap - saveW;
        int undoX = saveX - gap - undoW;
        int previewX = undoX - gap - previewW;
        int lateOverrideW = 80;
        int lateOverrideX = previewX - gap - lateOverrideW;

        this.lateOverrideButton = ui().toggle(lateOverrideX, topY, lateOverrideW)
                .compact()
                .value(VillagerConfig.enableVillagerTradeLateOverride)
                .labels(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.late_override.on"), KineticI18n.translatable("gui.contentstudio.villager.villager.trade.late_override.off"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.late_override.tooltip"))
                .onChange(value -> VillagerConfig.enableVillagerTradeLateOverride = value)
                .build();

        ui().button(previewX, topY, previewW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed_preview")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed_preview.tooltip")).onClick(this::openRemovedDefaultTradesScreen).build();

        this.undoButton = ui().button(undoX, topY, undoW).text(undoButtonText()).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.undo.tooltip")).onClick(this::undoLastChange).build();
        updateUndoButtonState();

        ui().button(saveX, topY, saveW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.save")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.save.tooltip")).onClick(() -> {
                    syncFieldValues();
                    if (levelSettingsActive) {
                        applyCurrentLevelSettings();
                    }
                    VillagerConfig.enableCustomVillagerTrades = true;
                    VillagerConfig.normalizeTradeLists();
                    VillagerNetwork.saveTradeConfig(true);
                    commitDraft();
                    undoHistory.clear();
                    updateUndoButtonState();
                    refreshEntries();
                }).build();

        ui().button(backX, topY, backW).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.back")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.back.tooltip")).onClick(this::close).build();

        addLevelExpandButtons();
        addLevelFields();
        addEditorFields();
        addEditorButtons();

        if (!selectedOwner.isEmpty() && isValidOwner(selectedOwner)) {
            selectedLevel = VillagerConfig.clampTradeLevel(selectedOwner, selectedLevel);
            loadGroupState();
        } else {
            selectedOwner = "";
            lastSelectedOwner = "";
        }
        refreshEntries();
        writeFieldsToWidgets();
        applyFieldDefaults();
        setEditorWidgetsVisible(!selectedOwner.isEmpty() && editorActive);
        setLevelWidgetsVisible(!selectedOwner.isEmpty() && levelSettingsActive);
    }

    private void setupLayout() {
        this.rootW = Math.min(this.width() - 8, 632);
        this.rootH = Math.min(this.height() - 8, 352);
        this.rootX = (this.width() - rootW) / 2;
        this.rootY = (this.height() - rootH) / 2;

        this.leftX = rootX + 8;
        this.leftY = rootY + 36;
        this.leftW = 230;
        this.leftH = rootH - 44;

        this.listX = leftX + 6;
        this.listY = leftY + 32;
        this.listW = leftW - 12;
        this.listH = leftH - 38;

        this.rightX = leftX + leftW + 8;
        this.rightY = leftY;
        this.rightW = rootX + rootW - rightX - 8;
        this.rightH = leftH;
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
            KineticControl control = button;
            control.setControlVisible(false);
            control.setEnabled(false);
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

        buyACountBox = addSmallNumberBox(contentX + 34, previewY + 18, buyACount, "gui.contentstudio.villager.villager.trade.count.buy_a.tooltip", 1);
        addNbtButton(0, contentX + 34, previewY + 40);
        addClearSlotButton(0, contentX + 34, previewY + 61);
        buyBCountBox = addSmallNumberBox(bX + 34, previewY + 18, buyBCount, "gui.contentstudio.villager.villager.trade.count.buy_b.tooltip", 0);
        addNbtButton(1, bX + 34, previewY + 40);
        addClearSlotButton(1, bX + 34, previewY + 61);
        sellCountBox = addSmallNumberBox(sellX + 34, previewY + 18, sellCount, "gui.contentstudio.villager.villager.trade.count.sell.tooltip", 1);
        addNbtButton(2, sellX + 34, previewY + 40);
        addClearSlotButton(2, sellX + 34, previewY + 61);

        int metaX = rightX + 14;
        int metaY = rightY + 196;
        int fieldW = 62;
        int fieldGap = 88;
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
                .onChange(value -> rewardExp = value)
                .build();
        addEditorWidget(this.rewardButton);

        this.restockButton = ui().toggle(metaX + 116, toggleY, 110)
                .compact()
                .value(allowRestock)
                .labels(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.on"), KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.off"))
                .tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.restock.tooltip"))
                .onChange(value -> allowRestock = value)
                .build();
        addEditorWidget(this.restockButton);
    }

    private void addClearSlotButton(int slot, int x, int y) {
        KineticButton clear = ui().button(x, y, 36).text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.slot.clear")).tooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.slot.clear.tooltip")).compact().onClick(() -> clearTradeItemSlot(slot)).build();
        addEditorWidget(clear);
    }

    private void clearTradeItemSlot(int slot) {
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
            widget.setEnabled(visible);
        }
    }

    private void setLevelWidgetsVisible(boolean visible) {
        for (KineticControl widget : levelWidgets) {
            widget.setControlVisible(visible);
            widget.setEnabled(visible);
        }
    }

    private void closeProfessionBoxFocus() {
        // 原 clearSuggestions()：失焦时自动完成框会自行清空候选 / Former clearSuggestions(): the field clears its suggestions when blurred.
        clearFocus();
    }

    @Override
    protected void onTick() {
        syncFieldValues();
        syncSelectedOwnerFromBox();
        updateUndoButtonState();
    }

    @Override
    protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        updateLevelExpandButtons();
        KineticTheme.panel(g, rootX, rootY, rootW, rootH);
        KineticTheme.panelAlt(g, leftX, leftY, leftW, leftH);
        KineticTheme.panelAlt(g, rightX, rightY, rightW, rightH);
        if (!selectedOwner.isEmpty() && editorActive && !levelSettingsActive) {
            renderTradeSlotPanels(g);
        }
        if (!selectedOwner.isEmpty() && !editorActive && !levelSettingsActive) {
            g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.right.empty.title"), rightX + 18, rightY + 24, 0xFFFFFF55, false);
            g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.right.empty.line1"), rightX + 18, rightY + 48, 0xFFFFFFFF, false);
            g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.right.empty.line2"), rightX + 18, rightY + 66, 0xFFFFFFFF, false);
        }
    }

    @Override
    protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
        renderLeftPanel(g, mx, my);
        renderRightPanel(g, mx, my);
        renderProfessionSearchHint(g);
    }

    private void renderProfessionSearchHint(KineticGraphics g) {
        if (professionBox == null || !selectedOwner.isEmpty() || isTradeSearching()) {
            return;
        }
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.profession.hint"), listX + 4, listY + 8, 0xFFFFAA00, false);
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
            g.centeredText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.content_search.empty"), listX + listW / 2, listY + 10, 0xFFFFFF55, true);
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
            KineticControl control = button;
            control.setControlVisible(false);
            control.setEnabled(false);
            control.moveControlX(-10000);
            control.moveControlY(-10000);
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
                    button.moveControlY(y + 4);
                    button.setText(levelExpandText(entry.level()));
                    KineticControl control = button;
                    control.setControlVisible(true);
                    control.setEnabled(true);
                }
            }
            y += h;
        }
    }

    private KineticButton hoveredLevelExpandButton(double mx, double my) {
        for (KineticButton button : levelExpandButtons) {
            KineticControl control = button;
            if (control.controlVisible() && control.contains(mx, my)) {
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

        if (entry.header()) {
            String left = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.trade_list.level", entry.level(), modeName(entry.mode())).getString();
            String right = KineticI18n.translatable("gui.contentstudio.villager.villager.trade.trade_list.level_summary", entry.levelCustomCount(), entry.levelOfferCount()).getString();

            int leftTextX = listX + 29;
            int rightPadding = 22;
            int minGap = 10;

            int maxRightWidth = Math.max(38, listX + listW - rightPadding - leftTextX - 74);
            String safeRight = trim(right, Math.min(154, maxRightWidth));
            int rightTextX = listX + listW - rightPadding - KineticText.width(safeRight);

            int maxLeftWidth = Math.max(32, rightTextX - leftTextX - minGap);
            String safeLeft = trim(left, maxLeftWidth);

            g.text(safeLeft, leftTextX, y + 7, 0xFFFFFF55, false);
            g.text(safeRight, rightTextX, y + 7, 0xFFFFFFFF, false);
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
        g.text(trim(source.getString(), 48), listX + 9, y + 4, sourceColor, false);
        renderOfferIconLine(g, entry.offer(), listX + 56, y + 2);
        g.text(trim(meta, listW - 28), listX + 9, y + 27, 0xFFFFFFFF, false);
    }

    private void renderOfferIconLine(KineticGraphics g, MerchantOffer offer, int x, int y) {
        if (offer == null) {
            g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer.invalid"), x, y + 5, 0xFFFF5555, false);
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
            g.item(safeStack, x + 1, y + 1);
            renderGreenItemCount(g, safeStack, x + 1, y + 1);
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
        int textX = x + 17 - KineticText.width(countText);
        int textY = y + 9;
        // 原 flush + translate(z=300)：抬高一层盖过物品 / Former flush + translate(z=300): raise one layer above items.
        g.push();
        g.raise(1);
        g.text(countText, textX + 1, textY + 1, 0xFF000000, false);
        g.text(countText, textX, textY, 0xFF55FF55, false);
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
        g.text(trim(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.selected_profession", ownerName).getString(), rightW - 20), rightX + 8, rightY + 8, 0xFFFFFF55, false);
        g.text(trim(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.selected_state", selectedLevel, modeName(selectedMode), countCustomOffers(selectedOwner, selectedLevel)).getString(), rightW - 20), rightX + 8, rightY + 24, 0xFFFFFFFF, false);

        int noticeX = rightX + 8;
        int noticeY = rightY + 64;
        KineticTheme.panelAlt(g, noticeX, noticeY, rightW - 16, 18);
        Component state = editingDefault ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.editing_default") : (editingCustomIndex >= 0 ? KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.editing_custom") : KineticI18n.translatable("gui.contentstudio.villager.villager.trade.inline.creating"));
        g.text(trim(state.getString(), rightW - 28), noticeX + 6, noticeY + 5, 0xFFFF55FF, false);

        renderTradeSlots(g, mx, my);

        int metaX = rightX + 14;
        int metaY = rightY + 196;
        int metaY2 = metaY + 38;
        int fieldGap = 88;
        int labelY = metaY - 14;
        int labelY2 = metaY2 - 14;
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.weight"), metaX, labelY, 0xFFFFFF55, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.max_uses"), metaX + fieldGap, labelY, 0xFFFFFF55, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.xp"), metaX + fieldGap * 2, labelY, 0xFFFFFF55, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.price"), metaX + fieldGap * 3, labelY, 0xFFFFFF55, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.demand"), metaX, labelY2, 0xFFFFFFFF, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.special_price"), metaX + fieldGap, labelY2, 0xFFFFFFFF, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.uses"), metaX + fieldGap * 2, labelY2, 0xFFFFFFFF, false);
    }

    private void renderLevelSettingsPanel(KineticGraphics g) {
        String ownerName = getProfessionName(selectedOwner);
        int defaultCount = VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, selectedLevel);
        int controlRightX = rightX + rightW - 190;

        g.text(trim(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.selected_profession", ownerName).getString(), controlRightX - rightX - 12), rightX + 8, rightY + 8, 0xFFFFFF55, false);
        g.text(trim(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.settings_state", selectedLevel, modeName(selectedMode), selectedOfferCount, defaultCount).getString(), controlRightX - rightX - 12), rightX + 8, rightY + 24, 0xFFFFFFFF, false);

        int buttonW = 92;
        int buttonX = rightX + rightW - 110;
        int buttonY = rightY + 8;
        int countX = buttonX - 70;

        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.offer_count.label"), countX, buttonY, 0xFFFFFF55, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.settings.title"), rightX + 8, rightY + 48, 0xFFFFFF55, false);
        g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.settings.tip"), rightX + 8, rightY + 64, 0xFFFFFFFF, false);

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
        KineticTheme.panelAlt(g, x - 6, y - 6, 78, 91);
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
        g.text(trim(label.getString(), 94), x, y, labelColor, false);
        renderInsetSlot(g, x, slotY, isHoverSlot(mx, my, slot));
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
        if (hoveredLevelExpandButton(smx, smy) != null) {
            showTooltip(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.level.arrow.tooltip"));
            return;
        }
        int hoverSlot = hoveredSlot(smx, smy);
        if (hoverSlot >= 0) {
            String key = hoverSlot == 0 ? "gui.contentstudio.villager.villager.trade.slot.buy_a.tooltip" : hoverSlot == 1 ? "gui.contentstudio.villager.villager.trade.slot.buy_b.tooltip" : "gui.contentstudio.villager.villager.trade.slot.sell.tooltip";
            showTooltip(KineticI18n.translatable(key));
            return;
        }
        ItemStack hoveredListStack = findHoveredLeftListStack(smx, smy);
        if (!hoveredListStack.isEmpty()) {
            showItemTooltip(hoveredListStack);
            return;
        }
        TradeEntry hoveredEntry = findEntryAt(smx, smy);
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
        // 原 canvasMouseClicked 全部在控件之前处理 / The old canvasMouseClicked handled all of this before controls.
        double mx = input.x();
        double my = input.y();
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
        if (input.isLeft() && handleSlotClick(mx, my)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        boolean handled =
                listScroll.release(input.button());

        return handled;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
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
        if (input.is(KineticKeyBindings.Key.Z) && KineticClientRuntime.controlModifierDown()) {
            if (!undoHistory.isEmpty()) {
                undoLastChange();
            }
            return true;
        }
        if (professionBox != null
                && isFocused(professionBox)
                && (input.is(KineticKeyBindings.Key.ENTER)
                || input.is(KineticKeyBindings.Key.KP_ENTER))) {
            syncSelectedOwnerFromBox();
            closeProfessionBoxFocus();
            return true;
        }
        return false;
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

            VillagerTrades.ItemListing[] listings = VillagerTradeRuntimeUtil.getVanillaListings(selectedOwner, level);
            if (listings != null) {
                for (int i = 0; i < listings.length; i++) {
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

            for (VillagerConfig.TradeOfferData data : VillagerConfig.getTradeOffers(selectedOwner, level)) {
                entries.add(TradeEntry.custom(selectedOwner, data));
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
                VillagerTrades.ItemListing[] listings = VillagerTradeRuntimeUtil.getVanillaListings(owner, level);
                if (listings != null) {
                    for (int i = 0; i < listings.length; i++) {
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

                for (VillagerConfig.TradeOfferData data : VillagerConfig.getTradeOffers(owner, level)) {
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
        if (stack.getTag() != null && !stack.getTag().isEmpty()) {
            text.append(stack.getTag()).append(' ');
        }
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
        return VillagerConfig.getTradeOffers(owner, level).size();
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
    }

    private void applyCurrentLevelSettings() {
        if (selectedOwner.isEmpty()) {
            return;
        }
        selectedOfferCount = currentOfferCountValue();
        selectedMode = "replace_level";
        VillagerConfig.setTradeGroup(new VillagerConfig.TradeGroup(selectedOwner, selectedLevel, selectedMode, selectedOfferCount));
        VillagerConfig.normalizeTradeLists();
        refreshEntries();
        writeFieldsToWidgets();
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
            return;
        }
        if (!entry.custom() && entry.offer() != null) {
            loadOffer(entry.offer(), VillagerConfig.getVanillaTradeWeight(selectedOwner, entry.level(), entry.vanillaIndex()));
            editingDefault = true;
            editingVanillaIndex = entry.vanillaIndex();
            editingCustomIndex = -1;
            editingOriginalVanillaOffer = entry.offer();
            updateBoolButtons();
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
        writeFieldsToWidgets();
        captureLoadedFieldValues();
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
        writeFieldsToWidgets();
        captureLoadedFieldValues();
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
        undoButton.setEnabled(!undoHistory.isEmpty());
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
        if (buyACountBox != null) {
            buyACountBox.setTextValue(buyACount);
        }
        if (buyBCountBox != null) {
            buyBCountBox.setTextValue(buyBCount);
        }
        if (sellCountBox != null) {
            sellCountBox.setTextValue(sellCount);
        }
        if (weightBox != null) {
            weightBox.setTextValue(weight);
        }
        if (offerCountBox != null) {
            offerCountBox.setTextValue(String.valueOf(selectedOfferCount));
        }
        if (maxUsesBox != null) {
            maxUsesBox.setTextValue(maxUses);
        }
        if (xpBox != null) {
            xpBox.setTextValue(xp);
        }
        if (priceBox != null) {
            priceBox.setTextValue(price);
        }
        if (demandBox != null) {
            demandBox.setTextValue(demand);
        }
        if (specialPriceBox != null) {
            specialPriceBox.setTextValue(specialPrice);
        }
        if (usesBox != null) {
            usesBox.setTextValue(uses);
        }
        updateBoolButtons();
    }

    private void saveCurrentOffer() {
        syncFieldValues();
        if (selectedOwner.isEmpty() || !editorActive) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        if (hasInvalidNbt()) {
            showWarningToast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.invalid_nbt"));
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
                KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.default_disabled"));
                return;
            }

            VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, selectedLevel, editingVanillaIndex, true, currentWeight));
            if (isSameAsOriginalVanilla()) {
                ensureControlledGroup();
                refreshEntries();
                finishUndoableChange(checkpoint);
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
        return !validNbt(buyANbt) || !validNbt(buyBNbt) || !validNbt(sellNbt);
    }

    private boolean validNbt(String nbt) {
        String text = nbt == null ? "" : nbt.trim();
        if (text.isEmpty() || text.equals("{}")) {
            return true;
        }
        try {
            TagParser.parseTag(text);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

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

        VillagerTrades.ItemListing[] listings = VillagerTradeRuntimeUtil.getVanillaListings(selectedOwner, safeLevel);
        if (listings != null) {
            for (int i = 0; i < listings.length; i++) {
                VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, safeLevel, i, false, 0));
            }
        }

        int defaultOfferCount = VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, safeLevel);
        VillagerConfig.setTradeGroup(new VillagerConfig.TradeGroup(selectedOwner, safeLevel, "replace_level", defaultOfferCount));
        VillagerConfig.normalizeTradeLists();

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
        if (!isAttached()) {
            return;
        }
        if (selectedOwner.isEmpty()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        openChild(new RemovedDefaultTradesPage(this, selectedOwner));
    }

    private void openItemSelector(int slot) {
        if (!isAttached()) {
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
            buyAId = id;
            buyACount = count.equals("0") ? "1" : count;
            buyANbt = nbt;
        } else if (slot == 1) {
            buyBId = id;
            buyBCount = count;
            buyBNbt = nbt;
        } else {
            sellId = id;
            sellCount = count.equals("0") ? "1" : count;
            sellNbt = nbt;
        }
        writeFieldsToWidgets();
    }

    private void openNbtEditor(int slot) {
        syncFieldValues();
        String initial = slot == 0 ? buyANbt : slot == 1 ? buyBNbt : sellNbt;
        {
            KineticSelectors.openNbtEditor(initial, value -> {
                if (slot == 0) {
                    buyANbt = value;
                } else if (slot == 1) {
                    buyBNbt = value;
                } else {
                    sellNbt = value;
                }
            });
        }
    }

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

    private void updateBoolButtons() {
        if (rewardButton != null) {
            rewardButton.setText(rewardText());
        }
        if (restockButton != null) {
            restockButton.setText(restockText());
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

    private String trim(String text, int width) {
        if (KineticText.width(text) <= width) {
            return text;
        }
        return KineticText.trim(text, Math.max(1, width - KineticText.width("..."))) + "...";
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
                VillagerTrades.ItemListing[] listings = VillagerTradeRuntimeUtil.getVanillaListings(owner, level);
                if (listings == null) {
                    continue;
                }
                for (int i = 0; i < listings.length; i++) {
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
            g.centeredText(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.title"), rootX + rootW / 2, rootY + 12, 0xFFFFFF55, true);
            KineticTheme.panelAlt(g, listX, listY, listW, listH);
        }

        @Override
        protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
            if (removedEntries.isEmpty()) {
                g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.empty"), listX + 10, listY + 12, 0xFF55FF55, false);
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
            g.text(KineticI18n.translatable("gui.contentstudio.villager.villager.trade.removed.entry", entry.level(), entry.index()), listX + 10, y + 5, 0xFFFFFF55, false);
            int iconX = listX + 126;
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
                g.item(safeStack, x + 1, y + 1);
                g.itemDecorations(safeStack, x + 1, y + 1);
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
            if (listScroll.release(input.button())) {
                return true;
            }

            return false;
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
        double my = input.y();
            if (listScroll.drag(
                    my,
                    listY + 4,
                    listH - 8,
                    24
            )) {
                return true;
            }

            return false;
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

                if (listScroll.scroll(delta)) {
                    return true;
                }
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
            VillagerConfig.normalizeTradeLists();

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
            VillagerConfig.normalizeTradeLists();

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
            VillagerConfig.normalizeTradeLists();

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
            return new TradeEntry(owner, false, true, data.level(), "", 0, 0, -1, data.index(), data.weight(), data.maxUses(), data.xp(), data.allowRestock(), data.createOffer(), data);
        }
    }
}
