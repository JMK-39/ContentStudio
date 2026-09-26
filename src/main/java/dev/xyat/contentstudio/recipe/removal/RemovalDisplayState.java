package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** The draft decision for one recipe from the server's original-recipe catalog. */
public record RemovalDisplayState(Status status, List<RemovalEntry> matchingRules,
                                  List<RemovalEntry> blockingRules) {
    public enum Status { ACTIVE, REMOVED, EXCLUDED }

    public RemovalDisplayState {
        matchingRules = List.copyOf(matchingRules);
        blockingRules = List.copyOf(blockingRules);
    }

    public static RemovalDisplayState of(RemovalCandidate candidate, List<RemovalEntry> rules) {
        List<RemovalEntry> matching = RemovalRuleEvaluator.matchingRules(candidate, rules);
        List<RemovalEntry> blocking = RemovalRuleEvaluator.blockingRules(candidate, rules);
        Status status = !blocking.isEmpty() ? Status.REMOVED
                : !matching.isEmpty() ? Status.EXCLUDED : Status.ACTIVE;
        return new RemovalDisplayState(status, matching, blocking);
    }

    /** Recover only this original recipe while retaining broad rules for all other IDs. */
    public static List<RemovalEntry> restore(RemovalCandidate candidate, List<RemovalEntry> rules) {
        if (candidate == null || candidate.id() == null) return List.copyOf(rules);
        List<RemovalEntry> result = new ArrayList<>(rules.size());
        for (RemovalEntry rule : rules) {
            if (!RemovalRuleEvaluator.matchesScope(rule, candidate)) {
                result.add(rule);
            } else if (rule.mode() != RemovalMode.RECIPE_ID) {
                LinkedHashSet<ResourceLocation> exclusions = new LinkedHashSet<>(rule.excludedRecipeIds());
                exclusions.add(candidate.id());
                result.add(new RemovalEntry(rule.mode(), rule.value(), rule.comment(), List.copyOf(exclusions)));
            }
        }
        return List.copyOf(result);
    }

    /** Reapply every matching rule; create an exact rule when no broad scope matches. */
    public static List<RemovalEntry> remove(RemovalCandidate candidate, List<RemovalEntry> rules) {
        if (candidate == null || candidate.id() == null) return List.copyOf(rules);
        List<RemovalEntry> result = new ArrayList<>(rules.size() + 1);
        boolean matching = false;
        for (RemovalEntry rule : rules) {
            if (!RemovalRuleEvaluator.matchesScope(rule, candidate)) {
                result.add(rule);
                continue;
            }
            matching = true;
            if (!rule.excludedRecipeIds().contains(candidate.id())) {
                result.add(rule);
                continue;
            }
            LinkedHashSet<ResourceLocation> exclusions = new LinkedHashSet<>(rule.excludedRecipeIds());
            exclusions.remove(candidate.id());
            result.add(new RemovalEntry(rule.mode(), rule.value(), rule.comment(), List.copyOf(exclusions)));
        }
        if (!matching) result.add(0, new RemovalEntry(RemovalMode.RECIPE_ID, candidate.id().toString(), ""));
        return List.copyOf(result);
    }
}
