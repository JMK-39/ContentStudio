//? if >=26.1 {
/*
package dev.xyat.contentstudio.recipe;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.contentstudio.recipe.removal.OriginalRecipeCatalog;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator;
import dev.xyat.contentstudio.recipe.removal.RemovalRulePruner;
import dev.xyat.contentstudio.recipe.removal.RecipeRemovalManager;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import dev.xyat.contentstudio.item.ItemData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.resource.event.KineticResourceEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

public final class RecipeMemoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RecipeManager, OriginalRecipeCatalog> CATALOGS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, HolderLookup.Provider> RELOAD_REGISTRIES = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, Long> CATALOG_VERSIONS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, ICondition.IContext> RELOAD_CONDITIONS = new java.util.WeakHashMap<>();
    // The recipe manager reading datapack recipes on this thread; its recipe JSON arrives in ModifyRecipeJsonsEvent.
    private static final ThreadLocal<RecipeScan> SCAN = new ThreadLocal<>();
    private static final Map<RecipeManager, ConditionalOps<JsonElement>> NATIVE_OPS = new java.util.WeakHashMap<>();
    private static long nextCatalogVersion;
    private static boolean registered;

    /^** Implemented by the recipe manager mixin: 26.1 has no API to replace the loaded recipes. *^/
    public interface RecipeMapAccess {
        void contentstudio$replaceRecipes(RecipeMap recipes);
    }

    private record RecipeScan(RecipeManager manager, ResourceManager resources, HolderLookup.Provider registries,
                              ICondition.IContext conditions) {
    }

    public static synchronized OriginalRecipeCatalog originalCatalog(RecipeManager manager) {
        return CATALOGS.getOrDefault(manager, OriginalRecipeCatalog.empty());
    }

    public static synchronized long catalogVersion(RecipeManager manager) {
        return CATALOG_VERSIONS.getOrDefault(manager, 0L);
    }

    public static List<RecipeHolder<?>> recipeCatalog(RecipeManager manager) {
        return originalCatalog(manager).entries().values().stream()
                .map(OriginalRecipeCatalog.Entry::recipe)
                .flatMap(Optional::stream)
                .toList();
    }

    public static synchronized RecipeHolder<?> decodeNative(RecipeManager manager, ResourceLocation id, JsonObject json) {
        var ops = NATIVE_OPS.get(manager);
        if (ops == null) throw new IllegalArgumentException("Recipe registry is not ready");
        var parsed = Recipe.CONDITIONAL_CODEC.parse(ops, json).getOrThrow();
        if (parsed.isEmpty()) throw new IllegalArgumentException("Recipe conditions are inactive");
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), parsed.get().carrier());
    }
    public static boolean containsRuntimeRecipe(RecipeManager manager, ResourceLocation id) {
        return manager.getRecipes().stream().anyMatch(recipe -> recipe.id().identifier().equals(id));
    }
    private RecipeMemoryManager() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        KineticResourceEvents.onAddReloadListener(KineticEventPriority.LOWEST, context -> {
                synchronized (RecipeMemoryManager.class) {
                    RELOAD_REGISTRIES.put(context.serverResources().getRecipeManager(), context.registryAccess());
                }
                context.addListener(new DatapackRecipeReloadListener(
                        context.serverResources().getRecipeManager()
                ));
        });
        // Original datapack recipes are filtered as their JSON is read, before script listeners modify it.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ModifyRecipeJsonsEvent event) -> {
            RecipeScan scan = SCAN.get();
            if (scan != null && event.getOps() instanceof ConditionalOps<JsonElement> conditions) {
                beforeScriptRecipes(scan.manager(), event.getRecipeJsons(), scan.resources(), conditions,
                        scan.registries(), scan.conditions());
            }
        });
        // 26.1 clients only receive the recipe types the server asks for; the recipe editors list all of them.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.sendRecipes(BuiltInRegistries.RECIPE_TYPE));
        registered = true;
    }

    /^** Called by the recipe manager mixin while it reads the datapack recipes on this thread. *^/
    public static void beginRecipeScan(RecipeManager manager, ResourceManager resources, HolderLookup.Provider registries,
                                       ICondition.IContext conditions) {
        SCAN.set(new RecipeScan(manager, resources, registries, conditions));
    }

    public static void endRecipeScan() {
        SCAN.remove();
    }

    public static CompletableFuture<Void> reloadDatapacks(MinecraftServer server) {
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("server is null"));
        }
        server.getPackRepository().reload();
        return server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds()));
    }

    private static void applySnapshot(
            RecipeManager recipeManager,
            RecipeConfigStore.Snapshot snapshot, ResourceManager resources
    ) {
        HolderLookup.Provider registries;
        synchronized (RecipeMemoryManager.class) {
            registries = RELOAD_REGISTRIES.get(recipeManager);
        }
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> configuredRecipes = new LinkedHashMap<>();
        int added = 0;
        int skippedConfigured = 0;
        for (RecipeRecord record : snapshot.recipes()) {
            try {
                RecipeHolder<?> recipe = buildRecipe(record, registries);
                if (recipe != null) {
                    configuredRecipes.put(recipe.id(), recipe);
                    added++;
                } else {
                    skippedConfigured++;
                }
            } catch (Exception e) {
                skippedConfigured++;
                LOGGER.warn("Skipping unusable RecipeModule datapack recipe {}: {}", recipeLabel(record), safeMessage(e));
            }
        }

        try {
            var overrides = dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeStore.load(resources);
            for (var entry : overrides.entrySet()) {
                try {
                    var recipe = decodeNative(recipeManager, entry.getKey(), entry.getValue());
                    configuredRecipes.put(recipe.id(), recipe);
                    added++;
                } catch (Exception e) {
                    skippedConfigured++;
                    LOGGER.warn("Skipping invalid native recipe {}: {}", entry.getKey(), safeMessage(e));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not read native recipe overrides; preserving other recipes", e);
        }
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        int skippedBaseline = 0;

        List<RecipeHolder<?>> baseline = new ArrayList<>(recipeManager.getRecipes());
        for (RecipeHolder<?> recipe : baseline) {
            if (recipe == null) {
                skippedBaseline++;
            } else {
                recipes.put(recipe.id(), recipe);
            }
        }
        recipes.putAll(configuredRecipes);

        List<RecipeHolder<?>> finalRecipes = new ArrayList<>(recipes.values());
        // The server finalizes the recipe map (property sets, stonecutter list, displays) after all reload listeners.
        ((RecipeMapAccess) recipeManager).contentstudio$replaceRecipes(RecipeMap.create(finalRecipes));
        OriginalRecipeCatalog catalog = originalCatalog(recipeManager);
        RecipeDatabase.reloadDatabase();
        RecipeRemovalManager.reloadData();
        RecipeSaveManager.markApplied();

        LOGGER.info(
                "Applied RecipeModule datapack: baseline={}, originalRemoved={}, configured={}, active={}, skippedBaseline={}, skippedConfigured={}",
                baseline.size(),
                catalog.entries().values().stream().filter(OriginalRecipeCatalog.Entry::removed).count(),
                added,
                finalRecipes.size(),
                skippedBaseline,
                skippedConfigured
        );
    }

    /^** Runs while the recipe manager reads datapack recipe JSON, before script listeners can add recipes. *^/
    public static void beforeScriptRecipes(RecipeManager manager,
                                           Map<ResourceLocation, JsonElement> source,
                                           ResourceManager resources,
                                           ConditionalOps<JsonElement> conditions, HolderLookup.Provider registries,
                                           ICondition.IContext conditionContext) {
        // The recipe bundle shares the recipe folder but is ContentStudio's configuration, not a recipe.
        source.remove(KineticResourceIds.of("contentstudio", "recipe_bundle"));
        synchronized (RecipeMemoryManager.class) {
            RELOAD_REGISTRIES.put(manager, registries);
            RELOAD_CONDITIONS.put(manager, conditionContext);
            NATIVE_OPS.put(manager, conditions);
        }
        final RecipeConfigStore.Snapshot snapshot;
        try {
            snapshot = RecipeConfigStore.load(resources, registries, conditionContext);
        } catch (Exception e) {
            LOGGER.error("Cannot read recipe removal rules; leaving original datapack recipes unchanged", e);
            publishCatalog(manager, OriginalRecipeCatalog.empty());
            return;
        }

        List<RemovalEntry> rules = snapshot.removals();
        List<RemovalEntry> cleaned = RemovalRulePruner.removeMissingOutputItems(rules,
                KineticRegistries.items()::contains);
        if (cleaned.size() != rules.size()) {
            try {
                RecipeConfigStore.replaceRemovalsAtomic(cleaned);
                LOGGER.info("Removed {} recipe rules for unregistered output items", rules.size() - cleaned.size());
                rules = cleaned;
            } catch (Exception e) {
                LOGGER.error("Could not persist cleanup of missing output-item rules", e);
            }
        }


        try {
            OriginalRecipeCatalog catalog = OriginalRecipeCatalog.filter(source, rules,
                    (id, json) -> inspectOriginal(id, json, conditions, registries, conditionContext));
            publishCatalog(manager, catalog);
            LOGGER.info("Removed {} original datapack recipes from {} candidates",
                    catalog.entries().values().stream().filter(OriginalRecipeCatalog.Entry::removed).count(),
                    catalog.entries().size());
        } catch (Exception e) {
            LOGGER.error("Cannot inspect original datapack recipes; leaving them unchanged", e);
            publishCatalog(manager, OriginalRecipeCatalog.empty());
        }
    }

    private static synchronized void publishCatalog(RecipeManager manager, OriginalRecipeCatalog catalog) {
        CATALOGS.put(manager, catalog);
        CATALOG_VERSIONS.put(manager, ++nextCatalogVersion);
    }

    private static Optional<OriginalRecipeCatalog.InspectedRecipe> inspectOriginal(
            ResourceLocation id, JsonElement source, ConditionalOps<JsonElement> conditions, HolderLookup.Provider registries,
            ICondition.IContext conditionContext) {
        if (!source.isJsonObject()) return Optional.empty();
        JsonObject json = source.getAsJsonObject();
        RecipeHolder<?> recipe;
        try {
            var decoded = Recipe.CONDITIONAL_CODEC.parse(conditions, json).getOrThrow();
            if (decoded.isEmpty()) return Optional.empty();
            recipe = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), decoded.get().carrier());
        } catch (Exception e) {
            // Check conditions separately if the recipe codec failed: an inactive recipe must not enter the catalog.
            try {
                if (!net.neoforged.neoforge.common.conditions.ICondition.conditionsMatched(conditions, json)) {
                    return Optional.empty();
                }
            } catch (Exception conditionError) {
                LOGGER.warn("Could not evaluate conditions of recipe {}; preserving it", id, conditionError);
                return Optional.empty();
            }
            LOGGER.warn("Could not parse recipe {}; only recipe ID rules can match it", id, e);
            return Optional.of(new OriginalRecipeCatalog.InspectedRecipe(
                    new RemovalCandidate(id, null, null, Set.of()), Optional.empty()));
        }
        return Optional.of(new OriginalRecipeCatalog.InspectedRecipe(
                candidateOf(recipe, registries, conditionContext, json), Optional.of(recipe)));
    }

    public static RemovalCandidate candidateOf(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        return candidateOf(holder, registries, null);
    }

    static RemovalCandidate candidateOf(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                                ICondition.IContext conditions) {
        return candidateOf(holder, registries, conditions, null);
    }

    // While datapacks reload, items have no components and tags are not bound: the output item comes from the recipe
    // display or else its JSON result, and its tags from the staged tag contents of the reload's condition context.
    private static RemovalCandidate candidateOf(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                                ICondition.IContext conditions, JsonObject source) {
        Recipe<?> recipe = holder.value();
        ResourceLocation id = holder.id().identifier();
        ResourceLocation type = null;
        ResourceLocation output = null;
        Set<ResourceLocation> tags = Set.of();
        try {
            type = KineticRegistries.recipeTypes().id(recipe.getType());
        } catch (RuntimeException e) {
            LOGGER.debug("Could not resolve recipe type for {}", id, e);
        }
        if (registries != null) {
            try {
                net.minecraft.core.Holder<Item> item = RecipeView.outputItem(recipe);
                if (item == null && source != null) {
                    item = jsonResultItem(source, registries);
                }
                if (item != null && item.value() != net.minecraft.world.item.Items.AIR) {
                    output = KineticRegistries.items().id(item.value());
                    tags = outputTags(item, registries, conditions);
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Could not resolve output for {}", id, e);
            }
        }
        return new RemovalCandidate(id, type, output, tags);
    }
    private static net.minecraft.core.Holder<Item> jsonResultItem(JsonObject json, HolderLookup.Provider registries) {
        JsonElement result = json.get("result");
        String raw = result == null ? null
                : result.isJsonPrimitive() ? result.getAsString()
                : result.isJsonObject() && result.getAsJsonObject().get("id") instanceof JsonElement itemId && itemId.isJsonPrimitive()
                        ? itemId.getAsString() : null;
        ResourceLocation id = raw == null ? null : KineticResourceIds.tryParse(raw);
        return id == null ? null
                : registries.lookupOrThrow(Registries.ITEM).get(ResourceKey.create(Registries.ITEM, id)).orElse(null);
    }

    // Staged item tags per reload, inverted once: item -> ids of the tags that contain it.
    private static final Map<ICondition.IContext, Map<net.minecraft.core.Holder<Item>, Set<ResourceLocation>>> STAGED_ITEM_TAGS =
            new java.util.WeakHashMap<>();

    private static Set<ResourceLocation> outputTags(net.minecraft.core.Holder<Item> item, HolderLookup.Provider registries,
                                                    ICondition.IContext conditions) {
        if (conditions == null) {
            return item.tags().map(TagKey::location).collect(Collectors.toUnmodifiableSet());
        }
        Map<net.minecraft.core.Holder<Item>, Set<ResourceLocation>> index;
        synchronized (STAGED_ITEM_TAGS) {
            index = STAGED_ITEM_TAGS.computeIfAbsent(conditions, context -> {
                Map<net.minecraft.core.Holder<Item>, Set<ResourceLocation>> byItem = new java.util.HashMap<>();
                registries.lookupOrThrow(Registries.ITEM).listTags().map(net.minecraft.core.HolderSet.Named::key).forEach(tag ->
                        context.getTag(tag).forEach(member -> byItem.computeIfAbsent(member, ignored -> new java.util.HashSet<>()).add(tag.location())));
                return byItem;
            });
        }
        return Set.copyOf(index.getOrDefault(item, Set.of()));
    }

    private record DatapackRecipeReloadListener(RecipeManager recipeManager) implements ResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
            try {
                HolderLookup.Provider registries;
                ICondition.IContext conditions;
                synchronized (RecipeMemoryManager.class) {
                    registries = RELOAD_REGISTRIES.get(recipeManager);
                    conditions = RELOAD_CONDITIONS.get(recipeManager);
                }
                RecipeConfigStore.Snapshot snapshot = RecipeConfigStore.load(resourceManager, registries, conditions);
                applySnapshot(recipeManager, snapshot, resourceManager);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to load RecipeModule datapack resource {}; keeping recipes loaded by Minecraft unchanged",
                        RecipeConfigStore.DATAPACK_RESOURCE,
                        e
                );
            }
        }
    }

    static RecipeHolder<?> buildRecipe(RecipeRecord record, HolderLookup.Provider registries) {
        RecipeRegistry.EditorType type = RecipeRegistry.EditorType.valueOf(record.editorType);
        ResourceLocation id = RecipeConfigStore.memoryRecipeId(record);
        ItemStack output = record.output == null ? ItemStack.EMPTY : record.output.copy();
        if (output.isEmpty()) {
            throw new IllegalArgumentException("recipe output is empty or missing");
        }
        validateRegisteredStack(output, "output");
        if (!record.outputUseNbt || type == RecipeRegistry.EditorType.SMITHING) {
            ItemData.reset(output);
        }
        ItemStackTemplate result = ItemStackTemplate.fromNonEmptyStack(output);
        Recipe.CommonInfo common = new Recipe.CommonInfo(true);

        Recipe<?> recipe = switch (type) {
            case CRAFTING -> record.isShapeless
                    ? buildShapeless(record, result, common, registries)
                    : buildShaped(record, result, common, registries);
            case SMITHING -> new SmithingTransformRecipe(
                    common,
                    Optional.ofNullable(ingredient(record, 0, registries)),
                    required(ingredient(record, 1, registries), "smithing base"),
                    Optional.ofNullable(ingredient(record, 2, registries)),
                    result
            );
            case FURNACE -> new SmeltingRecipe(common, cookingBook(), required(ingredient(record, 0, registries), "input"), result, 0.0F, 200);
            case BLAST -> new BlastingRecipe(common, cookingBook(), required(ingredient(record, 0, registries), "input"), result, 0.0F, 100);
            case SMOKER -> new SmokingRecipe(common, cookingBook(), required(ingredient(record, 0, registries), "input"), result, 0.0F, 100);
            case STONECUTTER -> new StonecutterRecipe(common, required(ingredient(record, 0, registries), "input"), result);
        };
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
    }

    private static AbstractCookingRecipe.CookingBookInfo cookingBook() {
        return new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "");
    }

    private static CraftingRecipe.CraftingBookInfo craftingBook() {
        return new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
    }

    private static Ingredient required(Ingredient ingredient, String role) {
        if (ingredient == null) {
            throw new IllegalArgumentException(role + " is empty");
        }
        return ingredient;
    }

    private static ShapelessRecipe buildShapeless(RecipeRecord record, ItemStackTemplate result, Recipe.CommonInfo common,
                                                  HolderLookup.Provider registries) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            Ingredient ingredient = ingredient(record, i, registries);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Shapeless recipe has no ingredients");
        }
        return new ShapelessRecipe(common, craftingBook(), result, ingredients);
    }

    private static ShapedRecipe buildShaped(RecipeRecord record, ItemStackTemplate result, Recipe.CommonInfo common,
                                            HolderLookup.Provider registries) {
        int minRow = 3;
        int maxRow = -1;
        int minColumn = 3;
        int maxColumn = -1;
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                int row = i / 3;
                int column = i % 3;
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < minRow || maxColumn < minColumn) {
            throw new IllegalArgumentException("Shaped recipe has no ingredients");
        }

        int width = maxColumn - minColumn + 1;
        int height = maxRow - minRow + 1;
        List<Optional<Ingredient>> ingredients = new ArrayList<>(java.util.Collections.nCopies(width * height, Optional.empty()));
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                int sourceIndex = row * 3 + column;
                int targetIndex = (row - minRow) * width + (column - minColumn);
                ingredients.set(targetIndex, Optional.ofNullable(ingredient(record, sourceIndex, registries)));
            }
        }
        return new ShapedRecipe(common, craftingBook(), new ShapedRecipePattern(width, height, ingredients, Optional.empty()), result);
    }

    /^** The ingredient of one input slot, or null for an empty slot. 26.1 has no empty ingredient. *^/
    static Ingredient ingredient(RecipeRecord record, int index, HolderLookup.Provider registries) {
        if (index < 0 || index >= record.inputs.size()) {
            return null;
        }
        ItemStack stack = record.inputs.get(index);
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        if (ItemData.customData(stack).contains("kt_tag")) {
            String raw = ItemData.customData(stack).getString("kt_tag");
            if (!raw.startsWith("#") || raw.length() == 1) {
                throw new IllegalArgumentException("item tag must start with #");
            }
            raw = raw.substring(1);
            TagKey<Item> tag = TagKey.create(Registries.ITEM, KineticResourceIds.parse(raw));
            return Ingredient.of(registries.lookupOrThrow(Registries.ITEM).getOrThrow(tag));
        }

        validateRegisteredStack(stack, "ingredient " + index);

        int mode = index < record.inputModes.size() ? record.inputModes.get(index) : 0;
        if (mode == 1) {
            return ComponentPatchIngredient.of(false, stack);
        }
        if (mode == 2) {
            return ComponentPatchIngredient.of(true, stack);
        }
        return Ingredient.of(stack.getItem());
    }

    public static boolean matchesRemoval(RecipeHolder<?> recipe, List<RemovalEntry> removals, RegistryAccess registryAccess) {
        return !RemovalRuleEvaluator.blockingRules(candidateOf(recipe, registryAccess), removals).isEmpty();
    }
    private static void validateRegisteredStack(ItemStack stack, String role) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException(role + " is empty");
        }
        ResourceLocation itemId = KineticRegistries.items().id(stack.getItem());
        if (itemId == null || !KineticRegistries.items().contains(itemId)) {
            throw new IllegalArgumentException(role + " references an unregistered item");
        }
        Item registered = KineticRegistries.items().get(itemId);
        if (registered == null || registered != stack.getItem()) {
            throw new IllegalArgumentException(role + " references missing item " + itemId);
        }
    }

    private static String recipeLabel(RecipeRecord record) {
        if (record == null) {
            return "<null>";
        }
        if (record.uuid != null && !record.uuid.isBlank()) {
            return record.uuid;
        }
        if (record.output != null && !record.output.isEmpty()) {
            ResourceLocation outputId = KineticRegistries.items().id(record.output.getItem());
            if (outputId != null) {
                return outputId.toString();
            }
        }
        return "<unknown>";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

}

*///?} else if >=1.21 {
/*
package dev.xyat.contentstudio.recipe;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.contentstudio.recipe.removal.OriginalRecipeCatalog;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator;
import dev.xyat.contentstudio.recipe.removal.RemovalRulePruner;
import dev.xyat.contentstudio.recipe.removal.RecipeRemovalManager;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import dev.xyat.contentstudio.item.ItemData;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.resource.event.KineticResourceEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

public final class RecipeMemoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RecipeManager, OriginalRecipeCatalog> CATALOGS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, HolderLookup.Provider> RELOAD_REGISTRIES = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, Long> CATALOG_VERSIONS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, ICondition.IContext> RELOAD_CONDITIONS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, ConditionalOps<JsonElement>> NATIVE_OPS = new java.util.WeakHashMap<>();
    private static long nextCatalogVersion;
    private static boolean registered;

    public static synchronized OriginalRecipeCatalog originalCatalog(RecipeManager manager) {
        return CATALOGS.getOrDefault(manager, OriginalRecipeCatalog.empty());
    }

    public static synchronized long catalogVersion(RecipeManager manager) {
        return CATALOG_VERSIONS.getOrDefault(manager, 0L);
    }

    public static List<RecipeHolder<?>> recipeCatalog(RecipeManager manager) {
        return originalCatalog(manager).entries().values().stream()
                .map(OriginalRecipeCatalog.Entry::recipe)
                .flatMap(Optional::stream)
                .toList();
    }

    public static synchronized RecipeHolder<?> decodeNative(RecipeManager manager, ResourceLocation id, JsonObject json) {
        var ops = NATIVE_OPS.get(manager);
        if (ops == null) throw new IllegalArgumentException("Recipe registry is not ready");
        var parsed = Recipe.CONDITIONAL_CODEC.parse(ops, json).getOrThrow();
        if (parsed.isEmpty()) throw new IllegalArgumentException("Recipe conditions are inactive");
        return new RecipeHolder<>(id, parsed.get().carrier());
    }
    public static boolean containsRuntimeRecipe(RecipeManager manager, ResourceLocation id) {
        return manager.getRecipes().stream().anyMatch(recipe -> recipe.id().equals(id));
    }
    private RecipeMemoryManager() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        KineticResourceEvents.onAddReloadListener(KineticEventPriority.LOWEST, context -> {
                synchronized (RecipeMemoryManager.class) {
                    RELOAD_REGISTRIES.put(context.serverResources().getRecipeManager(), context.registryAccess());
                }
                context.addListener(new DatapackRecipeReloadListener(
                        context.serverResources().getRecipeManager()
                ));
        });
        registered = true;
    }

    public static CompletableFuture<Void> reloadDatapacks(MinecraftServer server) {
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("server is null"));
        }
        server.getPackRepository().reload();
        return server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds()));
    }

    private static void applySnapshot(
            RecipeManager recipeManager,
            RecipeConfigStore.Snapshot snapshot, ResourceManager resources
    ) {
        Map<ResourceLocation, RecipeHolder<?>> configuredRecipes = new LinkedHashMap<>();
        int added = 0;
        int skippedConfigured = 0;
        for (RecipeRecord record : snapshot.recipes()) {
            try {
                RecipeHolder<?> recipe = buildRecipe(record);
                if (recipe != null) {
                    configuredRecipes.put(recipe.id(), recipe);
                    added++;
                } else {
                    skippedConfigured++;
                }
            } catch (Exception e) {
                skippedConfigured++;
                LOGGER.warn("Skipping unusable RecipeModule datapack recipe {}: {}", recipeLabel(record), safeMessage(e));
            }
        }

        try {
            var overrides = dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeStore.load(resources);
            for (var entry : overrides.entrySet()) {
                try {
                    var recipe = decodeNative(recipeManager, entry.getKey(), entry.getValue());
                    configuredRecipes.put(recipe.id(), recipe);
                    added++;
                } catch (Exception e) {
                    skippedConfigured++;
                    LOGGER.warn("Skipping invalid native recipe {}: {}", entry.getKey(), safeMessage(e));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not read native recipe overrides; preserving other recipes", e);
        }
        Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        int skippedBaseline = 0;

        List<RecipeHolder<?>> baseline = new ArrayList<>(recipeManager.getRecipes());
        for (RecipeHolder<?> recipe : baseline) {
            if (recipe == null) {
                skippedBaseline++;
            }
        }
        for (RecipeHolder<?> recipe : baseline) {
            if (recipe != null) recipes.put(recipe.id(), recipe);
        }
        recipes.putAll(configuredRecipes);

        List<RecipeHolder<?>> finalRecipes = new ArrayList<>(recipes.values());
        recipeManager.replaceRecipes(finalRecipes);
        OriginalRecipeCatalog catalog = originalCatalog(recipeManager);
        RecipeDatabase.reloadDatabase();
        RecipeRemovalManager.reloadData();
        RecipeSaveManager.markApplied();

        LOGGER.info(
                "Applied RecipeModule datapack: baseline={}, originalRemoved={}, configured={}, active={}, skippedBaseline={}, skippedConfigured={}",
                baseline.size(),
                catalog.entries().values().stream().filter(OriginalRecipeCatalog.Entry::removed).count(),
                added,
                finalRecipes.size(),
                skippedBaseline,
                skippedConfigured
        );
    }

    /^** Runs at RecipeManager.apply HEAD, before script reload listeners can add recipes. *^/
    public static void beforeScriptRecipes(RecipeManager manager,
                                           Map<ResourceLocation, JsonElement> source,
                                           ResourceManager resources,
                                           ConditionalOps<JsonElement> conditions, HolderLookup.Provider registries,
                                           ICondition.IContext conditionContext) {
        // The recipe bundle shares the recipe folder but is ContentStudio's configuration, not a recipe.
        source.remove(KineticResourceIds.of("contentstudio", "recipe_bundle"));
        synchronized (RecipeMemoryManager.class) {
            RELOAD_REGISTRIES.put(manager, registries);
            RELOAD_CONDITIONS.put(manager, conditionContext);
            NATIVE_OPS.put(manager, conditions);
        }
        final RecipeConfigStore.Snapshot snapshot;
        try {
            snapshot = RecipeConfigStore.load(resources, registries, conditionContext);
        } catch (Exception e) {
            LOGGER.error("Cannot read recipe removal rules; leaving original datapack recipes unchanged", e);
            publishCatalog(manager, OriginalRecipeCatalog.empty());
            return;
        }

        List<RemovalEntry> rules = snapshot.removals();
        List<RemovalEntry> cleaned = RemovalRulePruner.removeMissingOutputItems(rules,
                KineticRegistries.items()::contains);
        if (cleaned.size() != rules.size()) {
            try {
                RecipeConfigStore.replaceRemovalsAtomic(cleaned);
                LOGGER.info("Removed {} recipe rules for unregistered output items", rules.size() - cleaned.size());
                rules = cleaned;
            } catch (Exception e) {
                LOGGER.error("Could not persist cleanup of missing output-item rules", e);
            }
        }


        try {
            OriginalRecipeCatalog catalog = OriginalRecipeCatalog.filter(source, rules,
                    (id, json) -> inspectOriginal(id, json, conditions, registries, conditionContext));
            publishCatalog(manager, catalog);
            LOGGER.info("Removed {} original datapack recipes from {} candidates",
                    catalog.entries().values().stream().filter(OriginalRecipeCatalog.Entry::removed).count(),
                    catalog.entries().size());
        } catch (Exception e) {
            LOGGER.error("Cannot inspect original datapack recipes; leaving them unchanged", e);
            publishCatalog(manager, OriginalRecipeCatalog.empty());
        }
    }

    private static synchronized void publishCatalog(RecipeManager manager, OriginalRecipeCatalog catalog) {
        CATALOGS.put(manager, catalog);
        CATALOG_VERSIONS.put(manager, ++nextCatalogVersion);
    }

    private static Optional<OriginalRecipeCatalog.InspectedRecipe> inspectOriginal(
            ResourceLocation id, JsonElement source, ConditionalOps<JsonElement> conditions, HolderLookup.Provider registries,
            ICondition.IContext conditionContext) {
        if (!source.isJsonObject()) return Optional.empty();
        JsonObject json = source.getAsJsonObject();
        RecipeHolder<?> recipe;
        try {
            var decoded = Recipe.CONDITIONAL_CODEC.parse(conditions, json).getOrThrow();
            if (decoded.isEmpty()) return Optional.empty();
            recipe = new RecipeHolder<>(id, decoded.get().carrier());
        } catch (Exception e) {
            // Check conditions separately if the recipe codec failed: an inactive recipe must not enter the catalog.
            try {
                if (!net.neoforged.neoforge.common.conditions.ICondition.conditionsMatched(conditions, json)) {
                    return Optional.empty();
                }
            } catch (Exception conditionError) {
                LOGGER.warn("Could not evaluate conditions of recipe {}; preserving it", id, conditionError);
                return Optional.empty();
            }
            LOGGER.warn("Could not parse recipe {}; only recipe ID rules can match it", id, e);
            return Optional.of(new OriginalRecipeCatalog.InspectedRecipe(
                    new RemovalCandidate(id, null, null, Set.of()), Optional.empty()));
        }
        return Optional.of(new OriginalRecipeCatalog.InspectedRecipe(
                candidateOf(recipe, registries, conditionContext), Optional.of(recipe)));
    }

    public static RemovalCandidate candidateOf(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        return candidateOf(holder, registries, null);
    }

    static RemovalCandidate candidateOf(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                                ICondition.IContext conditions) {
        Recipe<?> recipe = holder.value();
        ResourceLocation type = null;
        ResourceLocation output = null;
        Set<ResourceLocation> tags = Set.of();
        try {
            type = KineticRegistries.recipeTypes().id(recipe.getType());
        } catch (RuntimeException e) {
            LOGGER.debug("Could not resolve recipe type for {}", holder.id(), e);
        }
        if (registries != null) {
            try {
                ItemStack stack = recipe.getResultItem(registries);
                if (!stack.isEmpty()) {
                    output = KineticRegistries.items().id(stack.getItem());
                    tags = conditions == null
                            ? stack.getTags().map(TagKey::location).collect(Collectors.toUnmodifiableSet())
                            : conditions.getAllTags(Registries.ITEM).entrySet().stream()
                                    .filter(entry -> entry.getValue().contains(stack.getItemHolder()))
                                    .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Could not resolve output for {}", holder.id(), e);
            }
        }
        return new RemovalCandidate(holder.id(), type, output, tags);
    }
    private record DatapackRecipeReloadListener(RecipeManager recipeManager) implements ResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
            try {
                HolderLookup.Provider registries;
                ICondition.IContext conditions;
                synchronized (RecipeMemoryManager.class) {
                    registries = RELOAD_REGISTRIES.get(recipeManager);
                    conditions = RELOAD_CONDITIONS.get(recipeManager);
                }
                RecipeConfigStore.Snapshot snapshot = RecipeConfigStore.load(resourceManager, registries, conditions);
                applySnapshot(recipeManager, snapshot, resourceManager);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to load RecipeModule datapack resource {}; keeping recipes loaded by Minecraft unchanged",
                        RecipeConfigStore.DATAPACK_RESOURCE,
                        e
                );
            }
        }
    }

    static RecipeHolder<?> buildRecipe(RecipeRecord record) {
        RecipeRegistry.EditorType type = RecipeRegistry.EditorType.valueOf(record.editorType);
        ResourceLocation id = RecipeConfigStore.memoryRecipeId(record);
        ItemStack output = record.output == null ? ItemStack.EMPTY : record.output.copy();
        if (output.isEmpty()) {
            throw new IllegalArgumentException("recipe output is empty or missing");
        }
        validateRegisteredStack(output, "output");
        if (!record.outputUseNbt || type == RecipeRegistry.EditorType.SMITHING) {
            ItemData.reset(output);
        }

        Recipe<?> recipe = switch (type) {
            case CRAFTING -> record.isShapeless
                    ? buildShapeless(id, record, output)
                    : buildShaped(id, record, output);
            case SMITHING -> new SmithingTransformRecipe(
                    ingredient(record, 0),
                    ingredient(record, 1),
                    ingredient(record, 2),
                    output
            );
            case FURNACE -> new SmeltingRecipe("", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 200);
            case BLAST -> new BlastingRecipe("", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 100);
            case SMOKER -> new SmokingRecipe("", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 100);
            case STONECUTTER -> new StonecutterRecipe("", ingredient(record, 0), output);
        };
        return new RecipeHolder<>(id, recipe);
    }

    private static ShapelessRecipe buildShapeless(ResourceLocation id, RecipeRecord record, ItemStack output) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                ingredients.add(ingredient(record, i));
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Shapeless recipe has no ingredients");
        }
        return new ShapelessRecipe("", CraftingBookCategory.MISC, output, ingredients);
    }

    private static ShapedRecipe buildShaped(ResourceLocation id, RecipeRecord record, ItemStack output) {
        int minRow = 3;
        int maxRow = -1;
        int minColumn = 3;
        int maxColumn = -1;
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                int row = i / 3;
                int column = i % 3;
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < minRow || maxColumn < minColumn) {
            throw new IllegalArgumentException("Shaped recipe has no ingredients");
        }

        int width = maxColumn - minColumn + 1;
        int height = maxRow - minRow + 1;
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                int sourceIndex = row * 3 + column;
                ItemStack stack = record.inputs.get(sourceIndex);
                if (stack != null && !stack.isEmpty()) {
                    int targetIndex = (row - minRow) * width + (column - minColumn);
                    ingredients.set(targetIndex, ingredient(record, sourceIndex));
                }
            }
        }
        return new ShapedRecipe("", CraftingBookCategory.MISC, new ShapedRecipePattern(width, height, ingredients, Optional.empty()), output);
    }

    static Ingredient ingredient(RecipeRecord record, int index) {
        if (index < 0 || index >= record.inputs.size()) {
            return Ingredient.EMPTY;
        }
        ItemStack stack = record.inputs.get(index);
        if (stack == null || stack.isEmpty()) {
            return Ingredient.EMPTY;
        }

        if (ItemData.customData(stack).contains("kt_tag")) {
            String raw = ItemData.customData(stack).getString("kt_tag");
            if (!raw.startsWith("#") || raw.length() == 1) {
                throw new IllegalArgumentException("item tag must start with #");
            }
            raw = raw.substring(1);
            TagKey<Item> tag = TagKey.create(Registries.ITEM, KineticResourceIds.parse(raw));
            return Ingredient.of(tag);
        }

        validateRegisteredStack(stack, "ingredient " + index);

        int mode = index < record.inputModes.size() ? record.inputModes.get(index) : 0;
        if (mode == 1) {
            return ComponentPatchIngredient.of(false, stack);
        }
        if (mode == 2) {
            return ComponentPatchIngredient.of(true, stack);
        }
        return Ingredient.of(stack.getItem());
    }

    public static boolean matchesRemoval(RecipeHolder<?> recipe, List<RemovalEntry> removals, RegistryAccess registryAccess) {
        return !RemovalRuleEvaluator.blockingRules(candidateOf(recipe, registryAccess), removals).isEmpty();
    }
    private static void validateRegisteredStack(ItemStack stack, String role) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException(role + " is empty");
        }
        ResourceLocation itemId = KineticRegistries.items().id(stack.getItem());
        if (itemId == null || !KineticRegistries.items().contains(itemId)) {
            throw new IllegalArgumentException(role + " references an unregistered item");
        }
        Item registered = KineticRegistries.items().get(itemId);
        if (registered == null || registered != stack.getItem()) {
            throw new IllegalArgumentException(role + " references missing item " + itemId);
        }
    }

    private static String recipeLabel(RecipeRecord record) {
        if (record == null) {
            return "<null>";
        }
        if (record.uuid != null && !record.uuid.isBlank()) {
            return record.uuid;
        }
        if (record.output != null && !record.output.isEmpty()) {
            ResourceLocation outputId = KineticRegistries.items().id(record.output.getItem());
            if (outputId != null) {
                return outputId.toString();
            }
        }
        return "<unknown>";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

}

*///?} else {
package dev.xyat.contentstudio.recipe;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.contentstudio.recipe.removal.OriginalRecipeCatalog;
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator;
import dev.xyat.contentstudio.recipe.removal.RemovalRulePruner;
import dev.xyat.contentstudio.recipe.removal.RecipeRemovalManager;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.resource.event.KineticResourceEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

