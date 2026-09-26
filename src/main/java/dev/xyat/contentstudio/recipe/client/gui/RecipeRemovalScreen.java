package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.widget.state.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.input.KineticAutoComplete.AutoCompleteBox;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetworkClient;
import dev.xyat.contentstudio.recipe.removal.OriginalRecipeRows;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalDisplayState;
import dev.xyat.contentstudio.recipe.removal.RemovalDraftEdits;
import dev.xyat.contentstudio.recipe.removal.RecipeSummary;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalMode;
import dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator;
import dev.xyat.contentstudio.recipe.removal.RecipeViewerCategoryFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Item-first removal: browsing never changes recipes; edits are sent only on save. */
public class RecipeRemovalScreen extends KineticScreen {
    private static final int GX = 8, GY = 32, COLS = 11, ROWS = 16, CELL = 20;
    private static final int LX = 244, LY = 58, LW = 380, LH = 180, RH = 30;
    private final List<RemovalEntry> allRemovals = new ArrayList<>();
    private List<RemovalEntry> savedRemovals;
    private final EditedEntryTracker<Item> edited = new EditedEntryTracker<>();
    private final Set<Item> errors = new HashSet<>();
    private final Set<Item> serverErrors = new HashSet<>();
    private final Set<Item> savedEditedItems = new HashSet<>();
    private final Set<Item> draftEditedItems = new HashSet<>();
    private final Map<Item, List<RecipeSummary>> catalog = new HashMap<>();
    private final OriginalRecipeRows<RecipeJeiBridge.Entry> originalRows = new OriginalRecipeRows<>();
    private final List<KineticItemSearch.CachedItem> items = new ArrayList<>();
    private final List<RecipeJeiBridge.Entry> recipes = new ArrayList<>(), visibleRecipes = new ArrayList<>();
    private final List<RemovalEntry> visibleRules = new ArrayList<>();
    private final GridScrollController itemScroll = new GridScrollController(), recipeScroll = new GridScrollController();
    private ItemStack selectedItem = ItemStack.EMPTY;
    private RecipeJeiBridge.Entry selectedRecipe;
    private RemovalEntry selectedRule;
    private AutoCompleteBox itemSearch, recipeSearch;
    private final List<KineticAutoComplete.Suggestion> itemDictionary = new ArrayList<>();
    private String itemQuery = "", recipeQuery = "";
    private StateButton viewerAllButton, previewButton, toggleButton, copyButton, saveButton, rulesButton, ruleScopeButton;
    private boolean rulesView, allRulesView, loading;
    private long pendingSaveId;
    private List<RemovalEntry> submittedRemovals = List.of();
    private List<KineticItemSearch.CachedItem> indexedItems;
    private Item pendingTypeMenuItem;
    private double pendingTypeMenuX, pendingTypeMenuY;

    public RecipeRemovalScreen(Screen parent, List<RemovalEntry> serverData, List<ItemStack> modifiedItems) {
        super(tr("title"));
        setParentScreen(parent);
        allRemovals.addAll(serverData);
        savedRemovals = List.copyOf(serverData);
        for (var stack : modifiedItems) { edited.update(stack.getItem(), true); savedEditedItems.add(stack.getItem()); }
        // JEI is a separate screen. Keep drafts local so opening JEI cannot trigger
        // GuiSession's automatic rollback when navigating to a non-core screen.
    }

    public static class SelectionEntry {
        public final String value;
        public final boolean alreadyExists;
        public boolean isSelected;
        public SelectionEntry(String value, boolean exists) {
            this.value = value;
            alreadyExists = exists; isSelected = exists;
        }
    }

    static Component tr(String key, Object... args) { return Component.translatable("gui.contentstudio.recipe.removal." + key, args); }
    public void showToast(Component message) { KineticOverlays.toast(message); }

    @Override protected void buildUi() {
        RecipeNetworkClient.registerRemovalScreen(this);
        itemSearch = addAutoCompleteField(
                8, 8, 150, tr("item_search"), tr("item_search"),
                () -> itemDictionary, null
        );
        itemSearch.setMaxLength(160); itemSearch.setValue(itemQuery);
        itemSearch.setResponder(query -> { itemQuery = query; refreshItems(); itemScroll.setOffset(0); });
        recipeSearch = addAutoCompleteField(
                244, 34, 380, tr("recipe_search"), tr("recipe_search"),
                this::recipeDictionary, null
        );
        recipeSearch.setMaxLength(256); recipeSearch.setValue(recipeQuery);
        recipeSearch.setResponder(query -> { recipeQuery = query; filterRecipes(); recipeScroll.setOffset(0); });
        button("scope_menu", "scope_menu_hint", 244, 8, 60, () -> openBulkMenu(244, 30));
        rulesButton = button("rules", "rules_button_hint", 310, 8, 60, () -> {
            rulesView = !rulesView; selectedRule = null; recipeScroll.setOffset(0); filterRecipes();
        });
        ruleScopeButton = button("all_rules", "rule_filter_hint", 508, 8, 50, () -> {
            allRulesView = !allRulesView;
            recipeScroll.setOffset(0);
            filterRecipes();
        });
        button("reset", "reset_hint", 376, 8, 60, () -> {
            allRemovals.clear(); allRemovals.addAll(savedRemovals);
            draftEditedItems.clear();
            rebuildEditedMarkers();
            refreshItems(); filterRecipes();
        });
        saveButton = button("save", "save_hint", 442, 8, 60, this::save);
        viewerAllButton = button("viewer_all", 564, 8, 60, () -> openViewerMenu(564, 30, null));
        button("back", 366, 334, 60, this::onClose);
        copyButton = button("copy", "context.copy_hint", 432, 334, 60, () -> {
            String value = selectedValue();
            if (value != null) { KineticClientRuntime.setClipboard(value); showToast(tr("copied")); }
        });
        previewButton = button("preview_one", "preview_one_hint", 498, 334, 60, () -> openRecipePreview(selectedRecipe));
        toggleButton = button("remove_one", "exact_hint", 564, 334, 60, this::toggleSelected);
        if (KineticItemSearch.ready()) refreshItems();
        else KineticItemSearch.prepare(() -> KineticClientRuntime.execute(this::refreshItems));
        filterRecipes();
    }

    private StateButton button(String key, int x, int y, int width, Runnable action) {
        return button(key, null, x, y, width, action);
    }

