package dev.xyat.contentstudio.recipe.nativeedit;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Matches viewer slots to editable data, without assuming a category's display order. */
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
                int overlap = (int) shown.get(i).stream().filter(values::contains).count();
                int weight = exact ? Integer.MAX_VALUE : overlap;
                if (weight > score) { score = weight; best = j; matches = 1; }
                else if (weight > 0 && weight == score) matches++;
            }
            // IDs cannot distinguish different counts, components or NBT. Never guess between paths.
            if (best >= 0 && matches == 1) { result[i] = best; used[best] = true; }
        }
        return result;
    }
}
