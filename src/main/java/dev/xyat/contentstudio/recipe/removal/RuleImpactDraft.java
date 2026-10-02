package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Selection for one broad removal rule over the current original-recipe catalog. */
public final class RuleImpactDraft {
    public enum Selection { ALL, PARTIAL, NONE }

    private final RemovalEntry rule;
    private final Map<ResourceLocation, RemovalCandidate> candidates;
    private final LinkedHashSet<ResourceLocation> selected = new LinkedHashSet<>();

    public RuleImpactDraft(RemovalEntry rule, List<RemovalCandidate> candidates) {
        this.rule = Objects.requireNonNull(rule, "rule");
        if (rule.mode() == RemovalMode.RECIPE_ID) {
            throw new IllegalArgumentException("Impact selection requires a broad removal rule");
        }
        Objects.requireNonNull(candidates, "candidates");
        this.candidates = new LinkedHashMap<>();
        for (RemovalCandidate candidate : candidates) {
            if (candidate != null && candidate.id() != null
                    && RemovalRuleEvaluator.matchesScope(rule, candidate)) {
                this.candidates.putIfAbsent(candidate.id(), candidate);
            }
        }
        Set<ResourceLocation> excluded = new HashSet<>(rule.excludedRecipeIds());
        for (ResourceLocation id : this.candidates.keySet()) if (!excluded.contains(id)) selected.add(id);
    }

    public Selection selectionForOutput(@Nullable ResourceLocation outputId) {
        int total = 0;
        int checked = 0;
        for (RemovalCandidate candidate : candidates.values()) {
            if (Objects.equals(outputId, candidate.outputItemId())) {
                total++;
                if (selected.contains(candidate.id())) checked++;
            }
        }
        if (checked == 0) return Selection.NONE;
        return checked == total ? Selection.ALL : Selection.PARTIAL;
    }

    public void toggleOutput(@Nullable ResourceLocation outputId) {
        List<ResourceLocation> group = new ArrayList<>();
        for (RemovalCandidate candidate : candidates.values()) {
            if (Objects.equals(outputId, candidate.outputItemId())) group.add(candidate.id());
        }
        if (group.isEmpty()) return;
        if (selected.containsAll(group)) group.forEach(selected::remove);
        else selected.addAll(group);
    }

    public void toggleRecipe(ResourceLocation recipeId) {
        if (!candidates.containsKey(recipeId)) return;
        if (!selected.remove(recipeId)) selected.add(recipeId);
    }

    public void selectAll(boolean checked) {
        selected.clear();
        if (checked) selected.addAll(candidates.keySet());
    }

    public List<ResourceLocation> selectedRecipeIds() {
        return candidates.keySet().stream().filter(selected::contains).toList();
    }

    public RemovalEntry buildRangeRule() {
        LinkedHashSet<ResourceLocation> exclusions = new LinkedHashSet<>(rule.excludedRecipeIds());
        for (ResourceLocation id : candidates.keySet()) {
            if (selected.contains(id)) exclusions.remove(id);
            else exclusions.add(id);
        }
        return new RemovalEntry(rule.mode(), rule.value(), rule.comment(), List.copyOf(exclusions));
    }

    public List<RemovalEntry> buildExactRules() {
        return selectedRecipeIds().stream()
                .map(id -> new RemovalEntry(RemovalMode.RECIPE_ID, id.toString(), rule.comment()))
                .toList();
    }
}
