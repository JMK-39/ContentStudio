package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeListIconTest {
    private static com.google.gson.JsonElement preview(com.google.gson.JsonObject recipe) throws Exception {
        return NativeRecipeListIcon.preview(recipe);
    }
    @Test void browsingRetainsTheCompleteNativeOutputInsteadOfOnlyItsRegistryId() throws Exception {
        for(String output:java.util.List.of(
                "{\"item\":\"tacz:modern_kinetic_gun\",\"count\":2,\"nbt\":{\"GunId\":\"tacz:ak47\"}}",
                "{\"id\":\"minecraft:diamond\",\"count\":3,\"components\":{\"minecraft:custom_name\":\"Named output\"}}",
                "{\"type\":\"gun\",\"id\":\"tacz:ak47\",\"attachments\":{\"scope\":\"tacz:scope\"}}")) {
            var recipe=JsonParser.parseString("{\"type\":\"tacz:gun_smith_table_crafting\",\"result\":"+output+"}").getAsJsonObject();
            assertEquals(recipe.get("result"),preview(recipe));
        }
    }
    @Test void stonecuttingPreviewRetainsTheRecipeLevelOutputCount() throws Exception {
        var recipe=JsonParser.parseString("{\"type\":\"minecraft:stonecutting\",\"ingredient\":\"minecraft:stone\",\"result\":\"minecraft:stone_slab\",\"count\":4}").getAsJsonObject();
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:stone_slab\",\"count\":4}"),preview(recipe));
        assertTrue(recipe.get("result").isJsonPrimitive(),"Preview preparation must not modify recipe source");
    }
    @Test void oversizedOriginalRecipeDoesNotPreventBrowsingOtherRecipes() throws Exception {
        var method=Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeListIcon")
                .getMethod("result",com.google.gson.JsonObject.class);
        var large=JsonParser.parseString("{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"notes\":\""+"a".repeat(13_000)+"\"}").getAsJsonObject();
        assertEquals("minecraft:knowledge_book",method.invoke(null,large));
        var normal=large.deepCopy();normal.remove("notes");
        assertEquals("minecraft:stone_slab",method.invoke(null,normal));
    }
}
