package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class RecipeTagSelectionScreen extends KineticScreen {
    private static final int ITEM_HEIGHT = 20;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<String> allTags = new ArrayList<>();
    private final KineticSearch.Model<String> tagModel;
    private final GridScrollController listScroll = new GridScrollController();

    private EditBox searchBox;
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
        this.onSelected = onSelected;

        useCanvas(
                640f,
                360f,
                6
        );

        Objects.requireNonNull(
                ForgeRegistries.ITEMS.tags()
        ).getTagNames().forEach(tagKey ->
                allTags.add("#" + tagKey.location())
        );

        allTags.sort(String::compareTo);
        tagModel = new KineticSearch.Model<>(
                allTags,
                tag -> tag
        );
        tagModel.refresh("");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void buildUi() {
        listW = Math.max(100, Math.min(360, canvasWidth - 24));
        listX = (canvasWidth - listW) / 2;

        searchBox = new EditBox(
                font,
                listX,
                20,
                listW,
                20,
                Component.empty()
        );
        searchBox.setResponder(this::onSearchUpdate);
        addRenderableWidget(searchBox);

        listY = 50;
        listH = Math.max(ITEM_HEIGHT, canvasHeight - listY - 40);
        visibleRows = Math.max(1, listH / ITEM_HEIGHT);

        listScroll.update(
                tagModel.items().size(),
                visibleRows
        );

        int btnW = 80;
        int btnH = 20;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.contentstudio.recipe.recipehud.back"
                                ),
                                button -> {
                                    if (minecraft != null) {
                                        minecraft.setScreen(parent);
                                    }
                                }
                        )
                        .bounds(
                                canvasWidth / 2 - btnW / 2,
                                canvasHeight - 30,
                                btnW,
                                btnH
                        )
                        .build()
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
                canvasWidth / 2,
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

        enableCanvasScissor(
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

            int backgroundColor =
                    row % 2 == 0
                            ? 0x44FFFFFF
                            : 0x44888888;

            if (hovered) {
                backgroundColor =
                        0x88FFFFFF;
            }

            graphics.fill(
                    listX,
                    y,
                    listX + listW,
                    y + ITEM_HEIGHT,
                    backgroundColor
            );

            graphics.drawString(
                    font,
                    tag,
                    listX + 5,
                    y + 6,
                    0xFFFFFF
            );
        }

        graphics.disableScissor();

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
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (searchBox != null
                && !searchBox.isFocused()
                && searchBox.getValue().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.contentstudio.recipe.recipehud.search_hint"
                    ),
                    searchBox.getX() + 6,
                    searchBox.getY() + 6,
                    0xFFAAAAAA,
                    false
            );
        }
    }

    @Override
    protected boolean canvasMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (searchBox != null
                && !searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(false);
        }

        if (super.canvasMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0
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

        if (button == 0
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
                    minecraft.setScreen(parent);
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
}
