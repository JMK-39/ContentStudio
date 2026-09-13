package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.text.KineticText;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.GuiSession;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBox;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBoxGroup;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetworkClient;
import dev.xyat.contentstudio.recipe.removal.RecipeSummary;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** Item-first removal: browsing never changes recipes; edits are sent only on save. */
public class RecipeRemovalScreen extends KineticScreen {
    private static final int GREEN = 0xFF55DD77, YELLOW = 0xFFFFD740, RED = 0xFFFF5555, BLUE = 0xFF55AAFF;
    private static final int GX = 8, GY = 32, COLS = 11, ROWS = 16, CELL = 20;
    private static final int LX = 244, LY = 104, LW = 380, LH = 120, RH = 30;
    private final List<RemovalEntry> allRemovals = new ArrayList<>();
    private List<RemovalEntry> savedRemovals;
    private final KineticSearch.EditedTracker<Item> edited = new KineticSearch.EditedTracker<>();
    private final Set<Item> errors = new HashSet<>();
    private final Set<Item> serverErrors = new HashSet<>();
    private final Set<Item> savedEditedItems = new HashSet<>();
    private final Map<Item, List<RecipeSummary>> catalog = new HashMap<>();
    private final List<ItemSearchIndex.CachedItem> items = new ArrayList<>();
    private final List<RecipeJeiBridge.Entry> recipes = new ArrayList<>(), visibleRecipes = new ArrayList<>();
    private final List<RemovalEntry> visibleRules = new ArrayList<>();
    private final GridScrollController itemScroll = new GridScrollController(), recipeScroll = new GridScrollController();
    private ItemStack selectedItem = ItemStack.EMPTY;
    private RecipeJeiBridge.Entry selectedRecipe;
    private RemovalEntry selectedRule;
    private AutoCompleteBox itemSearch, recipeSearch;
    private final AutoCompleteBoxGroup searchInputs = new AutoCompleteBoxGroup();
    private final List<String> itemDictionary = new ArrayList<>();
    private String itemQuery = "", recipeQuery = "";
    private Button viewerAllButton, previewButton, toggleButton, copyButton, saveButton, rulesButton;
    private boolean rulesView, loading;
    private List<ItemSearchIndex.CachedItem> indexedItems;

