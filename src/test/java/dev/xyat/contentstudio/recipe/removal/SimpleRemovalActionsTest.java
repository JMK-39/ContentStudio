package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRemovalActionsTest {
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    @Test
    void outputRuleCanBeAddedAndRemovedWithoutChangingExactIdRules() {
        var exact = new RemovalEntry(RemovalMode.RECIPE_ID, "example:crafting", "");
        var selected = SimpleRemovalActions.toggleOutput(id("example:plate"), List.of(exact));
        assertEquals(List.of(new RemovalEntry(RemovalMode.OUTPUT, "example:plate", ""), exact), selected);
        assertEquals(List.of(exact), SimpleRemovalActions.toggleOutput(id("example:plate"), selected));
    }

    @Test
    void outputRuleBlocksIndividualRecipeTogglingUntilItIsRemoved() {
        var output = new RemovalEntry(RemovalMode.OUTPUT, "example:plate", "");
        var recipe = id("example:smelting");
        assertFalse(SimpleRemovalActions.canToggleRecipe(id("example:plate"), List.of(output)));
        assertEquals(List.of(output), SimpleRemovalActions.toggleRecipe(recipe, id("example:plate"), List.of(output)));
        assertEquals(List.of(new RemovalEntry(RemovalMode.RECIPE_ID, recipe.toString(), "")),
                SimpleRemovalActions.toggleRecipe(recipe, id("example:plate"), List.of()));
    }
}
