package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticSizes;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetworkClient;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalImpactFlow;
import dev.xyat.contentstudio.recipe.removal.RuleImpactDraft;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One original-recipe impact preview for MOD, OUTPUT, TAG, and TYPE rules.
 *
 * <p>Every affected output is an item icon in a grid (green outline: all its recipes are removed, yellow: some are).
 * Left-click an icon to list its recipes on the right, where each recipe switches between remove and keep;
 * right-click it for view / remove-or-keep / copy ID. All actions sit in the two header rows.</p>
 */
public final class RecipeRemovalImpactPage extends KineticPage implements RecipeNetworkClient.ImpactListener {
    private static final int TEXT_GAP = 4;
    // Header: row 1 Back, title, counts and the selection buttons; row 2 search and the three apply actions.
    private static final int ROW1_Y = 4, ROW2_Y = 24, DIVIDER_Y = 44;
    private static final int RELOAD_W = 56, SELECT_W = 40;
    private static final int RELOAD_X = 636 - RELOAD_W, CLEAR_X = RELOAD_X - 4 - SELECT_W, SELECT_ALL_X = CLEAR_X - 4 - SELECT_W;
    private static final int ACTION_W = 84;
    private static final int RANGE_X = 636 - ACTION_W, EXACT_X = RANGE_X - 4 - ACTION_W, STALE_X = EXACT_X - 4 - ACTION_W;
    private static final int SEARCH_W = 196;
    // Content: the output grid on the left, the selected output's recipes on the right.
    // Standard item slots (KineticSizes): 22 px, 2 px apart.
    private static final int CELL = KineticSizes.ITEM_SLOT, PITCH = KineticSizes.ITEM_PITCH;
    private static final int GRID_X = 4, GRID_Y = DIVIDER_Y + 4, GRID_COLS = 16, GRID_ROWS = (356 - GRID_Y + 2) / PITCH;
    private static final int GRID_W = GRID_COLS * PITCH - 2, GRID_H = GRID_ROWS * PITCH - 2;
    private static final int GRID_SCROLL_X = GRID_X + GRID_W + 2;
    private static final int SPLIT_X = GRID_SCROLL_X + 6;
    private static final int DETAIL_X = SPLIT_X + 4, DETAIL_RIGHT = 636, DETAIL_W = DETAIL_RIGHT - DETAIL_X;
    private static final int RECIPE_TOP = GRID_Y + CELL + 6, RECIPE_ROW = 20, RECIPE_BOTTOM = 356;
    private static final int STATE_W = 44, STATE_H = 14;
    private static final int RECIPE_SCROLL_X = DETAIL_RIGHT - 4;
    private static final int STATE_X = RECIPE_SCROLL_X - 4 - STATE_W;

    private record OutputGroup(@Nullable ResourceLocation outputId, List<RemovalCandidate> recipes) { }

    private final RecipeRemovalPage parent;
    private final RemovalEntry rule;
    private final @Nullable ResourceLocation focusOutput;
    private final RemovalImpactFlow flow;
    private final boolean editingExisting;
    private final KineticScrollController gridScroll = new KineticScrollController();
    private final KineticScrollController recipeScroll = new KineticScrollController();
    private final List<OutputGroup> groups = new ArrayList<>();
    private final List<OutputGroup> visibleGroups = new ArrayList<>();
    private final Map<ResourceLocation, String> outputNames = new HashMap<>();
    private KineticAutoCompleteField search;
    private KineticButton selectAllButton, clearButton, exactButton, rangeButton, staleButton;
    private String query = "";
    private @Nullable OutputGroup selected;
    private long requestId;
    private int restartCount;
    private boolean loadFailed;
    private boolean clearStale;

    public RecipeRemovalImpactPage(RecipeRemovalPage parent, RemovalEntry rule,
                                     @Nullable ResourceLocation focusOutput) {
        super(RecipeRemovalPage.tr("impact_title", rule.mode().getDisplayName()));
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        this.parent = parent;
        this.rule = rule;
        this.focusOutput = focusOutput;
        List<RemovalEntry> currentRules = parent.draftRules();
        this.editingExisting = currentRules.stream().anyMatch(entry -> entry.key().equals(rule.key()));
        this.flow = new RemovalImpactFlow(rule, currentRules);
        gridScroll.bindSelection(() -> selected == null ? -1 : visibleGroups.indexOf(selected) / GRID_COLS);
    }

