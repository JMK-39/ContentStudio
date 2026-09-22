package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

final class LootPoolEditScreen extends KineticScreen {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 240;

    private final AbstractLootEditorScreen parent;
    private final int poolIndex;
    private final JsonObject workingPool;
    private NumericEditBox rollsMinBox;
    private NumericEditBox rollsMaxBox;
    private NumericEditBox bonusMinBox;
    private NumericEditBox bonusMaxBox;

    LootPoolEditScreen(AbstractLootEditorScreen parent, int poolIndex, JsonObject pool) {
        super(Component.translatable("gui.contentstudio.loot.loots.pool_editor.title"));
        this.parent = parent;
        setParentScreen(parent);
        this.poolIndex = poolIndex;
        this.workingPool = pool;
}

    @Override
    protected void buildUi() {
        LootJsonEditUtil.Range rolls = LootJsonEditUtil.range(workingPool.get("rolls"), 1);
        LootJsonEditUtil.Range bonus = LootJsonEditUtil.range(workingPool.get("bonus_rolls"), 0);
        rollsMinBox = numericBox(36, format(rolls.min()), LootNumericField.Type.POSITIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.rolls_min");
        rollsMaxBox = numericBox(132, format(rolls.max()), LootNumericField.Type.POSITIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.rolls_max");
        bonusMinBox = numericBox(228, format(bonus.min()), LootNumericField.Type.NON_NEGATIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.bonus_min");
        bonusMaxBox = numericBox(324, format(bonus.max()), LootNumericField.Type.NON_NEGATIVE_DECIMAL,
                "gui.contentstudio.loot.loots.tip.pool.bonus_max");

        addButton(196, 194, 70,
                Component.translatable("gui.contentstudio.loot.loots.pool_editor.apply"),
                Component.translatable("gui.contentstudio.loot.loots.tip.pool.apply"),
                this::applyChanges);
        addButton(272, 194, 70,
                Component.translatable("gui.contentstudio.loot.loots.pool_editor.delete"),
                Component.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm"),
                this::openDeleteConfirm);
        addButton(348, 194, 70,
                Component.translatable("gui.contentstudio.loot.loots.pool_editor.cancel"),
                null,
                this::closeToParent);

    }

    private NumericEditBox numericBox(
            int x,
            String value,
            LootNumericField.Type type,
            String tooltipKey
    ) {
        NumericEditBox box =
                LootNumericField.add(
                        this,
                        x,
                        84,
                        76,
                        value,
                        type,
                        tooltipKey
                );

        return box;
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
        KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.pool.applied"));
        closeToParent();
    }

    private void openDeleteConfirm() {
        openDialog(
                Component.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.title"),
                Component.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.desc"),
                Component.translatable("gui.contentstudio.loot.loots.confirm.delete"),
                Component.translatable("gui.contentstudio.loot.loots.confirm.cancel"),
                () -> {
                    if (parent.deletePoolFromEditor(poolIndex)) {
                        KineticOverlays.toast(Component.translatable("msg.contentstudio.loot.loots.pool.deleted"));
                    }
                    closeToParent();
                },
                () -> { }
        );
    }

    private void closeToParent() {
        if (minecraft != null) navigateBack();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.panel(g, 0, 0, WIDTH, HEIGHT);
        GuiTheme.stateSurface(
                g,
                12,
                12,
                WIDTH - 24,
                HEIGHT - 24,
                GuiTheme.Surface.PANEL_ALT,
                true,
                false,
                false
        );
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        g.drawString(font, getTitle(), 24, 24, 0xFFFFAA00, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.pool_editor.pool",
                Component.literal(String.valueOf(poolIndex + 1)).withStyle(ChatFormatting.YELLOW)), 24, 42, 0xFFE6E6E6, false);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.rolls_min", 36, 0xFF55FFFF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.rolls_max", 132, 0xFF55FFFF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.bonus_min", 228, 0xFFDD77FF);
        fieldLabel(g, "gui.contentstudio.loot.loots.pool_editor.bonus_max", 324, 0xFFDD77FF);

        int entries = arraySize(workingPool.get("entries"));
        int conditions = arraySize(workingPool.get("conditions"));
        int functions = arraySize(workingPool.get("functions"));
        Component summary = Component.translatable("gui.contentstudio.loot.loots.pool_editor.summary",
                Component.literal(String.valueOf(entries)).withStyle(ChatFormatting.YELLOW),
                Component.literal(String.valueOf(conditions)).withStyle(ChatFormatting.AQUA),
                Component.literal(String.valueOf(functions)).withStyle(ChatFormatting.LIGHT_PURPLE));
        g.drawString(font, summary, 36, 128, 0xFFE6E6E6, false);
        g.drawString(font, Component.translatable("gui.contentstudio.loot.loots.pool_editor.preserve"), 36, 149, 0xFFAAAAAA, false);
    }

    private void fieldLabel(GuiGraphics g, String key, int x, int color) {
        g.drawString(font, Component.translatable(key), x, 70, color, false);
    }

    private int arraySize(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray().size() : 0;
    }

    @Override
    protected boolean canvasKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (KineticKeyBindings.matchesKeyCode(KineticKeyBindings.Key.ESCAPE, keyCode)) {
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
