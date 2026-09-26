package dev.xyat.contentstudio.recipe.removal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OriginalRecipeFilterTest {
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    private static JsonObject json() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "example:serializer");
        return json;
    }

    private static OriginalRecipeCatalog.InspectedRecipe recipe(ResourceLocation recipeId, String outputId) {
        return new OriginalRecipeCatalog.InspectedRecipe(
                new RemovalCandidate(recipeId, id("minecraft:crafting"),
                        outputId == null ? null : id(outputId), Set.of()),
                Optional.empty());
    }

    @Test
    void outputRuleFiltersDatapackRecipesBeforeScriptRecipesAreAdded() {
        ResourceLocation originalId = id("example:original");
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(
                originalId, json(), id("example:other"), json()));

        OriginalRecipeCatalog catalog = OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.OUTPUT, "example:plate", "")),
                (recipeId, value) -> Optional.of(recipe(recipeId,
                        recipeId.equals(originalId) ? "example:plate" : "example:other")));

        assertEquals(Set.of(id("example:other")), source.keySet());
        assertTrue(catalog.entries().get(originalId).removed());

        JsonObject scriptRecipe = json();
        scriptRecipe.addProperty("written_by_script", true);
        source.put(originalId, scriptRecipe);
        assertSame(scriptRecipe, source.get(originalId));
    }

    @Test
    void exactRuleMatchesOnlyOneOriginalRecipeId() {
        ResourceLocation blockedId = id("example:crafting");
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(
                blockedId, json(), id("example:smelting"), json()));

        OriginalRecipeCatalog catalog = OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.RECIPE_ID, blockedId.toString(), "")),
                (recipeId, value) -> Optional.of(recipe(recipeId, "example:plate")));

        assertEquals(Set.of(id("example:smelting")), source.keySet());
        assertEquals(Set.of(blockedId, id("example:smelting")), catalog.entries().keySet());
    }
}
