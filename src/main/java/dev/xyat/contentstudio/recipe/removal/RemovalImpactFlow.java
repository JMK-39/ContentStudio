package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;

/** Collects one complete, version-consistent original catalog before allowing rule edits. */
public final class RemovalImpactFlow {
    public enum PageStatus { PROGRESS, COMPLETE, RESTART }

    private final RemovalEntry rule;
    private final List<RemovalEntry> otherRules;
    private final List<RemovalCandidate> loading = new ArrayList<>();
    private final Map<ResourceLocation, RemovalCandidate> byId = new LinkedHashMap<>();
    private final Set<ResourceLocation> loadedIds = new HashSet<>();
    private int nextPage;
    private int totalPages;
    private long catalogVersion;
    private RuleImpactDraft draft;

    public RemovalImpactFlow(RemovalEntry rule, List<RemovalEntry> currentRules) {
        this.rule = Objects.requireNonNull(rule, "rule");
        if (rule.mode() == RemovalMode.RECIPE_ID) throw new IllegalArgumentException("A range rule is required");
        this.otherRules = List.copyOf(currentRules).stream()
                .filter(entry -> !entry.key().equals(rule.key())).toList();
    }

    public PageStatus acceptPage(int page, int total, long version, boolean stale,
                                 List<RemovalCandidate> candidates) {
        if (stale || (nextPage > 0 && version != catalogVersion)) {
            reset();
            return PageStatus.RESTART;
        }
        if (draft != null || page != nextPage || total < 1 || page >= total
                || (nextPage > 0 && total != totalPages) || candidates.size() > 128) {
            reset();
            return PageStatus.RESTART;
        }
        if (nextPage == 0) {
            totalPages = total;
            catalogVersion = version;
        }
        for (RemovalCandidate candidate : candidates) {
            if (candidate == null || candidate.id() == null || !loadedIds.add(candidate.id())
                    || !RemovalRuleEvaluator.matchesScope(rule, candidate)) {
                reset();
                return PageStatus.RESTART;
            }
            loading.add(candidate);
            byId.put(candidate.id(), candidate);
        }
        nextPage++;
        if (nextPage < totalPages) return PageStatus.PROGRESS;
        draft = new RuleImpactDraft(rule, loading);
        return PageStatus.COMPLETE;
    }

    public void reset() {
        loading.clear();
        byId.clear();
        loadedIds.clear();
        nextPage = 0;
        totalPages = 0;
        catalogVersion = 0L;
        draft = null;
    }

    public boolean isComplete() { return draft != null; }
    public int loadedCount() { return loading.size(); }
    public int nextPage() { return nextPage; }
    public int totalPages() { return totalPages; }
    public long catalogVersion() { return catalogVersion; }

    public List<RemovalCandidate> candidates() {
        requireComplete();
        return List.copyOf(loading);
    }

    public RuleImpactDraft draft() {
        requireComplete();
        return draft;
    }

    public List<RemovalEntry> buildExactRules() {
        requireComplete();
        return draft.buildExactRules();
    }

    public RemovalEntry buildRangeRule(boolean clearStaleExclusions) {
        requireComplete();
        RemovalEntry next = draft.buildRangeRule();
        if (!clearStaleExclusions) return next;
        Set<ResourceLocation> present = Set.copyOf(loadedIds);
        return new RemovalEntry(next.mode(), next.value(), next.comment(),
                next.excludedRecipeIds().stream().filter(present::contains).toList());
    }

    public List<ResourceLocation> staleExcludedRecipeIds() {
        requireComplete();
        return rule.excludedRecipeIds().stream().filter(id -> !loadedIds.contains(id)).toList();
    }

    public List<RemovalEntry> otherBlockingRules(ResourceLocation recipeId) {
        requireComplete();
        RemovalCandidate candidate = byId.get(recipeId);
        return candidate == null ? List.of() : RemovalRuleEvaluator.blockingRules(candidate, otherRules);
    }

    private void requireComplete() {
        if (draft == null) throw new IllegalStateException("Original recipe catalog is still loading");
    }
}