    private StateButton button(String key, String tooltipKey, int x, int y, int width, Runnable action) {
        return addButton(x, y, width, tr(key), tooltipKey == null ? null : tr(tooltipKey), action);
    }

    private void refreshItems() {
        boolean indexChanged = indexedItems != KineticItemSearch.items();
        indexedItems = KineticItemSearch.items(); items.clear();
        if (indexChanged) {
            Map<String, Component> dictionary = new TreeMap<>();
            Map<String, Component> localizedItems = new HashMap<>();
            for (var indexedItem : indexedItems) {
                ResourceLocation itemId = KineticResourceIds.tryParse(indexedItem.id());
                Item registryItem = itemId == null ? null : KineticRegistries.items().get(itemId);
                if (registryItem == null) continue;
                String translated = KineticSearch.resolveTranslation(registryItem.getDescriptionId());
                if (translated != null) {
                    localizedItems.put(indexedItem.id(), Component.literal(translated));
                }
            }
            for (var item : indexedItems) {
                dictionary.putIfAbsent(item.id(), localizedItems.getOrDefault(item.id(), Component.empty()));
                dictionary.putIfAbsent("@" + item.namespace(), Component.empty());
                for (String tag : item.tagIds()) dictionary.putIfAbsent("#" + tag, Component.empty());
            }
            itemDictionary.clear();
            dictionary.forEach((value, translation) -> itemDictionary.add(new KineticAutoComplete.Suggestion(value, translation)));
        }
        for (var item : indexedItems) {
            boolean matches = itemQuery.startsWith("@") ? KineticSearch.match(item.namespace(), itemQuery.substring(1))
                    : itemQuery.startsWith("#") ? item.tagIds().stream().anyMatch(tag -> KineticSearch.match(tag, itemQuery.substring(1)))
                    : KineticSearch.match(item.searchText(), itemQuery);
            if (matches) items.add(item);
        }
        var order = edited.comparator(Comparator.comparing(item -> String.valueOf(KineticRegistries.items().id(item))));
        items.sort((a, b) -> order.compare(a.stack().getItem(), b.stack().getItem()));
        itemScroll.update((items.size() + COLS - 1) / COLS, ROWS);
    }

    private void selectItem(ItemStack stack) {
        selectedItem = stack.copy(); selectedRecipe = null; rulesView = false;
        pendingTypeMenuItem = null;
        recipeQuery = ""; recipeSearch.setValue(""); recipeScroll.setOffset(0); loading = true;
        catalog.remove(selectedItem.getItem());
        rebuildRecipes();
        RecipeNetworkClient.requestItemRecipes(this, selectedItem);
    }

    public void acceptRecipes(ItemStack item, List<RecipeSummary> data, boolean dataError) {
        catalog.put(item.getItem(), List.copyOf(data));
        if (dataError) serverErrors.add(item.getItem()); else serverErrors.remove(item.getItem());
        if (!selectedItem.isEmpty() && selectedItem.is(item.getItem())) { loading = false; rebuildRecipes(); }
        if (pendingTypeMenuItem == item.getItem() && selectedItem.is(item.getItem())) {
            pendingTypeMenuItem = null;
            if (KineticClientRuntime.currentScreen() == this) {
                itemTypeMenu(pendingTypeMenuX, pendingTypeMenuY, item);
            }
        }
    }

    public void acceptAffectedOutputs(List<ResourceLocation> ids, boolean firstPage) {
        if (firstPage) savedEditedItems.clear();
        for (ResourceLocation id : ids) {
            Item item = KineticRegistries.items().get(id);
            if (item != null && id.equals(KineticRegistries.items().id(item))) savedEditedItems.add(item);
        }
        rebuildEditedMarkers();
        refreshItems();
    }

    private void rebuildEditedMarkers() {
        edited.clear();
        for (Item item : savedEditedItems) edited.update(item, true);
        for (Item item : draftEditedItems) edited.update(item, true);
    }

    private void markItemEdited(Item item) {
        draftEditedItems.add(item);
        edited.update(item, true);
    }

    private void rebuildRecipes() {
        String previous = selectedRecipe == null ? null : rowKey(selectedRecipe);
        Map<ResourceLocation, RecipeJeiBridge.Entry> originals = new LinkedHashMap<>();
        Map<String, RecipeJeiBridge.Entry> protectedRows = new LinkedHashMap<>();
        originalRows.clear();
        recipes.clear();
        var level = KineticClientRuntime.currentLevel();
        if (selectedItem.isEmpty() || level == null) { filterRecipes(); return; }
        errors.remove(selectedItem.getItem());
        if (serverErrors.contains(selectedItem.getItem())) errors.add(selectedItem.getItem());
        for (var summary : catalog.getOrDefault(selectedItem.getItem(), List.of())) {
            if (summary.id() == null || summary.type() == null || summary.output().isEmpty()) {
                errors.add(selectedItem.getItem());
                continue;
            }
            var row = new RecipeJeiBridge.Entry(summary, typeName(summary.type()), true, null, null);
            originals.put(summary.id(), row);
            originalRows.register(row, candidateOf(summary));
        }
        for (var recipe : level.getRecipeManager().getRecipes()) {
            try {
                if (!recipe.getResultItem(level.registryAccess()).is(selectedItem.getItem())) continue;
                RecipeSummary summary = RecipeSummary.of(recipe, level.registryAccess());
                var original = originals.get(summary.id());
                if (original != null && RemovalDisplayState.of(originalRows.candidateFor(original), savedRemovals)
                        .status() != RemovalDisplayState.Status.REMOVED) continue;
                addProtectedSummary(protectedRows, summary);
            } catch (RuntimeException ignored) { /* Dynamic recipes are also queried through JEI. */ }
        }
        try {
            for (var entry : RecipeJeiBridge.recipes(selectedItem)) {
                if (!RecipeViewerCategoryFilter.isRecipeCategory(entry.recipe().type())) continue;
                var original = entry.recipe().id() == null ? null : originals.get(entry.recipe().id());
                if (original != null && RemovalDisplayState.of(originalRows.candidateFor(original), savedRemovals)
                        .status() != RemovalDisplayState.Status.REMOVED) {
                    var decorated = new RecipeJeiBridge.Entry(original.recipe(), entry.category(), true,
                            entry.show(), entry.preview());
                    originals.put(original.recipe().id(), decorated);
                    originalRows.register(decorated, originalRows.candidateFor(original));
                    continue;
                }
                String key = recipeKey(entry);
                var existing = protectedRows.get(key);
                if (existing != null) entry = new RecipeJeiBridge.Entry(existing.recipe(), entry.category(), false,
                        entry.show(), entry.preview());
                else entry = new RecipeJeiBridge.Entry(entry.recipe(), entry.category(), false,
                        entry.show(), entry.preview());
                protectedRows.put(key, entry);
            }
        } catch (RuntimeException exception) {
            errors.add(selectedItem.getItem());
        }
        recipes.addAll(originals.values()); recipes.addAll(protectedRows.values());
        recipes.sort(Comparator.comparing((RecipeJeiBridge.Entry entry) -> entry.category().getString())
                .thenComparing(RecipeRemovalScreen::recipeKey)
                .thenComparing(entry -> originalRows.candidateFor(entry) == null ? 1 : 0));
        selectedRecipe = recipes.stream().filter(entry -> rowKey(entry).equals(previous)).findFirst().orElse(null);
        filterRecipes();
    }

