package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Draft edits for the two new-rule actions exposed by the simplified editor. */
public final class SimpleRemovalActions {
    private SimpleRemovalActions() { }

    public static List<RemovalEntry> toggleOutput(ResourceLocation outputId, List<RemovalEntry> rules) {
        if (outputId == null) return List.copyOf(rules);
        var key = new RemovalEntry.Key(RemovalMode.OUTPUT, outputId.toString());
        List<RemovalEntry> result = new ArrayList<>(rules);
        if (!result.removeIf(rule -> rule.key().equals(key))) {
            result.add(0, new RemovalEntry(RemovalMode.OUTPUT, outputId.toString(), ""));
        }
        return List.copyOf(result);
    }

    public static boolean canToggleRecipe(ResourceLocation outputId, List<RemovalEntry> rules) {
        return outputId != null && rules.stream().noneMatch(rule ->
                rule.mode() == RemovalMode.OUTPUT && outputId.toString().equals(rule.value()));
    }

    public static List<RemovalEntry> toggleRecipe(ResourceLocation recipeId, ResourceLocation outputId,
                                                  List<RemovalEntry> rules) {
        if (recipeId == null || !canToggleRecipe(outputId, rules)) return List.copyOf(rules);
        var key = new RemovalEntry.Key(RemovalMode.RECIPE_ID, recipeId.toString());
        List<RemovalEntry> result = new ArrayList<>(rules);
        if (!result.removeIf(rule -> rule.key().equals(key))) {
            result.add(0, new RemovalEntry(RemovalMode.RECIPE_ID, recipeId.toString(), ""));
        }
        return List.copyOf(result);
    }
}
