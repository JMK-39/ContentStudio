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
            RecipeConfigStore.Snapshot snapshot
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
                applySnapshot(recipeManager, snapshot);
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
