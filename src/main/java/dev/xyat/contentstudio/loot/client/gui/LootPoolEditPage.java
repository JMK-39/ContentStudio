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
    private static final int WIDTH = 440;
    private static final int HEIGHT = 240;
    private static final int PANEL_X = (CANVAS_WIDTH - WIDTH) / 2;
    private static final int PANEL_Y = (CANVAS_HEIGHT - HEIGHT) / 2;

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
        LootJsonEditUtil.Range rolls = LootJsonEditUtil.range(workingPool.get("rolls"), 1);
        LootJsonEditUtil.Range bonus = LootJsonEditUtil.range(workingPool.get("bonus_rolls"), 0);
        rollsMinBox = numericBox(PANEL_X + 36, format(rolls.min()), LootNumericField.Type.POSITIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.rolls_min");
        rollsMaxBox = numericBox(PANEL_X + 132, format(rolls.max()), LootNumericField.Type.POSITIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.rolls_max");
        bonusMinBox = numericBox(PANEL_X + 228, format(bonus.min()), LootNumericField.Type.NON_NEGATIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.bonus_min");
        bonusMaxBox = numericBox(PANEL_X + 324, format(bonus.max()), LootNumericField.Type.NON_NEGATIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.bonus_max");

        ui().button(PANEL_X + 196, PANEL_Y + 194, 70).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.apply")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.apply")).onClick(this::applyChanges).build();
        ui().button(PANEL_X + 272, PANEL_Y + 194, 70).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.delete")).tooltip(KineticI18n.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm")).onClick(this::openDeleteConfirm).build();
        ui().button(PANEL_X + 348, PANEL_Y + 194, 70).text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.cancel")).onClick(this::closeToParent).build();

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
                PANEL_Y + 84,
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
        KineticTheme.stateSurface(
                g,
                PANEL_X + 12,
                PANEL_Y + 12,
                WIDTH - 24,
                HEIGHT - 24,
                KineticTheme.Surface.PANEL_ALT,
                true,
                false,
                false
        );
    }

    @Override
    protected void renderForeground(KineticGraphics g, int mx, int my, float pt) {
        g.text(title(), PANEL_X + 24, PANEL_Y + 24, 0xFFFFAA00, false);
        g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.pool",
                Component.literal(String.valueOf(poolIndex + 1))), PANEL_X + 24, PANEL_Y + 42, 0xFFE6E6E6, false);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.rolls_min", 36, 0xFF55FFFF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.rolls_max", 132, 0xFF55FFFF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.bonus_min", 228, 0xFFDD77FF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.bonus_max", 324, 0xFFDD77FF);

        int entries = arraySize(workingPool.get("entries"));
        int conditions = arraySize(workingPool.get("conditions"));
        int functions = arraySize(workingPool.get("functions"));
        Component summary = KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.summary",
                Component.literal(String.valueOf(entries)),
                Component.literal(String.valueOf(conditions)),
                Component.literal(String.valueOf(functions)));
        g.text(summary, PANEL_X + 36, PANEL_Y + 128, 0xFFE6E6E6, false);
        g.text(KineticI18n.translatable("gui.contentstudio.loot.loots.pool_editor.preserve"), PANEL_X + 36, PANEL_Y + 149, 0xFFAAAAAA, false);
    }

    private void fieldLabel(KineticGraphics g, String key, int x, int color) {
        g.text(KineticI18n.translatable(key), PANEL_X + x, PANEL_Y + 70, color, false);
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
