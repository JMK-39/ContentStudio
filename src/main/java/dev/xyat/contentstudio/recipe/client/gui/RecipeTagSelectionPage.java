package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticSizes;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Picks an item tag. The tags are a compact list on the left; the items of the hovered (or last hovered) tag show as
 * icons on the right, so a tag can be checked before it is picked. Clicking a tag picks it and goes back.
 */
public class RecipeTagSelectionPage extends KineticPage {
    private static final int TEXT_GAP = 4;
    private static final int SEARCH_W = 196;
    private static final int TOP = 28, BOTTOM = 356;
    // Tag list on the left.
    private static final int LIST_X = 4, LIST_W = 300, ROW_H = 14;
    private static final int LIST_SCROLL_X = LIST_X + LIST_W + 2;
    private static final int VISIBLE_ROWS = (BOTTOM - TOP) / ROW_H, LIST_H = VISIBLE_ROWS * ROW_H;
    private static final int SPLIT_X = LIST_SCROLL_X + 8;
    // Item preview on the right: standard item slots (KineticSizes).
    private static final int SLOT = KineticSizes.ITEM_SLOT, PITCH = KineticSizes.ITEM_PITCH;
    private static final int PREVIEW_X = SPLIT_X + 6, PREVIEW_Y = TOP + 14;
    private static final int PREVIEW_COLS = (636 - PREVIEW_X + 2) / PITCH;
    private static final int PREVIEW_ROWS = (BOTTOM - PREVIEW_Y + 2) / PITCH;

    private final Consumer<String> onSelected;
    private final List<String> allTags = new ArrayList<>();
    private final KineticSearch.Model<String> tagModel;
    private final KineticScrollController listScroll = new KineticScrollController();
    private final Map<String, List<ItemStack>> members = new HashMap<>();

    private KineticTextField searchBox;
    private String previewTag;

    public RecipeTagSelectionPage(Consumer<String> onSelected) {
        super(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tag_selection.title"));
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        this.onSelected = onSelected;
        KineticRegistries.items().tagIds().forEach(tagId -> allTags.add("#" + tagId));
        allTags.sort(String::compareTo);
        tagModel = new KineticSearch.Model<>(allTags, KineticSearch::match);
        tagModel.refresh("");
    }

    @Override
    protected void build(KineticUi ui) {
        ui().button(4, 4, 40).text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.back")).onClick(() -> {
            if (isAttached()) navigateBack();
        }).build();
        searchBox = ui().textField(48, 4, SEARCH_W).placeholder(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.search_hint")).build();
        searchBox.onTextChange(this::onSearchUpdate);
        listScroll.update(tagModel.items().size(), VISIBLE_ROWS);
    }

    private void onSearchUpdate(String query) {
        tagModel.refresh(query);
        listScroll.reset();
        listScroll.update(tagModel.items().size(), VISIBLE_ROWS);
    }