public final class RecipeMemoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RecipeManager, OriginalRecipeCatalog> CATALOGS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, RegistryAccess> RELOAD_REGISTRIES = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, Long> CATALOG_VERSIONS = new java.util.WeakHashMap<>();
    private static final Map<RecipeManager, ICondition.IContext> NATIVE_OPS = new java.util.WeakHashMap<>();
    private static long nextCatalogVersion;
    private static boolean registered;

    public static synchronized OriginalRecipeCatalog originalCatalog(RecipeManager manager) {
        return CATALOGS.getOrDefault(manager, OriginalRecipeCatalog.empty());
    }

    public static synchronized long catalogVersion(RecipeManager manager) {
        return CATALOG_VERSIONS.getOrDefault(manager, 0L);
    }

    public static List<Recipe<?>> recipeCatalog(RecipeManager manager) {
        return originalCatalog(manager).entries().values().stream()
                .map(OriginalRecipeCatalog.Entry::recipe)
                .flatMap(Optional::stream)
                .toList();
    }

    public static synchronized Recipe<?> decodeNative(RecipeManager manager, ResourceLocation id, JsonObject json) {
        var conditions = NATIVE_OPS.get(manager);
        if (conditions == null) throw new IllegalArgumentException("Recipe registry is not ready");
        if (!CraftingHelper.processConditions(json, "conditions", conditions))
            throw new IllegalArgumentException("Recipe conditions are inactive");
        var recipe = RecipeManager.fromJson(id, json, conditions);
        // Some legacy serializers derive their ID from editable fields instead of honoring the supplied ID.
        // Reject that draft before persistence: replacing it by the derived ID could overwrite another recipe.
        if (!recipe.getId().equals(id)) throw new IllegalArgumentException("DERIVED_ID:" + recipe.getId());
        dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeCompat.initialize(recipe);
        return recipe;
    }
    public static boolean containsRuntimeRecipe(RecipeManager manager, ResourceLocation id) {
        return manager.getRecipes().stream().anyMatch(recipe -> recipe.getId().equals(id));
    }
    private RecipeMemoryManager() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        KineticResourceEvents.onAddReloadListener(KineticEventPriority.LOWEST, context -> {
                synchronized (RecipeMemoryManager.class) {
                    RELOAD_REGISTRIES.put(context.serverResources().getRecipeManager(), context.registryAccess());
                }
                context.addListener(new DatapackRecipeReloadListener(
                        context.serverResources().getRecipeManager()
                ));
        });
        registered = true;
    }

    public static CompletableFuture<Void> reloadDatapacks(MinecraftServer server) {
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("server is null"));
        }
        server.getPackRepository().reload();
        return server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds()));
    }

    private static void applySnapshot(
            RecipeManager recipeManager,
            RecipeConfigStore.Snapshot snapshot, ResourceManager resources
    ) {
        Map<ResourceLocation, Recipe<?>> configuredRecipes = new LinkedHashMap<>();
        int added = 0;
        int skippedConfigured = 0;
        for (RecipeRecord record : snapshot.recipes()) {
            try {
                Recipe<?> recipe = buildRecipe(record);
                if (recipe != null) {
                    configuredRecipes.put(recipe.getId(), recipe);
                    added++;
                } else {
                    skippedConfigured++;
                }
            } catch (Exception e) {
                skippedConfigured++;
                LOGGER.warn("Skipping unusable RecipeModule datapack recipe {}: {}", recipeLabel(record), safeMessage(e));
            }
        }

        try {
            var overrides = dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeStore.load(resources);
            for (var entry : overrides.entrySet()) {
                try {
                    var recipe = decodeNative(recipeManager, entry.getKey(), entry.getValue());
                    configuredRecipes.put(recipe.getId(), recipe);
                    added++;
                } catch (Exception e) {
                    skippedConfigured++;
                    LOGGER.warn("Skipping invalid native recipe {}: {}", entry.getKey(), safeMessage(e));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not read native recipe overrides; preserving other recipes", e);
        }
        Map<ResourceLocation, Recipe<?>> recipes = new LinkedHashMap<>();
        int skippedBaseline = 0;

        List<Recipe<?>> baseline = new ArrayList<>(recipeManager.getRecipes());
        for (Recipe<?> recipe : baseline) {
            if (recipe == null) {
                skippedBaseline++;
            }
        }
        for (Recipe<?> recipe : baseline) {
            if (recipe != null) recipes.put(recipe.getId(), recipe);
        }
        recipes.putAll(configuredRecipes);

        List<Recipe<?>> finalRecipes = new ArrayList<>(recipes.values());
        recipeManager.replaceRecipes(finalRecipes);
        OriginalRecipeCatalog catalog = originalCatalog(recipeManager);
        RecipeDatabase.reloadDatabase();
        RecipeRemovalManager.reloadData();
        RecipeSaveManager.markApplied();

        LOGGER.info(
                "Applied RecipeModule datapack: baseline={}, originalRemoved={}, configured={}, active={}, skippedBaseline={}, skippedConfigured={}",
                baseline.size(),
                catalog.entries().values().stream().filter(OriginalRecipeCatalog.Entry::removed).count(),
                added,
                finalRecipes.size(),
                skippedBaseline,
                skippedConfigured
        );
    }

    /** Runs at RecipeManager.apply HEAD, before script reload listeners can add recipes. */
    public static void beforeScriptRecipes(RecipeManager manager,
                                           Map<ResourceLocation, JsonElement> source,
                                           ResourceManager resources,
                                           ICondition.IContext conditions) {
        synchronized (RecipeMemoryManager.class) { NATIVE_OPS.put(manager, conditions); }
        final RecipeConfigStore.Snapshot snapshot;
        try {
            snapshot = RecipeConfigStore.load(resources);
        } catch (Exception e) {
            LOGGER.error("Cannot read recipe removal rules; leaving original datapack recipes unchanged", e);
            publishCatalog(manager, OriginalRecipeCatalog.empty());
            return;
        }

        List<RemovalEntry> rules = snapshot.removals();
        List<RemovalEntry> cleaned = RemovalRulePruner.removeMissingOutputItems(rules,
                KineticRegistries.items()::contains);
        if (cleaned.size() != rules.size()) {
            try {
                RecipeConfigStore.replaceRemovalsAtomic(cleaned);
                LOGGER.info("Removed {} recipe rules for unregistered output items", rules.size() - cleaned.size());
                rules = cleaned;
            } catch (Exception e) {
                LOGGER.error("Could not persist cleanup of missing output-item rules", e);
            }
        }

        RegistryAccess registries;
        synchronized (RecipeMemoryManager.class) {
            registries = RELOAD_REGISTRIES.get(manager);
        }
        try {
            OriginalRecipeCatalog catalog = OriginalRecipeCatalog.filter(source, rules,
                    (id, json) -> inspectOriginal(id, json, conditions, registries));
            publishCatalog(manager, catalog);
            LOGGER.info("Removed {} original datapack recipes from {} candidates",
                    catalog.entries().values().stream().filter(OriginalRecipeCatalog.Entry::removed).count(),
                    catalog.entries().size());
        } catch (Exception e) {
            LOGGER.error("Cannot inspect original datapack recipes; leaving them unchanged", e);
            publishCatalog(manager, OriginalRecipeCatalog.empty());
        }
    }

    private static synchronized void publishCatalog(RecipeManager manager, OriginalRecipeCatalog catalog) {
        CATALOGS.put(manager, catalog);
        CATALOG_VERSIONS.put(manager, ++nextCatalogVersion);
    }

    private static Optional<OriginalRecipeCatalog.InspectedRecipe> inspectOriginal(
            ResourceLocation id, JsonElement source, ICondition.IContext conditions, RegistryAccess registries) {
        if (!source.isJsonObject()) return Optional.empty();
        JsonObject json = source.getAsJsonObject();
        try {
            if (!CraftingHelper.processConditions(json, "conditions", conditions)) return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Could not evaluate conditions of recipe {}; preserving it", id, e);
            return Optional.empty();
        }

        Recipe<?> recipe = null;
        try {
            recipe = RecipeManager.fromJson(id, json, conditions);
        } catch (Exception e) {
            LOGGER.warn("Could not parse recipe {}; only recipe ID rules can match it", id, e);
        }
        return Optional.of(new OriginalRecipeCatalog.InspectedRecipe(
                recipe == null ? new RemovalCandidate(id, null, null, Set.of()) : candidateOf(recipe, registries),
                Optional.ofNullable(recipe)));
    }

    public static RemovalCandidate candidateOf(Recipe<?> recipe, RegistryAccess registries) {
        ResourceLocation type = null;
        ResourceLocation output = null;
        Set<ResourceLocation> tags = Set.of();
        try {
            type = KineticRegistries.recipeTypes().id(recipe.getType());
        } catch (RuntimeException e) {
            LOGGER.debug("Could not resolve recipe type for {}", recipe.getId(), e);
        }
        if (registries != null) {
            try {
                ItemStack stack = recipe.getResultItem(registries);
                if (!stack.isEmpty()) {
                    output = KineticRegistries.items().id(stack.getItem());
                    tags = stack.getTags().map(TagKey::location).collect(Collectors.toUnmodifiableSet());
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Could not resolve output for {}", recipe.getId(), e);
            }
        }
        return new RemovalCandidate(recipe.getId(), type, output, tags);
    }

    private record DatapackRecipeReloadListener(RecipeManager recipeManager) implements ResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
            try {
                RecipeConfigStore.Snapshot snapshot = RecipeConfigStore.load(resourceManager);
                applySnapshot(recipeManager, snapshot, resourceManager);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to load RecipeModule datapack resource {}; keeping recipes loaded by Minecraft unchanged",
                        RecipeConfigStore.DATAPACK_RESOURCE,
                        e
                );
            }
        }
    }

    private static Recipe<?> buildRecipe(RecipeRecord record) {
        RecipeRegistry.EditorType type = RecipeRegistry.EditorType.valueOf(record.editorType);
        ResourceLocation id = RecipeConfigStore.memoryRecipeId(record);
        ItemStack output = record.output == null ? ItemStack.EMPTY : record.output.copy();
        if (output.isEmpty()) {
            throw new IllegalArgumentException("recipe output is empty or missing");
        }
        validateRegisteredStack(output, "output");
        if (!record.outputUseNbt || type == RecipeRegistry.EditorType.SMITHING) {
            output.setTag(null);
        }

        return switch (type) {
            case CRAFTING -> record.isShapeless
                    ? buildShapeless(id, record, output)
                    : buildShaped(id, record, output);
            case SMITHING -> new SmithingTransformRecipe(
                    id,
                    ingredient(record, 0),
                    ingredient(record, 1),
                    ingredient(record, 2),
                    output
            );
            case FURNACE -> new SmeltingRecipe(id, "", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 200);
            case BLAST -> new BlastingRecipe(id, "", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 100);
            case SMOKER -> new SmokingRecipe(id, "", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 100);
            case STONECUTTER -> new StonecutterRecipe(id, "", ingredient(record, 0), output);
        };
    }

    private static ShapelessRecipe buildShapeless(ResourceLocation id, RecipeRecord record, ItemStack output) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                ingredients.add(ingredient(record, i));
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Shapeless recipe has no ingredients");
        }
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, output, ingredients);
    }

    private static ShapedRecipe buildShaped(ResourceLocation id, RecipeRecord record, ItemStack output) {
        int minRow = 3;
        int maxRow = -1;
        int minColumn = 3;
        int maxColumn = -1;
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                int row = i / 3;
                int column = i % 3;
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < minRow || maxColumn < minColumn) {
            throw new IllegalArgumentException("Shaped recipe has no ingredients");
        }

        int width = maxColumn - minColumn + 1;
        int height = maxRow - minRow + 1;
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                int sourceIndex = row * 3 + column;
                ItemStack stack = record.inputs.get(sourceIndex);
                if (stack != null && !stack.isEmpty()) {
                    int targetIndex = (row - minRow) * width + (column - minColumn);
                    ingredients.set(targetIndex, ingredient(record, sourceIndex));
                }
            }
        }
        return new ShapedRecipe(id, "", CraftingBookCategory.MISC, width, height, ingredients, output);
    }

    private static Ingredient ingredient(RecipeRecord record, int index) {
        if (index < 0 || index >= record.inputs.size()) {
            return Ingredient.EMPTY;
        }
        ItemStack stack = record.inputs.get(index);
        if (stack == null || stack.isEmpty()) {
            return Ingredient.EMPTY;
        }

        if (stack.getTag() != null && stack.getTag().contains("kt_tag")) {
            String raw = stack.getTag().getString("kt_tag");
            if (!raw.startsWith("#") || raw.length() == 1) {
                throw new IllegalArgumentException("item tag must start with #");
            }
            raw = raw.substring(1);
            TagKey<Item> tag = TagKey.create(Registries.ITEM, KineticResourceIds.parse(raw));
            return Ingredient.of(tag);
        }

        validateRegisteredStack(stack, "ingredient " + index);

        int mode = index < record.inputModes.size() ? record.inputModes.get(index) : 0;
        if (mode == 1) {
            return stack.getTag() == null ? Ingredient.of(stack.getItem()) : PartialNBTIngredient.of(stack.getItem(), stack.getTag());
        }
        if (mode == 2) {
            return StrictNBTIngredient.of(stack);
        }
        return Ingredient.of(stack.getItem());
    }

    public static boolean matchesRemoval(Recipe<?> recipe, List<RemovalEntry> removals, RegistryAccess registryAccess) {
        return !RemovalRuleEvaluator.blockingRules(candidateOf(recipe, registryAccess), removals).isEmpty();
    }
    private static void validateRegisteredStack(ItemStack stack, String role) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException(role + " is empty");
        }
        ResourceLocation itemId = KineticRegistries.items().id(stack.getItem());
        if (itemId == null || !KineticRegistries.items().contains(itemId)) {
            throw new IllegalArgumentException(role + " references an unregistered item");
        }
        Item registered = KineticRegistries.items().get(itemId);
        if (registered == null || registered != stack.getItem()) {
            throw new IllegalArgumentException(role + " references missing item " + itemId);
        }
    }

    private static String recipeLabel(RecipeRecord record) {
        if (record == null) {
            return "<null>";
        }
        if (record.uuid != null && !record.uuid.isBlank()) {
            return record.uuid;
        }
        if (record.output != null && !record.output.isEmpty()) {
            ResourceLocation outputId = KineticRegistries.items().id(record.output.getItem());
            if (outputId != null) {
                return outputId.toString();
            }
        }
        return "<unknown>";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

}

//?}
