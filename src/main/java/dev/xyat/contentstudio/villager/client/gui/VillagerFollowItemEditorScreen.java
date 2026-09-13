package dev.xyat.contentstudio.villager.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VillagerFollowItemEditorScreen extends KineticScreen {
    private static final int PANEL_X = 42;
    private static final int PANEL_Y = 18;
    private static final int PANEL_WIDTH = 556;
    private static final int PANEL_HEIGHT = 330;
    private static final int GRID_X = 69;
    private static final int GRID_Y = 60;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final int COLUMNS = 27;
    private static final int ROWS_VISIBLE = 13;
    private static final int GRID_WIDTH = COLUMNS * CELL_SIZE - SLOT_GAP;
    private static final int GRID_HEIGHT = ROWS_VISIBLE * CELL_SIZE - SLOT_GAP;
    private static final int SCROLL_X = GRID_X + GRID_WIDTH + 6;
    private static final int PANEL_BACKGROUND = 0xFF1D1D1D;
    private static final int PANEL_OUTLINE = 0xFF3A3A3A;

    private final Screen parent;
    private final List<String> items = new ArrayList<>();
    private final Map<String, ItemStack> previewCache = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();
    private int hoveredIndex = -1;

    private VillagerFollowItemEditorScreen(Screen parent, List<String> initialItems) {
        super(Component.translatable("gui.contentstudio.villager.follow_item_editor.title"));
        this.parent = parent;
        if (initialItems != null) {
            Set<String> unique = new LinkedHashSet<>();
            for (String itemId : initialItems) {
                String normalized = normalizeItemId(itemId);
                if (!normalized.isEmpty()) unique.add(normalized);
            }
            items.addAll(unique);
        }
        useStandardCanvas();
}

    public static Screen create(Screen parent, List<String> items) {
        return new VillagerFollowItemEditorScreen(parent, items);
    }

    @Override
    protected void buildUi() {
        updateScrollRange();

        addButton(70, 316, 130, Component.translatable("gui.kineticcore.items.list_editor.add"), null, ignored -> openSelector());
        addButton(255, 316, 130, Component.translatable("gui.kineticcore.config.back"), null, ignored -> onClose());
        addButton(440, 316, 130, Component.translatable("gui.kineticcore.hud_editor.save"), null, ignored -> saveAndClose());
    }

    private void openSelector() {
        ItemSearchIndex.prepareCache(() -> Minecraft.getInstance().setScreen(
                new ItemSelectorScreen(this, this::acceptSelection)
        ));
    }

    private void acceptSelection(ItemSelectorScreen.Selection selection) {
        if (selection == null) return;
        if (!selection.isItem()) {
            GuiOverlay.toast(
                    "contentstudio_follow_item_item_only",
                    Component.translatable("gui.kineticcore.items.list_editor.item_only")
            );
            return;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(selection.stack().getItem());
        if (id == null) return;
        String value = id.toString();
        if (!items.contains(value)) {
            items.add(value);
            updateScrollRange();
        }
    }

    private void saveAndClose() {
        VillagerNetwork.saveFollowItems(List.copyOf(items));
        navigateBack();
    }

    private void updateScrollRange() {
        scroll.update(totalRows(), ROWS_VISIBLE);
    }

    private int totalRows() {
        return (items.size() + COLUMNS - 1) / COLUMNS;
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.panel(
                graphics,
                PANEL_X,
                PANEL_Y,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                PANEL_BACKGROUND,
                PANEL_OUTLINE
        );
        graphics.drawCenteredString(font, title, canvasWidth() / 2, 30, 0xFFFFAA00);
        GuiTheme.panel(
                graphics,
                GRID_X,
                GRID_Y,
                GRID_WIDTH,
                GRID_HEIGHT,
                PANEL_BACKGROUND,
                PANEL_OUTLINE
        );
        renderItems(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(
                scroll,
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                4,
                GRID_HEIGHT,
                18
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (items.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.items.list_editor.empty"),
                    GRID_X + GRID_WIDTH / 2,
                    GRID_Y + GRID_HEIGHT / 2 - font.lineHeight / 2,
                    0xFFAAAAAA
            );
        }
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredIndex = indexAt(mouseX, mouseY);
        int baseRow = scroll.smoothIndexOffset();
        int visualShift = scroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(items.size(), first + (ROWS_VISIBLE + 2) * COLUMNS);

        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_WIDTH, GRID_Y + GRID_HEIGHT);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int column = visible % COLUMNS;
            int row = visible / COLUMNS;
            int x = GRID_X + column * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE - visualShift;
            boolean hovered = index == hoveredIndex;

            GuiTheme.itemSlot(graphics, x, y, SLOT_SIZE, 4, hovered);
            ItemStack stack = previewStack(items.get(index));
            if (!stack.isEmpty()) {
                GuiTheme.item(
                        graphics,
                        font,
                        stack,
                        x,
                        y,
                        SLOT_SIZE,
                        1.0F,
                        true
                );
            }
        }
        disableCanvasScissor(graphics);
    }

    private ItemStack previewStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        return previewCache.computeIfAbsent(itemId, VillagerFollowItemEditorScreen::buildPreviewStack);
    }

    private static ItemStack buildPreviewStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT)) {
            return -1;
        }
        int localX = (int) (mouseX - GRID_X);
        int visualShift = scroll.visualShift(CELL_SIZE);
        int localY = (int) Math.floor(mouseY - GRID_Y + visualShift);
        int column = localX / CELL_SIZE;
        int row = localY / CELL_SIZE;
        if (column >= COLUMNS || row > ROWS_VISIBLE) return -1;
        if (localX % CELL_SIZE >= SLOT_SIZE || localY % CELL_SIZE >= SLOT_SIZE) return -1;
        int index = (scroll.smoothIndexOffset() + row) * COLUMNS + column;
        return index >= 0 && index < items.size() ? index : -1;
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean widget = super.canvasMouseClicked(mouseX, mouseY, button);

        if (button == 0 && scroll.beginDrag(
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                6,
                GRID_HEIGHT,
                18,
                2
        )) {
            return true;
        }

        if (button == 1) {
            int index = indexAt(mouseX, mouseY);
            if (index >= 0) {
                items.remove(index);
                updateScrollRange();
                return true;
            }
        }
        return widget;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return scroll.drag(mouseY, GRID_Y, GRID_HEIGHT, 18)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH + 12, GRID_HEIGHT)
                && scroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (hoveredIndex < 0 || hoveredIndex >= items.size()) return;
        String itemId = items.get(hoveredIndex);
        List<FormattedCharSequence> lines = new ArrayList<>();
        ItemStack stack = previewStack(itemId);
        if (!stack.isEmpty()) {
            lines.addAll(font.split(stack.getHoverName(), 300));
        }
        lines.addAll(font.split(Component.literal(itemId), 300));
        lines.addAll(font.split(
                Component.translatable("gui.kineticcore.items.list_editor.remove_hint"),
                300
        ));
        showFormattedTooltip(lines);
    }

    private static String normalizeItemId(String itemId) {
        if (itemId == null) return "";
        String normalized = itemId.trim();
        return ResourceLocation.tryParse(normalized) == null ? "" : normalized;
    }

    @Override
    public void onClose() {
        navigateBack();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