    private List<ItemStack> membersOf(String tag) {
        return members.computeIfAbsent(tag, key -> {
            List<ItemStack> stacks = new ArrayList<>();
            try {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, KineticResourceIds.parse(key.substring(1)));
                for (Item item : KineticRegistries.items().valuesInTag(tagKey)) stacks.add(new ItemStack(item));
            } catch (RuntimeException ignored) {
                // An unparsable tag id shows no items.
            }
            return stacks;
        });
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < LIST_X || mouseX >= LIST_X + LIST_W || mouseY < TOP || mouseY >= TOP + LIST_H) return -1;
        int index = listScroll.smoothIndexOffset() + (int) Math.floor((mouseY - TOP + listScroll.visualShift(ROW_H)) / ROW_H);
        return index >= 0 && index < tagModel.items().size() ? index : -1;
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, 640, 360);
        int titleX = 48 + SEARCH_W + TEXT_GAP;
        Component heading = Component.empty().append(title()).append(" · " + tagModel.items().size());
        graphics.scrollingText(heading, titleX, 8, 636 - titleX, KineticTheme.current().text(), false);
        graphics.hLine(4, 636, TOP - 3, KineticTheme.current().accentHover());
        graphics.vLine(SPLIT_X, TOP - 1, BOTTOM, KineticTheme.current().accentHover());

        List<String> tags = tagModel.items();
        int hoveredRow = rowAt(mouseX, mouseY);
        if (hoveredRow >= 0) previewTag = tags.get(hoveredRow);
        listScroll.update(tags.size(), VISIBLE_ROWS);
        graphics.scissor(LIST_X, TOP, LIST_X + LIST_W, TOP + LIST_H);
        int base = listScroll.smoothIndexOffset(), shift = listScroll.visualShift(ROW_H);
        for (int row = 0; row < VISIBLE_ROWS + 2; row++) {
            int index = base + row;
            if (index >= tags.size()) break;
            int y = TOP + row * ROW_H - shift;
            String tag = tags.get(index);
            boolean hovered = index == hoveredRow;
            boolean previewed = tag.equals(previewTag);
            if (hovered || previewed) {
                KineticTheme.stateSurface(graphics, LIST_X, y, LIST_W, ROW_H, KineticTheme.Surface.PANEL_ALT, previewed && !hovered, hovered, false);
            }
            int colon = tag.indexOf(':');
            String namespace = colon > 0 ? tag.substring(0, colon + 1) : "";
            String path = colon > 0 ? tag.substring(colon + 1) : tag;
            int nsW = graphics.text(Component.literal(namespace), LIST_X + 4, y + 3, KineticTheme.current().mutedText()) - (LIST_X + 4);
            graphics.scrollingText(Component.literal(path), LIST_X + 4 + nsW, y + 3, LIST_W - 8 - nsW, KineticTheme.current().text(), false);
        }
        graphics.endScissor();
        listScroll.render(graphics, mouseX, mouseY, LIST_SCROLL_X, TOP, 4, LIST_H, 20);

        if (previewTag == null && !tags.isEmpty()) previewTag = tags.get(0);
        if (previewTag != null) renderPreview(graphics, previewTag);
    }

    private void renderPreview(KineticGraphics graphics, String tag) {
        List<ItemStack> stacks = membersOf(tag);
        Component heading = Component.literal(tag + " · " + stacks.size());
        graphics.scrollingText(heading, PREVIEW_X, TOP, 636 - PREVIEW_X, KineticTheme.current().text(), false);
        int limit = Math.min(stacks.size(), PREVIEW_COLS * PREVIEW_ROWS);
        for (int i = 0; i < limit; i++) {
            int x = PREVIEW_X + i % PREVIEW_COLS * PITCH, y = PREVIEW_Y + i / PREVIEW_COLS * PITCH;
            KineticTheme.itemSlot(graphics, x, y, SLOT, false);
            KineticTheme.item(graphics, stacks.get(i), x, y, SLOT, 1.0F, false);
        }
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (previewTag == null || mouseX < PREVIEW_X || mouseY < PREVIEW_Y) return;
        int col = (mouseX - PREVIEW_X) / PITCH, row = (mouseY - PREVIEW_Y) / PITCH;
        if (col >= PREVIEW_COLS || (mouseX - PREVIEW_X) % PITCH >= SLOT || (mouseY - PREVIEW_Y) % PITCH >= SLOT) return;
        int index = row * PREVIEW_COLS + col;
        List<ItemStack> stacks = membersOf(previewTag);
        if (index < Math.min(stacks.size(), PREVIEW_COLS * PREVIEW_ROWS)) {
            showItemTooltip(stacks.get(index));
            tooltipAnchor(PREVIEW_X + col * PITCH, PREVIEW_Y + row * PITCH, SLOT, SLOT);
        }
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 canvasMouseClicked 在控件之前失焦搜索框（不消费点击）
        // The old canvasMouseClicked blurred the search box before controls (without consuming the click).
        if (searchBox != null && !searchBox.contains(input.x(), input.y())) blur(searchBox);
        return false;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        if (listScroll.beginDrag(input.x(), input.y(), input.button(), LIST_SCROLL_X, TOP, 4, LIST_H, 20, 0)) return true;
        int index = rowAt(input.x(), input.y());
        if (input.isLeft() && index >= 0) {
            onSelected.accept(tagModel.items().get(index));
            if (isAttached()) navigateBack();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        return listScroll.drag(input.y(), TOP, LIST_H, 20);
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        return listScroll.release(input.button());
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        return input.x() < SPLIT_X && listScroll.scroll(input.deltaY());
    }
}