    @Override protected void build(KineticUi ui) {
        ui().button(4, ROW1_Y, 40).text(RecipeRemovalPage.tr("back")).onClick(this::close).build();
        selectAllButton = ui().button(SELECT_ALL_X, ROW1_Y, SELECT_W).text(RecipeRemovalPage.tr("impact_select_all"))
                .onClick(() -> { flow.draft().selectAll(true); updateButtons(); }).build();
        clearButton = ui().button(CLEAR_X, ROW1_Y, SELECT_W).text(RecipeRemovalPage.tr("impact_clear_all"))
                .onClick(() -> { flow.draft().selectAll(false); updateButtons(); }).build();
        ui().button(RELOAD_X, ROW1_Y, RELOAD_W).text(RecipeRemovalPage.tr("impact_reload")).onClick(this::startLoad).build();

        search = ui().autoComplete(4, ROW2_Y, SEARCH_W, KineticSuggestion.fromStrings(() -> flow.isComplete()
                        ? flow.candidates().stream().map(candidate -> candidate.id().toString()).toList()
                        : List.of())).label(RecipeRemovalPage.tr("impact_search")).placeholder(RecipeRemovalPage.tr("impact_search")).build();
        search.limitTextLength(256);
        search.setTextValue(query);
        search.onTextChange(value -> { query = value; rebuildVisibleGroups(); gridScroll.setOffset(0); });
        staleButton = ui().button(STALE_X, ROW2_Y, ACTION_W).text(RecipeRemovalPage.tr("impact_keep_stale"))
                .tooltip(() -> RecipeRemovalPage.tr("impact_stale_count", flow.staleExcludedRecipeIds().size()))
                .onClick(() -> {
                    clearStale = !clearStale;
                    updateButtons();
                }).build();
        // The former notes under the list live in these tooltips.
        exactButton = ui().button(EXACT_X, ROW2_Y, ACTION_W).text(RecipeRemovalPage.tr("impact_exact"))
                .tooltip(Component.empty().append(RecipeRemovalPage.tr("impact_exact_hint")).append("\n")
                        .append(RecipeRemovalPage.tr("impact_scope_note")))
                .onClick(() -> {
                    parent.applyImpactExactRules(rule, flow.buildExactRules());
                    close();
                }).build();
        rangeButton = ui().button(RANGE_X, ROW2_Y, ACTION_W).text(RecipeRemovalPage.tr("impact_range"))
                .tooltip(Component.empty().append(RecipeRemovalPage.tr("impact_range_hint")).append("\n")
                        .append(RecipeRemovalPage.tr("impact_future_note")))
                .onClick(() -> {
                    parent.applyImpactRangeRule(flow.buildRangeRule(clearStale));
                    close();
                }).build();
        updateButtons();
        startLoad();
    }

    private void startLoad() {
        flow.reset();
        selected = null;
        groups.clear();
        visibleGroups.clear();
        outputNames.clear();
        clearStale = false;
        restartCount = 0;
        loadFailed = false;
        gridScroll.setOffset(0);
        recipeScroll.setOffset(0);
        requestId = RecipeNetworkClient.requestImpact(this, rule.mode(), rule.value(), 0, 0L, 0L);
        updateButtons();
    }

    @Override public void acceptImpactPage(RecipeNetwork.RuleImpactPagePacket page) {
        if (!isOpen() || page.requestId() != requestId
                || page.mode() != rule.mode() || !page.value().equals(rule.value())) return;
        RemovalImpactFlow.PageStatus status = flow.acceptPage(page.page(), page.totalPages(),
                page.catalogVersion(), page.stale(), page.candidates());
        if (status == RemovalImpactFlow.PageStatus.RESTART) {
            if (++restartCount > 3) {
                loadFailed = true;
                updateButtons();
                return;
            }
            requestId = RecipeNetworkClient.requestImpact(this, rule.mode(), rule.value(), 0, 0L, 0L);
        } else if (status == RemovalImpactFlow.PageStatus.PROGRESS) {
            RecipeNetworkClient.requestImpact(this, rule.mode(), rule.value(), flow.nextPage(),
                    flow.catalogVersion(), requestId);
        } else {
            buildGroups();
            rebuildVisibleGroups();
            if (focusOutput != null) {
                for (int i = 0; i < visibleGroups.size(); i++) {
                    if (focusOutput.equals(visibleGroups.get(i).outputId())) {
                        select(visibleGroups.get(i));
                        gridScroll.setOffset(i / GRID_COLS);
                        break;
                    }
                }
            }
        }
        updateButtons();
    }

