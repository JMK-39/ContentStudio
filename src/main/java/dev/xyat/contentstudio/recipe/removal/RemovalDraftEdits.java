package dev.xyat.contentstudio.recipe.removal;

import java.util.ArrayList;
import java.util.List;

/** Complete local-draft edits used by both impact confirmation modes. */
public final class RemovalDraftEdits {
    private RemovalDraftEdits() { }

    /** Convert the edited range into only the selected exact IDs, preserving every other rule. */
    public static List<RemovalEntry> replaceRangeWithExact(List<RemovalEntry> current,
                                                            RemovalEntry sourceRange,
                                                            List<RemovalEntry> exactRules) {
        if (sourceRange.mode() == RemovalMode.RECIPE_ID) {
            throw new IllegalArgumentException("A range rule is required");
        }
        List<RemovalEntry> result = new ArrayList<>(current);
        result.removeIf(existing -> existing.key().equals(sourceRange.key()));
        for (RemovalEntry exact : exactRules) {
            if (exact.mode() != RemovalMode.RECIPE_ID) {
                throw new IllegalArgumentException("Only exact recipe ID rules are allowed");
            }
            result.removeIf(existing -> existing.key().equals(exact.key()));
            result.add(0, exact);
        }
        return List.copyOf(result);
    }
}