    public RecipeRemovalScreen(Screen parent, List<RemovalEntry> serverData, List<ItemStack> modifiedItems) {
        super(tr("title"));
        allRemovals.addAll(serverData);
        savedRemovals = List.copyOf(serverData);
        for (var stack : modifiedItems) { edited.update(stack.getItem(), true); savedEditedItems.add(stack.getItem()); }
        GuiSession.setParent(this, parent);
        useStandardCanvas();
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
    public void showToast(Component message) { GuiOverlay.toast(message); }

    @Override protected void buildUi() {
        itemSearch = addAutoCompleteField(8, 8, 110, tr("item_search"), () -> itemDictionary, null);
        itemSearch.setMaxLength(160); itemSearch.setValue(itemQuery);
        itemSearch.setResponder(query -> { itemQuery = query; refreshItems(); itemScroll.setOffset(0); });
        recipeSearch = addAutoCompleteField(244, 80, 380, tr("recipe_search"), this::recipeDictionary, null);
        recipeSearch.setMaxLength(256); recipeSearch.setValue(recipeQuery);
        recipeSearch.setResponder(query -> { recipeQuery = query; filterRecipes(); recipeScroll.setOffset(0); });
        searchInputs.set(itemSearch, recipeSearch);
        button("scope_menu", "scope_menu_hint", 244, 54, 60, () -> openBulkMenu(244, 76));
        rulesButton = button("rules", "rules_button_hint", 310, 54, 60, () -> {
            rulesView = !rulesView; selectedRule = null; recipeScroll.setOffset(0); filterRecipes();
        });
        button("reset", "reset_hint", 376, 54, 60, () -> {
            allRemovals.clear(); allRemovals.addAll(savedRemovals); edited.clear();
            for (var item : savedEditedItems) edited.update(item, true);
            refreshItems(); filterRecipes();
        });
        saveButton = button("save", "save_hint", 442, 54, 60, this::save);
        viewerAllButton = button("viewer_all", 564, 8, 60, () -> openViewerMenu(564, 30, null));
        button("back", 366, 334, 60, this::onClose);
        copyButton = button("copy", "context.copy_hint", 432, 334, 60, () -> {
            String value = selectedValue();
            if (value != null) { minecraft.keyboardHandler.setClipboard(value); showToast(tr("copied")); }
        });
        previewButton = button("preview_one", "preview_one_hint", 498, 334, 60, () -> openRecipePreview(selectedRecipe));
        toggleButton = button("remove_one", "exact_hint", 564, 334, 60, this::toggleSelected);
        if (ItemSearchIndex.isReady()) refreshItems();
        else ItemSearchIndex.prepareCache(() -> { if (minecraft != null) minecraft.execute(this::refreshItems); });
        filterRecipes();
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return button(key, null, x, y, width, action);
    }

    private Button button(String key, String tooltipKey, int x, int y, int width, Runnable action) {
        return addButton(
                x, y, width, tr(key), tooltipKey == null ? null : tr(tooltipKey),
                ignored -> action.run()
        );
    }

    private void refreshItems() {
        boolean indexChanged = indexedItems != ItemSearchIndex.getItems();
        indexedItems = ItemSearchIndex.getItems(); items.clear();
        if (indexChanged) {
            Set<String> dictionary = new TreeSet<>();
            for (var item : indexedItems) {
                dictionary.add(item.idStr + " - " + item.displayName);
                dictionary.add("@" + item.namespace);
                for (String tag : item.tagIds) dictionary.add("#" + tag);
            }
            itemDictionary.clear(); itemDictionary.addAll(dictionary);
        }
        for (var item : indexedItems) {
            boolean matches = itemQuery.startsWith("@") ? KineticSearch.match(item.namespace, itemQuery.substring(1))
                    : itemQuery.startsWith("#") ? item.tagIds.stream().anyMatch(tag -> KineticSearch.match(tag, itemQuery.substring(1)))
                    : KineticSearch.match(item.searchData, itemQuery);
            if (matches) items.add(item);
        }
        var order = edited.comparator(Comparator.comparing(item -> String.valueOf(ForgeRegistries.ITEMS.getKey(item))));
        items.sort((a, b) -> order.compare(a.stack.getItem(), b.stack.getItem()));
        itemScroll.update((items.size() + COLS - 1) / COLS, ROWS);
    }

    private void selectItem(ItemStack stack) {
        selectedItem = stack.copy(); selectedRecipe = null; rulesView = false;
        recipeQuery = ""; recipeSearch.setValue(""); recipeScroll.setOffset(0); loading = true;
        rebuildRecipes();
        RecipeNetworkClient.requestItemRecipes(this, selectedItem);
    }

    public void acceptRecipes(ItemStack item, List<RecipeSummary> data, boolean dataError) {
        catalog.put(item.getItem(), List.copyOf(data));
        if (dataError) serverErrors.add(item.getItem()); else serverErrors.remove(item.getItem());
        if (!selectedItem.isEmpty() && selectedItem.is(item.getItem())) { loading = false; rebuildRecipes(); }
    }

    private void rebuildRecipes() {
        String previous = selectedRecipe == null ? null : recipeKey(selectedRecipe);
        Map<String, RecipeJeiBridge.Entry> merged = new LinkedHashMap<>();
        if (selectedItem.isEmpty() || minecraft == null || minecraft.level == null) return;
        errors.remove(selectedItem.getItem());
        if (serverErrors.contains(selectedItem.getItem())) errors.add(selectedItem.getItem());
        for (var recipe : minecraft.level.getRecipeManager().getRecipes()) {
            try {
                if (recipe.getResultItem(minecraft.level.registryAccess()).is(selectedItem.getItem()))
                    addSummary(merged, RecipeSummary.of(recipe, minecraft.level.registryAccess()));
            } catch (RuntimeException ignored) { /* Dynamic recipes are also queried through JEI. */ }
        }
        for (var summary : catalog.getOrDefault(selectedItem.getItem(), List.of())) addSummary(merged, summary);
        try {
            for (var entry : RecipeJeiBridge.recipes(selectedItem)) {
                String key = recipeKey(entry);
                var existing = merged.get(key);
                if (existing != null) entry = new RecipeJeiBridge.Entry(existing.recipe(), entry.category(), true, entry.show(), entry.preview());
                merged.put(key, entry);
            }
        } catch (RuntimeException exception) {
            errors.add(selectedItem.getItem());
        }
        recipes.clear(); recipes.addAll(merged.values());
        recipes.sort(Comparator.comparing((RecipeJeiBridge.Entry entry) -> entry.category().getString()).thenComparing(RecipeRemovalScreen::recipeKey));
        selectedRecipe = recipes.stream().filter(entry -> recipeKey(entry).equals(previous)).findFirst().orElse(null);
        filterRecipes();
    }

    private void addSummary(Map<String, RecipeJeiBridge.Entry> merged, RecipeSummary summary) {
        if (summary.id() == null || summary.type() == null || summary.output().isEmpty()) { errors.add(selectedItem.getItem()); return; }
        var entry = new RecipeJeiBridge.Entry(summary, typeName(summary.type()), true, null, null);
        merged.put(recipeKey(entry), entry);
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
        for (var rule : allRemovals) if (KineticSearch.match(rule.value() + " " + rule.mode().getDisplayName().getString(), recipeQuery)) visibleRules.add(rule);
        if (!visibleRecipes.contains(selectedRecipe)) selectedRecipe = visibleRecipes.isEmpty() ? null : visibleRecipes.get(0);
        if (!visibleRules.contains(selectedRule)) selectedRule = null;
        recipeScroll.update(rulesView ? visibleRules.size() : visibleRecipes.size(), LH / RH);
        updateButtons();
    }

    private List<RemovalEntry> blockers(RecipeJeiBridge.Entry entry) { return allRemovals.stream().filter(rule -> matches(rule, entry.recipe())).toList(); }

    private static boolean matches(RemovalEntry rule, RecipeSummary recipe) {
        return switch (rule.mode()) {
            case RECIPE_ID -> recipe.id() != null && rule.value().equals(recipe.id().toString());
            case TYPE -> rule.value().equals(recipe.type().toString());
            case MOD -> recipe.id() != null && rule.value().equals(recipe.id().getNamespace());
            case OUTPUT -> rule.value().equals(String.valueOf(ForgeRegistries.ITEMS.getKey(recipe.output().getItem())));
            case TAG -> {
                ResourceLocation id = ResourceLocation.tryParse(rule.value().replaceFirst("^#", ""));
                yield id != null && recipe.output().is(TagKey.create(Registries.ITEM, id));
            }
        };
    }

    private void updateButtons() {
        if (toggleButton == null) return;
        viewerAllButton.active = !selectedItem.isEmpty() && RecipeJeiBridge.available();
        registerWidgetTooltip(viewerAllButton, tr(RecipeJeiBridge.available() ? "viewer_all_hint" : "viewer_missing"));
        previewButton.active = !rulesView && selectedRecipe != null;
        registerWidgetTooltip(previewButton, tr("preview_one_hint"));
        copyButton.active = selectedValue() != null;
        saveButton.active = !new HashSet<>(allRemovals).equals(new HashSet<>(savedRemovals));
        rulesButton.setMessage(tr(rulesView ? "recipes" : "rules"));
        if (rulesView) {
            toggleButton.setMessage(tr("restore_rule")); toggleButton.active = selectedRule != null;
            registerWidgetTooltip(toggleButton, tr("restore_rule_hint"));
        } else {
            List<RemovalEntry> blocked = selectedRecipe == null ? List.of() : blockers(selectedRecipe);
            boolean broad = blocked.stream().anyMatch(rule -> rule.mode() != RemovalMode.RECIPE_ID);
            toggleButton.setMessage(tr(broad ? "blocked" : blocked.isEmpty() ? "remove_one" : "restore_one"));
            toggleButton.active = selectedRecipe != null && selectedRecipe.removable() && !broad;
            registerWidgetTooltip(toggleButton, tr(broad ? "blocked_hint" : "exact_hint"));
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
        if (!allRemovals.contains(entry)) { allRemovals.add(0, entry); markEdited(entry); }
    }
    public void removeEntryDirectly(RemovalMode mode, String value) {
        var entry = new RemovalEntry(mode, value, "");
        if (allRemovals.remove(entry)) markEdited(entry);
    }

    private void markEdited(RemovalEntry entry) {
        for (var item : ItemSearchIndex.getItems()) {
            boolean changed = entry.mode() == RemovalMode.OUTPUT && entry.value().equals(item.idStr)
                    || entry.mode() == RemovalMode.TAG && item.tagIds.contains(entry.value())
                    || catalog.getOrDefault(item.stack.getItem(), List.of()).stream().anyMatch(recipe -> matches(entry, recipe));
            if (changed) edited.update(item.stack.getItem(), true);
        }
        if (!selectedItem.isEmpty() && recipes.stream().anyMatch(recipe -> matches(entry, recipe.recipe()))) edited.update(selectedItem.getItem(), true);
        refreshItems(); itemScroll.setOffset(0); filterRecipes();
    }

    void save() {
        for (var entry : savedRemovals) if (!allRemovals.contains(entry)) RecipeNetwork.sendRemove(entry);
        for (var entry : allRemovals) if (!savedRemovals.contains(entry)) RecipeNetwork.sendAdd(entry);
        RecipeNetwork.sendSaveRemovalRequest(); savedRemovals = List.copyOf(allRemovals);
        for (var item : ItemSearchIndex.getItems()) if (edited.isEdited(item.stack.getItem())) savedEditedItems.add(item.stack.getItem());
        showToast(Component.translatable("gui.contentstudio.recipe.recipehud.msg.saving_apply")); updateButtons();
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
        List<GuiOverlay.MenuItem> items = new ArrayList<>();
        for (RecipeJeiBridge.Viewer viewer : viewers) {
            items.add(GuiOverlay.MenuItem.action(
                    viewer.displayName(),
                    tr("viewer_choice_hint", viewer.displayName()),
                    () -> openViewer(viewer, entry)
            ));
        }
        itemSearch.setFocused(false);
        recipeSearch.setFocused(false);
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
        itemSearch.setFocused(false);
        recipeSearch.setFocused(false);
        openContextMenu(x, y, List.of(
                GuiOverlay.MenuItem.action(tr("by_mod"), tr("context.by_mod_hint"), () -> bulkOptions(RemovalMode.MOD)),
                GuiOverlay.MenuItem.action(tr("by_tag"), tr("context.by_tag_hint"), () -> bulkOptions(RemovalMode.TAG)),
                GuiOverlay.MenuItem.action(tr("by_type"), tr("context.by_type_hint"), () -> bulkOptions(RemovalMode.TYPE))
        ));
    }

    private void bulkOptions(RemovalMode mode) {
        Set<String> values = new TreeSet<>();
        if (mode == RemovalMode.TYPE) {
            ForgeRegistries.RECIPE_TYPES.getKeys().forEach(id -> values.add(id.toString()));
        } else if (mode == RemovalMode.MOD) {
            ForgeRegistries.ITEMS.getKeys().forEach(id -> values.add(id.getNamespace()));
            if (minecraft.level != null) minecraft.level.getRecipeManager().getRecipes().forEach(recipe -> values.add(recipe.getId().getNamespace()));
        } else if (mode == RemovalMode.TAG) {
            for (var item : ItemSearchIndex.getItems()) values.addAll(item.tagIds);
        }
        allRemovals.stream().filter(rule -> rule.mode() == mode).forEach(rule -> values.add(rule.value()));
        List<SelectionEntry> options = values.stream().map(value -> new SelectionEntry(value,
                allRemovals.contains(new RemovalEntry(mode, value, "")))).toList();
        String initial = mode == RemovalMode.MOD && itemQuery.startsWith("@") || mode == RemovalMode.TAG && itemQuery.startsWith("#")
                ? itemQuery.substring(1) : "";
        minecraft.setScreen(new RecipeRemovalSelectionScreen(this, mode, options, initial));
    }

    private List<String> recipeDictionary() {
        Set<String> values = new TreeSet<>();
        for (var entry : recipes) {
            if (entry.recipe().id() != null) values.add(entry.recipe().id() + " - " + entry.category().getString());
            values.add(entry.recipe().type().toString());
            values.add(entry.category().getString());
        }
        if (rulesView) for (var rule : allRemovals) values.add(rule.value());
        return List.copyOf(values);
    }

    private void openRecipePreview(RecipeJeiBridge.Entry entry) {
        if (entry != null) minecraft.setScreen(new RecipeRemovalPreviewScreen(this, entry));
    }

    void markRecipeError() { if (!selectedItem.isEmpty()) errors.add(selectedItem.getItem()); }

    boolean canToggle(RecipeJeiBridge.Entry entry) {
        return entry.removable() && blockers(entry).stream().noneMatch(rule -> rule.mode() != RemovalMode.RECIPE_ID);
    }

    Component recipeAction(RecipeJeiBridge.Entry entry) {
        var blocked = blockers(entry);
        return tr(blocked.stream().anyMatch(rule -> rule.mode() != RemovalMode.RECIPE_ID) ? "blocked"
                : blocked.isEmpty() ? "remove_one" : "restore_one");
    }

    void toggleRecipe(RecipeJeiBridge.Entry entry) {
        if (!canToggle(entry)) return;
        String id = entry.recipe().id().toString();
        if (blockers(entry).isEmpty()) addEntryFromSelection(RemovalMode.RECIPE_ID, id);
        else removeEntryDirectly(RemovalMode.RECIPE_ID, id);
    }

    private void itemMenu(double x, double y, ItemStack item) {
        if (!ItemStack.isSameItemSameTags(selectedItem, item)) selectItem(item);
        List<GuiOverlay.MenuItem> items = new ArrayList<>();
        items.add(RecipeJeiBridge.available()
                ? GuiOverlay.MenuItem.action(tr("open_viewer"), tr("context.open_viewer_hint"), () -> openViewerMenu(x, y, null))
                : GuiOverlay.MenuItem.disabled(tr("open_viewer"), tr("context.open_viewer_hint")));
        items.add(GuiOverlay.MenuItem.action(
                tr("remove_output"), tr("context.remove_output_hint"),
                () -> addEntryFromSelection(RemovalMode.OUTPUT, String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem())))
        ));
        items.add(GuiOverlay.MenuItem.action(tr("by_mod"), tr("context.by_mod_hint"), () -> bulkOptions(RemovalMode.MOD)));
        items.add(GuiOverlay.MenuItem.action(tr("by_tag"), tr("context.by_tag_hint"), () -> bulkOptions(RemovalMode.TAG)));
        items.add(GuiOverlay.MenuItem.action(tr("by_type"), tr("context.by_type_hint"), () -> bulkOptions(RemovalMode.TYPE)));
        itemSearch.setFocused(false);
        recipeSearch.setFocused(false);
        openContextMenu(x, y, items);
    }

