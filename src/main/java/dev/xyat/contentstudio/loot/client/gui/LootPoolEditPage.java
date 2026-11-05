package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.KeyInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import net.minecraft.network.chat.Component;

final class LootPoolEditPage extends KineticPage {
    private static final int WIDTH = 400;
    // Apply and Delete sit in the header row, so the dialog ends 12 px under the preserve note.
    private static final int HEIGHT = 80;
    private static final int ACTION_W = 40;
    private static final int APPLY_X = WIDTH - 4 - ACTION_W;
    private static final int DELETE_X = APPLY_X - 4 - ACTION_W;
    private static final int PANEL_X = (CANVAS_WIDTH - WIDTH) / 2;
    private static final int PANEL_Y = (CANVAS_HEIGHT - HEIGHT) / 2;
    private static final int FIELD_COLUMN_PITCH = 96;
    private static final int TEXT_GAP = 4;

    private final AbstractLootEditorPage parent;
    private final int poolIndex;
    private final JsonObject workingPool;
    private KineticNumberField rollsMinBox;
    private KineticNumberField rollsMaxBox;
    private KineticNumberField bonusMinBox;
    private KineticNumberField bonusMaxBox;

    LootPoolEditPage(AbstractLootEditorPage parent, int poolIndex, JsonObject pool) {
        super(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.title"));
        this.parent = parent;
        this.poolIndex = poolIndex;
        this.workingPool = pool;
}

    @Override
    protected void build(KineticUi ui) {
        bindDeleteAction(() -> true, this::openDeleteConfirm);
        LootJsonEditUtil.Range rolls = LootJsonEditUtil.range(workingPool.get("rolls"), 1);
        LootJsonEditUtil.Range bonus = LootJsonEditUtil.range(workingPool.get("bonus_rolls"), 0);
        rollsMinBox = numericBox(PANEL_X + 4, format(rolls.min()), LootNumericField.Type.POSITIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.rolls_min");
        rollsMaxBox = numericBox(PANEL_X + 4 + FIELD_COLUMN_PITCH, format(rolls.max()), LootNumericField.Type.POSITIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.rolls_max");
        bonusMinBox = numericBox(PANEL_X + 4 + FIELD_COLUMN_PITCH * 2, format(bonus.min()), LootNumericField.Type.NON_NEGATIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.bonus_min");
        bonusMaxBox = numericBox(PANEL_X + 4 + FIELD_COLUMN_PITCH * 3, format(bonus.max()), LootNumericField.Type.NON_NEGATIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.bonus_max");

        ui().button(PANEL_X + APPLY_X, PANEL_Y + 4, ACTION_W).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.apply")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.apply")).onClick(this::applyChanges).build();
        ui().button(PANEL_X + DELETE_X, PANEL_Y + 4, ACTION_W).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.delete")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm")).onClick(this::openDeleteConfirm).build();
        ui().button(PANEL_X + 4, PANEL_Y + 4, 40).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.cancel")).onClick(this::closeToParent).build();

    }

    private KineticNumberField numericBox(
            int x,
            String value,
            LootNumericField.Type type,
            String tooltipKey
    ) {

        return LootNumericField.add(
                ui(),
                x,
                PANEL_Y + 40,
                76,
                value,
                value,
                type,
                tooltipKey
        );
    }

    private void applyChanges() {
        Double rollsMin = LootNumericField.decimal(rollsMinBox);
        Double rollsMax = LootNumericField.decimal(rollsMaxBox);
        Double bonusMin = LootNumericField.decimal(bonusMinBox);
        Double bonusMax = LootNumericField.decimal(bonusMaxBox);
        if (rollsMin == null || rollsMax == null || bonusMin == null || bonusMax == null) {
            return;
        }
        if (rollsMin <= 0 || rollsMax < rollsMin || bonusMin < 0 || bonusMax < bonusMin) {
            LootNumericField.notifyInvalid("msg.contentstudio.loot.loots.number.min_max");
            return;
        }
        workingPool.add("rolls", LootJsonEditUtil.rangeValue(rollsMin, rollsMax));
        workingPool.add("bonus_rolls", LootJsonEditUtil.rangeValue(bonusMin, bonusMax));
        parent.applyPoolEdit(poolIndex, workingPool);
        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.pool.applied"));
        closeToParent();
    }

    private void openDeleteConfirm() {
        openDialog(
                KineticI18n.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.title"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.desc"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.confirm.delete"),
                KineticI18n.translatable("gui.contentstudio.loot.loots.confirm.cancel"),
                () -> {
                    if (parent.deletePoolFromEditor(poolIndex)) {
                        KineticOverlays.toast(KineticI18n.translatable("msg.contentstudio.loot.loots.pool.deleted"));
                    }
                    closeToParent();
                },
                () -> { }
        );
    }

    private void closeToParent() {
        if (isAttached()) navigateBack();
    }

    @Override
    protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        KineticTheme.panel(g, PANEL_X, PANEL_Y, WIDTH, HEIGHT);
        KineticTheme.separator(g, PANEL_X + 4, PANEL_Y + 24, WIDTH - 8);
    }

    @Override
    protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
        // Title and pool number centred between Back and the Delete and Apply buttons.
        Component heading = title().copy().append(Component.literal(" " + (poolIndex + 1)).withStyle(net.minecraft.ChatFormatting.YELLOW));
        g.scrollingTextCentered(heading, PANEL_X + WIDTH / 2, PANEL_Y + 10, WIDTH - 2 * (4 + 2 * ACTION_W + 8), 0xFFFFAA00, false);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.rolls_min", 4, 0xFF55FFFF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.rolls_max", 4 + FIELD_COLUMN_PITCH, 0xFF55FFFF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.bonus_min", 4 + FIELD_COLUMN_PITCH * 2, 0xFFDD77FF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.bonus_max", 4 + FIELD_COLUMN_PITCH * 3, 0xFFDD77FF);

        int entries = arraySize(workingPool.get("entries"));
        int conditions = arraySize(workingPool.get("conditions"));
        int functions = arraySize(workingPool.get("functions"));
        Component summary = KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.summary",
                Component.literal(String.valueOf(entries)),
                Component.literal(String.valueOf(conditions)),
                Component.literal(String.valueOf(functions)));
        g.scrollingText(summary, PANEL_X + 4, PANEL_Y + 64, WIDTH - 8, 0xFFE6E6E6, false);
    }

    private void fieldLabel(KineticGraphics g, String key, int x, int color) {
        int columnRight = Math.min(x + FIELD_COLUMN_PITCH, WIDTH - 4);
        g.scrollingText(KineticI18n.translatable(key), PANEL_X + x, PANEL_Y + 29, columnRight - x - TEXT_GAP, color, false);
    }

    private int arraySize(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray().size() : 0;
    }

    @Override
    protected boolean onKeyPress(KeyInput input) {
        if (input.is(KineticKeyBindings.Key.ESCAPE)) {
            closeToParent();
            return true;
        }
        return false;
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) return String.valueOf((long) Math.rint(value));
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
