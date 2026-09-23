package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;

import com.google.gson.JsonObject;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.ToggleButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class LootEntryEditScreen extends KineticScreen {
    private static final int WIDTH = 520;
    private static final int COMPACT_HEIGHT = 250;
    private static final int ENTITY_HEIGHT = 286;
    private static final int ITEM_X = 24;
    private static final int ITEM_Y = 40;
    private static final int ITEM_SIZE = 28;
    private static final int NBT_BUTTON_X = 390;
    private static final int NBT_BUTTON_Y = 48;
    private static final int NBT_BUTTON_W = 106;
    private static final int NUMERIC_LABEL_Y = 82;
    private static final int NUMERIC_FIELD_Y = 94;
    private static final int FUNCTION_TITLE_Y = 124;
    private static final int FUNCTION_BUTTON_Y = 139;
    private static final int ENCHANT_FIELD_Y = 174;
    private static final int ENTITY_TITLE_Y = 203;
    private static final int ENTITY_BUTTON_Y = 218;

    private final AbstractLootEditorScreen parent;
    private final AbstractLootEditorScreen.DropVisual original;
    private final JsonObject workingEntry;
    private final boolean itemEntry;
    private final boolean entityMode;
    private final int screenHeight;

    private NumericEditBox chanceBox;
    private NumericEditBox countMinBox;
    private NumericEditBox countMaxBox;
    private NumericEditBox weightBox;
    private NumericEditBox lootingMinBox;
    private NumericEditBox lootingMaxBox;
    private NumericEditBox enchantMinBox;
    private NumericEditBox enchantMaxBox;
    private NumericEditBox damageMinBox;
    private NumericEditBox damageMaxBox;
    private ToggleButton randomEnchantButton;
    private ToggleButton levelEnchantButton;
    private ToggleButton furnaceButton;
    private ToggleButton explosionButton;
    private ToggleButton killedButton;
    private ToggleButton fireRequiredButton;
    private ToggleButton lootingButton;

    private boolean randomEnchant;
    private boolean levelEnchant;
    private boolean furnaceSmelt;
    private boolean explosionDecay;
    private boolean killedByPlayer;
    private boolean requiresFire;
    private boolean looting;
    private boolean damageableItem;
    private List<Component> deferredTooltip;
    private String selectedItemId;
    private ItemStack selectedItemStack;
    private boolean itemSelectionChanged;
    private String draftChance;
    private String draftCountMin;
    private String draftCountMax;
    private String draftWeight;
    private String draftLootingMin;
    private String draftLootingMax;
    private String draftEnchantMin;
    private String draftEnchantMax;
    private String draftDamageMin;
    private String draftDamageMax;

    LootEntryEditScreen(AbstractLootEditorScreen parent, AbstractLootEditorScreen.DropVisual visual) {
        super(Component.translatable("gui.contentstudio.loot.loots.entry_editor.title"));
        this.parent = parent;
        setParentScreen(parent);
        this.original = visual;
        this.workingEntry = visual.entry.deepCopy();
        String type = LootJsonEditUtil.string(workingEntry.get("type"));
        this.itemEntry = type.isBlank() || type.endsWith("item");
        this.entityMode = parent.editorMode() == LootEntryInfo.MODE_ENTITY;
        this.screenHeight = entityMode ? ENTITY_HEIGHT : COMPACT_HEIGHT;
        useCanvas(WIDTH, screenHeight, 6);
        this.selectedItemId = LootJsonEditUtil.string(workingEntry.get("name"));
        this.selectedItemStack = visual.stack == null ? ItemStack.EMPTY : visual.stack.copy();
        this.randomEnchant = LootJsonEditUtil.hasFunction(workingEntry, "enchant_randomly");
        this.levelEnchant = LootJsonEditUtil.hasFunction(workingEntry, "enchant_with_levels");
        this.furnaceSmelt = LootJsonEditUtil.hasFunction(workingEntry, "furnace_smelt");
        this.explosionDecay = LootJsonEditUtil.hasFunction(workingEntry, "explosion_decay");
        this.killedByPlayer = LootJsonEditUtil.killedByPlayer(workingEntry);
        this.requiresFire = LootJsonEditUtil.requiresFire(workingEntry);
        this.looting = LootJsonEditUtil.hasFunction(workingEntry, "looting_enchant");
}

    @Override
    protected void buildUi() {
        LootJsonEditUtil.Range count = LootJsonEditUtil.count(workingEntry);
        LootJsonEditUtil.Range loot = LootJsonEditUtil.looting(workingEntry);
        LootJsonEditUtil.Range levels = LootJsonEditUtil.enchantmentLevels(workingEntry);
        LootJsonEditUtil.Range damage = LootJsonEditUtil.damage(workingEntry);
        chanceBox = numericBox(24, NUMERIC_FIELD_Y, 65, draft(draftChance, format(LootJsonEditUtil.chance(workingEntry))), LootNumericField.Type.PROBABILITY, "gui.contentstudio.loot.loots.tip.chance");
        countMinBox = numericBox(101, NUMERIC_FIELD_Y, 65, draft(draftCountMin, format(count.min())), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.count_min");
        countMaxBox = numericBox(178, NUMERIC_FIELD_Y, 65, draft(draftCountMax, format(count.max())), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.count_max");
        weightBox = numericBox(255, NUMERIC_FIELD_Y, 65, draft(draftWeight, String.valueOf(Math.max(1, LootJsonEditUtil.integer(workingEntry.get("weight"))))), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.weight");
        lootingMinBox = numericBox(332, NUMERIC_FIELD_Y, 65, draft(draftLootingMin, format(loot.min())), LootNumericField.Type.NON_NEGATIVE_INTEGER, "gui.contentstudio.loot.loots.tip.looting_min");
        lootingMaxBox = numericBox(409, NUMERIC_FIELD_Y, 65, draft(draftLootingMax, format(loot.max())), LootNumericField.Type.NON_NEGATIVE_INTEGER, "gui.contentstudio.loot.loots.tip.looting_max");
        lootingMinBox.setVisible(entityMode);
        lootingMaxBox.setVisible(entityMode);

        randomEnchantButton = addToggleButton(
                24, FUNCTION_BUTTON_Y, 112, randomEnchant,
                toggleText("gui.contentstudio.loot.loots.entry.random_enchant", true),
                toggleText("gui.contentstudio.loot.loots.entry.random_enchant", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.random_enchant"), null,
                value -> {
                    randomEnchant = value;
                    if (randomEnchant) levelEnchant = false;
                    updateToggleValues();
                });
        levelEnchantButton = addToggleButton(
                142, FUNCTION_BUTTON_Y, 112, levelEnchant,
                toggleText("gui.contentstudio.loot.loots.entry.level_enchant", true),
                toggleText("gui.contentstudio.loot.loots.entry.level_enchant", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.level_enchant"), null,
                value -> {
                    levelEnchant = value;
                    if (levelEnchant) randomEnchant = false;
                    updateToggleValues();
                });
        furnaceButton = addToggleButton(
                260, FUNCTION_BUTTON_Y, 112, furnaceSmelt,
                toggleText("gui.contentstudio.loot.loots.entry.furnace", true),
                toggleText("gui.contentstudio.loot.loots.entry.furnace", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.furnace"), null,
                value -> furnaceSmelt = value);
        explosionButton = addToggleButton(
                378, FUNCTION_BUTTON_Y, 112, explosionDecay,
                toggleText("gui.contentstudio.loot.loots.entry.explosion", true),
                toggleText("gui.contentstudio.loot.loots.entry.explosion", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.explosion"), null,
                value -> explosionDecay = value);

        enchantMinBox = numericBox(82, ENCHANT_FIELD_Y, 46, draft(draftEnchantMin, format(levels.min())), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.entry.enchant_min");
        enchantMaxBox = numericBox(136, ENCHANT_FIELD_Y, 46, draft(draftEnchantMax, format(levels.max())), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.entry.enchant_max");
        damageMinBox = numericBox(264, ENCHANT_FIELD_Y, 46, draft(draftDamageMin, format(damage.min())), LootNumericField.Type.RATIO, "gui.contentstudio.loot.loots.tip.entry.damage_min");
        damageMaxBox = numericBox(318, ENCHANT_FIELD_Y, 46, draft(draftDamageMax, format(damage.max())), LootNumericField.Type.RATIO, "gui.contentstudio.loot.loots.tip.entry.damage_max");

        killedButton = addToggleButton(
                24, ENTITY_BUTTON_Y, 151, killedByPlayer,
                toggleText("gui.contentstudio.loot.loots.entry.killed", true),
                toggleText("gui.contentstudio.loot.loots.entry.killed", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.killed_by_player"), null,
                value -> killedByPlayer = value);
        fireRequiredButton = addToggleButton(
                181, ENTITY_BUTTON_Y, 151, requiresFire,
                toggleText("gui.contentstudio.loot.loots.entry.fire_required", true),
                toggleText("gui.contentstudio.loot.loots.entry.fire_required", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.fire_required"), null,
                value -> requiresFire = value);
        lootingButton = addToggleButton(
                338, ENTITY_BUTTON_Y, 151, looting,
                toggleText("gui.contentstudio.loot.loots.entry.looting", true),
                toggleText("gui.contentstudio.loot.loots.entry.looting", false),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.looting_bonus"), null,
                value -> {
                    looting = value;
                    updateToggleValues();
                });
        killedButton.setVisible(entityMode);
        fireRequiredButton.setVisible(entityMode);
        lootingButton.setVisible(entityMode);

        addButton(
                NBT_BUTTON_X, NBT_BUTTON_Y, NBT_BUTTON_W,
                Component.translatable("gui.contentstudio.loot.loots.entry.nbt"),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.nbt"),
                this::openNbtEditor
        );

        int actionY = screenHeight - 38;
        addButton(
                360, actionY, 64,
                Component.translatable("gui.contentstudio.loot.loots.entry.apply"),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.apply"),
                this::applyChanges
        );
        addButton(
                432, actionY, 64,
                Component.translatable("gui.contentstudio.loot.loots.entry.cancel"),
                Component.translatable("gui.contentstudio.loot.loots.tip.entry.cancel"),
                this::closeToParent
        );
        updateToggleValues();
    }

    private NumericEditBox numericBox(
            int x,
            int y,
            int width,
            String value,
            LootNumericField.Type type,
            String tooltipKey
    ) {

        return LootNumericField.add(
                this,
                x,
                y,
                width,
                value,
                type,
                tooltipKey
        );
    }

    private Component toggleText(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled
                ? "gui.contentstudio.loot.loots.state.on"
                : "gui.contentstudio.loot.loots.state.off"));
    }

    private void updateToggleValues() {
        if (randomEnchantButton != null) randomEnchantButton.setValue(randomEnchant);
        if (levelEnchantButton != null) levelEnchantButton.setValue(levelEnchant);
        if (furnaceButton != null) furnaceButton.setValue(furnaceSmelt);
        if (explosionButton != null) explosionButton.setValue(explosionDecay);
        if (killedButton != null) killedButton.setValue(killedByPlayer);
        if (fireRequiredButton != null) fireRequiredButton.setValue(requiresFire);
        if (lootingButton != null) lootingButton.setValue(looting);
        if (enchantMinBox != null) enchantMinBox.setEnabled(levelEnchant);
        if (enchantMaxBox != null) enchantMaxBox.setEnabled(levelEnchant);
        if (lootingMinBox != null) lootingMinBox.setEnabled(entityMode && looting);
        if (lootingMaxBox != null) lootingMaxBox.setEnabled(entityMode && looting);
        updateDamageFields();
    }

    private void updateDamageFields() {
        damageableItem = itemEntry && itemStack().isDamageableItem();
        if (damageMinBox != null) {
            damageMinBox.setEnabled(damageableItem);
            registerWidgetTooltip(damageMinBox, Component.translatable(damageableItem
                    ? "gui.contentstudio.loot.loots.tip.entry.damage_min"
                    : "gui.contentstudio.loot.loots.tip.entry.damage_disabled"));
        }
        if (damageMaxBox != null) {
            damageMaxBox.setEnabled(damageableItem);
            registerWidgetTooltip(damageMaxBox, Component.translatable(damageableItem
                    ? "gui.contentstudio.loot.loots.tip.entry.damage_max"
                    : "gui.contentstudio.loot.loots.tip.entry.damage_disabled"));
        }
    }

    private void applyChanges() {
        if (itemEntry && invalidItem(selectedItemId)) {
            KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.invalid_item"));
            return;
        }
        Double chance = LootNumericField.decimal(chanceBox);
        Integer countMin = LootNumericField.integer(countMinBox);
        Integer countMax = LootNumericField.integer(countMaxBox);
        Integer weight = LootNumericField.integer(weightBox);
        Integer enchantMin = LootNumericField.integer(enchantMinBox);
        Integer enchantMax = LootNumericField.integer(enchantMaxBox);
        Integer lootMin = LootNumericField.integer(lootingMinBox);
        Integer lootMax = LootNumericField.integer(lootingMaxBox);
        Double damageMin = LootNumericField.decimal(damageMinBox);
        Double damageMax = LootNumericField.decimal(damageMaxBox);
        if (chance == null || countMin == null || countMax == null || weight == null
                || enchantMin == null || enchantMax == null || lootMin == null || lootMax == null
                || damageMin == null || damageMax == null) {
            return;
        }
        if (chance < 0.01D || chance > 1.0D || countMin < 1 || countMax < countMin || weight < 1
                || enchantMin < 1 || enchantMax < enchantMin || lootMin < 0 || lootMax < lootMin) {
            LootNumericField.notifyInvalid("msg.contentstudio.loot.loots.number.out_of_range");
            return;
        }
        if (damageMin < 0.0D || damageMax > 1.0D || damageMax < damageMin) {
            LootNumericField.notifyInvalid("msg.contentstudio.loot.loots.number.damage_ratio");
            return;
        }
        if (itemEntry) {
            workingEntry.addProperty("type", "minecraft:item");
            workingEntry.addProperty("name", selectedItemId);
            if (itemSelectionChanged) {
                LootJsonEditUtil.setItemNbt(workingEntry, selectedItemStack);
            }
        }
        workingEntry.addProperty("weight", weight);
        LootJsonEditUtil.setChance(workingEntry, chance);
        LootJsonEditUtil.setCount(workingEntry, countMin, countMax);
        boolean keepDamageFunction = damageableItem && (LootJsonEditUtil.hasFunction(workingEntry, "set_damage") || damageMin < 0.999999D);
        LootJsonEditUtil.setDamage(workingEntry, keepDamageFunction, damageMin, damageMax);
        LootJsonEditUtil.setFunctionEnabled(workingEntry, "enchant_randomly", randomEnchant);
        LootJsonEditUtil.setEnchantmentLevels(workingEntry, levelEnchant, enchantMin, enchantMax);
        LootJsonEditUtil.setFunctionEnabled(workingEntry, "furnace_smelt", furnaceSmelt);
        LootJsonEditUtil.setFunctionEnabled(workingEntry, "explosion_decay", explosionDecay);
        if (entityMode) {
            LootJsonEditUtil.setKilledByPlayer(workingEntry, killedByPlayer);
            LootJsonEditUtil.setRequiresFire(workingEntry, requiresFire);
            LootJsonEditUtil.setLooting(workingEntry, looting, lootMin, lootMax);
        }
        parent.applyEntryEdit(original, workingEntry);
        KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.entry.applied"));
        closeToParent();
    }

    private boolean invalidItem(String id) {
        try {
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(id));
            return item == null || item == Items.AIR;
        } catch (Exception ignored) {
            return true;
        }
    }

    private void openItemPicker() {
        if (!itemEntry || minecraft == null) {
            return;
        }
        captureDraft();
        KineticSelectors.openItemSelector(this, selection -> {
            if (selection != null && selection.isItem()) {
                ResourceLocation id = KineticRegistries.items().id(selection.stack().getItem());
                if (id != null) {
                    selectedItemId = id.toString();
                    selectedItemStack = selection.stack().copy();
                    itemSelectionChanged = true;
                }
            }
        });
    }

    private void openNbtEditor() {
        if (minecraft == null) {
            return;
        }
        captureDraft();
        KineticSelectors.openNbtEditor(this, currentItemNbt(), value -> {
            LootJsonEditUtil.setItemNbt(workingEntry, value);
            selectedItemStack = ItemStack.EMPTY;
        });
    }

    private String currentItemNbt() {
        if (itemSelectionChanged && selectedItemStack != null && !selectedItemStack.isEmpty()) {
            if (selectedItemStack.getTag() == null || selectedItemStack.getTag().isEmpty()) {
                return "";
            }
            return selectedItemStack.getTag().toString();
        }
        return LootJsonEditUtil.itemNbt(workingEntry);
    }

    private void captureDraft() {
        if (chanceBox != null) draftChance = chanceBox.getValue();
        if (countMinBox != null) draftCountMin = countMinBox.getValue();
        if (countMaxBox != null) draftCountMax = countMaxBox.getValue();
        if (weightBox != null) draftWeight = weightBox.getValue();
        if (lootingMinBox != null) draftLootingMin = lootingMinBox.getValue();
        if (lootingMaxBox != null) draftLootingMax = lootingMaxBox.getValue();
        if (enchantMinBox != null) draftEnchantMin = enchantMinBox.getValue();
        if (enchantMaxBox != null) draftEnchantMax = enchantMaxBox.getValue();
        if (damageMinBox != null) draftDamageMin = damageMinBox.getValue();
        if (damageMaxBox != null) draftDamageMax = damageMaxBox.getValue();
    }

    private String draft(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private void closeToParent() {
        if (minecraft != null) navigateBack();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.panel(g, 0, 0, WIDTH, screenHeight);
        GuiTheme.stateSurface(
                g,
                12,
                12,
                WIDTH - 24,
                screenHeight - 24,
                GuiTheme.Surface.PANEL_ALT,
                true,
                false,
                false
        );
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        deferredTooltip = null;
        g.drawString(font, getTitle(), 24, 21, 0xFFFFAA00, false);
        g.drawString(font, trim(parent.selectedTableName().getString(), WIDTH - 194), 170, 21, 0xFFFFD75F, false);
        drawItem(g, mx, my);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.chance", 24);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.count_min", 101);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.count_max", 178);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.weight", 255);
        if (entityMode) {
            fieldLabel(g, "gui.contentstudio.loot.loots.entry.looting_min", 332);
            fieldLabel(g, "gui.contentstudio.loot.loots.entry.looting_max", 409);
        }
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.entry.functions"), 24, FUNCTION_TITLE_Y, 0xFFFF55FF, false);
        g.drawString(font, trim(Component.translatable("gui.contentstudio.loot.loots.entry.functions_help").getString(), 270), 94, FUNCTION_TITLE_Y, 0xFFAAAAAA, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.entry.enchant_levels"), 24, ENCHANT_FIELD_Y + 6, 0xFFDD77FF, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.entry.damage"), 204, ENCHANT_FIELD_Y + 6,
                damageableItem ? 0xFFFFD75F : 0xFF777777, false);
        Component damageHelp = Component.translatable(damageableItem
                ? "gui.contentstudio.loot.loots.entry.damage_enabled"
                : "gui.contentstudio.loot.loots.entry.damage_disabled");
        g.drawString(font, trim(damageHelp.getString(), 124), 372, ENCHANT_FIELD_Y + 6,
                damageableItem ? 0xFF55FF55 : 0xFF777777, false);
        if (entityMode) {
            g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.entry.entity_conditions"), 24, ENTITY_TITLE_Y, 0xFF55FFFF, false);
            g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.entry.entity_help"), 132, ENTITY_TITLE_Y, 0xFFAAAAAA, false);
        }
        renderUnknownFunctions(g, mx, my);
    }

    private void fieldLabel(GuiGraphics g, String key, int x) {
        g.drawString(font, Component.translatable(key), x, NUMERIC_LABEL_Y, 0xFFE6E6E6, false);
    }

    private void drawItem(GuiGraphics g, int mx, int my) {
        ItemStack stack = itemStack();
        boolean hovered = mx >= ITEM_X && mx < ITEM_X + ITEM_SIZE
                && my >= ITEM_Y && my < ITEM_Y + ITEM_SIZE;
        LootCheckerboard.draw(g, stack, ITEM_X, ITEM_Y, ITEM_SIZE, ITEM_SIZE, hovered);
        GuiTheme.stateOutline(g, ITEM_X, ITEM_Y, ITEM_SIZE, ITEM_SIZE, itemEntry, hovered, false);
        if (!stack.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(ITEM_X + 6, ITEM_Y + 6, 100);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        Component itemName = stack.isEmpty() ? original.name : stack.getHoverName();
        int itemTextWidth = NBT_BUTTON_X - 70;
        g.drawString(font, trim(itemName.getString(), itemTextWidth), 62, ITEM_Y + 1, 0xFFFFAA00, false);
        g.drawString(font, trim(selectedItemId, itemTextWidth), 62, ITEM_Y + 13, 0xFF55FFFF, false);
        Component hint = Component.translatable(itemEntry
                ? "gui.contentstudio.loot.loots.entry.item_hint"
                : "gui.contentstudio.loot.loots.tip.entry.locked_type");
        g.drawString(font, trim(hint.getString(), itemTextWidth), 62, ITEM_Y + 25, itemEntry ? 0xFF55FF55 : 0xFFFFDD55, false);
        if (mx >= ITEM_X && mx < WIDTH - 24 && my >= ITEM_Y && my < ITEM_Y + ITEM_SIZE + 10) {
            deferredTooltip = stack.isEmpty()
                    ? List.of(Component.translatable("gui.contentstudio.loot.loots.tip.entry.locked_type"))
                    : List.of(stack.getHoverName().copy().withStyle(ChatFormatting.GOLD),
                    Component.literal(selectedItemId).withStyle(ChatFormatting.AQUA),
                    Component.translatable(itemEntry
                            ? "gui.contentstudio.loot.loots.tip.entry.item_icon"
                            : "gui.contentstudio.loot.loots.tip.entry.locked_type").withStyle(itemEntry ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
    }

    private ItemStack itemStack() {
        if (selectedItemStack != null && !selectedItemStack.isEmpty()) {
            ResourceLocation selectedId = KineticRegistries.items().id(selectedItemStack.getItem());
            if (selectedId != null && selectedId.toString().equals(selectedItemId)) {
                return selectedItemStack;
            }
        }
        try {
            Item item = KineticRegistries.items().get(KineticResourceIds.parse(selectedItemId));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item);
            LootJsonEditUtil.applyItemNbt(workingEntry, stack);
            selectedItemStack = stack;
            return selectedItemStack;
        } catch (Exception ignored) {
            return original.stack == null ? ItemStack.EMPTY : original.stack;
        }
    }

    private void renderUnknownFunctions(GuiGraphics g, int mx, int my) {
        int count = LootJsonEditUtil.unknownFunctionCount(workingEntry);
        Component text = Component.translatable("gui.contentstudio.loot.loots.entry.unknown_functions",
                Component.literal(String.valueOf(count)).withStyle(ChatFormatting.YELLOW));
        int x = WIDTH - font.width(text) - 24;
        int y = FUNCTION_TITLE_Y;
        g.drawString(font, text, x, y, 0xFFAAAAAA, false);
        if (mx >= x && mx <= x + font.width(text) && my >= y - 2 && my <= y + 11) {
            deferredTooltip = new ArrayList<>();
            deferredTooltip.add(Component.translatable("gui.contentstudio.loot.loots.tip.entry.unknown_functions").withStyle(ChatFormatting.GRAY));
            for (String id : LootJsonEditUtil.unknownFunctions(workingEntry)) {
                deferredTooltip.add(Component.literal(id).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mx, double my, int button) {
        if (KineticMouseButtons.isPrimary(button) && itemEntry && mx >= ITEM_X && mx < ITEM_X + ITEM_SIZE && my >= ITEM_Y && my < ITEM_Y + ITEM_SIZE) {
            openItemPicker();
            return true;
        }
        return super.canvasMouseClicked(mx, my, button);
    }

    @Override
    protected boolean canvasKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (KineticKeyBindings.matchesKeyCode(KineticKeyBindings.Key.ESCAPE, keyCode)) {
            closeToParent();
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

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) return String.valueOf((long) Math.rint(value));
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String trim(String text, int width) {
        if (text == null || font.width(text) <= width) {
            return text == null ? "" : text;
        }
        return font.plainSubstrByWidth(text, Math.max(4, width - font.width("..."))) + "...";
    }
}
