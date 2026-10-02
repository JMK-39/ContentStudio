package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.search.KineticSuggestion;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetworkClient;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalImpactFlow;
import dev.xyat.contentstudio.recipe.removal.RuleImpactDraft;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One original-recipe impact preview for MOD, OUTPUT, TAG, and TYPE rules. */
public final class RecipeRemovalImpactPage extends KineticPage implements RecipeNetworkClient.ImpactListener {
    private static final int ROW_TOP = 82;
    private static final int ROW_BOTTOM = 258;
    private static final int ROW_HEIGHT = 20;

    private record OutputGroup(@Nullable ResourceLocation outputId, List<RemovalCandidate> recipes) { }
    private record Row(OutputGroup group, @Nullable RemovalCandidate recipe) { }

    private final RecipeRemovalPage parent;
    private final RemovalEntry rule;
    private final @Nullable ResourceLocation focusOutput;
    private final RemovalImpactFlow flow;
    private final boolean editingExisting;
    private final KineticScrollController scroll = new KineticScrollController();
    private final List<OutputGroup> groups = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final Set<ResourceLocation> expanded = new HashSet<>();
    private final Map<ResourceLocation, String> outputNames = new HashMap<>();
    private KineticAutoCompleteField search;
    private KineticButton selectAllButton, clearButton, exactButton, rangeButton, staleButton;
    private String query = "";
    // 勾选是多选而非单一选中：中键跳转目标为最近点击的行（按行值跟踪，展开/搜索会重建列表），重新加载时清除
    // The checkboxes are multi-select, not a single selection: the middle-click target is the last clicked row (tracked
    // by value because expanding/searching rebuilds the list), cleared on reload.
    private @Nullable Row lastClickedRow;
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
        scroll.bindSelection(() -> lastClickedRow == null ? -1 : visibleRows.indexOf(lastClickedRow));
    }

    @Override protected void build(KineticUi ui) {
        search = ui().autoComplete(16, 48, 316, KineticSuggestion.fromStrings(() -> flow.isComplete()
                        ? flow.candidates().stream().map(candidate -> candidate.id().toString()).toList()
                        : List.of())).label(RecipeRemovalPage.tr("impact_search")).placeholder(RecipeRemovalPage.tr("impact_search")).build();
        search.limitTextLength(256);
        search.setTextValue(query);
        search.onTextChange(value -> { query = value; rebuildVisibleRows(); scroll.setOffset(0); });
        selectAllButton = ui().button(340, 48, 68).text(RecipeRemovalPage.tr("impact_select_all")).onClick(() -> { flow.draft().selectAll(true); updateButtons(); }).build();
        clearButton = ui().button(412, 48, 68).text(RecipeRemovalPage.tr("impact_clear_all")).onClick(() -> { flow.draft().selectAll(false); updateButtons(); }).build();
        ui().button(484, 48, 72).text(RecipeRemovalPage.tr("impact_reload")).onClick(this::startLoad).build();
        staleButton = ui().button(80, 328, 158).text(RecipeRemovalPage.tr("impact_keep_stale")).onClick(() -> {
            clearStale = !clearStale;
            updateButtons();
        }).build();
        ui().button(16, 328, 60).text(RecipeRemovalPage.tr("back")).onClick(this::close).build();
        exactButton = ui().button(246, 328, 172).text(RecipeRemovalPage.tr("impact_exact")).tooltip(RecipeRemovalPage.tr("impact_exact_hint")).onClick(() -> {
                    parent.applyImpactExactRules(rule, flow.buildExactRules());
                    close();
                }).build();
        rangeButton = ui().button(424, 328, 200).text(RecipeRemovalPage.tr("impact_range")).tooltip(RecipeRemovalPage.tr("impact_range_hint")).onClick(() -> {
                    parent.applyImpactRangeRule(flow.buildRangeRule(clearStale));
                    close();
                }).build();
        updateButtons();
        startLoad();
    }

    private void startLoad() {
        flow.reset();
        lastClickedRow = null;
        groups.clear();
        visibleRows.clear();
        outputNames.clear();
        expanded.clear();
        clearStale = false;
        restartCount = 0;
        loadFailed = false;
        scroll.setOffset(0);
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
            rebuildVisibleRows();
            if (focusOutput != null) {
                expanded.add(focusOutput);
                rebuildVisibleRows();
                for (int i = 0; i < visibleRows.size(); i++) {
                    Row row = visibleRows.get(i);
                    if (row.recipe() == null && focusOutput.equals(row.group().outputId())) {
                        scroll.setOffset(i);
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
        groups.sort(Comparator.comparing(group -> group.outputId() == null ? "\uffff" : group.outputId().toString()));
    }

    private String outputName(@Nullable ResourceLocation outputId) {
        if (outputId == null) return RecipeRemovalPage.tr("impact_unknown_output").getString();
        return outputNames.computeIfAbsent(outputId, id -> {
            if (!KineticRegistries.items().contains(id)) return id.toString();
            Item item = KineticRegistries.items().get(id);
            return new ItemStack(item).getHoverName().getString();
        });
    }

    private void rebuildVisibleRows() {
        visibleRows.clear();
        if (!flow.isComplete()) return;
        String value = query.trim();
        for (OutputGroup group : groups) {
            boolean groupMatch = value.isEmpty() || KineticSearch.match(outputName(group.outputId()), value)
                    || group.outputId() != null && KineticSearch.match(group.outputId().toString(), value);
            List<RemovalCandidate> matching = value.isEmpty() ? group.recipes()
                    : group.recipes().stream().filter(recipe -> groupMatch
                    || KineticSearch.match(recipe.id().toString(), value)
                    || recipe.recipeType() != null && KineticSearch.match(recipe.recipeType().toString(), value)).toList();
            if (!groupMatch && matching.isEmpty()) continue;
            visibleRows.add(new Row(group, null));
            if (expanded.contains(group.outputId()) || !value.isEmpty()) {
                for (RemovalCandidate recipe : matching) visibleRows.add(new Row(group, recipe));
            }
        }
        scroll.update(visibleRows.size(), 8);
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

    @Override protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 0, 0, 640, 360);
        graphics.text(title(), 16, 10, KineticTheme.current().text(), false);
        graphics.text(KineticText.trim(rule.value(), 390), 16, 28, KineticTheme.current().mutedText(), false);
        if (flow.isComplete()) {
            graphics.text(RecipeRemovalPage.tr("impact_counts", groups.size(),
                    flow.candidates().size(), flow.draft().selectedRecipeIds().size()), 400, 28, KineticTheme.current().text(), false);
        }
        KineticTheme.panelAlt(graphics, 14, 80, 612, 180);
        if (!flow.isComplete()) {
            graphics.text(RecipeRemovalPage.tr(loadFailed ? "impact_load_failed" : "impact_loading",
                    flow.loadedCount(), flow.totalPages()), 24, 93, KineticTheme.current().mutedText(), false);
        } else if (flow.candidates().isEmpty()) {
            graphics.text(RecipeRemovalPage.tr(editingExisting
                    ? "impact_no_original_existing" : "impact_no_original"), 24, 93, KineticTheme.current().mutedText(), false);
        } else if (visibleRows.isEmpty()) {
            graphics.text(RecipeRemovalPage.tr("impact_no_search_result"), 24, 93, KineticTheme.current().mutedText(), false);
        }
        scroll.update(visibleRows.size(), 8);
        graphics.scissor(16, ROW_TOP, 624, ROW_BOTTOM);
        int start = scroll.smoothIndexOffset(), shift = scroll.visualShift(ROW_HEIGHT);
        for (int i = start; i < Math.min(visibleRows.size(), start + 10); i++) {
            Row row = visibleRows.get(i);
            int y = ROW_TOP + (i - start) * ROW_HEIGHT - shift;
            if (y + ROW_HEIGHT < ROW_TOP || y >= ROW_BOTTOM) continue;
            boolean hover = mouseX >= 16 && mouseX < 620 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (row.recipe() == null) renderGroup(graphics, row.group(), y, hover);
            else renderRecipe(graphics, row.recipe(), y, hover);
            scroll.renderSelectionFlash(graphics, i, 16, y + 1, 608, 18);
        }
        graphics.endScissor();
        scroll.render(graphics, mouseX, mouseY, 628, ROW_TOP, 4, ROW_BOTTOM - ROW_TOP, 8);
        graphics.text(RecipeRemovalPage.tr("impact_scope_note"), 16, 270, KineticTheme.current().mutedText(), false);
        graphics.text(RecipeRemovalPage.tr("impact_future_note"), 16, 284, KineticTheme.current().mutedText(), false);
        if (flow.isComplete() && !flow.staleExcludedRecipeIds().isEmpty()) {
            graphics.text(RecipeRemovalPage.tr("impact_stale_count", flow.staleExcludedRecipeIds().size()), 16, 302, KineticTheme.current().mutedText(), false);
        }
    }

    private void renderGroup(KineticGraphics graphics, OutputGroup group, int y, boolean hover) {
        RuleImpactDraft.Selection selection = flow.draft().selectionForOutput(group.outputId());
        KineticTheme.stateSurface(graphics, 16, y + 1, 608, 18, KineticTheme.Surface.PANEL_ALT,
                selection == RuleImpactDraft.Selection.ALL, hover, false);
        String check = switch (selection) {
            case ALL -> "[x]";
            case PARTIAL -> "[-]";
            case NONE -> "[ ]";
        };
        graphics.text(check, 23, y + 6, KineticTheme.current().text(), false);
        graphics.text(expanded.contains(group.outputId()) ? "-" : "+", 49, y + 6, KineticTheme.current().mutedText(), false);
        ResourceLocation id = group.outputId();
        if (id != null && KineticRegistries.items().contains(id)) {
            ItemStack stack = new ItemStack(KineticRegistries.items().get(id));
            graphics.item(stack, 62, y + 2);
        }
        String label = outputName(id) + (id == null ? "" : "  " + id);
        graphics.text(KineticText.trim(label, 442), 84, y + 6, KineticTheme.current().text(), false);
        graphics.text(RecipeRemovalPage.tr("impact_group_count", group.recipes().size()), 545, y + 6, KineticTheme.current().mutedText(), false);
    }

    private void renderRecipe(KineticGraphics graphics, RemovalCandidate recipe, int y, boolean hover) {
        boolean selected = flow.draft().selectedRecipeIds().contains(recipe.id());
        KineticTheme.stateSurface(graphics, 16, y + 1, 608, 18, KineticTheme.Surface.PANEL_ALT, selected, hover, false);
        graphics.text(selected ? "[x]" : "[ ]", 42, y + 6, KineticTheme.current().text(), false);
        String detail = recipe.id() + (recipe.recipeType() == null ? "" : "  ·  " + recipe.recipeType());
        graphics.text(KineticText.trim(detail, 449), 78, y + 6, KineticTheme.current().text(), false);
        if (!selected && !flow.otherBlockingRules(recipe.id()).isEmpty()) {
            graphics.text(RecipeRemovalPage.tr("impact_other_blocker"), 529, y + 6, KineticTheme.current().mutedText(), false);
        }
    }

    @Override protected boolean onMouseClick(MouseInput input) {
        // 原逻辑在控件之后运行 / The old logic ran after controls.
        double x = input.x();
        double y = input.y();
        blur(search);
        if (!input.isLeft() || !flow.isComplete()) return false;
        if (scroll.beginDrag(x, y, input.button(), 628, ROW_TOP, 4, ROW_BOTTOM - ROW_TOP, 8, 1)) return true;
        if (x >= 16 && x < 624 && y >= ROW_TOP && y < ROW_BOTTOM) {
            int index = scroll.smoothIndexOffset() + (int) ((y - ROW_TOP + scroll.visualShift(ROW_HEIGHT)) / ROW_HEIGHT);
            if (index < 0 || index >= visibleRows.size()) return true;
            Row row = visibleRows.get(index);
            lastClickedRow = row;
            if (row.recipe() != null) flow.draft().toggleRecipe(row.recipe().id());
            else if (x < 43) flow.draft().toggleOutput(row.group().outputId());
            else {
                if (!expanded.add(row.group().outputId())) expanded.remove(row.group().outputId());
                rebuildVisibleRows();
            }
            updateButtons();
            return true;
        }
        return false;
    }

    @Override protected boolean onMouseScroll(ScrollInput input) {
        double x = input.x();
        double y = input.y();
        double delta = input.deltaY();
        return x >= 16 && x < 634 && y >= ROW_TOP && y < ROW_BOTTOM
                && scroll.scroll(delta);
    }

    @Override protected boolean onMouseDrag(MouseDragInput input) {
        double y = input.y();
        return scroll.drag(y, ROW_TOP, ROW_BOTTOM - ROW_TOP, 8);
    }

    @Override protected boolean onMouseRelease(MouseInput input) {
        return scroll.release(input.button());
    }

}
