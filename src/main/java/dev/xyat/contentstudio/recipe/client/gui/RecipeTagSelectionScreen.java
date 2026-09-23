package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RecipeTagSelectionScreen extends KineticScreen {
    private static final int ITEM_HEIGHT = 20;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<String> allTags = new ArrayList<>();
    private final KineticSearch.Model<String> tagModel;
    private final GridScrollController listScroll = new GridScrollController();

    private KineticEditBox searchBox;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int visibleRows;

    public RecipeTagSelectionScreen(
            Screen parent,
            Consumer<String> onSelected
    ) {
        super(Component.translatable(
                "gui.contentstudio.recipe.recipehud.tag_selection.title"
        ));

        this.parent = parent;
        setParentScreen(parent);
        this.onSelected = onSelected;
        KineticRegistries.items().tagIds().forEach(tagId ->
                allTags.add("#" + tagId)
        );

        allTags.sort(String::compareTo);
        tagModel = new KineticSearch.Model<>(
                allTags,
                KineticSearch::match
        );
        tagModel.refresh("");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void buildUi() {
        listW = Math.max(100, Math.min(360, canvasWidth() - 24));
        listX = (canvasWidth() - listW) / 2;

        searchBox = addTextField(
                listX,
                20,
                listW,
                Component.empty(),
                Component.translatable("gui.contentstudio.recipe.recipehud.search_hint"),
                null,
                null
        );
        searchBox.setResponder(this::onSearchUpdate);

        listY = 50;
        listH = Math.max(ITEM_HEIGHT, canvasHeight() - listY - 40);
        visibleRows = Math.max(1, listH / ITEM_HEIGHT);

        listScroll.update(
                tagModel.items().size(),
                visibleRows
        );

        int btnW = 80;

        addButton(
                canvasWidth() / 2 - btnW / 2,
                canvasHeight() - 30,
                btnW,
                Component.translatable("gui.contentstudio.recipe.recipehud.back"),
                null,
                () -> {
                    if (minecraft != null) {
                        navigateBack();
                    }
                }
        );
    }

    private void onSearchUpdate(String query) {
        tagModel.refresh(query);
        listScroll.reset();
        listScroll.update(
                tagModel.items().size(),
                visibleRows
        );
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.drawCenteredString(
                font,
                title,
                canvasWidth() / 2,
                5,
                0xFFFFFF
        );

        GuiTheme.panelAlt(
                graphics,
                listX,
                listY,
                listW,
                listH
        );

        enableUiScissor(
                graphics,
                listX,
                listY,
                listX + listW,
                listY + listH
        );

        List<String> displayTags =
                tagModel.items();

        int baseRow = listScroll.smoothIndexOffset();
        int visualShift = listScroll.visualShift(ITEM_HEIGHT);
        for (int row = 0;
             row < visibleRows + 2;
             row++) {
            int index = baseRow + row;

            if (index >= displayTags.size()) {
                break;
            }

            String tag =
                    displayTags.get(index);

            int y =
                    listY
                            + row * ITEM_HEIGHT
                            - visualShift;

            boolean hovered =
                    mouseX >= listX
                            && mouseX < listX + listW
                            && mouseY >= y
                            && mouseY < y + ITEM_HEIGHT;

            GuiTheme.stateSurface(
                    graphics, listX, y, listW, ITEM_HEIGHT,
                    row % 2 == 0 ? GuiTheme.Surface.PANEL_ALT : GuiTheme.Surface.PANEL,
                    false, hovered, false
            );

            graphics.drawString(
                    font,
                    tag,
                    listX + 5,
                    y + 6,
                    0xFFFFFF
            );
        }

        disableUiScissor(graphics);

        listScroll.render(
                graphics,
                mouseX,
                mouseY,
                listX + listW + 2,
                listY,
                4,
                listH,
                20
        );
    }

    @Override
    protected boolean canvasMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (searchBox != null
                && !searchBox.isMouseOver(mouseX, mouseY)) {
            blurControl(searchBox);
        }

        if (super.canvasMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (KineticMouseButtons.isPrimary(button)
                && listScroll.beginDrag(
                        mouseX,
                        mouseY,
                        listX + listW + 2,
                        listY,
                        4,
                        listH,
                        20,
                        0
                )) {
            return true;
        }

        if (KineticMouseButtons.isPrimary(button)
                && mouseX >= listX
                && mouseX < listX + listW
                && mouseY >= listY
                && mouseY < listY + listH) {
            int visualShift = listScroll.visualShift(ITEM_HEIGHT);
            int index = listScroll.smoothIndexOffset()
                    + (int) Math.floor((mouseY - listY + visualShift) / ITEM_HEIGHT);

            List<String> displayTags = tagModel.items();

            if (index >= 0 && index < displayTags.size()) {
                onSelected.accept(displayTags.get(index));

                if (minecraft != null) {
                    navigateBack();
                }

                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (listScroll.drag(
                mouseY,
                listY,
                listH,
                20
        )) {
            return true;
        }

        return super.canvasMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean canvasMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (listScroll.release(button)) {
            return true;
        }

        return super.canvasMouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        return listScroll.scroll(delta)
                || super.canvasMouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                );
    }

    public Screen getParent() {
        return parent;
    }
}
