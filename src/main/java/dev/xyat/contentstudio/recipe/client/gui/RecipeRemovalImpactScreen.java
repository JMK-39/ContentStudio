package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetworkClient;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalImpactFlow;
import dev.xyat.contentstudio.recipe.removal.RuleImpactDraft;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.GuiGraphics;
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
public final class RecipeRemovalImpactScreen extends KineticScreen implements RecipeNetworkClient.ImpactListener {
    private static final int ROW_TOP = 82;
    private static final int ROW_BOTTOM = 258;
    private static final int ROW_HEIGHT = 20;

    private record OutputGroup(@Nullable ResourceLocation outputId, List<RemovalCandidate> recipes) { }
    private record Row(OutputGroup group, @Nullable RemovalCandidate recipe) { }

    private final RecipeRemovalScreen parent;
    private final RemovalEntry rule;
    private final @Nullable ResourceLocation focusOutput;
    private final RemovalImpactFlow flow;
    private final boolean editingExisting;
    private final GridScrollController scroll = new GridScrollController();
    private final List<OutputGroup> groups = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final Set<ResourceLocation> expanded = new HashSet<>();
    private final Map<ResourceLocation, String> outputNames = new HashMap<>();
    private AutoCompleteBox search;
    private StateButton selectAllButton, clearButton, exactButton, rangeButton, staleButton;
    private String query = "";
    private long requestId;
    private int restartCount;
    private boolean loadFailed;
    private boolean clearStale;

    public RecipeRemovalImpactScreen(RecipeRemovalScreen parent, RemovalEntry rule,
                                     @Nullable ResourceLocation focusOutput) {
        super(RecipeRemovalScreen.tr("impact_title", rule.mode().getDisplayName()));
        setParentScreen(parent);
        this.parent = parent;
        this.rule = rule;
        this.focusOutput = focusOutput;
        List<RemovalEntry> currentRules = parent.draftRules();
        this.editingExisting = currentRules.stream().anyMatch(entry -> entry.key().equals(rule.key()));
        this.flow = new RemovalImpactFlow(rule, currentRules);
    }

    @Override protected void buildUi() {
        search = addAutoCompleteField(16, 48, 316, RecipeRemovalScreen.tr("impact_search"),
                RecipeRemovalScreen.tr("impact_search"),
                KineticAutoComplete.stringDictionary(() -> flow.isComplete()
                        ? flow.candidates().stream().map(candidate -> candidate.id().toString()).toList()
                        : List.of()), null);
        search.setMaxLength(256);
        search.setValue(query);
        search.setResponder(value -> { query = value; rebuildVisibleRows(); scroll.setOffset(0); });
        selectAllButton = addButton(340, 48, 68, RecipeRemovalScreen.tr("impact_select_all"), null,
                () -> { flow.draft().selectAll(true); updateButtons(); });
        clearButton = addButton(412, 48, 68, RecipeRemovalScreen.tr("impact_clear_all"), null,
                () -> { flow.draft().selectAll(false); updateButtons(); });
        addButton(484, 48, 72, RecipeRemovalScreen.tr("impact_reload"), null, this::startLoad);
        staleButton = addButton(80, 328, 158, RecipeRemovalScreen.tr("impact_keep_stale"), null, () -> {
            clearStale = !clearStale;
            updateButtons();
        });
        addButton(16, 328, 60, RecipeRemovalScreen.tr("back"), null, this::onClose);
        exactButton = addButton(246, 328, 172, RecipeRemovalScreen.tr("impact_exact"),
                RecipeRemovalScreen.tr("impact_exact_hint"), () -> {
                    parent.applyImpactExactRules(rule, flow.buildExactRules());
                    onClose();
                });
        rangeButton = addButton(424, 328, 200, RecipeRemovalScreen.tr("impact_range"),
                RecipeRemovalScreen.tr("impact_range_hint"), () -> {
                    parent.applyImpactRangeRule(flow.buildRangeRule(clearStale));
                    onClose();
                });
        updateButtons();
        startLoad();
    }

