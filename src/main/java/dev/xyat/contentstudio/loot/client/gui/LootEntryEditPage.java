package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.KeyInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;

import com.google.gson.JsonObject;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

final class LootEntryEditPage extends KineticPage {
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

    private final AbstractLootEditorPage parent;
    private final AbstractLootEditorPage.DropVisual original;
    private final JsonObject workingEntry;
    private final boolean itemEntry;
    private final boolean entityMode;
    private final int screenHeight;

    private KineticNumberField chanceBox;
    private KineticNumberField countMinBox;
    private KineticNumberField countMaxBox;
    private KineticNumberField weightBox;
    private KineticNumberField lootingMinBox;
    private KineticNumberField lootingMaxBox;
    private KineticNumberField enchantMinBox;
    private KineticNumberField enchantMaxBox;
    private KineticNumberField damageMinBox;
    private KineticNumberField damageMaxBox;
    private KineticToggle randomEnchantButton;
    private KineticToggle levelEnchantButton;
    private KineticToggle furnaceButton;
    private KineticToggle explosionButton;
    private KineticToggle killedButton;
    private KineticToggle fireRequiredButton;
    private KineticToggle lootingButton;

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

    LootEntryEditPage(AbstractLootEditorPage parent, AbstractLootEditorPage.DropVisual visual) {
        super(KineticI18n.translatable("gui.contentstudio.loot.loots.entry_editor.title"));
        this.parent = parent;
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
    protected void build(KineticUi ui) {
        LootJsonEditUtil.Range count = LootJsonEditUtil.count(workingEntry);
        LootJsonEditUtil.Range loot = LootJsonEditUtil.looting(workingEntry);
        LootJsonEditUtil.Range levels = LootJsonEditUtil.enchantmentLevels(workingEntry);
        LootJsonEditUtil.Range damage = LootJsonEditUtil.damage(workingEntry);
        chanceBox = numericBox(24, NUMERIC_FIELD_Y, 65, draft(draftChance, format(LootJsonEditUtil.chance(workingEntry))), format(LootJsonEditUtil.chance(workingEntry)), LootNumericField.Type.PROBABILITY, "gui.contentstudio.loot.loots.tip.chance");
        countMinBox = numericBox(101, NUMERIC_FIELD_Y, 65, draft(draftCountMin, format(count.min())), format(count.min()), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.count_min");
        countMaxBox = numericBox(178, NUMERIC_FIELD_Y, 65, draft(draftCountMax, format(count.max())), format(count.max()), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.count_max");
        weightBox = numericBox(255, NUMERIC_FIELD_Y, 65, draft(draftWeight, String.valueOf(Math.max(1, LootJsonEditUtil.integer(workingEntry.get("weight"))))), String.valueOf(Math.max(1, LootJsonEditUtil.integer(workingEntry.get("weight")))), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.weight");
        lootingMinBox = numericBox(332, NUMERIC_FIELD_Y, 65, draft(draftLootingMin, format(loot.min())), format(loot.min()), LootNumericField.Type.NON_NEGATIVE_INTEGER, "gui.contentstudio.loot.loots.tip.looting_min");
        lootingMaxBox = numericBox(409, NUMERIC_FIELD_Y, 65, draft(draftLootingMax, format(loot.max())), format(loot.max()), LootNumericField.Type.NON_NEGATIVE_INTEGER, "gui.contentstudio.loot.loots.tip.looting_max");
        lootingMinBox.setControlVisible(entityMode);
        lootingMaxBox.setControlVisible(entityMode);

        randomEnchantButton = ui().toggle(24, FUNCTION_BUTTON_Y, 112).value(randomEnchant).labels(toggleText("gui.contentstudio.loot.loots.entry.random_enchant", true), toggleText("gui.contentstudio.loot.loots.entry.random_enchant", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.random_enchant")).onChange(value -> {
                    randomEnchant = value;
                    if (randomEnchant) levelEnchant = false;
                    updateToggleValues();
                }).build();
        levelEnchantButton = ui().toggle(142, FUNCTION_BUTTON_Y, 112).value(levelEnchant).labels(toggleText("gui.contentstudio.loot.loots.entry.level_enchant", true), toggleText("gui.contentstudio.loot.loots.entry.level_enchant", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.level_enchant")).onChange(value -> {
                    levelEnchant = value;
                    if (levelEnchant) randomEnchant = false;
                    updateToggleValues();
                }).build();
        furnaceButton = ui().toggle(260, FUNCTION_BUTTON_Y, 112).value(furnaceSmelt).labels(toggleText("gui.contentstudio.loot.loots.entry.furnace", true), toggleText("gui.contentstudio.loot.loots.entry.furnace", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.furnace")).onChange(value -> furnaceSmelt = value).build();
        explosionButton = ui().toggle(378, FUNCTION_BUTTON_Y, 112).value(explosionDecay).labels(toggleText("gui.contentstudio.loot.loots.entry.explosion", true), toggleText("gui.contentstudio.loot.loots.entry.explosion", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.explosion")).onChange(value -> explosionDecay = value).build();

        enchantMinBox = numericBox(82, ENCHANT_FIELD_Y, 46, draft(draftEnchantMin, format(levels.min())), format(levels.min()), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.entry.enchant_min");
        enchantMaxBox = numericBox(136, ENCHANT_FIELD_Y, 46, draft(draftEnchantMax, format(levels.max())), format(levels.max()), LootNumericField.Type.POSITIVE_INTEGER, "gui.contentstudio.loot.loots.tip.entry.enchant_max");
        damageMinBox = numericBox(264, ENCHANT_FIELD_Y, 46, draft(draftDamageMin, format(damage.min())), format(damage.min()), LootNumericField.Type.RATIO, "gui.contentstudio.loot.loots.tip.entry.damage_min");
        damageMaxBox = numericBox(318, ENCHANT_FIELD_Y, 46, draft(draftDamageMax, format(damage.max())), format(damage.max()), LootNumericField.Type.RATIO, "gui.contentstudio.loot.loots.tip.entry.damage_max");

        killedButton = ui().toggle(24, ENTITY_BUTTON_Y, 151).value(killedByPlayer).labels(toggleText("gui.contentstudio.loot.loots.entry.killed", true), toggleText("gui.contentstudio.loot.loots.entry.killed", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.killed_by_player")).onChange(value -> killedByPlayer = value).build();
        fireRequiredButton = ui().toggle(181, ENTITY_BUTTON_Y, 151).value(requiresFire).labels(toggleText("gui.contentstudio.loot.loots.entry.fire_required", true), toggleText("gui.contentstudio.loot.loots.entry.fire_required", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.fire_required")).onChange(value -> requiresFire = value).build();
        lootingButton = ui().toggle(338, ENTITY_BUTTON_Y, 151).value(looting).labels(toggleText("gui.contentstudio.loot.loots.entry.looting", true), toggleText("gui.contentstudio.loot.loots.entry.looting", false)).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.looting_bonus")).onChange(value -> {
                    looting = value;
                    updateToggleValues();
                }).build();
        killedButton.setControlVisible(entityMode);
        fireRequiredButton.setControlVisible(entityMode);
        lootingButton.setControlVisible(entityMode);

        ui().button(NBT_BUTTON_X, NBT_BUTTON_Y, NBT_BUTTON_W).text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.nbt")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.nbt")).onClick(this::openNbtEditor).build();

        int actionY = screenHeight - 38;
        ui().button(360, actionY, 64).text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.apply")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.apply")).onClick(this::applyChanges).build();
        ui().button(432, actionY, 64).text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.cancel")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.cancel")).onClick(this::closeToParent).build();
        updateToggleValues();
    }

    private KineticNumberField numericBox(
            int x,
            int y,
            int width,
            String value,
            String defaultText,
            LootNumericField.Type type,
            String tooltipKey
    ) {

        return LootNumericField.add(
                ui(),
                x,
                y,
                width,
                value,
                defaultText,
                type,
                tooltipKey
        );
    }

    private Component toggleText(String key, boolean enabled) {
        return KineticI18n.translatable(key, KineticI18n.translatable(enabled
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
            damageMinBox.setTooltip(KineticI18n.translatable(damageableItem
                    ? "gui.contentstudio.loot.loots.tip.entry.damage_min"
                    : "gui.contentstudio.loot.loots.tip.entry.damage_disabled"));
        }
        if (damageMaxBox != null) {
            damageMaxBox.setEnabled(damageableItem);
            damageMaxBox.setTooltip(KineticI18n.translatable(damageableItem
                    ? "gui.contentstudio.loot.loots.tip.entry.damage_max"
                    : "gui.contentstudio.loot.loots.tip.entry.damage_disabled"));
        }
    }

    private void applyChanges() {
        if (itemEntry && invalidItem(selectedItemId)) {
            KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.invalid_item"));
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
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.entry.applied"));
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
        if (!itemEntry || !isAttached()) {
            return;
        }
        captureDraft();
        KineticSelectors.openItemSelector(selection -> {
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
        if (!isAttached()) {
            return;
        }
        captureDraft();
        KineticSelectors.openNbtEditor(currentItemNbt(), value -> {
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
        if (chanceBox != null) draftChance = chanceBox.textValue();
        if (countMinBox != null) draftCountMin = countMinBox.textValue();
        if (countMaxBox != null) draftCountMax = countMaxBox.textValue();
        if (weightBox != null) draftWeight = weightBox.textValue();
        if (lootingMinBox != null) draftLootingMin = lootingMinBox.textValue();
        if (lootingMaxBox != null) draftLootingMax = lootingMaxBox.textValue();
        if (enchantMinBox != null) draftEnchantMin = enchantMinBox.textValue();
        if (enchantMaxBox != null) draftEnchantMax = enchantMaxBox.textValue();
        if (damageMinBox != null) draftDamageMin = damageMinBox.textValue();
        if (damageMaxBox != null) draftDamageMax = damageMaxBox.textValue();
    }

    private String draft(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private void closeToParent() {
        if (isAttached()) navigateBack();
    }

    @Override
    protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        KineticTheme.panel(g, 0, 0, WIDTH, screenHeight);
        KineticTheme.stateSurface(
                g,
                12,
                12,
                WIDTH - 24,
                screenHeight - 24,
                KineticTheme.Surface.PANEL_ALT,
                true,
                false,
                false
        );
    }

    @Override
    protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
        deferredTooltip = null;
        g.text(title(), 24, 21, 0xFFFFAA00, false);
        g.text(trim(parent.selectedTableName().getString(), WIDTH - 194), 170, 21, 0xFFFFD75F, false);
        drawItem(g, mx, my);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.chance", 24);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.count_min", 101);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.count_max", 178);
        fieldLabel(g, "gui.contentstudio.loot.loots.entry.weight", 255);
        if (entityMode) {
            fieldLabel(g, "gui.contentstudio.loot.loots.entry.looting_min", 332);
            fieldLabel(g, "gui.contentstudio.loot.loots.entry.looting_max", 409);
        }
        g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.functions"), 24, FUNCTION_TITLE_Y, 0xFFFF55FF, false);
        g.text(trim(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.functions_help").getString(), 270), 94, FUNCTION_TITLE_Y, 0xFFAAAAAA, false);
        g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.enchant_levels"), 24, ENCHANT_FIELD_Y + 6, 0xFFDD77FF, false);
        g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.damage"), 204, ENCHANT_FIELD_Y + 6, damageableItem ? 0xFFFFD75F : 0xFF777777, false);
        Component damageHelp = KineticI18n.translatable(damageableItem
                ? "gui.contentstudio.loot.loots.entry.damage_enabled"
                : "gui.contentstudio.loot.loots.entry.damage_disabled");
        g.text(trim(damageHelp.getString(), 124), 372, ENCHANT_FIELD_Y + 6, damageableItem ? 0xFF55FF55 : 0xFF777777, false);
        if (entityMode) {
            g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.entity_conditions"), 24, ENTITY_TITLE_Y, 0xFF55FFFF, false);
            g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.entry.entity_help"), 132, ENTITY_TITLE_Y, 0xFFAAAAAA, false);
        }
        renderUnknownFunctions(g, mx, my);
    }

    private void fieldLabel(KineticGraphics g, String key, int x) {
        g.text(KineticI18n.translatable(key), x, NUMERIC_LABEL_Y, 0xFFE6E6E6, false);
    }

    private void drawItem(KineticGraphics g, int mx, int my) {
        ItemStack stack = itemStack();
        boolean hovered = mx >= ITEM_X && mx < ITEM_X + ITEM_SIZE
                && my >= ITEM_Y && my < ITEM_Y + ITEM_SIZE;
        LootCheckerboard.draw(g, ITEM_X, ITEM_Y, ITEM_SIZE, ITEM_SIZE);
        KineticTheme.stateOutline(g, ITEM_X, ITEM_Y, ITEM_SIZE, ITEM_SIZE, itemEntry, hovered, false);
        if (!stack.isEmpty()) {
            g.push();
            g.translate(ITEM_X + 6, ITEM_Y + 6);
            g.item(stack, 0, 0);
            g.pop();
        }
        Component itemName = stack.isEmpty() ? original.name : stack.getHoverName();
        int itemTextWidth = NBT_BUTTON_X - 70;
        g.text(trim(itemName.getString(), itemTextWidth), 62, ITEM_Y + 1, 0xFFFFAA00, false);
        g.text(trim(selectedItemId, itemTextWidth), 62, ITEM_Y + 13, 0xFF55FFFF, false);
        Component hint = KineticI18n.translatable(itemEntry
                ? "gui.contentstudio.loot.loots.entry.item_hint"
                : "gui.contentstudio.loot.loots.tip.entry.locked_type");
        g.text(trim(hint.getString(), itemTextWidth), 62, ITEM_Y + 25, itemEntry ? 0xFF55FF55 : 0xFFFFDD55, false);
        if (mx >= ITEM_X && mx < WIDTH - 24 && my >= ITEM_Y && my < ITEM_Y + ITEM_SIZE + 10) {
            deferredTooltip = stack.isEmpty()
                    ? List.of(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.locked_type"))
                    : List.of(KineticI18n.styled("gui.contentstudio.loot.style.name", stack.getHoverName()),
                    KineticI18n.styled("gui.contentstudio.loot.style.id", selectedItemId),
                    KineticI18n.styled(itemEntry ? "gui.contentstudio.loot.style.hint_ok" : "gui.contentstudio.loot.style.hint_warning", KineticI18n.translatable(itemEntry
                            ? "gui.contentstudio.loot.loots.tip.entry.item_icon"
                            : "gui.contentstudio.loot.loots.tip.entry.locked_type")));
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

    private void renderUnknownFunctions(KineticGraphics g, int mx, int my) {
        int count = LootJsonEditUtil.unknownFunctionCount(workingEntry);
        Component text = KineticI18n.translatable("gui.contentstudio.loot.loots.entry.unknown_functions",
                Component.literal(String.valueOf(count)));
        int x = WIDTH - KineticText.width(text) - 24;
        int y = FUNCTION_TITLE_Y;
        g.text(text, x, y, 0xFFAAAAAA, false);
        if (mx >= x && mx <= x + KineticText.width(text) && my >= y - 2 && my <= y + 11) {
            deferredTooltip = new ArrayList<>();
            deferredTooltip.add(KineticTheme.muted(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.entry.unknown_functions")));
            for (String id : LootJsonEditUtil.unknownFunctions(workingEntry)) {
                deferredTooltip.add(KineticI18n.styled("gui.contentstudio.loot.style.value", id));
            }
        }
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在控件之前处理 / The old canvasMouseClicked handled this before controls.
        double mx = input.x();
        double my = input.y();
        if (input.isLeft() && itemEntry && mx >= ITEM_X && mx < ITEM_X + ITEM_SIZE && my >= ITEM_Y && my < ITEM_Y + ITEM_SIZE) {
            openItemPicker();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPress(KeyInput input) {
        if (input.isEscape()) {
            closeToParent();
            return true;
        }
        return false;
    }

    @Override
    protected void renderTooltips(int mx, int my) {
        if (deferredTooltip != null && !deferredTooltip.isEmpty()) {
            showTooltip(deferredTooltip);
        }
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) return String.valueOf((long) Math.rint(value));
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String trim(String text, int width) {
        if (text == null || KineticText.width(text) <= width) {
            return text == null ? "" : text;
        }
        return KineticText.trim(text, Math.max(4, width - KineticText.width("..."))) + "...";
    }
}