    private void addProtectedSummary(Map<String, RecipeJeiBridge.Entry> merged, RecipeSummary summary) {
        if (summary.id() == null || summary.type() == null || summary.output().isEmpty()) { errors.add(selectedItem.getItem()); return; }
        var entry = new RecipeJeiBridge.Entry(summary, typeName(summary.type()), false, null, null);
        merged.put(recipeKey(entry), entry);
    }

    private static RemovalCandidate candidateOf(RecipeSummary summary) {
        ResourceLocation outputId = summary.output().isEmpty() ? null
                : KineticRegistries.items().id(summary.output().getItem());
        Set<ResourceLocation> tags = summary.output().isEmpty() ? Set.of()
                : summary.output().getTags().map(TagKey::location).collect(java.util.stream.Collectors.toSet());
        return new RemovalCandidate(summary.id(), summary.type(), outputId, tags);
    }

    private String rowKey(RecipeJeiBridge.Entry entry) {
        return (originalRows.candidateFor(entry) == null ? "protected:" : "original:") + recipeKey(entry);
    }

    private static String recipeKey(RecipeJeiBridge.Entry entry) {
        return entry.recipe().id() == null ? "jei:" + entry.recipe().type() + ":" + System.identityHashCode(entry.show()) : entry.recipe().id().toString();
    }

    private static Component typeName(ResourceLocation id) {
        if (id.getNamespace().equals("minecraft")) {
            String block = switch (id.getPath()) {
                case "crafting" -> "crafting_table"; case "smelting" -> "furnace"; case "blasting" -> "blast_furnace";
                case "smoking" -> "smoker"; case "stonecutting" -> "stonecutter"; case "smithing" -> "smithing_table";
                case "campfire_cooking" -> "campfire"; default -> null;
            };
            if (block != null) return Component.translatable("block.minecraft." + block);
        }
        return Component.literal(id.toString());
    }

    private void filterRecipes() {
        visibleRecipes.clear(); visibleRules.clear();
        for (var recipe : recipes) if (KineticSearch.match(recipeKey(recipe) + " " + recipe.category().getString() + " " + recipe.recipe().type(), recipeQuery)) visibleRecipes.add(recipe);
        for (var rule : allRemovals) {
            if (!allRulesView && !selectedItem.isEmpty() && !relevantToSelectedItem(rule)) continue;
            if (KineticSearch.match(rule.value() + " " + rule.mode().getDisplayName().getString(), recipeQuery)) visibleRules.add(rule);
        }
        if (!visibleRecipes.contains(selectedRecipe)) selectedRecipe = visibleRecipes.isEmpty() ? null : visibleRecipes.get(0);
        if (!visibleRules.contains(selectedRule)) selectedRule = null;
        recipeScroll.update(rulesView ? visibleRules.size() : visibleRecipes.size(), LH / RH);
        updateButtons();
    }

    private boolean relevantToSelectedItem(RemovalEntry rule) {
        if (catalog.getOrDefault(selectedItem.getItem(), List.of()).stream()
                .map(RecipeRemovalScreen::candidateOf)
                .anyMatch(candidate -> RemovalRuleEvaluator.matchesScope(rule, candidate))) return true;
        ResourceLocation output = KineticRegistries.items().id(selectedItem.getItem());
        if (rule.mode() == RemovalMode.OUTPUT && rule.value().equals(String.valueOf(output))) return true;
        return rule.mode() == RemovalMode.TAG && selectedItem.getTags()
                .anyMatch(tag -> rule.value().equals(tag.location().toString()));
    }

    private RemovalDisplayState displayState(RecipeJeiBridge.Entry entry) {
        RemovalCandidate candidate = entry == null ? null : originalRows.candidateFor(entry);
        return candidate == null ? null : RemovalDisplayState.of(candidate, allRemovals);
    }

    Component statusLabel(RecipeJeiBridge.Entry entry) {
        RemovalDisplayState state = displayState(entry);
        if (state == null) return tr("status_protected");
        RemovalDisplayState applied = RemovalDisplayState.of(originalRows.candidateFor(entry), savedRemovals);
        if (state.status() != applied.status()) return tr(switch (state.status()) {
            case ACTIVE -> "status_draft_active";
            case REMOVED -> "status_draft_removed";
            case EXCLUDED -> "status_draft_excluded";
        });
        return tr(switch (state.status()) {
            case ACTIVE -> "status_active";
            case REMOVED -> "status_removed";
            case EXCLUDED -> "status_excluded";
        });
    }

    List<Component> recipeReasonLines(RecipeJeiBridge.Entry entry) {
        RemovalDisplayState state = displayState(entry);
        if (state == null) return List.of(tr("view_only_hint"));
        List<Component> lines = new ArrayList<>();
        lines.add(statusLabel(entry));
        lines.add(tr("reason_count", state.blockingRules().size(), state.matchingRules().size()));
        for (RemovalEntry rule : state.matchingRules()) {
            lines.add(tr(state.blockingRules().contains(rule) ? "blocked_by" : "excluded_by",
                    rule.mode().getDisplayName(), rule.value()));
        }
        return List.copyOf(lines);
    }

