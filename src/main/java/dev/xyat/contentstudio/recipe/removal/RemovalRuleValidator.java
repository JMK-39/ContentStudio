package dev.xyat.contentstudio.recipe.removal;

import java.util.List;

public final class RemovalRuleValidator {
    private RemovalRuleValidator() { }

    public static boolean canAdd(RemovalEntry submitted, List<RemovalEntry> existingRules) {
        return submitted != null && existingRules.stream()
                .noneMatch(existing -> existing.key().equals(submitted.key()));
    }
}
