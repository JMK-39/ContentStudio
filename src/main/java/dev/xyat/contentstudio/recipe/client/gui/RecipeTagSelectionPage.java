package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.client.search.KineticSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RecipeTagSelectionPage extends KineticPage {
    private static final int ITEM_HEIGHT = 20;

    private final Consumer<String> onSelected;
    private final List<String> allTags = new ArrayList<>();
    private final KineticSearch.Model<String> tagModel;
    private final KineticScrollController listScroll = new KineticScrollController();

    private KineticTextField searchBox;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int visibleRows;

    public RecipeTagSelectionPage(
            Consumer<String> onSelected
    ) {
        super(KineticI18n.translatable(
                "gui.contentstudio.recipe.recipehud.tag_selection.title"
        ));
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);

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
    protected void build(KineticUi ui) {
        listW = Math.max(100, Math.min(360, width() - 24));
        listX = (width() - listW) / 2;

        searchBox = ui().textField(listX, 20, listW).placeholder(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.search_hint")).build();
        searchBox.onTextChange(this::onSearchUpdate);

        listY = 50;
        listH = Math.max(ITEM_HEIGHT, height() - listY - 40);
        visibleRows = Math.max(1, listH / ITEM_HEIGHT);

        listScroll.update(
                tagModel.items().size(),
                visibleRows
        );

        int btnW = 80;

        ui().button(width() / 2 - btnW / 2, height() - 30, btnW).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.back")).onClick(() -> {
                    if (isAttached()) {
                        navigateBack();
                    }
                }).build();
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
    protected void renderBackground(KineticGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.centeredText(title(), width() / 2, 5, 0xFFFFFF, true);

        KineticTheme.panelAlt(
                graphics,
                listX,
                listY,
                listW,
                listH
        );

        graphics.scissor(listX,
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

            KineticTheme.stateSurface(
                    graphics, listX, y, listW, ITEM_HEIGHT,
                    row % 2 == 0 ? KineticTheme.Surface.PANEL_ALT : KineticTheme.Surface.PANEL,
                    false, hovered, false
            );

            graphics.text(tag, listX + 5, y + 6, 0xFFFFFF, true);
        }

        graphics.endScissor();

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
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在控件之前失焦搜索框（不消费点击）
        // The old canvasMouseClicked blurred the search box before controls (without consuming the click).
        if (searchBox != null
                && !searchBox.contains(input.x(), input.y())) {
            blur(searchBox);
        }
        return false;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x();
        double mouseY = input.y();
        if (listScroll.beginDrag(
                        mouseX,
                        mouseY,
                        input.button(),
                        listX + listW + 2,
                        listY,
                        4,
                        listH,
                        20,
                        0
                )) {
            return true;
        }

        if (input.isLeft()
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

                if (isAttached()) {
                    navigateBack();
                }

                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseY = input.y();
        if (listScroll.drag(
                mouseY,
                listY,
                listH,
                20
        )) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        if (listScroll.release(input.button())) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double delta = input.deltaY();
        return listScroll.scroll(delta);
    }
}
