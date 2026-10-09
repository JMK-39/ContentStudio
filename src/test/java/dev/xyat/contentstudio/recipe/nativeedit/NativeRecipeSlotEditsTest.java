package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeSlotEditsTest {
    private static NativeRecipeDocument draft(String json) {
        return new NativeRecipeDocument(JsonParser.parseString(json).getAsJsonObject());
    }
    private static void remove(NativeRecipeDocument document, int index) throws Exception {
        var layout=NativeRecipeLayout.of(document.json(),0);
        Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeSlotEdits")
                .getMethod("remove",NativeRecipeDocument.class,NativeRecipeLayout.class,int.class)
                .invoke(null,document,layout,index);
    }
    @Test void removingAnArrayIngredientKeepsOtherInputsAndMetadata() throws Exception {
        var document=draft("{\"type\":\"powah:energizing\",\"ingredients\":[{\"tag\":\"minecraft:planks\"},{\"item\":\"minecraft:stone\"}],\"result\":{\"item\":\"minecraft:diamond\"},\"energy\":1000}");
        remove(document,0);
        assertEquals(1,document.json().getAsJsonArray("ingredients").size());
        assertEquals("minecraft:stone",document.at(List.of("ingredients","0","item")).getAsString());
        assertEquals(1000,document.at(List.of("energy")).getAsInt());
    }
    @Test void removingOneRepeatedPatternSlotDoesNotRemoveItsNeighbours() throws Exception {
        var document=draft("{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"AA\",\" A\"],\"key\":{\"A\":{\"tag\":\"minecraft:planks\"}},\"result\":{\"item\":\"minecraft:stick\"}}");
        remove(document,1);
        assertEquals("A ",document.at(List.of("pattern","0")).getAsString());
        assertEquals(" A",document.at(List.of("pattern","1")).getAsString());
        assertTrue(document.json().getAsJsonObject("key").has("A"));
    }
    @Test void removingTheLastPatternOccurrenceRemovesItsUnusedKey() throws Exception {
        var document=draft("{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"AB\"],\"key\":{\"A\":\"minecraft:stone\",\"B\":\"minecraft:dirt\"},\"result\":{\"item\":\"minecraft:stick\"}}");
        remove(document,0);
        assertEquals(" B",document.at(List.of("pattern","0")).getAsString());
        assertFalse(document.json().getAsJsonObject("key").has("A"));
        assertTrue(document.json().getAsJsonObject("key").has("B"));
    }
}
