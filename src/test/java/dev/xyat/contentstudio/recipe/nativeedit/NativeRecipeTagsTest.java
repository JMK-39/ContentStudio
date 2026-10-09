package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeTagsTest {
    private static List<String> tags(String json) {
        return NativeIngredientTags.candidates(JsonParser.parseString(json));
    }
    @Test void identifiesLegacyAndModernTagIngredients() {
        assertEquals(List.of("minecraft:planks"),tags("{\"tag\":\"minecraft:planks\",\"count\":8}"));
        assertEquals(List.of("minecraft:planks"),tags("\"#minecraft:planks\""));
    }
    @Test void retainsTagsInNativeIngredientWrappersAndAlternatives() {
        assertEquals(List.of("minecraft:planks","minecraft:logs"),tags("{\"ingredient\":{\"alternatives\":[{\"tag\":\"minecraft:planks\"},\"#minecraft:logs\",\"#minecraft:planks\",{\"item\":\"minecraft:diamond\"}]}}"));
    }
    @Test void neverTreatsCustomItemDataAsAnIngredientTag() {
        assertTrue(tags("{\"id\":\"minecraft:diamond\",\"components\":{\"minecraft:custom_data\":{\"tag\":\"minecraft:planks\"}},\"nbt\":{\"tag\":\"minecraft:logs\"}}").isEmpty());
        assertTrue(tags("{\"custom\":{\"tag\":\"minecraft:planks\"}}").isEmpty());
    }
}