    private void recipeMenu(double x, double y, RecipeJeiBridge.Entry entry) {
        List<GuiOverlay.MenuItem> items = new ArrayList<>();
        items.add(RecipeJeiBridge.available()
                ? GuiOverlay.MenuItem.action(tr("open_viewer"), tr("context.open_viewer_recipe_hint"), () -> openViewerMenu(x, y, entry))
                : GuiOverlay.MenuItem.disabled(tr("open_viewer"), tr("context.open_viewer_recipe_hint")));
        items.add(canToggle(entry)
                ? GuiOverlay.MenuItem.action(recipeAction(entry), tr("context.toggle_recipe_hint"), () -> toggleRecipe(entry))
                : GuiOverlay.MenuItem.disabled(recipeAction(entry), tr("context.toggle_recipe_hint")));
        boolean knownType = ForgeRegistries.RECIPE_TYPES.containsKey(entry.recipe().type());
        items.add(knownType
                ? GuiOverlay.MenuItem.action(tr("remove_type"), tr("context.remove_type_hint"),
                        () -> addEntryFromSelection(RemovalMode.TYPE, entry.recipe().type().toString()))
                : GuiOverlay.MenuItem.disabled(tr("remove_type"), tr("context.remove_type_hint")));
        items.add(entry.recipe().id() != null
                ? GuiOverlay.MenuItem.action(tr("copy"), tr("context.copy_hint"),
                        () -> minecraft.keyboardHandler.setClipboard(entry.recipe().id().toString()))
                : GuiOverlay.MenuItem.disabled(tr("copy"), tr("context.copy_hint")));
        itemSearch.setFocused(false);
        recipeSearch.setFocused(false);
        openContextMenu(x, y, items);
    }

