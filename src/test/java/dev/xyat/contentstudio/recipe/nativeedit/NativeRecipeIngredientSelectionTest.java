package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeIngredientSelectionTest {
    @Test void wrappedPrimitiveAndAlternativeArraysKeepTheOuterQuantity() throws Exception {
        for(String ingredient:List.of("\"minecraft:iron_ingot\"","[{\"item\":\"minecraft:iron_ingot\"},{\"tag\":\"minecraft:planks\"}]")) {
            var doc=new NativeRecipeDocument(JsonParser.parseString("{\"type\":\"test:recipe\",\"ingredients\":[{\"ingredient\":"+ingredient+",\"count\":2}]}").getAsJsonObject());
            NativeRecipeSlotEdits.select(doc,List.of("ingredients","0"),"minecraft:diamond",false);
            var wrapper=doc.at(List.of("ingredients","0")).getAsJsonObject();
            assertFalse(wrapper.has("item"));
            assertEquals(2,wrapper.get("count").getAsInt());
            var selected=wrapper.get("ingredient");
            assertEquals("minecraft:diamond",selected.isJsonPrimitive()?selected.getAsString():selected.getAsJsonObject().get("item").getAsString());
        }
    }
    @Test void changingATagToAnItemKeepsItsQuantityAndOtherFields() throws Exception {
        var doc=new NativeRecipeDocument(JsonParser.parseString("{\"type\":\"powah:energizing\",\"ingredients\":[{\"ingredient\":{\"tag\":\"minecraft:planks\"},\"count\":4,\"custom\":true}]}").getAsJsonObject());
        Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeSlotEdits").getMethod("select",NativeRecipeDocument.class,List.class,String.class,boolean.class)
                .invoke(null,doc,List.of("ingredients","0"),"minecraft:diamond",false);
        assertEquals("minecraft:diamond",doc.at(List.of("ingredients","0","ingredient","item")).getAsString());
        assertFalse(doc.at(List.of("ingredients","0","ingredient")).getAsJsonObject().has("tag"));
        assertEquals(4,doc.at(List.of("ingredients","0","count")).getAsInt());
        assertTrue(doc.at(List.of("ingredients","0","custom")).getAsBoolean());
    }
    @Test void changingAnOutputPreservesItsNativeComponentsAndChance() throws Exception {
        var doc=new NativeRecipeDocument(JsonParser.parseString("{\"type\":\"create:mixing\",\"results\":[{\"id\":\"minecraft:diamond\",\"components\":{\"minecraft:custom_data\":{\"keep\":1}},\"chance\":0.5}]}").getAsJsonObject());
        Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeSlotEdits").getMethod("select",NativeRecipeDocument.class,List.class,String.class,boolean.class)
                .invoke(null,doc,List.of("results","0"),"minecraft:emerald",false);
        assertEquals("minecraft:emerald",doc.at(List.of("results","0","id")).getAsString());
        assertEquals(0.5,doc.at(List.of("results","0","chance")).getAsDouble());
        assertEquals(1,doc.at(List.of("results","0","components","minecraft:custom_data","keep")).getAsInt());
    }
}