    private int localAffected(RemovalEntry rule) {
        if (selectedItem.isEmpty()) return 0;
        return (int) catalog.getOrDefault(selectedItem.getItem(), List.of()).stream()
                .map(RecipeRemovalScreen::candidateOf)
                .filter(candidate -> RemovalRuleEvaluator.matchesScope(rule, candidate)
                        && !rule.excludedRecipeIds().contains(candidate.id())).count();
    }

    private int localExcluded(RemovalEntry rule) {
        if (selectedItem.isEmpty()) return 0;
        return (int) catalog.getOrDefault(selectedItem.getItem(), List.of()).stream()
                .map(RecipeSummary::id)
                .filter(rule.excludedRecipeIds()::contains).count();
    }

    private void updateButtons() {
        if (toggleButton == null) return;
        viewerAllButton.setEnabled(!selectedItem.isEmpty() && RecipeJeiBridge.available());
        registerWidgetTooltip(viewerAllButton, tr(RecipeJeiBridge.available() ? "viewer_all_hint" : "viewer_missing"));
        previewButton.setEnabled(!rulesView && selectedRecipe != null);
        registerWidgetTooltip(previewButton, tr("preview_one_hint"));
        copyButton.setEnabled(selectedValue() != null);
        saveButton.setEnabled(pendingSaveId == 0L && !new HashSet<>(allRemovals).equals(new HashSet<>(savedRemovals)));
        rulesButton.setText(tr(rulesView ? "recipes" : "rules"));
        ruleScopeButton.setEnabled(rulesView && !selectedItem.isEmpty());
        ruleScopeButton.setText(tr(allRulesView ? "related_rules" : "all_rules"));
        registerWidgetTooltip(ruleScopeButton, tr("rule_filter_hint"));
        if (rulesView) {
            toggleButton.setText(tr("restore_rule")); toggleButton.setEnabled(selectedRule != null);
            registerWidgetTooltip(toggleButton, tr("restore_rule_hint"));
        } else {
            RemovalDisplayState state = displayState(selectedRecipe);
            toggleButton.setText(tr(state == null ? "view_only"
                    : state.status() == RemovalDisplayState.Status.REMOVED ? "restore_one" : "remove_one"));
            toggleButton.setEnabled(state != null);
            registerWidgetTooltip(toggleButton, tr(state == null ? "view_only_hint" : "exact_hint"));
        }
    }

    private String selectedValue() {
        if (rulesView) return selectedRule == null ? null : selectedRule.value();
        return selectedRecipe == null || selectedRecipe.recipe().id() == null ? null : selectedRecipe.recipe().id().toString();
    }

    private void toggleSelected() {
        if (rulesView) { if (selectedRule != null) removeEntryDirectly(selectedRule.mode(), selectedRule.value()); }
        else if (selectedRecipe != null) toggleRecipe(selectedRecipe);
    }

    public void addEntryFromSelection(RemovalMode mode, String value) {
        var entry = new RemovalEntry(mode, value, "");
        if (allRemovals.stream().noneMatch(rule -> rule.key().equals(entry.key()))) { allRemovals.add(0, entry); markEdited(entry); }
    }
    public void removeEntryDirectly(RemovalMode mode, String value) {
        var entry = new RemovalEntry(mode, value, "");
        if (allRemovals.removeIf(rule -> rule.key().equals(entry.key()))) markEdited(entry);
    }

    private void markEdited(RemovalEntry entry) {
        for (var item : KineticItemSearch.items()) {
            boolean changed = entry.mode() == RemovalMode.OUTPUT && entry.value().equals(item.id())
                    || entry.mode() == RemovalMode.TAG && item.tagIds().contains(entry.value())
                    || catalog.getOrDefault(item.stack().getItem(), List.of()).stream()
                    .map(RecipeRemovalScreen::candidateOf)
                    .anyMatch(candidate -> RemovalRuleEvaluator.matchesScope(entry, candidate));
            if (changed) markItemEdited(item.stack().getItem());
        }
        if (!selectedItem.isEmpty() && catalog.getOrDefault(selectedItem.getItem(), List.of()).stream()
                .map(RecipeRemovalScreen::candidateOf)
                .anyMatch(candidate -> RemovalRuleEvaluator.matchesScope(entry, candidate))) {
            markItemEdited(selectedItem.getItem());
        }
        refreshItems(); itemScroll.setOffset(0); filterRecipes();
    }

    void save() {
        if (pendingSaveId != 0L) return;
        submittedRemovals = List.copyOf(allRemovals);
        try {
            pendingSaveId = RecipeNetwork.sendRemovalDraft(submittedRemovals);
        } catch (IllegalArgumentException exception) {
            showToast(tr("save_too_large"));
            return;
        }
        showToast(Component.translatable("gui.contentstudio.recipe.recipehud.msg.saving_apply")); updateButtons();
    }

    public void acceptSaveResult(long requestId, RecipeNetwork.SaveStatus status, List<RemovalEntry> serverData) {
        if (requestId != pendingSaveId || pendingSaveId == 0L) return;
        pendingSaveId = 0L;
        if (status == RecipeNetwork.SaveStatus.APPLIED) {
            savedRemovals = List.copyOf(serverData);
            if (allRemovals.equals(submittedRemovals)) {
                allRemovals.clear();
                allRemovals.addAll(serverData);
                draftEditedItems.clear();
                rebuildEditedMarkers();
            }
            catalog.clear();
            originalRows.clear();
            if (!selectedItem.isEmpty()) {
                loading = true;
                rebuildRecipes();
                RecipeNetworkClient.requestItemRecipes(this, selectedItem);
            }
            showToast(Component.translatable("gui.contentstudio.recipe.recipehud.msg.removals_saved_applied"));
        } else if (status == RecipeNetwork.SaveStatus.PERSISTED_RELOAD_FAILED) {
            showToast(tr("save_persisted_reload_failed"));
        } else {
            showToast(Component.translatable("gui.contentstudio.recipe.recipehud.err.save_failed_plain"));
        }
        submittedRemovals = List.of();
        filterRecipes();
        updateButtons();
    }

    private void openViewerMenu(double x, double y, RecipeJeiBridge.Entry entry) {
        List<RecipeJeiBridge.Viewer> viewers = RecipeJeiBridge.availableViewers();
        if (viewers.isEmpty()) {
            showToast(tr("viewer_missing"));
            return;
        }
        if (viewers.size() == 1) {
            openViewer(viewers.get(0), entry);
            return;
        }
        itemSearch.clearSuggestions();
        recipeSearch.clearSuggestions();
        clearControlFocus();
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (RecipeJeiBridge.Viewer viewer : viewers) {
            items.add(KineticOverlays.MenuItem.action(
                    viewer.displayName(),
                    tr("viewer_choice_hint", viewer.displayName()),
                    () -> openViewer(viewer, entry)
            ));
        }
        openContextMenu(x, y, items);
    }

