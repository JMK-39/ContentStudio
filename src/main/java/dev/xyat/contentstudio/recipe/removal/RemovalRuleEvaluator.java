package dev.xyat.contentstudio.recipe.removal;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class RemovalRuleEvaluator {
    private RemovalRuleEvaluator() { }

    public static boolean matchesScope(RemovalEntry rule, RemovalCandidate candidate) {
        if (rule == null || candidate == null || rule.mode() == null || rule.value() == null) return false;
        String value = rule.value();
        ResourceLocation parsed = KineticResourceIds.tryParse(value);
        return switch (rule.mode()) {
            case RECIPE_ID -> parsed != null && parsed.equals(candidate.id());
            case MOD -> candidate.id() != null && value.equals(candidate.id().getNamespace());
            case OUTPUT -> parsed != null && parsed.equals(candidate.outputItemId());
            case TAG -> {
                ResourceLocation tag = KineticResourceIds.tryParse(value.startsWith("#") ? value.substring(1) : value);
                yield tag != null && candidate.outputTagIds().contains(tag);
            }
            case TYPE -> parsed != null && parsed.equals(candidate.recipeType());
        };
    }

    public static List<RemovalEntry> matchingRules(RemovalCandidate candidate, List<RemovalEntry> rules) {
        return rules.stream().filter(rule -> matchesScope(rule, candidate)).toList();
    }

    public static List<RemovalEntry> blockingRules(RemovalCandidate candidate, List<RemovalEntry> rules) {
        return matchingRules(candidate, rules).stream()
                .filter(rule -> candidate.id() == null || !rule.excludedRecipeIds().contains(candidate.id())).toList();
    }
}
