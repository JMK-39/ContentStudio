package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeListIconTest {
    @Test void oversizedOriginalRecipeDoesNotPreventBrowsingOtherRecipes() throws Exception {
        var method=Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeListIcon")
                .getMethod("result",com.google.gson.JsonObject.class);
        var large=JsonParser.parseString("{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"notes\":\""+"a".repeat(13_000)+"\"}").getAsJsonObject();
        assertEquals("minecraft:knowledge_book",method.invoke(null,large));
        var normal=large.deepCopy();normal.remove("notes");
        assertEquals("minecraft:stone_slab",method.invoke(null,normal));
    }
}