    private void openViewer(RecipeJeiBridge.Viewer viewer, RecipeJeiBridge.Entry entry) {
        ItemStack output = entry == null ? selectedItem : entry.recipe().output();
        if (!RecipeJeiBridge.show(viewer, output, entry)) {
            if (!selectedItem.isEmpty()) errors.add(selectedItem.getItem());
            showToast(tr("viewer_failed", viewer.displayName()));
        }
    }

    private void openBulkMenu(double x, double y) {
        itemSearch.clearSuggestions();
        recipeSearch.clearSuggestions();
        clearControlFocus();
        openContextMenu(x, y, List.of(
                KineticOverlays.MenuItem.action(tr("by_mod"), tr("context.by_mod_hint"), () -> bulkOptions(RemovalMode.MOD)),
                KineticOverlays.MenuItem.action(tr("by_output"), tr("context.by_output_hint"), () -> bulkOptions(RemovalMode.OUTPUT)),
                KineticOverlays.MenuItem.action(tr("by_tag"), tr("context.by_tag_hint"), () -> bulkOptions(RemovalMode.TAG)),
                KineticOverlays.MenuItem.action(tr("by_type"), tr("context.by_type_hint"), () -> bulkOptions(RemovalMode.TYPE))));
    }

    private void bulkOptions(RemovalMode mode) {
        Set<String> values = new TreeSet<>();
        if (mode == RemovalMode.TYPE) {
            KineticRegistries.recipeTypes().ids().forEach(id -> values.add(id.toString()));
        } else if (mode == RemovalMode.MOD) {
            KineticRegistries.items().ids().forEach(id -> values.add(id.getNamespace()));
            var level = KineticClientRuntime.currentLevel();
            if (level != null) level.getRecipeManager().getRecipes().forEach(recipe -> values.add(recipe.getId().getNamespace()));
        } else if (mode == RemovalMode.OUTPUT) {
            KineticRegistries.items().ids().forEach(id -> values.add(id.toString()));
        } else if (mode == RemovalMode.TAG) {
            for (var item : KineticItemSearch.items()) values.addAll(item.tagIds());
        }
        allRemovals.stream().filter(rule -> rule.mode() == mode).forEach(rule -> values.add(rule.value()));
        List<SelectionEntry> options = values.stream().map(value -> new SelectionEntry(value,
                allRemovals.stream().anyMatch(rule -> rule.key().equals(new RemovalEntry.Key(mode, value))))).toList();
        String initial = mode == RemovalMode.MOD && itemQuery.startsWith("@") || mode == RemovalMode.TAG && itemQuery.startsWith("#")
                ? itemQuery.substring(1) : "";
        KineticClientRuntime.openScreen(new RecipeRemovalSelectionScreen(this, mode, options, initial));
    }

    private List<KineticAutoComplete.Suggestion> recipeDictionary() {
        Map<String, Component> values = new TreeMap<>();
        boolean showTranslations = !KineticClientRuntime.isEnglishLanguage();
        for (var entry : recipes) {
            if (entry.recipe().id() != null) {
                String id = entry.recipe().id().toString();
                values.putIfAbsent(id, Component.empty());
            }
            String type = entry.recipe().type().toString();
            Component typeTranslation = typeName(entry.recipe().type());
            String translatedType = typeTranslation.getString();
            values.putIfAbsent(type, showTranslations && !translatedType.isBlank() && !translatedType.equals(type)
                    ? typeTranslation
                    : Component.empty());
        }
        if (rulesView) for (var rule : allRemovals) values.putIfAbsent(rule.value(), Component.empty());
        List<KineticAutoComplete.Suggestion> result = new ArrayList<>(values.size());
        values.forEach((value, translation) -> result.add(new KineticAutoComplete.Suggestion(value, translation)));
        return List.copyOf(result);
    }

    private void openRecipePreview(RecipeJeiBridge.Entry entry) {
        if (entry != null) KineticClientRuntime.openScreen(new RecipeRemovalPreviewScreen(this, entry));
    }

    void markRecipeError() { if (!selectedItem.isEmpty()) errors.add(selectedItem.getItem()); }

    boolean canToggle(RecipeJeiBridge.Entry entry) {
        return displayState(entry) != null;
    }

    Component recipeAction(RecipeJeiBridge.Entry entry) {
        RemovalDisplayState state = displayState(entry);
        return tr(state == null ? "view_only"
                : state.status() == RemovalDisplayState.Status.REMOVED ? "restore_one" : "remove_one");
    }

    void toggleRecipe(RecipeJeiBridge.Entry entry) {
        RemovalCandidate candidate = originalRows.candidateFor(entry);
        if (candidate == null) return;
        RemovalDisplayState state = RemovalDisplayState.of(candidate, allRemovals);
        replaceDraftRules(state.status() == RemovalDisplayState.Status.REMOVED
                ? RemovalDisplayState.restore(candidate, allRemovals)
                : RemovalDisplayState.remove(candidate, allRemovals));
    }

    public List<RemovalEntry> draftRules() {
        return List.copyOf(allRemovals);
    }

    public void applyImpactExactRules(RemovalEntry sourceRange, List<RemovalEntry> rules) {
        replaceDraftRules(RemovalDraftEdits.replaceRangeWithExact(allRemovals, sourceRange, rules));
    }

    public void applyImpactRangeRule(RemovalEntry rule) {
        if (rule.mode() == RemovalMode.RECIPE_ID) return;
        List<RemovalEntry> result = new ArrayList<>(allRemovals);
        result.removeIf(existing -> existing.key().equals(rule.key()));
        result.add(0, rule);
        replaceDraftRules(result);
    }

    public void openImpactEditor(RemovalMode mode, String value, ResourceLocation focusOutput) {
        if (mode == RemovalMode.RECIPE_ID) return;
        RemovalEntry rule = allRemovals.stream()
                .filter(entry -> entry.key().equals(new RemovalEntry.Key(mode, value)))
                .findFirst().orElse(new RemovalEntry(mode, value, ""));
        KineticClientRuntime.openScreen(new RecipeRemovalImpactScreen(this, rule, focusOutput));
    }