    private void startLoad() {
        flow.reset();
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
        if (KineticClientRuntime.currentScreen() != this || page.requestId() != requestId
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
        if (outputId == null) return RecipeRemovalScreen.tr("impact_unknown_output").getString();
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
        staleButton.setText(RecipeRemovalScreen.tr(clearStale ? "impact_clear_stale" : "impact_keep_stale"));
    }

    @Override protected void renderCanvasBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 0, 0, 640, 360);
        graphics.drawString(font, title, 16, 10, GuiTheme.current().text(), false);
        graphics.drawString(font, font.plainSubstrByWidth(rule.value(), 390), 16, 28,
                GuiTheme.current().mutedText(), false);
        if (flow.isComplete()) {
            graphics.drawString(font, RecipeRemovalScreen.tr("impact_counts", groups.size(),
                    flow.candidates().size(), flow.draft().selectedRecipeIds().size()),
                    400, 28, GuiTheme.current().text(), false);
        }
        GuiTheme.panelAlt(graphics, 14, 80, 612, 180);
        if (!flow.isComplete()) {
            graphics.drawString(font, RecipeRemovalScreen.tr(loadFailed ? "impact_load_failed" : "impact_loading",
                    flow.loadedCount(), flow.totalPages()), 24, 93, GuiTheme.current().mutedText(), false);
        } else if (flow.candidates().isEmpty()) {
            graphics.drawString(font, RecipeRemovalScreen.tr(editingExisting
                    ? "impact_no_original_existing" : "impact_no_original"), 24, 93,
                    GuiTheme.current().mutedText(), false);
        } else if (visibleRows.isEmpty()) {
            graphics.drawString(font, RecipeRemovalScreen.tr("impact_no_search_result"), 24, 93,
                    GuiTheme.current().mutedText(), false);
        }
        scroll.update(visibleRows.size(), 8);
        enableUiScissor(graphics, 16, ROW_TOP, 624, ROW_BOTTOM);
        int start = scroll.smoothIndexOffset(), shift = scroll.visualShift(ROW_HEIGHT);
        for (int i = start; i < Math.min(visibleRows.size(), start + 10); i++) {
            Row row = visibleRows.get(i);
            int y = ROW_TOP + (i - start) * ROW_HEIGHT - shift;
            if (y + ROW_HEIGHT < ROW_TOP || y >= ROW_BOTTOM) continue;
            boolean hover = mouseX >= 16 && mouseX < 620 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (row.recipe() == null) renderGroup(graphics, row.group(), y, hover);
            else renderRecipe(graphics, row.recipe(), y, hover);
        }
        disableUiScissor(graphics);
        scroll.render(graphics, mouseX, mouseY, 628, ROW_TOP, 4, ROW_BOTTOM - ROW_TOP, 8);
        graphics.drawString(font, RecipeRemovalScreen.tr("impact_scope_note"), 16, 270,
                GuiTheme.current().mutedText(), false);
        graphics.drawString(font, RecipeRemovalScreen.tr("impact_future_note"), 16, 284,
                GuiTheme.current().mutedText(), false);
        if (flow.isComplete() && !flow.staleExcludedRecipeIds().isEmpty()) {
            graphics.drawString(font, RecipeRemovalScreen.tr("impact_stale_count", flow.staleExcludedRecipeIds().size()),
                    16, 302, GuiTheme.current().mutedText(), false);
        }
    }

    private void renderGroup(GuiGraphics graphics, OutputGroup group, int y, boolean hover) {
        RuleImpactDraft.Selection selection = flow.draft().selectionForOutput(group.outputId());
        GuiTheme.stateSurface(graphics, 16, y + 1, 608, 18, GuiTheme.Surface.PANEL_ALT,
                selection == RuleImpactDraft.Selection.ALL, hover, false);
        String check = switch (selection) {
            case ALL -> "[x]";
            case PARTIAL -> "[-]";
            case NONE -> "[ ]";
        };
        graphics.drawString(font, check, 23, y + 6, GuiTheme.current().text(), false);
        graphics.drawString(font, expanded.contains(group.outputId()) ? "-" : "+", 49, y + 6,
                GuiTheme.current().mutedText(), false);
        ResourceLocation id = group.outputId();
        if (id != null && KineticRegistries.items().contains(id)) {
            ItemStack stack = new ItemStack(KineticRegistries.items().get(id));
            graphics.renderItem(stack, 62, y + 2);
        }
        String label = outputName(id) + (id == null ? "" : "  " + id);
        graphics.drawString(font, font.plainSubstrByWidth(label, 442), 84, y + 6,
                GuiTheme.current().text(), false);
        graphics.drawString(font, RecipeRemovalScreen.tr("impact_group_count", group.recipes().size()),
                545, y + 6, GuiTheme.current().mutedText(), false);
    }

    private void renderRecipe(GuiGraphics graphics, RemovalCandidate recipe, int y, boolean hover) {
        boolean selected = flow.draft().selectedRecipeIds().contains(recipe.id());
        GuiTheme.stateSurface(graphics, 16, y + 1, 608, 18, GuiTheme.Surface.PANEL_ALT, selected, hover, false);
        graphics.drawString(font, selected ? "[x]" : "[ ]", 42, y + 6, GuiTheme.current().text(), false);
        String detail = recipe.id() + (recipe.recipeType() == null ? "" : "  ·  " + recipe.recipeType());
        graphics.drawString(font, font.plainSubstrByWidth(detail, 449), 78, y + 6,
                GuiTheme.current().text(), false);
        if (!selected && !flow.otherBlockingRules(recipe.id()).isEmpty()) {
            graphics.drawString(font, RecipeRemovalScreen.tr("impact_other_blocker"), 529, y + 6,
                    GuiTheme.current().mutedText(), false);
        }
    }

    @Override protected boolean canvasMouseClicked(double x, double y, int button) {
        if (super.canvasMouseClicked(x, y, button)) return true;
        blurControl(search);
        if (!KineticMouseButtons.isPrimary(button) || !flow.isComplete()) return false;
        if (scroll.beginDrag(x, y, 628, ROW_TOP, 4, ROW_BOTTOM - ROW_TOP, 8, 1)) return true;
        if (x >= 16 && x < 624 && y >= ROW_TOP && y < ROW_BOTTOM) {
            int index = scroll.smoothIndexOffset() + (int) ((y - ROW_TOP + scroll.visualShift(ROW_HEIGHT)) / ROW_HEIGHT);
            if (index < 0 || index >= visibleRows.size()) return true;
            Row row = visibleRows.get(index);
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

    @Override protected boolean canvasMouseScrolled(double x, double y, double delta) {
        return x >= 16 && x < 634 && y >= ROW_TOP && y < ROW_BOTTOM
                ? scroll.scroll(delta) : super.canvasMouseScrolled(x, y, delta);
    }

    @Override protected boolean canvasMouseDragged(double x, double y, int button, double dx, double dy) {
        return scroll.drag(y, ROW_TOP, ROW_BOTTOM - ROW_TOP, 8) || super.canvasMouseDragged(x, y, button, dx, dy);
    }

    @Override protected boolean canvasMouseReleased(double x, double y, int button) {
        return scroll.release(button) || super.canvasMouseReleased(x, y, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}