    private void buildGroups() {
        Map<ResourceLocation, List<RemovalCandidate>> byOutput = new LinkedHashMap<>();
        for (RemovalCandidate candidate : flow.candidates()) {
            byOutput.computeIfAbsent(candidate.outputItemId(), ignored -> new ArrayList<>()).add(candidate);
        }
        groups.clear();
        byOutput.forEach((outputId, recipes) -> groups.add(new OutputGroup(outputId, List.copyOf(recipes))));
        groups.sort(Comparator.comparing(group -> group.outputId() == null ? "￿" : group.outputId().toString()));
    }

    private String outputName(@Nullable ResourceLocation outputId) {
        if (outputId == null) return RecipeRemovalPage.tr("impact_unknown_output").getString();
        return outputNames.computeIfAbsent(outputId, id -> {
            if (!KineticRegistries.items().contains(id)) return id.toString();
            Item item = KineticRegistries.items().get(id);
            return new ItemStack(item).getHoverName().getString();
        });
    }

    private static ItemStack stack(@Nullable ResourceLocation id) {
        return id != null && KineticRegistries.items().contains(id) ? new ItemStack(KineticRegistries.items().get(id)) : ItemStack.EMPTY;
    }

    /** The recipes of a group that match the search (all of them when the output itself matches). */
    private List<RemovalCandidate> matchingRecipes(OutputGroup group) {
        String value = query.trim();
        if (value.isEmpty() || groupMatches(group, value)) return group.recipes();
        return group.recipes().stream().filter(recipe -> KineticSearch.match(recipe.id().toString(), value)
                || recipe.recipeType() != null && KineticSearch.match(recipe.recipeType().toString(), value)).toList();
    }

    private boolean groupMatches(OutputGroup group, String value) {
        return KineticSearch.match(outputName(group.outputId()), value)
                || group.outputId() != null && KineticSearch.match(group.outputId().toString(), value);
    }

    private void rebuildVisibleGroups() {
        visibleGroups.clear();
        if (!flow.isComplete()) return;
        for (OutputGroup group : groups) {
            if (!matchingRecipes(group).isEmpty()) visibleGroups.add(group);
        }
        if (selected != null && !visibleGroups.contains(selected)) selected = null;
        if (selected == null && !visibleGroups.isEmpty()) selected = visibleGroups.get(0);
        gridScroll.update(gridRowCount(), GRID_ROWS);
    }

    private int gridRowCount() {
        return (visibleGroups.size() + GRID_COLS - 1) / GRID_COLS;
    }

    private void select(OutputGroup group) {
        if (selected != group) recipeScroll.setOffset(0);
        selected = group;
    }