    @Override public void tick() {
        super.tick();
        if (ItemSearchIndex.isReady() && indexedItems != ItemSearchIndex.getItems()) refreshItems();
        updateButtons();
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void renderCanvasBackground(GuiGraphics g, int mx, int my, float partialTick) {
        GuiTheme.panel(g, 0, 0, 640, 360);
        text(g, tr("item_count", items.size()), 122, 12, 108, GuiTheme.current().mutedText());
        GuiTheme.panelAlt(g, 6, 30, 232, 324);
        itemScroll.update((items.size() + COLS - 1) / COLS, ROWS);
        enableCanvasScissor(g, GX, GY, GX + COLS * CELL, GY + ROWS * CELL);
        int start = itemScroll.smoothIndexOffset() * COLS, shift = itemScroll.visualShift(CELL);
        for (int i = start; i < Math.min(items.size(), start + (ROWS + 1) * COLS); i++) {
            ItemStack stack = items.get(i).stack;
            int x = GX + (i - start) % COLS * CELL, y = GY + (i - start) / COLS * CELL - shift;
            boolean hover = mx >= x && mx < x + CELL && my >= y && my < y + CELL && my >= GY && my < GY + ROWS * CELL;
            boolean selected = !selectedItem.isEmpty() && ItemStack.isSameItemSameTags(stack, selectedItem);
            GuiTheme.itemSlot(g, x, y, CELL, hover);
            drawItem(g, stack, x + 2, y + 2);
            int status = selected ? YELLOW : hover ? BLUE : errors.contains(stack.getItem()) ? RED
                    : edited.isEdited(stack.getItem()) ? GREEN : 0;
            if (status != 0) g.renderOutline(x, y, CELL, CELL, status);
        }
        disableCanvasScissor(g); scrollbar(g, itemScroll, mx, my, 232, GY, ROWS * CELL);
        if (!selectedItem.isEmpty()) {
            GuiTheme.itemSlot(g, 246, 8, 20, false);
            drawItem(g, selectedItem, 248, 10);
            text(g, selectedItem.getHoverName(), 272, 8, 252, GuiTheme.current().text());
            text(g, Component.literal(String.valueOf(ForgeRegistries.ITEMS.getKey(selectedItem.getItem()))), 272, 22, 252, GuiTheme.current().mutedText());
        } else text(g, tr("choose_item"), 244, 14, 280, GuiTheme.current().mutedText());
        renderRecipeList(g, mx, my); renderPreview(g);
    }

    private void renderRecipeList(GuiGraphics g, int mx, int my) {
        GuiTheme.panelAlt(g, LX, LY, LW, LH);
        int count = rulesView ? visibleRules.size() : visibleRecipes.size();
        recipeScroll.update(count, LH / RH);
        enableCanvasScissor(g, LX, LY, LX + LW, LY + LH);
        int start = recipeScroll.smoothIndexOffset(), shift = recipeScroll.visualShift(RH);
        for (int i = start; i < Math.min(count, start + LH / RH + 1); i++) {
            int y = LY + (i - start) * RH - shift;
            boolean selected = rulesView ? visibleRules.get(i).equals(selectedRule) : visibleRecipes.get(i).equals(selectedRecipe);
            boolean hovered = mx >= LX && mx < LX + LW && my >= y && my < y + RH;
            int borderColor;
            if (selected) borderColor = YELLOW;
            else if (hovered) borderColor = BLUE;
            else if (rulesView) borderColor = RED;
            else borderColor = blockers(visibleRecipes.get(i)).isEmpty() ? GREEN : RED;
            g.renderOutline(LX, y + 1, LW, RH - 2, borderColor);
            if (rulesView) {
                var rule = visibleRules.get(i);
                text(g, Component.literal(rule.value()), LX + 6, y + 5, LW - 12, GuiTheme.current().text());
                text(g, rule.mode().getDisplayName(), LX + 6, y + 17, LW - 12, GuiTheme.current().mutedText());
            } else {
                var entry = visibleRecipes.get(i);
                text(g, entry.recipe().id() == null ? tr("no_id") : Component.literal(entry.recipe().id().toString()), LX + 6, y + 5, LW - 12, GuiTheme.current().text());
                text(g, entry.category(), LX + 6, y + 17, LW - 12, GuiTheme.current().mutedText());
            }
        }
        if (count == 0) text(g, tr(loading && !rulesView ? "loading" : "empty"), LX + 8, LY + 12, LW - 16, GuiTheme.current().mutedText());
        disableCanvasScissor(g); scrollbar(g, recipeScroll, mx, my, 628, LY, LH);
    }

    private void renderPreview(GuiGraphics g) {
        GuiTheme.panelAlt(g, 244, 244, 380, 78);
        if (rulesView) {
            text(g, tr("rules_hint"), 252, 254, 364, GuiTheme.current().mutedText());
            if (selectedRule != null) text(g, Component.literal(selectedRule.value()), 252, 272, 364, GuiTheme.current().text());
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
        text(g, tr("exact_hint_short"), 512, 270, 104, GuiTheme.current().mutedText());
        if (errors.contains(selectedItem.getItem())) text(g, tr("data_error"), 512, 288, 104, RED);
    }

    private void drawItem(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.hasTag() && stack.getTag().getBoolean("contentstudio_invalid_placeholder")) errors.add(stack.getItem());
        try { g.renderItem(stack, x, y); }
        catch (RuntimeException exception) {
            errors.add(stack.getItem()); g.drawString(font, "!", x + 5, y + 4, RED, false);
        }
    }
    private void text(GuiGraphics g, Component text, int x, int y, int width, int color) {
        KineticText.drawScrollingLeft(g, font, text, x, y, width, color, false);
    }
    private void scrollbar(GuiGraphics g, GridScrollController scroll, int mx, int my, int x, int y, int height) {
        GuiTheme.scrollbar(scroll, g, mx, my, x, y, 4, height, 16);
    }
    private ItemStack hoveredItem(double mx, double my) {
        if (mx < GX || mx >= GX + COLS * CELL || my < GY || my >= GY + ROWS * CELL) return ItemStack.EMPTY;
        int index = ((int) ((my - GY + itemScroll.visualShift(CELL)) / CELL) + itemScroll.smoothIndexOffset()) * COLS + (int) (mx - GX) / CELL;
        return index >= 0 && index < items.size() ? items.get(index).stack : ItemStack.EMPTY;
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
        if (itemSearch.isFocused() || recipeSearch.isFocused()) return;
        ItemStack hovered = hoveredItem(mx, my);
        if (!hovered.isEmpty()) {
            if (errors.contains(hovered.getItem())) showTooltip(List.of(hovered.getHoverName(), tr("data_error")), 260);
            else showItemTooltip(hovered);
            return;
        }
        ItemStack previewHovered = hoveredPreviewStack(mx, my);
        if (!previewHovered.isEmpty()) {
            showItemTooltip(previewHovered);
            return;
        }
        if (mx >= LX && mx < LX + LW && my >= LY && my < LY + LH) {
            int i = hoveredRecipe(my);
            if (rulesView && i < visibleRules.size()) {
                showTooltip(Component.literal(visibleRules.get(i).value()), 260);
            } else if (!rulesView && i < visibleRecipes.size()) {
                var recipeEntry = visibleRecipes.get(i);
                List<Component> lines = new ArrayList<>();
                boolean removed = !blockers(recipeEntry).isEmpty();
                lines.add(recipeEntry.recipe().id() == null ? tr("no_id") : Component.literal(recipeEntry.recipe().id().toString()));
                lines.add(recipeEntry.category());
                lines.add(Component.literal(recipeEntry.recipe().type().toString()));
                lines.add(tr(removed ? "status_disabled" : "status_valid"));
                lines.add(tr("border_hint"));
                for (var rule : blockers(recipeEntry)) lines.add(tr("blocked_by", rule.mode().getDisplayName(), rule.value()));
                if (!recipeEntry.removable()) lines.add(tr("view_only_hint"));
                showTooltip(lines, 300);
            }
        }
    }

    @Override protected boolean canvasMouseClicked(double mx, double my, int button) {
        if (button == 0 && searchInputs.handleSuggestionClick(mx, my)) return true;
        if (super.canvasMouseClicked(mx, my, button)) return true;
        if (button != 0 && button != 1) return false;
        itemSearch.setFocused(false); recipeSearch.setFocused(false);
        if (button == 0 && (itemScroll.beginDrag(mx, my, 232, GY, 4, ROWS * CELL, 16, 1) || recipeScroll.beginDrag(mx, my, 628, LY, 4, LH, 16, 1))) return true;
        ItemStack item = hoveredItem(mx, my);
        if (!item.isEmpty()) {
            if (button == 1) itemMenu(mx, my, item); else selectItem(item);
            return true;
        }
        if (mx >= LX && mx < LX + LW && my >= LY && my < LY + LH) {
            int i = hoveredRecipe(my);
            if (rulesView && i < visibleRules.size()) selectedRule = visibleRules.get(i);
            else if (!rulesView && i < visibleRecipes.size()) {
                selectedRecipe = visibleRecipes.get(i);
                if (button == 1) recipeMenu(mx, my, selectedRecipe);
            }
            updateButtons();
            return true;
        }
        return false;
    }

    @Override protected boolean canvasMouseScrolled(double mx, double my, double delta) {
        if (searchInputs.handleMouseScrolled(delta)) return true;
        if (mx >= 6 && mx < 240 && my >= GY && my < GY + ROWS * CELL) return itemScroll.scroll(delta);
        if (mx >= LX && mx < 634 && my >= LY && my < LY + LH) return recipeScroll.scroll(delta);
        return super.canvasMouseScrolled(mx, my, delta);
    }

    @Override protected boolean canvasMouseDragged(double mx, double my, int button, double dx, double dy) {
        if (searchInputs.handleMouseDragged(mx, my)) return true;
        return itemScroll.drag(my, GY, ROWS * CELL, 16) || recipeScroll.drag(my, LY, LH, 16) || super.canvasMouseDragged(mx, my, button, dx, dy);
    }

    @Override protected boolean canvasMouseReleased(double mx, double my, int button) {
        boolean handled = searchInputs.handleMouseReleased(button) | itemScroll.release(button) | recipeScroll.release(button);
        return handled || super.canvasMouseReleased(mx, my, button);
    }

    @Override protected void renderCanvasForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTextFieldPlaceholder(graphics, itemSearch, tr("item_search"));
        renderTextFieldPlaceholder(graphics, recipeSearch, tr("recipe_search"));
        searchInputs.renderSuggestions(graphics, mouseX, mouseY);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 264 || keyCode == 265 || keyCode == 257 || keyCode == 335)
                && searchInputs.handleKeyPressed(keyCode)) return true;
        if (keyCode == 258 && (itemSearch.isFocused() || recipeSearch.isFocused())) {
            searchInputs.handleKeyPressed(264);
            if (searchInputs.handleKeyPressed(257)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

}