    private void replaceDraftRules(List<RemovalEntry> rules) {
        allRemovals.clear();
        allRemovals.addAll(rules);
        selectedRule = allRemovals.stream().filter(rule -> selectedRule != null
                && rule.key().equals(selectedRule.key())).findFirst().orElse(null);
        if (!selectedItem.isEmpty()) markItemEdited(selectedItem.getItem());
        refreshItems();
        filterRecipes();
    }

    private void itemMenu(double x, double y, ItemStack item) {
        if (!ItemStack.isSameItemSameTags(selectedItem, item)) selectItem(item);
        itemSearch.clearSuggestions();
        recipeSearch.clearSuggestions();
        clearControlFocus();
        openContextMenu(x, y, List.of(
                KineticOverlays.MenuItem.create(
                        tr("open_viewer"), Component.empty(), tr("context.open_viewer_hint"), null,
                        () -> openViewerMenu(x, y, null), RecipeJeiBridge.available(), KineticOverlays.MenuItemStyle.NORMAL),
                KineticOverlays.MenuItem.action(
                        tr("remove_output"), tr("context.remove_output_hint"),
                        () -> openImpactEditor(RemovalMode.OUTPUT,
                                String.valueOf(KineticRegistries.items().id(item.getItem())),
                                KineticRegistries.items().id(item.getItem()))),
                KineticOverlays.MenuItem.action(tr("by_mod"), tr("context.by_mod_hint"), () -> bulkOptions(RemovalMode.MOD)),
                KineticOverlays.MenuItem.action(tr("by_tag"), tr("context.by_tag_hint"), () -> bulkOptions(RemovalMode.TAG)),
                KineticOverlays.MenuItem.action(tr("by_type"), tr("context.by_type_hint"),
                        () -> itemTypeMenu(x, y, item))));
    }

    private void itemTypeMenu(double x, double y, ItemStack item) {
        if (!catalog.containsKey(item.getItem())) {
            pendingTypeMenuItem = item.getItem();
            pendingTypeMenuX = x;
            pendingTypeMenuY = y;
            return;
        }
        ResourceLocation output = KineticRegistries.items().id(item.getItem());
        Set<ResourceLocation> types = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        catalog.getOrDefault(item.getItem(), List.of()).stream().map(RecipeSummary::type).forEach(types::add);
        if (types.isEmpty()) {
            showToast(tr("no_item_types"));
            return;
        }
        openContextMenu(x, y, types.stream()
                .map(type -> KineticOverlays.MenuItem.action(typeName(type),
                        tr("context.by_type_hint"),
                        () -> openImpactEditor(RemovalMode.TYPE, type.toString(), output)))
                .toList());
    }

    private void recipeMenu(double x, double y, RecipeJeiBridge.Entry entry) {
        itemSearch.clearSuggestions();
        recipeSearch.clearSuggestions();
        clearControlFocus();
        boolean canToggle = canToggle(entry);
        boolean hasType = originalRows.candidateFor(entry) != null
                && KineticRegistries.recipeTypes().contains(entry.recipe().type());
        boolean hasId = entry.recipe().id() != null;
        openContextMenu(x, y, List.of(
                KineticOverlays.MenuItem.create(
                        tr("open_viewer"), Component.empty(), tr("context.open_viewer_recipe_hint"), null,
                        () -> openViewerMenu(x, y, entry), RecipeJeiBridge.available(), KineticOverlays.MenuItemStyle.NORMAL),
                KineticOverlays.MenuItem.create(
                        recipeAction(entry), Component.empty(), tr("context.toggle_recipe_hint"), null,
                        () -> toggleRecipe(entry), canToggle, KineticOverlays.MenuItemStyle.NORMAL),
                KineticOverlays.MenuItem.create(
                        tr("remove_type"), Component.empty(), tr("context.remove_type_hint"), null,
                        () -> openImpactEditor(RemovalMode.TYPE, entry.recipe().type().toString(),
                                KineticRegistries.items().id(entry.recipe().output().getItem())),
                        hasType, KineticOverlays.MenuItemStyle.NORMAL),
                KineticOverlays.MenuItem.create(
                        tr("copy"), Component.empty(), tr("context.copy_hint"), null,
                        () -> KineticClientRuntime.setClipboard(entry.recipe().id().toString()), hasId, KineticOverlays.MenuItemStyle.NORMAL)));
    }

