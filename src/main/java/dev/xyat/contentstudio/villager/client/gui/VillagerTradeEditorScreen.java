package dev.xyat.contentstudio.villager.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class VillagerTradeEditorScreen extends KineticScreen {
    private static final int PANEL_COLOR = 0xE0181A22;
    private static final int PANEL_DARK = 0xE008090D;
    private static final int PANEL_MID = 0xD0212530;
    private static final int OUTLINE = 0xFFFFFFFF;
    private static final int TRADE_SLOT_PANEL = 0xE03C4048;
    private static final int TRADE_SLOT_OUTLINE = 0xFFBFC4CC;
    private static final int BUY_A_LABEL = 0xFFFFFF55;
    private static final int BUY_B_LABEL = 0xFF55FFFF;
    private static final int SELL_LABEL = 0xFF55FF55;
    private static final int ROW_H = 41;
    private static final int LEVEL_ROW_H = 24;
    private static final int LEVEL_BUTTON_SIZE = 14;

    public Screen getParent() {
        return parent;
    }

    private static final int MAX_UNDO_STEPS = 10;

    private record TradeConfigState(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean enabled
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
    private static List<String> professionDictionary;
    private static final boolean[] LEVEL_EXPANDED = new boolean[]{false, false, false, false, false};

    private final Screen parent;
    private final List<TradeEntry> entries = new ArrayList<>();
    private final List<TradeSearchEntry> tradeSearchSource = new ArrayList<>();
    private final KineticSearch.Model<TradeSearchEntry> tradeSearchModel =
            new KineticSearch.Model<>(List.of(), TradeSearchEntry::searchText);
    private boolean tradeSearchIndexDirty = true;
    private final List<AbstractWidget> editorWidgets = new ArrayList<>();
    private final List<AbstractWidget> levelWidgets = new ArrayList<>();
    private final List<LevelExpandButton> levelExpandButtons = new ArrayList<>();

    private AutoCompleteBox professionBox;
    private EditBox tradeSearchBox;
    private NumericEditBox buyACountBox;
    private NumericEditBox buyBCountBox;
    private NumericEditBox sellCountBox;
    private NumericEditBox weightBox;
    private NumericEditBox offerCountBox;
    private NumericEditBox maxUsesBox;
    private NumericEditBox xpBox;
    private NumericEditBox priceBox;
    private NumericEditBox demandBox;
    private NumericEditBox specialPriceBox;
    private NumericEditBox usesBox;
    private Button rewardButton;
    private Button restockButton;
    private Button undoButton;
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
    private final GridScrollController listScroll =
            new GridScrollController();

    private String selectedOwner = lastSelectedOwner;
    private int selectedLevel = lastSelectedLevel;
    private String selectedMode = "replace_level";
    private int selectedOfferCount = 0;
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

    public VillagerTradeEditorScreen(Screen parent) {
        super(Component.translatable("gui.contentstudio.villager.villager.trade.title"));
        this.parent = parent;
        dev.xyat.kineticcore.api.client.screen.GuiSession.setParent(this, parent);
        useCanvas(
                640f,
                360f,
                6
        );
        configureDraft(this::captureTradeConfigState, this::restoreTradeConfigState);
    }

    private TradeConfigState captureTradeConfigState() {
        return new TradeConfigState(
                List.copyOf(VillagerConfig.villagerTradeGroups),
                List.copyOf(VillagerConfig.villagerTradeOffers),
                List.copyOf(VillagerConfig.villagerDefaultTradeOverrides),
                VillagerConfig.enableCustomVillagerTrades
        );
    }

    private void restoreTradeConfigState(TradeConfigState state) {
        if (state == null) return;
        VillagerConfig.replaceTradeLists(state.groups(), state.offers(), state.overrides());
        VillagerConfig.enableCustomVillagerTrades = state.enabled();
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
                professionBox.setValue("");
                suppressProfessionResponder = false;
                lastProfessionText = "";
            } else {
                setProfessionBoxDisplay(selectedOwner);
            }
        }
        refreshEntries();
        writeFieldsToWidgets();
        setEditorWidgetsVisible(!selectedOwner.isEmpty() && editorActive);
        setLevelWidgetsVisible(!selectedOwner.isEmpty() && levelSettingsActive);
    }

    @Override
    protected void buildUi() {
        setupLayout();
        this.renderRenderablesOnly = false;
        listScroll.restoreOffset(lastListScroll);

        int topY = rootY + 8;
        this.professionBox = new AutoCompleteBox(this.font, leftX + 6, topY, leftW - 34, 20, Component.translatable("gui.contentstudio.villager.villager.trade.profession.search"), VillagerTradeEditorScreen::getProfessionDictionary);
        this.professionBox.setValue(lastProfessionText);
        this.professionBox.setResponder(value -> {
            if (!suppressProfessionResponder) {
                lastProfessionText = value;
                trySelectOwnerFromText(value);
            }
        });
        this.professionBox.setTooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.profession.search.tooltip")));
        this.addRenderableWidget(this.professionBox);

        Button clearSearchButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.search.clear.short"), button -> clearProfessionSearch())
                .bounds(leftX + leftW - 24, topY, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.search.clear.tooltip")))
                .build();
        this.addRenderableWidget(clearSearchButton);

        this.tradeSearchBox = new EditBox(
                this.font,
                leftX + 6,
                leftY + 6,
                leftW - 34,
                20,
                Component.translatable("gui.contentstudio.villager.villager.trade.content_search")
        );
        this.tradeSearchBox.setMaxLength(128);
        this.tradeSearchBox.setValue(lastTradeSearchText);
        this.tradeSearchBox.setResponder(value -> {
            lastTradeSearchText = value;
            listScroll.reset();
            lastListScroll = 0;
            refreshEntries();
        });
        this.tradeSearchBox.setTooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.content_search.tooltip")));
        this.addRenderableWidget(this.tradeSearchBox);

        Button clearTradeSearchButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.search.clear.short"), button -> clearTradeSearch())
                .bounds(leftX + leftW - 24, leftY + 6, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.content_search.clear.tooltip")))
                .build();
        this.addRenderableWidget(clearTradeSearchButton);

        int gap = 6;
        int backW = 58;
        int saveW = 58;
        int undoW = 78;
        int previewW = 94;
        int backX = rootX + rootW - 8 - backW;
        int saveX = backX - gap - saveW;
        int undoX = saveX - gap - undoW;
        int previewX = undoX - gap - previewW;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.removed_preview"), button -> openRemovedDefaultTradesScreen())
                .bounds(previewX, topY, previewW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.removed_preview.tooltip")))
                .build());

        this.undoButton = this.addRenderableWidget(Button.builder(undoButtonText(), button -> undoLastChange())
                .bounds(undoX, topY, undoW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.undo.tooltip")))
                .build());
        updateUndoButtonState();

        this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.save"), button -> {
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
        }).bounds(saveX, topY, saveW, 20).tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.save.tooltip"))).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.back"), button -> onClose())
                .bounds(backX, topY, backW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.back.tooltip")))
                .build());

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
        setEditorWidgetsVisible(!selectedOwner.isEmpty() && editorActive);
        setLevelWidgetsVisible(!selectedOwner.isEmpty() && levelSettingsActive);
    }

    private void setupLayout() {
        this.rootW = Math.min(this.canvasWidth - 8, 632);
        this.rootH = Math.min(this.canvasHeight - 8, 352);
        this.rootX = (this.canvasWidth - rootW) / 2;
        this.rootY = (this.canvasHeight - rootH) / 2;

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
            LevelExpandButton button = new LevelExpandButton(levelExpandText(level), ignored -> {
                selectedLevel = targetLevel;
                lastSelectedLevel = targetLevel;
                loadGroupState();
                toggleLevelExpanded(targetLevel);
                refreshEntries();
            });
            button.visible = false;
            button.active = false;
            levelExpandButtons.add(this.addWidget(button));
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

        Button addTradeButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.level.add_trade"), button -> beginNewTradeFromLevel())
                .bounds(buttonX, buttonY, buttonW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.level.add_trade.tooltip")))
                .build();
        addLevelWidget(addTradeButton);

        Button clearLevelButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.level.clear"), button -> clearCurrentLevel(selectedLevel))
                .bounds(buttonX, buttonY + 24, buttonW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.level.clear.tooltip")))
                .build();
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
        this.rewardButton = Button.builder(rewardText(), button -> {
            rewardExp = !rewardExp;
            button.setMessage(rewardText());
        }).bounds(metaX, toggleY, 110, 18).tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.reward.tooltip"))).build();
        addEditorWidget(this.rewardButton);

        this.restockButton = Button.builder(restockText(), button -> {
            allowRestock = !allowRestock;
            button.setMessage(restockText());
        }).bounds(metaX + 116, toggleY, 110, 18).tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.restock.tooltip"))).build();
        addEditorWidget(this.restockButton);
    }

    private void addClearSlotButton(int slot, int x, int y) {
        Button clear = Button.builder(
                        Component.translatable("gui.contentstudio.villager.villager.trade.slot.clear"),
                        button -> clearTradeItemSlot(slot)
                )
                .bounds(x, y, 36, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.slot.clear.tooltip")))
                .build();
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

    private NumericEditBox addSmallNumberBox(
            int x,
            int y,
            String value,
            String tooltipKey,
            int minValue
    ) {
        NumericEditBox box = NumericEditBox.integer(
                this.font,
                x,
                y,
                36,
                18,
                Component.empty(),
                minValue < 0,
                minValue,
                64
        );

        box.setMaxLength(3);
        box.setValue(value);
        box.setTooltip(
                Tooltip.create(
                        Component.translatable(tooltipKey)
                )
        );

        addEditorWidget(box);
        return box;
    }

    private NumericEditBox addLevelSmallNumberBox(
            int x,
            int y,
            int value
    ) {
        NumericEditBox box = NumericEditBox.integer(
                this.font,
                x,
                y,
                50,
                18,
                Component.empty(),
                false,
                0,
                64
        );

        box.setMaxLength(3);
        box.setIntValue(value);
        box.setTooltip(
                Tooltip.create(
                        Component.translatable(
                                "gui.contentstudio.villager.villager.trade.offer_count.tooltip"
                        )
                )
        );

        addLevelWidget(box);
        return box;
    }

    private NumericEditBox addIntegerBox(
            int x,
            int y,
            int width,
            String value,
            String tooltipKey,
            int minValue
    ) {
        NumericEditBox box = NumericEditBox.integer(
                this.font,
                x,
                y,
                width,
                18,
                Component.empty(),
                minValue < 0,
                minValue,
                999999
        );

        box.setMaxLength(10);
        box.setValue(value);
        box.setTooltip(
                Tooltip.create(
                        Component.translatable(tooltipKey)
                )
        );

        addEditorWidget(box);
        return box;
    }

    private NumericEditBox addDecimalBox(
            int x,
            int y,
            int width,
            String value
    ) {
        NumericEditBox box = NumericEditBox.decimal(
                this.font,
                x,
                y,
                width,
                18,
                Component.empty(),
                false,
                0.0,
                1000.0
        );

        box.setMaxLength(10);
        box.setValue(value);
        box.setTooltip(
                Tooltip.create(
                        Component.translatable("gui.contentstudio.villager.villager.trade.price.tooltip")
                )
        );

        addEditorWidget(box);
        return box;
    }

    private void addNbtButton(int slot, int x, int y) {
        Button button = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.nbt.button"), b -> openNbtEditor(slot))
                .bounds(x, y, 36, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.nbt.tooltip")))
                .build();
        addEditorWidget(button);
    }

    private void addEditorButtons() {
        int y = rightY + 40;
        int x = rightX + 8;
        int saveW = 84;
        int deleteW = 84;
        int clearW = 96;
        int gap = 8;

        Button saveButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.offer.save"), button -> saveCurrentOffer())
                .bounds(x, y, saveW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.inline.save.tooltip")))
                .build();
        addEditorWidget(saveButton);

        Button deleteButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.offer.delete"), button -> deleteSelectedEntry())
                .bounds(x + saveW + gap, y, deleteW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.inline.delete.tooltip")))
                .build();
        addEditorWidget(deleteButton);

        Button clearCurrentButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.clear_current"), button -> clearCurrentTarget())
                .bounds(x + saveW + gap + deleteW + gap, y, clearW, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.inline.clear_current.tooltip")))
                .build();
        addEditorWidget(clearCurrentButton);
    }

    private void addEditorWidget(AbstractWidget widget) {
        this.editorWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void addLevelWidget(AbstractWidget widget) {
        this.levelWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void setEditorWidgetsVisible(boolean visible) {
        for (AbstractWidget widget : editorWidgets) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private void setLevelWidgetsVisible(boolean visible) {
        for (AbstractWidget widget : levelWidgets) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private void closeProfessionBoxFocus() {
        if (professionBox != null) {
            professionBox.clearSuggestions();
            professionBox.setFocused(false);
        }
        this.setFocused(null);
    }

    @Override
    public void tick() {
        super.tick();
        syncFieldValues();
        syncSelectedOwnerFromBox();
        updateUndoButtonState();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        g.fill(rootX, rootY, rootX + rootW, rootY + rootH, PANEL_COLOR);
        g.renderOutline(rootX, rootY, rootW, rootH, OUTLINE);
        g.fill(leftX, leftY, leftX + leftW, leftY + leftH, PANEL_DARK);
        g.renderOutline(leftX, leftY, leftW, leftH, OUTLINE);
        g.fill(rightX, rightY, rightX + rightW, rightY + rightH, PANEL_DARK);
        g.renderOutline(rightX, rightY, rightW, rightH, OUTLINE);
        if (!selectedOwner.isEmpty() && editorActive && !levelSettingsActive) {
            renderTradeSlotPanels(g);
        }
        if (!selectedOwner.isEmpty() && !editorActive && !levelSettingsActive) {
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.right.empty.title"), rightX + 18, rightY + 24, 0xFFFFFF55, false);
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.right.empty.line1"), rightX + 18, rightY + 48, 0xFFFFFFFF, false);
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.right.empty.line2"), rightX + 18, rightY + 66, 0xFFFFFFFF, false);
        }
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderLeftPanel(g, mx, my);
        renderLevelExpandButtons(g, mx, my, pt);
        renderRightPanel(g, mx, my);
        renderProfessionPlaceholder(g);
        renderTradeSearchPlaceholder(g);
        renderProfessionSearchHint(g);
        if (professionBox != null) {
            professionBox.renderSuggestions(g, mx, my);
        }
    }

    private void renderProfessionPlaceholder(GuiGraphics g) {
        if (professionBox == null || professionBox.isFocused() || !professionBox.getValue().isEmpty()) {
            return;
        }
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.profession.placeholder"), professionBox.getX() + 5, professionBox.getY() + 6, 0xFFCFCFCF, false);
    }

    private void renderTradeSearchPlaceholder(GuiGraphics g) {
        if (tradeSearchBox == null || tradeSearchBox.isFocused() || !tradeSearchBox.getValue().isEmpty()) {
            return;
        }
        g.drawString(
                this.font,
                Component.translatable("gui.contentstudio.villager.villager.trade.content_search.placeholder"),
                tradeSearchBox.getX() + 5,
                tradeSearchBox.getY() + 6,
                0xFFCFCFCF,
                false
        );
    }

    private void renderProfessionSearchHint(GuiGraphics g) {
        if (professionBox == null || !selectedOwner.isEmpty() || isTradeSearching()) {
            return;
        }
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.profession.hint"), listX + 4, listY + 8, 0xFFFFAA00, false);
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

    private void renderLeftPanel(GuiGraphics g, int mx, int my) {
        if (canUseTradeList()) {
            g.fill(listX, listY, listX + listW, listY + listH, 0x66000000);
            g.renderOutline(listX, listY, listW, listH, 0xBBFFFFFF);
            return;
        }

        g.fill(listX, listY, listX + listW, listY + listH, 0x66000000);
        g.renderOutline(listX, listY, listW, listH, 0xFFAAAAAA);

        if (isTradeSearching() && entries.isEmpty()) {
            g.drawCenteredString(
                    this.font,
                    Component.translatable("gui.contentstudio.villager.villager.trade.content_search.empty"),
                    listX + listW / 2,
                    listY + 10,
                    0xFFFFFF55
            );
            return;
        }

        int y = listY + 3 - smoothListShift();
        int start = smoothListStart();
        enableCanvasScissor(g, listX, listY + 3, listX + listW - 10, listY + listH - 3);
        for (int i = start; i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int h = entry.header() ? LEVEL_ROW_H : ROW_H;
            if (y >= listY + listH - 3) {
                break;
            }
            renderEntry(g, mx, my, entry, y, h);
            y += h;
        }
        g.disableScissor();
        renderListScrollbar(g, mx, my);
    }

    private void renderLevelExpandButtons(GuiGraphics g, int mx, int my, float pt) {
        updateLevelExpandButtons();
        for (LevelExpandButton button : levelExpandButtons) {
            if (button.visible) {
                button.render(g, mx, my, pt);
            }
        }
    }

    private void updateLevelExpandButtons() {
        for (LevelExpandButton button : levelExpandButtons) {
            button.visible = false;
            button.active = false;
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
                    LevelExpandButton button = levelExpandButtons.get(index);
                    button.setX(listX + 8);
                    button.setY(y + 4);
                    button.setMessage(levelExpandText(entry.level()));
                    button.visible = true;
                    button.active = true;
                }
            }
            y += h;
        }
    }

    private LevelExpandButton hoveredLevelExpandButton(double mx, double my) {
        for (LevelExpandButton button : levelExpandButtons) {
            if (button.visible && button.isMouseOver(mx, my)) {
                return button;
            }
        }
        return null;
    }

    private void renderEntry(GuiGraphics g, int mx, int my, TradeEntry entry, int y, int h) {
        boolean hover = mx >= listX + 4 && mx <= listX + listW - 10 && my >= y && my < y + h;
        boolean selected = Objects.equals(selectedKey, entry.key());
        int bg;
        int outline;
        if (entry.header()) {
            bg = selected ? 0xCC5A3E12 : hover ? 0xAA4A3518 : 0xAA16181E;
            outline = selected || hover ? 0xFFFFAA00 : 0xCC555555;
        } else if (entry.custom()) {
            bg = selected ? 0xAA444444 : hover ? 0x88383838 : 0x66222222;
            outline = selected ? 0xFFAAAAAA : hover ? 0xFF888888 : 0xAA666666;
        } else {
            bg = selected ? 0xAA444444 : hover ? 0x88403A24 : 0x66191B20;
            outline = selected ? 0xFFAAAAAA : hover ? 0xFFFFAA00 : 0x99FFFFFF;
        }
        g.fill(listX + 4, y, listX + listW - 12, y + h - 2, bg);
        g.renderOutline(listX + 4, y, listW - 16, h - 2, outline);

        if (entry.header()) {
            String left = Component.translatable("gui.contentstudio.villager.villager.trade.trade_list.level", entry.level(), modeName(entry.mode())).getString();
            String right = Component.translatable("gui.contentstudio.villager.villager.trade.trade_list.level_summary", entry.levelCustomCount(), entry.levelOfferCount()).getString();

            int leftTextX = listX + 29;
            int rightPadding = 22;
            int minGap = 10;

            int maxRightWidth = Math.max(38, listX + listW - rightPadding - leftTextX - 74);
            String safeRight = trim(right, Math.min(154, maxRightWidth));
            int rightTextX = listX + listW - rightPadding - this.font.width(safeRight);

            int maxLeftWidth = Math.max(32, rightTextX - leftTextX - minGap);
            String safeLeft = trim(left, maxLeftWidth);

            g.drawString(this.font, safeLeft, leftTextX, y + 7, 0xFFFFFF55, false);
            g.drawString(this.font, safeRight, rightTextX, y + 7, 0xFFFFFFFF, false);
            return;
        }

        Component source = Component.translatable(entry.custom() ? "gui.contentstudio.villager.villager.trade.source.custom" : "gui.contentstudio.villager.villager.trade.source.default");
        String offerMeta = Component.translatable("gui.contentstudio.villager.villager.trade.offer.meta.weight", entry.weight(), entry.maxUses(), entry.xp(), entry.restock() ? Component.translatable("gui.contentstudio.villager.villager.trade.yes").getString() : Component.translatable("gui.contentstudio.villager.villager.trade.no").getString()).getString();
        String meta = isTradeSearching()
                ? Component.translatable(
                "gui.contentstudio.villager.villager.trade.content_search.result_meta",
                getProfessionName(entry.owner()),
                entry.level(),
                offerMeta
        ).getString()
                : offerMeta;

        int sourceColor = entry.custom() ? 0xFFFF55FF : 0xFF55FF55;
        g.drawString(this.font, trim(source.getString(), 48), listX + 9, y + 4, sourceColor, false);
        renderOfferIconLine(g, entry.offer(), listX + 56, y + 2);
        g.drawString(this.font, trim(meta, listW - 28), listX + 9, y + 27, 0xFFFFFFFF, false);
    }

    private void renderOfferIconLine(GuiGraphics g, MerchantOffer offer, int x, int y) {
        if (offer == null) {
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.offer.invalid"), x, y + 5, 0xFFFF5555, false);
            return;
        }

        int slotY = y + 1;
        int plusX = x + 22;
        int bX = plusX + 12;
        int arrowX = bX + 22;
        int outX = arrowX + 16;

        renderMiniStack(g, offer.getBaseCostA(), x, slotY);
        g.drawString(this.font, "+", plusX + 2, slotY + 6, 0xFFFFFF55, false);
        ItemStack buyB = offer.getCostB();
        renderMiniStack(g, buyB, bX, slotY);
        g.drawString(this.font, "→", arrowX + 1, slotY + 6, 0xFF55FF55, false);
        renderMiniStack(g, offer.getResult(), outX, slotY);
    }

    private void renderMiniStack(GuiGraphics g, ItemStack stack, int x, int y) {
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        renderListFlatSlot(g, x, y);
        if (!safeStack.isEmpty()) {
            g.renderItem(safeStack, x + 1, y + 1);
            renderGreenItemCount(g, safeStack, x + 1, y + 1);
        }
    }

    private void renderListFlatSlot(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        GuiTheme.itemSlot(
                graphics,
                x,
                y,
                18,
                4,
                false
        );
    }

    private void renderGreenItemCount(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }
        String countText = String.valueOf(stack.getCount());
        int textX = x + 17 - this.font.width(countText);
        int textY = y + 9;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.drawString(this.font, countText, textX + 1, textY + 1, 0xFF000000, false);
        g.drawString(this.font, countText, textX, textY, 0xFF55FF55, false);
        g.pose().popPose();
    }

    private void renderListScrollbar(
            GuiGraphics graphics,
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
                24,
                GuiTheme.current().scrollTrack(),
                GuiTheme.current().scrollThumb(),
                GuiTheme.current().scrollThumbHover()
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

    private void renderRightPanel(GuiGraphics g, int mx, int my) {
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
        g.drawString(this.font, trim(Component.translatable("gui.contentstudio.villager.villager.trade.selected_profession", ownerName).getString(), rightW - 20), rightX + 8, rightY + 8, 0xFFFFFF55, false);
        g.drawString(this.font, trim(Component.translatable("gui.contentstudio.villager.villager.trade.selected_state", selectedLevel, modeName(selectedMode), countCustomOffers(selectedOwner, selectedLevel)).getString(), rightW - 20), rightX + 8, rightY + 24, 0xFFFFFFFF, false);

        int noticeX = rightX + 8;
        int noticeY = rightY + 64;
        g.fill(noticeX, noticeY, noticeX + rightW - 16, noticeY + 18, PANEL_MID);
        g.renderOutline(noticeX, noticeY, rightW - 16, 18, 0xAAFFFFFF);
        Component state = editingDefault ? Component.translatable("gui.contentstudio.villager.villager.trade.inline.editing_default") : (editingCustomIndex >= 0 ? Component.translatable("gui.contentstudio.villager.villager.trade.inline.editing_custom") : Component.translatable("gui.contentstudio.villager.villager.trade.inline.creating"));
        g.drawString(this.font, trim(state.getString(), rightW - 28), noticeX + 6, noticeY + 5, 0xFFFF55FF, false);

        renderTradeSlots(g, mx, my);

        int metaX = rightX + 14;
        int metaY = rightY + 196;
        int metaY2 = metaY + 38;
        int fieldGap = 88;
        int labelY = metaY - 14;
        int labelY2 = metaY2 - 14;
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.weight"), metaX, labelY, 0xFFFFFF55, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.max_uses"), metaX + fieldGap, labelY, 0xFFFFFF55, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.xp"), metaX + fieldGap * 2, labelY, 0xFFFFFF55, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.price"), metaX + fieldGap * 3, labelY, 0xFFFFFF55, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.demand"), metaX, labelY2, 0xFFFFFFFF, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.special_price"), metaX + fieldGap, labelY2, 0xFFFFFFFF, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.uses"), metaX + fieldGap * 2, labelY2, 0xFFFFFFFF, false);
    }

    private void renderLevelSettingsPanel(GuiGraphics g) {
        String ownerName = getProfessionName(selectedOwner);
        int defaultCount = VillagerTradeRuntimeUtil.getDefaultOfferCount(selectedOwner, selectedLevel);
        int controlRightX = rightX + rightW - 190;

        g.drawString(this.font, trim(Component.translatable("gui.contentstudio.villager.villager.trade.selected_profession", ownerName).getString(), controlRightX - rightX - 12), rightX + 8, rightY + 8, 0xFFFFFF55, false);
        g.drawString(this.font, trim(Component.translatable("gui.contentstudio.villager.villager.trade.level.settings_state", selectedLevel, modeName(selectedMode), selectedOfferCount, defaultCount).getString(), controlRightX - rightX - 12), rightX + 8, rightY + 24, 0xFFFFFFFF, false);

        int buttonW = 92;
        int buttonX = rightX + rightW - 110;
        int buttonY = rightY + 8;
        int countX = buttonX - 70;

        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.offer_count.label"), countX, buttonY, 0xFFFFFF55, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.level.settings.title"), rightX + 8, rightY + 48, 0xFFFFFF55, false);
        g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.level.settings.tip"), rightX + 8, rightY + 64, 0xFFFFFFFF, false);

        g.renderOutline(countX - 8, buttonY - 4, buttonW + 82, 52, 0x55FFFFFF);
    }

    private void renderTradeSlotPanels(GuiGraphics g) {
        int previewY = rightY + 90;
        int contentX = rightX + 14;
        int bX = contentX + 112;
        int sellX = contentX + 270;

        renderTradeSlotSection(g, contentX, previewY);
        renderTradeSlotSection(g, bX, previewY);
        renderTradeSlotSection(g, sellX, previewY);
    }

    private void renderTradeSlots(GuiGraphics g, int mx, int my) {
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

    private void renderTradeSlotSection(GuiGraphics g, int x, int y) {
        g.fill(x - 6, y - 6, x + 72, y + 85, TRADE_SLOT_PANEL);
        g.renderOutline(x - 6, y - 6, 78, 91, TRADE_SLOT_OUTLINE);
    }

    private void renderLargeTradeSymbol(GuiGraphics g, String text, int centerX, int centerY) {
        float scale = 5.0F;
        g.pose().pushPose();
        float drawX = centerX - this.font.width(text) * scale / 2.0F;
        float drawY = centerY - this.font.lineHeight * scale / 2.0F;
        g.pose().translate(drawX, drawY, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(this.font, text, 0, 0, 0xFFFFFF55, false);
        g.pose().popPose();
    }

    private void renderSlotCell(GuiGraphics g, int mx, int my, int slot, int x, int y, String id, String count, String nbt) {
        Component label = Component.translatable(slot == 0 ? "gui.contentstudio.villager.villager.trade.slot.buy_a" : slot == 1 ? "gui.contentstudio.villager.villager.trade.slot.buy_b" : "gui.contentstudio.villager.villager.trade.slot.sell");
        ItemStack stack = createStack(id, VillagerConfig.parseIntValue(count, slot == 1 ? 0 : 1, slot == 1 ? 0 : 1, 64), nbt);
        int slotY = y + 20;

        int labelColor = slot == 0 ? BUY_A_LABEL : slot == 1 ? BUY_B_LABEL : SELL_LABEL;
        g.drawString(this.font, trim(label.getString(), 94), x, y, labelColor, false);
        renderInsetSlot(g, x, slotY, isHoverSlot(mx, my, slot));
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 4, slotY + 4);
            g.renderItemDecorations(this.font, stack, x + 4, slotY + 4);
        }
    }

    private void renderInsetSlot(
            GuiGraphics graphics,
            int x,
            int y,
            boolean hovered
    ) {
        GuiTheme.itemSlot(
                graphics,
                x,
                y,
                24,
                4,
                hovered
        );
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        if (hoveredLevelExpandButton(smx, smy) != null) {
            GuiOverlay.requestTooltip(Component.translatable("gui.contentstudio.villager.villager.trade.level.arrow.tooltip"), mx, my);
            return;
        }
        int hoverSlot = hoveredSlot(smx, smy);
        if (hoverSlot >= 0) {
            String key = hoverSlot == 0 ? "gui.contentstudio.villager.villager.trade.slot.buy_a.tooltip" : hoverSlot == 1 ? "gui.contentstudio.villager.villager.trade.slot.buy_b.tooltip" : "gui.contentstudio.villager.villager.trade.slot.sell.tooltip";
            GuiOverlay.requestTooltip(Component.translatable(key), mx, my);
            return;
        }
        ItemStack hoveredListStack = findHoveredLeftListStack(smx, smy);
        if (!hoveredListStack.isEmpty()) {
            GuiOverlay.requestItemTooltip(hoveredListStack, mx, my);
            return;
        }
        TradeEntry hoveredEntry = findEntryAt(smx, smy);
        if (hoveredEntry != null && hoveredEntry.header()) {
            GuiOverlay.requestTooltip(Component.translatable("gui.contentstudio.villager.villager.trade.level.row.tooltip"), mx, my);
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

    private boolean handleLevelExpandButtonClick(double mx, double my, int btn) {
        LevelExpandButton button = hoveredLevelExpandButton(mx, my);
        return button != null && button.mouseClicked(mx, my, btn);
    }

    @Override
    protected boolean canvasMouseClicked(double mx, double my, int btn) {
        if (professionBox != null) {
            String before = professionBox.getValue();
            if (professionBox.handleMouseClick(mx, my)) {
                String after = professionBox.getValue();
                if (!Objects.equals(before, after)) {
                    syncSelectedOwnerFromBox();
                    closeProfessionBoxFocus();
                }
                return true;
            }
            if (btn == 0 && !professionBox.isMouseOver(mx, my)) {
                closeProfessionBoxFocus();
            }
        }

        updateMainListScrollRange();

        if (btn == 0
                && listScroll.beginDrag(
                mx,
                my,
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

        if (handleLevelExpandButtonClick(mx, my, btn)) {
            return true;
        }
        if (handleListClick(mx, my, btn)) {
            return true;
        }
        if (btn == 0 && handleSlotClick(mx, my)) {
            return true;
        }
        return super.canvasMouseClicked(mx, my, btn);
    }

    @Override
    protected boolean canvasMouseReleased(
            double mx,
            double my,
            int btn
    ) {
        boolean handled =
                listScroll.release(btn);

        if (professionBox != null) {
            handled |=
                    professionBox.handleMouseReleased(btn);
        }

        return handled
                || super.canvasMouseReleased(
                mx,
                my,
                btn
        );
    }

    @Override
    protected boolean canvasMouseDragged(
            double mx,
            double my,
            int btn,
            double dx,
            double dy
    ) {
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

        if (professionBox != null
                && professionBox.handleMouseDragged(my)) {
            return true;
        }

        return super.canvasMouseDragged(
                mx,
                my,
                btn,
                dx,
                dy
        );
    }

    @Override
    protected boolean canvasMouseScrolled(double mx, double my, double delta) {
        if (professionBox != null && professionBox.handleMouseScrolled(delta)) {
            return true;
        }
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
        return super.canvasMouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasControlDown()) {
            if (!undoHistory.isEmpty()) {
                undoLastChange();
            }
            return true;
        }
        if (professionBox != null && professionBox.isFocused() && professionBox.handleKeyPressed(keyCode)) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                syncSelectedOwnerFromBox();
                closeProfessionBoxFocus();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleListClick(double mx, double my, int btn) {
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

                    if (btn == 1) {
                        clearCurrentLevel(entry.level());
                        return true;
                    }

                    if (btn == 0) {
                        selectLevelSettings(entry.level());
                        refreshEntries();
                        return true;
                    }
                }
                if (btn == 1) {
                    deleteEntry(entry);
                    return true;
                }
                if (btn == 0) {
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
        String value = professionBox.getValue();
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
        String clean = VillagerConfig.clean(stripDictionaryText(raw));
        if (isValidOwner(clean)) {
            return clean;
        }
        for (String option : getProfessionDictionary()) {
            String id = stripDictionaryText(option);
            String name = getProfessionName(id);
            if (name.equalsIgnoreCase(raw) || option.equalsIgnoreCase(raw)) {
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
        String display = getProfessionName(owner);
        professionBox.setValue(display);
        professionBox.setCursorPosition(display.length());
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
            professionBox.setValue("");
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
        if (tradeSearchBox != null && !tradeSearchBox.getValue().isEmpty()) {
            tradeSearchBox.setValue("");
        } else {
            refreshEntries();
        }
    }

    private boolean isValidOwner(String owner) {
        String clean = VillagerConfig.clean(owner);
        if (clean.equals(VillagerConfig.WANDERING_TRADER_ID)) {
            return true;
        }
        ResourceLocation id = ResourceLocation.tryParse(clean);
        return id != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id);
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
            offerCountBox.setValue(String.valueOf(selectedOfferCount));
        }
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

        for (String option : getProfessionDictionary()) {
            String owner = stripDictionaryText(option);
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
                .append(Component.translatable(
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
        String value = tradeSearchBox == null ? lastTradeSearchText : tradeSearchBox.getValue();
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
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        UndoCheckpoint checkpoint = beginUndoableChange();
        applyCurrentLevelSettings();
        clearSelectionToNewOffer();
        refreshEntries();
        finishUndoableChange(checkpoint);
        GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.new_draft"));
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
        return Component.translatable(
                "gui.contentstudio.villager.villager.trade.undo",
                undoHistory.size(),
                MAX_UNDO_STEPS
        );
    }

    private void updateUndoButtonState() {
        if (undoButton == null) return;
        undoButton.setMessage(undoButtonText());
        undoButton.active = !undoHistory.isEmpty();
    }

    private void syncFieldValues() {
        if (buyACountBox != null) {
            buyACount = buyACountBox.getValue();
        }
        if (buyBCountBox != null) {
            buyBCount = buyBCountBox.getValue();
        }
        if (sellCountBox != null) {
            sellCount = sellCountBox.getValue();
        }
        if (weightBox != null) {
            weight = weightBox.getValue();
        }
        if (offerCountBox != null) {
            selectedOfferCount = currentOfferCountValue();
        }
        if (maxUsesBox != null) {
            maxUses = maxUsesBox.getValue();
        }
        if (xpBox != null) {
            xp = xpBox.getValue();
        }
        if (priceBox != null) {
            price = priceBox.getValue();
        }
        if (demandBox != null) {
            demand = demandBox.getValue();
        }
        if (specialPriceBox != null) {
            specialPrice = specialPriceBox.getValue();
        }
        if (usesBox != null) {
            uses = usesBox.getValue();
        }
    }

    private void writeFieldsToWidgets() {
        if (buyACountBox != null) {
            buyACountBox.setValue(buyACount);
        }
        if (buyBCountBox != null) {
            buyBCountBox.setValue(buyBCount);
        }
        if (sellCountBox != null) {
            sellCountBox.setValue(sellCount);
        }
        if (weightBox != null) {
            weightBox.setValue(weight);
        }
        if (offerCountBox != null) {
            offerCountBox.setValue(String.valueOf(selectedOfferCount));
        }
        if (maxUsesBox != null) {
            maxUsesBox.setValue(maxUses);
        }
        if (xpBox != null) {
            xpBox.setValue(xp);
        }
        if (priceBox != null) {
            priceBox.setValue(price);
        }
        if (demandBox != null) {
            demandBox.setValue(demand);
        }
        if (specialPriceBox != null) {
            specialPriceBox.setValue(specialPrice);
        }
        if (usesBox != null) {
            usesBox.setValue(uses);
        }
        updateBoolButtons();
    }

    private void saveCurrentOffer() {
        syncFieldValues();
        if (selectedOwner.isEmpty() || !editorActive) {
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        if (!validateNbt()) {
            showWarningToast(Component.translatable("msg.contentstudio.villager.villager.trade.invalid_nbt"));
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
                GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.default_disabled"));
                return;
            }

            VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, selectedLevel, editingVanillaIndex, true, currentWeight));
            if (isSameAsOriginalVanilla()) {
                ensureControlledGroup();
                refreshEntries();
                finishUndoableChange(checkpoint);
                GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.default_saved"));
                return;
            }

            VillagerConfig.setVanillaTradeEnabled(selectedOwner, selectedLevel, editingVanillaIndex, false);
            VillagerConfig.TradeOfferData converted = buildOfferData(-1);
            if (converted == null) {
                finishUndoableChange(checkpoint);
                showWarningToast(Component.translatable("msg.contentstudio.villager.villager.trade.invalid_items"));
                return;
            }
            editingCustomIndex = VillagerConfig.addTradeOffer(converted);
            editingDefault = false;
            editingVanillaIndex = -1;
            selectedKey = selectedOwner + "|custom:" + editingCustomIndex;
            ensureControlledGroup();
            refreshEntries();
            finishUndoableChange(checkpoint);
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.default_converted"));
            return;
        }

        VillagerConfig.TradeOfferData data = buildOfferData(editingCustomIndex);
        if (data == null) {
            finishUndoableChange(checkpoint);
            showWarningToast(Component.translatable("msg.contentstudio.villager.villager.trade.invalid_items"));
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
        GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.offer_saved"));
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

    private boolean validateNbt() {
        return validNbt(buyANbt) && validNbt(buyBNbt) && validNbt(sellNbt);
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
        GuiOverlay.toast("villager_trade_warning", message, GuiOverlay.Position.BOTTOM_CENTER, 5000, 0, -30);
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
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.no_offer_selected"));
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
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.offer_deleted"));
        } else {
            VillagerConfig.setVanillaTradeOverride(new VillagerConfig.VanillaTradeOverride(selectedOwner, entry.level(), entry.vanillaIndex(), false, 0));
            selectedLevel = entry.level();
            ensureControlledGroup();
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.default_disabled"));
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
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }

        TradeEntry entry = findSelectedEntry();
        if (entry != null && entry.header()) {
            clearCurrentLevel(entry.level());
            return;
        }

        if (editorActive) {
            loadBlankOffer();
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.current_content_cleared"));
            return;
        }

        clearCurrentLevel(selectedLevel);
    }

    private void clearCurrentLevel(int level) {
        if (selectedOwner.isEmpty()) {
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
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
        GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.current_level_cleared"));
    }

    private void openRemovedDefaultTradesScreen() {
        if (this.minecraft == null) {
            return;
        }
        if (selectedOwner.isEmpty()) {
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.no_profession"));
            return;
        }
        this.minecraft.setScreen(new RemovedDefaultTradesScreen(this, selectedOwner));
    }

    private void openItemSelector(int slot) {
        if (this.minecraft == null) {
            return;
        }
        syncFieldValues();
        this.minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
            if (selection != null && selection.isItem()) {
                ItemStack stack = selection.stack().copy();
                setSlotStack(slot, stack);
            }
            Minecraft.getInstance().setScreen(this);
        }));
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
        if (this.minecraft != null) {
            this.minecraft.setScreen(new NbtEditorScreen(initial, value -> {
                if (slot == 0) {
                    buyANbt = value;
                } else if (slot == 1) {
                    buyBNbt = value;
                } else {
                    sellNbt = value;
                }
            }, this));
        }
    }

    private ItemStack createStack(String itemId, int count, String nbt) {
        String id = VillagerConfig.clean(itemId);
        if (id.isEmpty() || id.equals("minecraft:air") || id.equals("air") || count <= 0) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(location);
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
            rewardButton.setMessage(rewardText());
        }
        if (restockButton != null) {
            restockButton.setMessage(restockText());
        }
    }

    private Component rewardText() {
        return Component.translatable(rewardExp ? "gui.contentstudio.villager.villager.trade.reward.on" : "gui.contentstudio.villager.villager.trade.reward.off");
    }

    private Component restockText() {
        return Component.translatable(allowRestock ? "gui.contentstudio.villager.villager.trade.restock.on" : "gui.contentstudio.villager.villager.trade.restock.off");
    }

    public static List<String> getProfessionDictionary() {
        if (professionDictionary == null) {
            professionDictionary = new ArrayList<>();
            professionDictionary.add(VillagerConfig.WANDERING_TRADER_ID + " - " + Component.translatable("entity.minecraft.wandering_trader").getString());
            professionDictionary.addAll(BuiltInRegistries.VILLAGER_PROFESSION.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .map(id -> id + " - " + getProfessionName(id))
                    .toList());
        }
        return professionDictionary;
    }

    public static String stripDictionaryText(String value) {
        String text = value == null ? "" : value.trim();
        int split = text.indexOf(" - ");
        return split >= 0 ? text.substring(0, split).trim() : text;
    }

    public static String getProfessionName(String profession) {
        String id = VillagerConfig.clean(profession);
        if (id.equals(VillagerConfig.WANDERING_TRADER_ID)) {
            return Component.translatable("entity.minecraft.wandering_trader").getString();
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return id;
        }
        String key = "entity.minecraft.villager." + location.getPath();
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? id : translated;
    }

    public static String modeName(String mode) {
        return switch (VillagerConfig.TradeGroup.normalizeMode(mode)) {
            case "add" -> Component.translatable("gui.contentstudio.villager.villager.trade.mode.short.add").getString();
            case "disable_level" -> Component.translatable("gui.contentstudio.villager.villager.trade.mode.short.disable").getString();
            default -> Component.translatable("gui.contentstudio.villager.villager.trade.mode.short.replace").getString();
        };
    }

    private String trim(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(1, width - this.font.width("..."))) + "...";
    }

    private final class LevelExpandButton extends Button {
        private LevelExpandButton(Component message, OnPress onPress) {
            super(0, 0, LEVEL_BUTTON_SIZE, LEVEL_BUTTON_SIZE, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            boolean hovered = isMouseOver(mouseX, mouseY);

            g.fill(x, y, x + width, y + height, hovered ? 0xFF777A86 : 0xFF555863);
            g.fill(x + 1, y + 1, x + width - 1, y + 2, hovered ? 0xFFFFFFFF : 0xFFC4C7D0);
            g.fill(x + 1, y + 1, x + 2, y + height - 1, hovered ? 0xFFFFFFFF : 0xFFC4C7D0);
            g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF202129);
            g.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF202129);
            g.renderOutline(x, y, width, height, hovered ? 0xFFFFFFFF : 0xFF8E929E);

            int iconColor = active ? 0xFF55FF55 : 0xFFAAAAAA;
            g.drawCenteredString(VillagerTradeEditorScreen.this.font, getMessage(), x + width / 2, y + 3, iconColor);
        }
    }

    private static class RemovedDefaultTradesScreen extends KineticScreen {
        private static final int ROW_HEIGHT = 42;

        private final VillagerTradeEditorScreen parentScreen;
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
        private final GridScrollController listScroll =
                new GridScrollController();
        private boolean resetConfirmOpen;
        private Button resetConfirmYesButton;
        private Button resetConfirmNoButton;

        RemovedDefaultTradesScreen(VillagerTradeEditorScreen parentScreen, String owner) {
            super(Component.translatable("gui.contentstudio.villager.villager.trade.removed.title"));
            this.parentScreen = parentScreen;
            this.owner = VillagerConfig.clean(owner);
            useCanvas(
                    520f,
                    320f,
                    6
            );
        }

        @Override
        protected void buildUi() {
            this.renderRenderablesOnly = false;
            this.rootW = Math.min(this.canvasWidth - 8, 512);
            this.rootH = Math.min(this.canvasHeight - 8, 312);
            this.rootX = (this.canvasWidth - rootW) / 2;
            this.rootY = (this.canvasHeight - rootH) / 2;
            this.listX = rootX + 10;
            this.listY = rootY + 32;
            this.listW = rootW - 20;
            this.listH = rootH - 44;

            int buttonY = rootY + 8;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.removed.restore_all"), button -> restoreAllRemovedTrades())
                    .bounds(rootX + 10, buttonY, 50, 20)
                    .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.removed.restore_all.tooltip")))
                    .build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset"), button -> openResetConfirmPopup())
                    .bounds(rootX + 68, buttonY, 50, 20)
                    .tooltip(Tooltip.create(Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset.tooltip")))
                    .build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.removed.back"), button -> returnToParent())
                    .bounds(rootX + rootW - 60, buttonY, 50, 20)
                    .build());

            int popupW = 270;
            int popupH = 112;
            int popupX = rootX + (rootW - popupW) / 2;
            int popupY = rootY + (rootH - popupH) / 2;
            int confirmY = popupY + 78;
            this.resetConfirmYesButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.yes"), button -> resetCurrentProfessionToDefault())
                    .bounds(popupX + 32, confirmY, 72, 20)
                    .build();
            this.resetConfirmNoButton = Button.builder(Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.no"), button -> closeResetConfirmPopup())
                    .bounds(popupX + popupW - 104, confirmY, 72, 20)
                    .build();
            this.addRenderableWidget(this.resetConfirmYesButton);
            this.addRenderableWidget(this.resetConfirmNoButton);
            setResetConfirmButtonsVisible(false);

            refreshRemovedEntries();
        }

        private void refreshRemovedEntries() {
            removedEntries.clear();
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
        protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
            g.fill(rootX, rootY, rootX + rootW, rootY + rootH, PANEL_COLOR);
            g.renderOutline(rootX, rootY, rootW, rootH, OUTLINE);
            g.drawCenteredString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.removed.title"), rootX + rootW / 2, rootY + 12, 0xFFFFFF55);
            g.fill(listX, listY, listX + listW, listY + listH, PANEL_DARK);
            g.renderOutline(listX, listY, listW, listH, 0xFFAAAAAA);
        }

        @Override
        protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
            if (removedEntries.isEmpty()) {
                g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.removed.empty"), listX + 10, listY + 12, 0xFF55FF55, false);
            } else {
                int visualShift = listScroll.visualShift(ROW_HEIGHT);
                int y = listY + 4 - visualShift;
                enableCanvasScissor(g, listX, listY + 4, listX + listW - 10, listY + listH - 4);
                for (int i = listScroll.smoothIndexOffset(); i < removedEntries.size(); i++) {
                    RemovedDefaultEntry entry = removedEntries.get(i);
                    if (y >= listY + listH - 4) {
                        break;
                    }
                    renderRemovedEntry(g, mx, my, entry, y);
                    y += ROW_HEIGHT;
                }
                g.disableScissor();
                renderRemovedScrollbar(g, mx, my);
            }

            if (resetConfirmOpen) {
                renderResetConfirmPopup(g, mx, my, pt);
            }
        }


        private void renderResetConfirmPopup(GuiGraphics g, int mx, int my, float pt) {
            int popupW = 270;
            int popupH = 112;
            int popupX = rootX + (rootW - popupW) / 2;
            int popupY = rootY + (rootH - popupH) / 2;

            GuiTheme.shadow(g, this.canvasWidth, this.canvasHeight);
            GuiTheme.panel(g, popupX, popupY, popupW, popupH, PANEL_COLOR, 0xFFFFAA00);

            g.drawCenteredString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.title"), popupX + popupW / 2, popupY + 10, 0xFFFFFF55);
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.line1"), popupX + 12, popupY + 32, 0xFFFFFFFF, false);
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.removed.reset.confirm.line2"), popupX + 12, popupY + 48, 0xFFFF5555, false);

            if (resetConfirmYesButton != null) {
                resetConfirmYesButton.render(g, mx, my, pt);
            }
            if (resetConfirmNoButton != null) {
                resetConfirmNoButton.render(g, mx, my, pt);
            }
        }

        private void openResetConfirmPopup() {
            resetConfirmOpen = true;
            setResetConfirmButtonsVisible(true);
        }

        private void closeResetConfirmPopup() {
            resetConfirmOpen = false;
            setResetConfirmButtonsVisible(false);
        }

        private void setResetConfirmButtonsVisible(boolean visible) {
            if (resetConfirmYesButton != null) {
                resetConfirmYesButton.visible = visible;
                resetConfirmYesButton.active = visible;
            }
            if (resetConfirmNoButton != null) {
                resetConfirmNoButton.visible = visible;
                resetConfirmNoButton.active = visible;
            }
        }

        private void handleResetConfirmClick(double mx, double my, int btn) {
            if (resetConfirmYesButton != null) {
                resetConfirmYesButton.mouseClicked(mx, my, btn);
            }
            if (resetConfirmNoButton != null) {
                resetConfirmNoButton.mouseClicked(mx, my, btn);
            }
        }

        private void renderRemovedEntry(GuiGraphics g, int mx, int my, RemovedDefaultEntry entry, int y) {
            boolean hover = mx >= listX + 4 && mx <= listX + listW - 12 && my >= y && my < y + ROW_HEIGHT - 2;
            int bg = hover ? 0x884A3518 : 0x66191B20;
            int outline = hover ? 0xFFFFAA00 : 0x99FFFFFF;
            g.fill(listX + 4, y, listX + listW - 12, y + ROW_HEIGHT - 2, bg);
            g.renderOutline(listX + 4, y, listW - 16, ROW_HEIGHT - 2, outline);
            g.drawString(this.font, Component.translatable("gui.contentstudio.villager.villager.trade.removed.entry", entry.level(), entry.index()), listX + 10, y + 5, 0xFFFFFF55, false);
            int iconX = listX + 126;
            int iconY = y + 13;
            renderRemovedMiniStack(g, entry.offer().getBaseCostA(), iconX, iconY);
            g.drawString(this.font, "+", iconX + 24, iconY + 6, 0xFFFFFF55, false);
            renderRemovedMiniStack(g, entry.offer().getCostB(), iconX + 42, iconY);
            g.drawString(this.font, "→", iconX + 68, iconY + 6, 0xFF55FF55, false);
            renderRemovedMiniStack(g, entry.offer().getResult(), iconX + 90, iconY);
        }

        private void renderRemovedMiniStack(GuiGraphics g, ItemStack stack, int x, int y) {
            ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
            renderRemovedFlatSlot(g, x, y);
            if (!safeStack.isEmpty()) {
                g.renderItem(safeStack, x + 1, y + 1);
                g.renderItemDecorations(this.font, safeStack, x + 1, y + 1);
            }
        }

        private void renderRemovedFlatSlot(
                GuiGraphics graphics,
                int x,
                int y
        ) {
            GuiTheme.itemSlot(
                    graphics,
                    x,
                    y,
                    18,
                    4,
                    false
            );
        }

        private void renderRemovedScrollbar(
                GuiGraphics graphics,
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
                    24,
                    GuiTheme.current().scrollTrack(),
                    GuiTheme.current().scrollThumb(),
                    GuiTheme.current().scrollThumbHover()
            );
        }

        @Override
        protected boolean canvasMouseClicked(double mx, double my, int btn) {
            if (resetConfirmOpen) {
                handleResetConfirmClick(mx, my, btn);
                return true;
            }
            listScroll.update(
                    removedEntries.size(),
                    getVisibleRows()
            );

            if (btn == 0
                    && listScroll.beginDrag(
                    mx,
                    my,
                    listX + listW - 8,
                    listY + 4,
                    4,
                    listH - 8,
                    24,
                    2
            )) {
                return true;
            }
            if (btn == 1) {
                RemovedDefaultEntry entry = findEntryAt(mx, my);
                if (entry != null) {
                    restoreOneRemovedTrade(entry);
                    return true;
                }
            }
            return super.canvasMouseClicked(mx, my, btn);
        }

        @Override
        protected boolean canvasMouseReleased(
                double mx,
                double my,
                int btn
        ) {
            if (listScroll.release(btn)) {
                return true;
            }

            return super.canvasMouseReleased(
                    mx,
                    my,
                    btn
            );
        }

        @Override
        protected boolean canvasMouseDragged(
                double mx,
                double my,
                int btn,
                double dx,
                double dy
        ) {
            if (listScroll.drag(
                    my,
                    listY + 4,
                    listH - 8,
                    24
            )) {
                return true;
            }

            return super.canvasMouseDragged(
                    mx,
                    my,
                    btn,
                    dx,
                    dy
            );
        }

        @Override
        protected boolean canvasMouseScrolled(
                double mx,
                double my,
                double delta
        ) {
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

            return super.canvasMouseScrolled(
                    mx,
                    my,
                    delta
            );
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
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.removed_restored"));
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
                GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.removed_empty"));
                return;
            }

            VillagerConfig.enableCustomVillagerTrades = true;
            VillagerConfig.normalizeTradeLists();

            refreshRemovedEntries();
            parentScreen.loadGroupState();
            parentScreen.invalidateTradeSearchIndex();
            parentScreen.refreshEntries();
            parentScreen.finishUndoableChange(checkpoint);
            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.removed_restored_all"));
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

            closeResetConfirmPopup();
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

            GuiOverlay.toast(Component.translatable("msg.contentstudio.villager.villager.trade.removed_reset_done"));
        }

        private void returnToParent() {
            if (this.minecraft != null) {
                parentScreen.refreshEntries();
                this.minecraft.setScreen(parentScreen);
            }
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
