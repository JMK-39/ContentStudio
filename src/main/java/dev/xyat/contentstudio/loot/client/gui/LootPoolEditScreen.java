package dev.xyat.contentstudio.loot.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

final class LootPoolEditScreen extends KineticScreen {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 240;

    private final AbstractLootEditorScreen parent;
    private final int poolIndex;
    private final JsonObject workingPool;
    private EditBox rollsMinBox;
    private EditBox rollsMaxBox;
    private EditBox bonusMinBox;
    private EditBox bonusMaxBox;

    LootPoolEditScreen(AbstractLootEditorScreen parent, int poolIndex, JsonObject pool) {
        super(Component.translatable("gui.contentstudio.loot.loots.pool_editor.title"));
        this.parent = parent;
        this.poolIndex = poolIndex;
        this.workingPool = pool;
        useResponsiveCanvas(
                WIDTH,
                HEIGHT,
                6
        );
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

        addButton(196, 194, 70, Component.translatable("gui.contentstudio.loot.loots.pool_editor.apply"), Component.translatable("gui.contentstudio.loot.loots.tip.pool.apply"), button -> applyChanges());
        addButton(272, 194, 70, Component.translatable("gui.contentstudio.loot.loots.pool_editor.delete"), Component.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm"), button -> openDeleteConfirm());
        addButton(348, 194, 70, Component.translatable("gui.contentstudio.loot.loots.pool_editor.cancel"), null, button -> closeToParent());

    }

    private EditBox numericBox(
            int x,
            String value,
            LootNumericField.Type type,
            String tooltipKey
    ) {
        EditBox box =
                LootNumericField.create(
                        this,
                        x,
                        84,
                        76,
                        value,
                        type,
                        tooltipKey
                );

        addControl(box, null);
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
        GuiOverlay.toast(Component.translatable("msg.contentstudio.loot.loots.pool.applied"));
        closeToParent();
    }

    private void openDeleteConfirm() {
        if (minecraft != null) {
            minecraft.setScreen(new LootPoolDeleteConfirmScreen(parent, this, poolIndex));
        }
    }

    private void closeToParent() {
        if (minecraft != null) navigateBack();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, WIDTH, HEIGHT, 0xFA1E1E1E);
        g.renderOutline(0, 0, WIDTH, HEIGHT, 0xFF555555);
        g.fill(12, 12, WIDTH - 12, HEIGHT - 12, 0xE0000000);
        g.renderOutline(12, 12, WIDTH - 24, HEIGHT - 24, 0xFFFFAA00);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) return String.valueOf((long) Math.rint(value));
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