    @Override protected void canvasTick() {
        if (KineticItemSearch.ready() && indexedItems != KineticItemSearch.items()) refreshItems();
        updateButtons();
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void renderCanvasBackground(GuiGraphics g, int mx, int my, float partialTick) {
        GuiTheme.panel(g, 0, 0, 640, 360);
        text(g, tr("item_count", items.size()), 162, 12, 68, GuiTheme.current().mutedText());
        GuiTheme.panelAlt(g, 6, 30, 232, 324);
        itemScroll.update((items.size() + COLS - 1) / COLS, ROWS);
        enableUiScissor(g, GX, GY, GX + COLS * CELL, GY + ROWS * CELL);
        int start = itemScroll.smoothIndexOffset() * COLS, shift = itemScroll.visualShift(CELL);
        for (int i = start; i < Math.min(items.size(), start + (ROWS + 1) * COLS); i++) {
            ItemStack stack = items.get(i).stack();
            int x = GX + (i - start) % COLS * CELL, y = GY + (i - start) / COLS * CELL - shift;
            boolean hover = mx >= x && mx < x + CELL && my >= y && my < y + CELL && my >= GY && my < GY + ROWS * CELL;
            boolean selected = !selectedItem.isEmpty() && ItemStack.isSameItemSameTags(stack, selectedItem);
            GuiTheme.itemSlot(g, x, y, CELL, hover);
            drawItem(g, stack, x + 2, y + 2);
            boolean error = errors.contains(stack.getItem());
            if (selected || hover || error) {
                GuiTheme.stateOutline(g, x, y, CELL, CELL, selected, hover, error);
            } else if (edited.isEdited(stack.getItem())) {
                GuiTheme.indicatorOutline(g, x, y, CELL, CELL, GuiTheme.Indicator.SUCCESS);
            }
        }
        disableUiScissor(g); scrollbar(g, itemScroll, mx, my, 232, GY, ROWS * CELL);
        renderRecipeList(g, mx, my); renderPreview(g);
    }

    private void renderRecipeList(GuiGraphics g, int mx, int my) {
        GuiTheme.panelAlt(g, LX, LY, LW, LH);
        int count = rulesView ? visibleRules.size() : visibleRecipes.size();
        recipeScroll.update(count, LH / RH);
        enableUiScissor(g, LX, LY, LX + LW, LY + LH);
        int start = recipeScroll.smoothIndexOffset(), shift = recipeScroll.visualShift(RH);
        for (int i = start; i < Math.min(count, start + LH / RH + 1); i++) {
            int y = LY + (i - start) * RH - shift;
            boolean selected = rulesView ? visibleRules.get(i).equals(selectedRule) : visibleRecipes.get(i).equals(selectedRecipe);
            boolean hovered = mx >= LX && mx < LX + LW && my >= y && my < y + RH;
            if (selected || hovered) {
                GuiTheme.stateOutline(g, LX, y + 1, LW, RH - 2, selected, hovered, false);
            } else if (rulesView) {
                GuiTheme.indicatorOutline(g, LX, y + 1, LW, RH - 2, GuiTheme.Indicator.DANGER);
            } else {
                RemovalDisplayState state = displayState(visibleRecipes.get(i));
                GuiTheme.indicatorOutline(
                        g,
                        LX,
                        y + 1,
                        LW,
                        RH - 2,
                        state == null ? GuiTheme.Indicator.WARNING
                                : state.status() == RemovalDisplayState.Status.REMOVED
                                ? GuiTheme.Indicator.DANGER : GuiTheme.Indicator.SUCCESS
                );
            }
            if (rulesView) {
                var rule = visibleRules.get(i);
                text(g, tr("rule_row_title", rule.mode().getDisplayName(), rule.value()),
                        LX + 6, y + 5, LW - 12, GuiTheme.current().text());
                text(g, tr("rule_row_counts", localAffected(rule), localExcluded(rule),
                        rule.excludedRecipeIds().size()),
                        LX + 6, y + 17, LW - 12, GuiTheme.current().mutedText());
            } else {
                var entry = visibleRecipes.get(i);
                text(g, entry.recipe().id() == null ? tr("no_id") : Component.literal(entry.recipe().id().toString()), LX + 6, y + 5, LW - 12, GuiTheme.current().text());
                text(g, tr("recipe_row_detail", entry.category(), statusLabel(entry)),
                        LX + 6, y + 17, LW - 12, GuiTheme.current().mutedText());
            }
        }
        if (count == 0) text(g, tr(loading && !rulesView ? "loading" : "empty"), LX + 8, LY + 12, LW - 16, GuiTheme.current().mutedText());
        disableUiScissor(g); scrollbar(g, recipeScroll, mx, my, 628, LY, LH);
    }

    private void renderPreview(GuiGraphics g) {
        GuiTheme.panelAlt(g, 244, 244, 380, 78);
        if (rulesView) {
            text(g, tr("rules_hint"), 252, 252, 364, GuiTheme.current().mutedText());
            if (selectedRule != null) {
                text(g, tr("rule_row_title", selectedRule.mode().getDisplayName(), selectedRule.value()),
                        252, 270, 364, GuiTheme.current().text());
                text(g, tr("rule_row_counts", localAffected(selectedRule), localExcluded(selectedRule),
                        selectedRule.excludedRecipeIds().size()),
                        252, 288, 364, GuiTheme.current().mutedText());
                text(g, tr("rule_edit_hint"), 252, 306, 364, GuiTheme.current().mutedText());
            }
            return;
        }
        if (selectedRecipe == null) { text(g, tr("choose_recipe"), 252, 254, 364, GuiTheme.current().mutedText()); return; }
        var summary = selectedRecipe.recipe();
        int columns = summary.craftingWidth() > 0 ? Math.min(9, summary.craftingWidth()) : 9;
        int limit = Math.min(summary.inputs().size(), columns * 3);
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < limit; i++) {
            int x = 252 + i % columns * 18, y = 252 + i / columns * 18;
            GuiTheme.itemSlot(g, x, y, 18, false);
            try {
                var alternatives = summary.inputs().get(i).getItems();
                if (alternatives.length > 0) drawItem(g, alternatives[cycle % alternatives.length], x + 1, y + 1);
            } catch (RuntimeException exception) { errors.add(selectedItem.getItem()); }
        }
        if (summary.inputs().isEmpty()) text(g, tr("jei_details"), 252, 258, 200, GuiTheme.current().mutedText());
        if (summary.inputs().size() > limit) text(g, tr("more_inputs"), 252, 310, 225, GuiTheme.current().mutedText());
        g.drawString(font, "→", 462, 270, GuiTheme.current().text(), false);
        GuiTheme.itemSlot(g, 482, 264, 20, false);
        drawItem(g, summary.output(), 484, 266);
        g.renderItemDecorations(font, summary.output(), 484, 266);
        text(g, tr("recipe_count", visibleRecipes.size()), 512, 252, 104, GuiTheme.current().mutedText());
        text(g, statusLabel(selectedRecipe), 512, 270, 104, GuiTheme.current().mutedText());
        RemovalDisplayState state = displayState(selectedRecipe);
        if (state != null) text(g, tr("reason_count", state.blockingRules().size(), state.matchingRules().size()),
                512, 288, 104, GuiTheme.current().mutedText());
        if (errors.contains(selectedItem.getItem())) text(g, tr("data_error"), 512, 306, 104, GuiTheme.current().danger());
    }

