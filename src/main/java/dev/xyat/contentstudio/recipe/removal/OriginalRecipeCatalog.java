//? if >=1.21 {
/*
package dev.xyat.contentstudio.recipe.removal;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/^** A snapshot of datapack recipes before script listeners add or replace recipes. *^/
public final class OriginalRecipeCatalog {
    @FunctionalInterface
    public interface Inspector {
        Optional<InspectedRecipe> inspect(ResourceLocation id, JsonElement json);
    }

    public record InspectedRecipe(RemovalCandidate candidate, Optional<RecipeHolder<?>> recipe) {
        public InspectedRecipe {
            recipe = Objects.requireNonNullElse(recipe, Optional.empty());
        }
    }

    public record Entry(RemovalCandidate candidate, Optional<RecipeHolder<?>> recipe,
                        List<RemovalEntry> blockingRules) {
        public Entry {
            recipe = Objects.requireNonNullElse(recipe, Optional.empty());
            blockingRules = List.copyOf(blockingRules);
        }

        public boolean removed() {
            return !blockingRules.isEmpty();
        }
    }

    private final Map<ResourceLocation, Entry> entries;
    private final Map<ResourceLocation, JsonElement> sources;
    private final java.util.Set<ResourceLocation> originalIds;

    private OriginalRecipeCatalog(Map<ResourceLocation, Entry> entries, Map<ResourceLocation, JsonElement> sources, java.util.Set<ResourceLocation> originalIds) {
        this.originalIds = java.util.Set.copyOf(originalIds);
        this.sources = Map.copyOf(sources);
        this.entries = Map.copyOf(entries);
    }

    public static OriginalRecipeCatalog empty() {
        return new OriginalRecipeCatalog(Map.of(), Map.of(), java.util.Set.of());
    }

    public Map<ResourceLocation, JsonElement> sources() {
        Map<ResourceLocation, JsonElement> copy = new LinkedHashMap<>();
        sources.forEach((id, json) -> copy.put(id, json.deepCopy()));
        return copy;
    }

    public java.util.Set<ResourceLocation> originalIds() { return originalIds; }

    public Map<ResourceLocation, Entry> entries() {
        return entries;
    }

    /^** Inspect first and mutate only after a complete snapshot has been evaluated. *^/
    public static OriginalRecipeCatalog filter(Map<ResourceLocation, JsonElement> source,
                                               List<RemovalEntry> rules, Inspector inspector) {
        var originalIds = java.util.Set.copyOf(source.keySet());
        Map<ResourceLocation, Entry> catalog = new LinkedHashMap<>();
        Map<ResourceLocation, JsonElement> originals = new LinkedHashMap<>();
        for (var sourceEntry : source.entrySet()) {
            ResourceLocation id = sourceEntry.getKey();
            if (id.getPath().startsWith("_")) continue;
            Optional<InspectedRecipe> inspected = inspector.inspect(id, sourceEntry.getValue().deepCopy());
            if (inspected.isEmpty()) continue;
            InspectedRecipe value = inspected.get();
            List<RemovalEntry> blocking = RemovalRuleEvaluator.blockingRules(value.candidate(), rules);
            catalog.put(id, new Entry(value.candidate(), value.recipe(), blocking));
            if (value.recipe().isPresent()) originals.put(id, sourceEntry.getValue().deepCopy());
        }
        catalog.forEach((id, entry) -> {
            if (entry.removed()) source.remove(id);
        });
        return new OriginalRecipeCatalog(catalog, originals, originalIds);
    }
}

*///?} else {
package dev.xyat.contentstudio.recipe.removal;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/** A snapshot of datapack recipes before script listeners add or replace recipes. */
public final class OriginalRecipeCatalog {
    @FunctionalInterface
    public interface Inspector {
        Optional<InspectedRecipe> inspect(ResourceLocation id, JsonElement json);
    }

    public record InspectedRecipe(RemovalCandidate candidate, Optional<Recipe<?>> recipe) {
        public InspectedRecipe {
            recipe = Objects.requireNonNullElse(recipe, Optional.empty());
        }
    }

    public record Entry(RemovalCandidate candidate, Optional<Recipe<?>> recipe,
                        List<RemovalEntry> blockingRules) {
        public Entry {
            recipe = Objects.requireNonNullElse(recipe, Optional.empty());
            blockingRules = List.copyOf(blockingRules);
        }

        public boolean removed() {
            return !blockingRules.isEmpty();
        }
    }

    private final Map<ResourceLocation, Entry> entries;
    private final Map<ResourceLocation, JsonElement> sources;
    private final java.util.Set<ResourceLocation> originalIds;

    private OriginalRecipeCatalog(Map<ResourceLocation, Entry> entries, Map<ResourceLocation, JsonElement> sources, java.util.Set<ResourceLocation> originalIds) {
        this.originalIds = java.util.Set.copyOf(originalIds);
        this.sources = Map.copyOf(sources);
        this.entries = Map.copyOf(entries);
    }

    public static OriginalRecipeCatalog empty() {
        return new OriginalRecipeCatalog(Map.of(), Map.of(), java.util.Set.of());
    }

    public Map<ResourceLocation, JsonElement> sources() {
        Map<ResourceLocation, JsonElement> copy = new LinkedHashMap<>();
        sources.forEach((id, json) -> copy.put(id, json.deepCopy()));
        return copy;
    }

    public java.util.Set<ResourceLocation> originalIds() { return originalIds; }

    public Map<ResourceLocation, Entry> entries() {
        return entries;
    }

    /** Inspect first and mutate only after a complete snapshot has been evaluated. */
    public static OriginalRecipeCatalog filter(Map<ResourceLocation, JsonElement> source,
                                               List<RemovalEntry> rules, Inspector inspector) {
        var originalIds = java.util.Set.copyOf(source.keySet());
        Map<ResourceLocation, Entry> catalog = new LinkedHashMap<>();
        Map<ResourceLocation, JsonElement> originals = new LinkedHashMap<>();
        for (var sourceEntry : source.entrySet()) {
            ResourceLocation id = sourceEntry.getKey();
            if (id.getPath().startsWith("_")) continue;
            Optional<InspectedRecipe> inspected = inspector.inspect(id, sourceEntry.getValue().deepCopy());
            if (inspected.isEmpty()) continue;
            InspectedRecipe value = inspected.get();
            List<RemovalEntry> blocking = RemovalRuleEvaluator.blockingRules(value.candidate(), rules);
            catalog.put(id, new Entry(value.candidate(), value.recipe(), blocking));
            if (value.recipe().isPresent()) originals.put(id, sourceEntry.getValue().deepCopy());
        }
        catalog.forEach((id, entry) -> {
            if (entry.removed()) source.remove(id);
        });
        return new OriginalRecipeCatalog(catalog, originals, originalIds);
    }
}

//?}
