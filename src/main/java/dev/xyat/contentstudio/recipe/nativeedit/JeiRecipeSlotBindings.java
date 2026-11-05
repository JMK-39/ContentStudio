package dev.xyat.contentstudio.recipe.nativeedit;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Matches viewer slots to editable data, without assuming a category's display order. A slot's tokens are its
 * ingredient identifiers ({@code item:}, {@code resource:}) plus the shown stack counts ({@code count:}); counts only
 * separate otherwise identical entries (three shards from one shard) and never make unrelated items look alike.
 */
public final class JeiRecipeSlotBindings {
    private JeiRecipeSlotBindings() { }
    public static boolean isEditableRole(String role) {
        return role.equals("INPUT") || role.equals("OUTPUT");
    }
    public static int[] match(List<Boolean> outputs, List<Set<String>> shown,
                              List<Boolean> candidateOutputs, List<Set<String>> candidates) {
        if (outputs.size() != shown.size() || candidateOutputs.size() != candidates.size())
            throw new IllegalArgumentException("Slot metadata lengths differ");
        int[] result = new int[shown.size()];
        Arrays.fill(result, -1);
        boolean[] used = new boolean[candidates.size()];
        // Exact matches first, so a single-item slot cannot consume a tag's alternative set.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < shown.size(); i++) {
            if (result[i] >= 0 || shown.get(i).isEmpty()) continue;
            int best = -1, score = 0, matches = 0;
            for (int j = 0; j < candidates.size(); j++) {
                if (used[j] || !outputs.get(i).equals(candidateOutputs.get(j))) continue;
                Set<String> values = candidates.get(j);
                boolean exact = shown.get(i).equals(values);
                if (pass == 0 && !exact) continue;
                int overlap = (int) shown.get(i).stream().filter(token -> !token.startsWith("count:") && values.contains(token)).count();
                int weight = exact ? Integer.MAX_VALUE : overlap;
                if (weight > score) { score = weight; best = j; matches = 1; }
                else if (weight > 0 && weight == score) matches++;
            }
            // IDs cannot distinguish different components or NBT. Never guess between paths.
            if (best >= 0 && matches == 1) { result[i] = best; used[best] = true; }
        }
        // Identical inputs (the same material in two sequence steps or ritual pedestals) are shown in recipe order,
        // so they pair with the identical entries in that order. Outputs never pair by order: a wrong guess there would
        // change a different result.
        // A tag shown with fewer items than it holds pairs the same way, by the strongest item overlap.
        for (int i = 0; i < shown.size(); i++) {
            if (result[i] >= 0 || shown.get(i).isEmpty() || outputs.get(i)) continue;
            int best = -1, score = 0;
            for (int j = 0; j < candidates.size(); j++) {
                if (used[j] || candidateOutputs.get(j)) continue;
                Set<String> values = candidates.get(j);
                int weight = shown.get(i).equals(values) ? Integer.MAX_VALUE
                        : (int) shown.get(i).stream().filter(token -> !token.startsWith("count:") && values.contains(token)).count();
                if (weight > score) { score = weight; best = j; }
            }
            if (best >= 0) { result[i] = best; used[best] = true; }
        }
        // A shaped pattern shows one key in many cells. An input cell whose items belong to a single entry, even one
        // already bound, is that entry: editing it changes every cell using the key. A tag key may show fewer items
        // than it holds, so the strongest item overlap decides; a tie leaves the cell unbound.
        for (int i = 0; i < shown.size(); i++) {
            if (result[i] >= 0 || shown.get(i).isEmpty() || outputs.get(i)) continue;
            int best = -1, score = 0, ties = 0;
            for (int j = 0; j < candidates.size(); j++) {
                if (candidateOutputs.get(j)) continue;
                Set<String> values = candidates.get(j);
                int weight = shown.get(i).equals(values) ? Integer.MAX_VALUE
                        : (int) shown.get(i).stream().filter(token -> !token.startsWith("count:") && values.contains(token)).count();
                if (weight > score) { score = weight; best = j; ties = 1; }
                else if (weight > 0 && weight == score) ties++;
            }
            if (best >= 0 && ties == 1) result[i] = best;
        }
        // Some categories show one result twice (in the output slot and again in a preview). A second output slot that
        // exactly matches a single result entry is that same entry; partial matches never bind outputs.
        for (int i = 0; i < shown.size(); i++) {
            if (result[i] >= 0 || shown.get(i).isEmpty() || !outputs.get(i)) continue;
            int only = -1, exact = 0;
            for (int j = 0; j < candidates.size(); j++)
                if (candidateOutputs.get(j) && shown.get(i).equals(candidates.get(j))) { only = j; exact++; }
            if (exact == 1) result[i] = only;
        }
        return result;
    }
}