    private void drawItem(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.hasTag() && stack.getTag().getBoolean("contentstudio_invalid_placeholder")) errors.add(stack.getItem());
        try { g.renderItem(stack, x, y); }
        catch (RuntimeException exception) {
            errors.add(stack.getItem()); g.drawString(font, "!", x + 5, y + 4, GuiTheme.current().danger(), false);
        }
    }
    private void text(GuiGraphics g, Component text, int x, int y, int width, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, color, false);
    }
    private void scrollbar(GuiGraphics g, GridScrollController scroll, int mx, int my, int x, int y, int height) {
        GuiTheme.scrollbar(scroll, g, mx, my, x, y, 4, height, 16);
    }
    private ItemStack hoveredItem(double mx, double my) {
        if (mx < GX || mx >= GX + COLS * CELL || my < GY || my >= GY + ROWS * CELL) return ItemStack.EMPTY;
        int index = ((int) ((my - GY + itemScroll.visualShift(CELL)) / CELL) + itemScroll.smoothIndexOffset()) * COLS + (int) (mx - GX) / CELL;
        return index >= 0 && index < items.size() ? items.get(index).stack() : ItemStack.EMPTY;
    }
    private int hoveredRecipe(double my) { return recipeScroll.smoothIndexOffset() + (int) ((my - LY + recipeScroll.visualShift(RH)) / RH); }

    private ItemStack hoveredPreviewStack(double mx, double my) {
        if (rulesView || selectedRecipe == null) return ItemStack.EMPTY;
        RecipeSummary summary = selectedRecipe.recipe();
        int columns = summary.craftingWidth() > 0 ? Math.min(9, summary.craftingWidth()) : 9;
        int limit = Math.min(summary.inputs().size(), columns * 3);
        int cycle = (int) ((System.currentTimeMillis() / 1000) % Integer.MAX_VALUE);
        for (int i = 0; i < limit; i++) {
            int x = 252 + i % columns * 18, y = 252 + i / columns * 18;
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                try {
                    ItemStack[] alternatives = summary.inputs().get(i).getItems();
                    if (alternatives.length > 0) return alternatives[cycle % alternatives.length];
                } catch (RuntimeException ignored) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (mx >= 482 && mx < 502 && my >= 264 && my < 284) return summary.output();
        return ItemStack.EMPTY;
    }

    @Override protected void renderTooltips(GuiGraphics g, int mx, int my, int rawX, int rawY) {
        if (isControlFocused(itemSearch) || isControlFocused(recipeSearch)) return;
        ItemStack hovered = hoveredItem(mx, my);
        if (!hovered.isEmpty()) {
            if (errors.contains(hovered.getItem())) KineticOverlays.requestTooltip(List.of(hovered.getHoverName(), tr("data_error")), 260, rawX, rawY);
            else KineticOverlays.requestItemTooltip(hovered, rawX, rawY);
            return;
        }
        ItemStack previewHovered = hoveredPreviewStack(mx, my);
        if (!previewHovered.isEmpty()) {
            KineticOverlays.requestItemTooltip(previewHovered, rawX, rawY);
            return;
        }
        if (mx >= LX && mx < LX + LW && my >= LY && my < LY + LH) {
            int i = hoveredRecipe(my);
            if (rulesView && i < visibleRules.size()) {
                RemovalEntry rule = visibleRules.get(i);
                KineticOverlays.requestTooltip(List.of(
                        tr("rule_row_title", rule.mode().getDisplayName(), rule.value()),
                        tr("rule_row_counts", localAffected(rule), localExcluded(rule), rule.excludedRecipeIds().size()),
                        tr("rule_edit_hint")), 320, rawX, rawY);
            } else if (!rulesView && i < visibleRecipes.size()) {
                var recipeEntry = visibleRecipes.get(i);
                List<Component> lines = new ArrayList<>();
                RemovalDisplayState state = displayState(recipeEntry);
                lines.add(recipeEntry.recipe().id() == null ? tr("no_id") : Component.literal(recipeEntry.recipe().id().toString()));
                lines.add(recipeEntry.category());
                lines.add(Component.literal(recipeEntry.recipe().type().toString()));
                lines.add(statusLabel(recipeEntry));
                if (state != null) {
                    lines.add(tr("reason_count", state.blockingRules().size(), state.matchingRules().size()));
                    for (var rule : state.matchingRules()) {
                        lines.add(tr(state.blockingRules().contains(rule) ? "blocked_by" : "excluded_by",
                                rule.mode().getDisplayName(), rule.value()));
                    }
                } else lines.add(tr("view_only_hint"));
                KineticOverlays.requestTooltip(lines, 300, rawX, rawY);
            }
        }
    }

    @Override protected boolean canvasMouseClicked(double mx, double my, int button) {
        if (super.canvasMouseClicked(mx, my, button)) return true;
        if (!KineticMouseButtons.isPrimary(button) && !KineticMouseButtons.isSecondary(button)) return false;
        itemSearch.clearSuggestions();
        recipeSearch.clearSuggestions();
        clearControlFocus();
        if (KineticMouseButtons.isPrimary(button) && (itemScroll.beginDrag(mx, my, 232, GY, 4, ROWS * CELL, 16, 1) || recipeScroll.beginDrag(mx, my, 628, LY, 4, LH, 16, 1))) return true;
        ItemStack item = hoveredItem(mx, my);
        if (!item.isEmpty()) {
            if (KineticMouseButtons.isSecondary(button)) itemMenu(mx, my, item); else selectItem(item);
            return true;
        }
        if (mx >= LX && mx < LX + LW && my >= LY && my < LY + LH) {
            int i = hoveredRecipe(my);
            if (rulesView && i < visibleRules.size()) {
                selectedRule = visibleRules.get(i);
                if (KineticMouseButtons.isSecondary(button))
                    openImpactEditor(selectedRule.mode(), selectedRule.value(),
                            selectedItem.isEmpty() ? null : KineticRegistries.items().id(selectedItem.getItem()));
            }
            else if (!rulesView && i < visibleRecipes.size()) {
                selectedRecipe = visibleRecipes.get(i);
                if (KineticMouseButtons.isSecondary(button)) recipeMenu(mx, my, selectedRecipe);
            }
            updateButtons();
            return true;
        }
        return false;
    }

    @Override protected boolean canvasMouseScrolled(double mx, double my, double delta) {
        if (mx >= 6 && mx < 240 && my >= GY && my < GY + ROWS * CELL) return itemScroll.scroll(delta);
        if (mx >= LX && mx < 634 && my >= LY && my < LY + LH) return recipeScroll.scroll(delta);
        return super.canvasMouseScrolled(mx, my, delta);
    }

    @Override protected boolean canvasMouseDragged(double mx, double my, int button, double dx, double dy) {
        return itemScroll.drag(my, GY, ROWS * CELL, 16) || recipeScroll.drag(my, LY, LH, 16) || super.canvasMouseDragged(mx, my, button, dx, dy);
    }

    @Override protected boolean canvasMouseReleased(double mx, double my, int button) {
        boolean handled = itemScroll.release(button) | recipeScroll.release(button);
        return handled || super.canvasMouseReleased(mx, my, button);
    }

}
