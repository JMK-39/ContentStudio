package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

final class LootPoolDeleteConfirmScreen extends KineticScreen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 154;

    private final AbstractLootEditorScreen parent;
    private final Screen cancelTarget;
    private final int poolIndex;

    LootPoolDeleteConfirmScreen(AbstractLootEditorScreen parent, Screen cancelTarget, int poolIndex) {
        super(Component.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.title"));
        this.parent = parent;
        this.cancelTarget = cancelTarget;
        this.poolIndex = poolIndex;
        useResponsiveCanvas(
                WIDTH,
                HEIGHT,
                6
        );
    }

    @Override
    protected void buildUi() {
        addButton(86, 112, 78, Component.translatable("gui.contentstudio.loot.loots.confirm.delete"), Component.translatable("gui.contentstudio.loot.loots.tip.pool.delete_confirm"), button -> confirmDelete());
        addButton(176, 112, 78, Component.translatable("gui.contentstudio.loot.loots.confirm.cancel"), null, button -> cancel());
    }

    private void confirmDelete() {
        if (parent.deletePoolFromEditor(poolIndex)) {
            GuiOverlay.toast(Component.translatable("msg.contentstudio.loot.loots.pool.deleted"));
        }
        if (minecraft != null) {
            navigateBack();
        }
    }

    private void cancel() {
        if (minecraft != null) {
            minecraft.setScreen(cancelTarget);
        }
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, WIDTH, HEIGHT, 0xFA1E1E1E);
        g.renderOutline(0, 0, WIDTH, HEIGHT, 0xFF555555);
        g.fill(12, 12, WIDTH - 12, HEIGHT - 12, 0xF0000000);
        g.renderOutline(12, 12, WIDTH - 24, HEIGHT - 24, 0xFFFF5555);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        g.drawCenteredString(font, getTitle(), WIDTH / 2, 23, 0xFFFF5555);
        Component pool = Component.translatable("gui.contentstudio.loot.loots.pool_editor.pool",
                Component.literal(String.valueOf(poolIndex + 1)).withStyle(ChatFormatting.YELLOW));
        g.drawCenteredString(font, pool, WIDTH / 2, 42, 0xFFE6E6E6);
        List<FormattedCharSequence> lines = font.split(
                Component.translatable("gui.contentstudio.loot.loots.pool.delete_confirm.desc"), WIDTH - 48);
        for (int index = 0; index < Math.min(3, lines.size()); index++) {
            g.drawCenteredString(font, lines.get(index), WIDTH / 2, 63 + index * 11, 0xFFE6E6E6);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
