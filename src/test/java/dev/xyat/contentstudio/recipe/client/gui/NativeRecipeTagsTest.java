package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeTagsTest {
    @SuppressWarnings("unchecked")
    private static List<String> tags(String json) throws Exception {
        var method=NativeRecipeStacks.class.getDeclaredMethod("tagIds",com.google.gson.JsonElement.class);
        method.setAccessible(true);
        return (List<String>)method.invoke(null,JsonParser.parseString(json));
    }
    @Test void identifiesLegacyAndModernTagIngredients() throws Exception {
        assertEquals(List.of("minecraft:planks"),tags("{\"tag\":\"minecraft:planks\",\"count\":8}"));
        assertEquals(List.of("minecraft:planks"),tags("\"#minecraft:planks\""));
    }
    @Test void retainsTagsInNativeIngredientWrappersAndAlternatives() throws Exception {
        assertEquals(List.of("minecraft:planks","minecraft:logs"),tags("{\"ingredient\":{\"alternatives\":[{\"tag\":\"minecraft:planks\"},\"#minecraft:logs\",\"#minecraft:planks\",{\"item\":\"minecraft:diamond\"}]}}"));
    }
    @Test void neverTreatsCustomItemDataAsAnIngredientTag() throws Exception {
        assertTrue(tags("{\"id\":\"minecraft:diamond\",\"components\":{\"minecraft:custom_data\":{\"tag\":\"minecraft:planks\"}},\"nbt\":{\"tag\":\"minecraft:logs\"}}").isEmpty());
        assertTrue(tags("{\"custom\":{\"tag\":\"minecraft:planks\"}}").isEmpty());
    }
}
