package dev.xyat.contentstudio.recipe.removal;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The recipes supplied by datapacks before any script listener writes to the manager. */
public final class OriginalRecipeCatalog {
    @FunctionalInterface
    public interface Inspector {
        Optional<InspectedRecipe> inspect(ResourceLocation id, JsonElement json);
    }

    public record InspectedRecipe(RemovalCandidate candidate, Optional<Recipe<?>> recipe) {
        public InspectedRecipe {
            recipe = recipe == null ? Optional.empty() : recipe;
        }
    }

    public record Entry(RemovalCandidate candidate, Optional<Recipe<?>> recipe,
                        List<RemovalEntry> blockingRules) {
        public Entry {
            recipe = recipe == null ? Optional.empty() : recipe;
            blockingRules = List.copyOf(blockingRules);
        }

        public boolean removed() {
            return !blockingRules.isEmpty();
        }
    }

    private final Map<ResourceLocation, Entry> entries;

    private OriginalRecipeCatalog(Map<ResourceLocation, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static OriginalRecipeCatalog empty() {
        return new OriginalRecipeCatalog(Map.of());
    }

    public Map<ResourceLocation, Entry> entries() {
        return entries;
    }

    /** Inspect the complete source before changing it, so an inspection error never partially filters a reload. */
    public static OriginalRecipeCatalog filter(Map<ResourceLocation, JsonElement> source,
                                               List<RemovalEntry> rules, Inspector inspector) {
        Map<ResourceLocation, Entry> catalog = new LinkedHashMap<>();
        for (var sourceEntry : source.entrySet()) {
            ResourceLocation id = sourceEntry.getKey();
            if (id.getPath().startsWith("_")) continue;
            Optional<InspectedRecipe> inspected = inspector.inspect(id, sourceEntry.getValue());
            if (inspected.isEmpty()) continue;
            InspectedRecipe value = inspected.get();
            List<RemovalEntry> blocking = RemovalRuleEvaluator.blockingRules(value.candidate(), rules);
            catalog.put(id, new Entry(value.candidate(), value.recipe(), blocking));
        }
        catalog.forEach((id, entry) -> {
            if (entry.removed()) source.remove(id);
        });
        return new OriginalRecipeCatalog(catalog);
    }
}