    private void updateButtons() {
        if (exactButton == null) return;
        boolean complete = flow.isComplete() && !loadFailed;
        boolean hasCandidates = complete && flow.loadedCount() > 0;
        selectAllButton.setEnabled(hasCandidates);
        clearButton.setEnabled(hasCandidates);
        exactButton.setEnabled(hasCandidates && !flow.draft().selectedRecipeIds().isEmpty());
        rangeButton.setEnabled(complete && (editingExisting || hasCandidates));
        staleButton.setEnabled(complete && !flow.staleExcludedRecipeIds().isEmpty());
        staleButton.setText(RecipeRemovalPage.tr(clearStale ? "impact_clear_stale" : "impact_keep_stale"));
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    @Override protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, 640, 360);
        int textY = ROW1_Y + 4;
        Component heading = Component.empty().append(title()).append(" · " + rule.value());
        int countsW = 132;
        int countsX = SELECT_ALL_X - TEXT_GAP - countsW;
        graphics.scrollingText(heading, 48, textY, countsX - 48 - TEXT_GAP, KineticTheme.current().text(), false);
        if (flow.isComplete()) {
            graphics.scrollingTextRight(RecipeRemovalPage.tr("impact_counts", groups.size(),
                    flow.candidates().size(), flow.draft().selectedRecipeIds().size()), countsX + countsW, textY,
                    countsW, KineticTheme.current().mutedText(), false);
        }
        graphics.hLine(4, 636, DIVIDER_Y, KineticTheme.current().accentHover());
        graphics.vLine(SPLIT_X, DIVIDER_Y + 2, 356, KineticTheme.current().accentHover());
        renderGrid(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);
    }

    private void renderGrid(KineticGraphics graphics, int mouseX, int mouseY) {
        Component message = null;
        if (!flow.isComplete()) {
            message = RecipeRemovalPage.tr(loadFailed ? "impact_load_failed" : "impact_loading", flow.loadedCount(), flow.totalPages());
        } else if (flow.candidates().isEmpty()) {
            message = RecipeRemovalPage.tr(editingExisting ? "impact_no_original_existing" : "impact_no_original");
        } else if (visibleGroups.isEmpty()) {
            message = RecipeRemovalPage.tr("impact_no_search_result");
        }
        if (message != null) {
            graphics.scrollingTextCentered(message, GRID_X + GRID_W / 2, GRID_Y + GRID_H / 2 - 4, GRID_W - 8,
                    KineticTheme.current().mutedText(), false);
            return;
        }
        gridScroll.update(gridRowCount(), GRID_ROWS);
        int startRow = gridScroll.smoothIndexOffset(), shift = gridScroll.visualShift(PITCH);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int row = startRow; row <= startRow + GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = row * GRID_COLS + col;
                if (index >= visibleGroups.size()) break;
                OutputGroup group = visibleGroups.get(index);
                int x = GRID_X + col * PITCH, y = GRID_Y + (row - startRow) * PITCH - shift;
                boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                        && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H;
                KineticTheme.itemSlot(graphics, x, y, CELL, hovered);
                ItemStack stack = stack(group.outputId());
                if (!stack.isEmpty()) KineticTheme.item(graphics, stack, x, y, CELL, 1.0F, false);
                else graphics.centeredText("?", x + CELL / 2, y + (CELL - 8) / 2, KineticTheme.current().mutedText(), false);
                RuleImpactDraft.Selection state = flow.draft().selectionForOutput(group.outputId());
                if (state == RuleImpactDraft.Selection.ALL) KineticTheme.indicatorOutline(graphics, x, y, CELL, CELL, KineticTheme.Indicator.SUCCESS);
                else if (state == RuleImpactDraft.Selection.PARTIAL) KineticTheme.indicatorOutline(graphics, x, y, CELL, CELL, KineticTheme.Indicator.WARNING);
                if (group == selected) KineticTheme.stateOutline(graphics, x - 1, y - 1, CELL + 2, CELL + 2, true, false, false);
            }
        }
        graphics.endScissor();
        gridScroll.render(graphics, mouseX, mouseY, GRID_SCROLL_X, GRID_Y, 4, GRID_H, 8);
    }

    private void renderDetail(KineticGraphics graphics, int mouseX, int mouseY) {
        if (selected == null) return;
        ItemStack stack = stack(selected.outputId());
        KineticTheme.itemSlot(graphics, DETAIL_X, GRID_Y, CELL, false);
        if (!stack.isEmpty()) KineticTheme.item(graphics, stack, DETAIL_X, GRID_Y, CELL, 1.0F, false);
        int textX = DETAIL_X + CELL + TEXT_GAP;
        graphics.scrollingText(Component.literal(outputName(selected.outputId())), textX, GRID_Y + 2,
                DETAIL_RIGHT - textX, KineticTheme.current().text(), false);
        graphics.scrollingText(RecipeRemovalPage.tr("impact_group_count", selected.recipes().size()), textX, GRID_Y + 12,
                DETAIL_RIGHT - textX, KineticTheme.current().mutedText(), false);
        graphics.hLine(DETAIL_X, DETAIL_RIGHT, RECIPE_TOP - 4, KineticTheme.current().accentHover());

        List<RemovalCandidate> recipes = matchingRecipes(selected);
        int visibleRows = (RECIPE_BOTTOM - RECIPE_TOP) / RECIPE_ROW;
        recipeScroll.update(recipes.size(), visibleRows);
        int start = recipeScroll.smoothIndexOffset(), shift = recipeScroll.visualShift(RECIPE_ROW);
        graphics.scissor(DETAIL_X, RECIPE_TOP, DETAIL_RIGHT, RECIPE_BOTTOM);
        for (int i = start; i < Math.min(recipes.size(), start + visibleRows + 1); i++) {
            RemovalCandidate recipe = recipes.get(i);
            int y = RECIPE_TOP + (i - start) * RECIPE_ROW - shift;
            boolean removed = flow.draft().selectedRecipeIds().contains(recipe.id());
            boolean hover = mouseX >= DETAIL_X && mouseX < RECIPE_SCROLL_X && mouseY >= y && mouseY < y + RECIPE_ROW - 2
                    && mouseY >= RECIPE_TOP && mouseY < RECIPE_BOTTOM;
            KineticTheme.stateSurface(graphics, DETAIL_X, y, RECIPE_SCROLL_X - 2 - DETAIL_X, RECIPE_ROW - 2,
                    KineticTheme.Surface.PANEL_ALT, removed, hover, false);
            int textW = STATE_X - DETAIL_X - 4 - TEXT_GAP;
            graphics.scrollingText(Component.literal(recipe.id().toString()), DETAIL_X + 4, y + 1, textW,
                    KineticTheme.current().text(), false);
            Component kind = !removed && !flow.otherBlockingRules(recipe.id()).isEmpty()
                    ? RecipeRemovalPage.tr("impact_other_blocker")
                    : Component.literal(recipe.recipeType() == null ? "" : recipe.recipeType().toString());
            graphics.scrollingText(kind, DETAIL_X + 4, y + 10, textW, KineticTheme.current().mutedText(), false);
            KineticTheme.button(graphics, STATE_X, y + 2, STATE_W, STATE_H,
                    stateLabel(removed ? RuleImpactDraft.Selection.ALL : RuleImpactDraft.Selection.NONE),
                    inState(y, mouseX, mouseY), true, false);
        }
        graphics.endScissor();
        recipeScroll.render(graphics, mouseX, mouseY, RECIPE_SCROLL_X, RECIPE_TOP, 4, RECIPE_BOTTOM - RECIPE_TOP, 8);
    }

    private static Component stateLabel(RuleImpactDraft.Selection selection) {
        return RecipeRemovalPage.tr(switch (selection) {
            case ALL -> "impact_state_remove";
            case PARTIAL -> "impact_state_partial";
            case NONE -> "impact_state_keep";
        });
    }

    private static boolean inState(int rowY, double mouseX, double mouseY) {
        return mouseX >= STATE_X && mouseX < STATE_X + STATE_W && mouseY >= rowY + 2 && mouseY < rowY + 2 + STATE_H;
    }

    // ---- hit testing ----------------------------------------------------------------------------------------------

    private @Nullable OutputGroup groupAt(double x, double y) {
        if (x < GRID_X || x >= GRID_X + GRID_W || y < GRID_Y || y >= GRID_Y + GRID_H) return null;
        int col = (int) (x - GRID_X) / PITCH;
        if ((x - GRID_X) % PITCH >= CELL) return null;
        double rowPos = (y - GRID_Y + gridScroll.visualShift(PITCH)) / PITCH;
        int row = gridScroll.smoothIndexOffset() + (int) Math.floor(rowPos);
        if ((rowPos - Math.floor(rowPos)) * PITCH >= CELL) return null;
        int index = row * GRID_COLS + col;
        return index >= 0 && index < visibleGroups.size() ? visibleGroups.get(index) : null;
    }

    private int recipeIndexAt(double x, double y) {
        if (selected == null || x < DETAIL_X || x >= RECIPE_SCROLL_X || y < RECIPE_TOP || y >= RECIPE_BOTTOM) return -1;
        int index = recipeScroll.smoothIndexOffset() + (int) ((y - RECIPE_TOP + recipeScroll.visualShift(RECIPE_ROW)) / RECIPE_ROW);
        return index >= 0 && index < matchingRecipes(selected).size() ? index : -1;
    }

    private int recipeRowY(int index) {
        return RECIPE_TOP + (index - recipeScroll.smoothIndexOffset()) * RECIPE_ROW - recipeScroll.visualShift(RECIPE_ROW);
    }

    @Override protected void renderTooltips(int mx, int my) {
        OutputGroup group = groupAt(mx, my);
        if (group == null) return;
        ItemStack stack = stack(group.outputId());
        List<Component> lines = new ArrayList<>();
        lines.add(RecipeRemovalPage.tr("impact_group_count", group.recipes().size()));
        lines.add(stateLabel(flow.draft().selectionForOutput(group.outputId())));
        if (!stack.isEmpty()) showItemTooltip(stack, lines);
        else {
            lines.add(0, Component.literal(outputName(group.outputId())));
            showTooltip(lines);
        }
    }

    @Override protected boolean onMouseClick(MouseInput input) {
        double x = input.x();
        double y = input.y();
        blur(search);
        if (!flow.isComplete()) return false;
        if (input.isLeft() && gridScroll.beginDrag(x, y, input.button(), GRID_SCROLL_X, GRID_Y, 4, GRID_H, 8, 1)) return true;
        if (input.isLeft() && recipeScroll.beginDrag(x, y, input.button(), RECIPE_SCROLL_X, RECIPE_TOP, 4, RECIPE_BOTTOM - RECIPE_TOP, 8, 1)) return true;
        OutputGroup group = groupAt(x, y);
        if (group != null) {
            select(group);
            if (input.isRight()) openGroupMenu(x, y, group);
            return true;
        }
        int recipe = recipeIndexAt(x, y);
        if (recipe >= 0 && input.isLeft()) {
            if (inState(recipeRowY(recipe), x, y)) {
                flow.draft().toggleRecipe(matchingRecipes(selected).get(recipe).id());
                updateButtons();
            }
            return true;
        }
        return false;
    }

    private void openGroupMenu(double x, double y, OutputGroup group) {
        boolean removed = flow.draft().selectionForOutput(group.outputId()) == RuleImpactDraft.Selection.ALL;
        ResourceLocation id = group.outputId();
        openContextMenu(x, y, List.of(
                KineticOverlays.MenuItem.action(RecipeRemovalPage.tr("impact_show_recipes"), Component.empty(), () -> select(group)),
                KineticOverlays.MenuItem.action(RecipeRemovalPage.tr(removed ? "impact_state_keep" : "impact_state_remove"), Component.empty(),
                        () -> { flow.draft().toggleOutput(id); updateButtons(); }),
                KineticOverlays.MenuItem.create(RecipeRemovalPage.tr("copy"), Component.empty(), RecipeRemovalPage.tr("context.copy_hint"), null,
                        () -> { if (id != null) KineticClientRuntime.setClipboard(id.toString()); }, id != null,
                        KineticOverlays.MenuItemStyle.NORMAL)));
    }

    @Override protected boolean onMouseScroll(ScrollInput input) {
        double x = input.x();
        double y = input.y();
        double delta = input.deltaY();
        if (x >= GRID_X && x < SPLIT_X && y >= GRID_Y && y < GRID_Y + GRID_H) return gridScroll.scroll(delta);
        if (x >= DETAIL_X && x < DETAIL_RIGHT && y >= RECIPE_TOP && y < RECIPE_BOTTOM) return recipeScroll.scroll(delta);
        return false;
    }

    @Override protected boolean onMouseDrag(MouseDragInput input) {
        double y = input.y();
        return gridScroll.drag(y, GRID_Y, GRID_H, 8) || recipeScroll.drag(y, RECIPE_TOP, RECIPE_BOTTOM - RECIPE_TOP, 8);
    }

    @Override protected boolean onMouseRelease(MouseInput input) {
        boolean grid = gridScroll.release(input.button());
        return recipeScroll.release(input.button()) || grid;
    }
}
