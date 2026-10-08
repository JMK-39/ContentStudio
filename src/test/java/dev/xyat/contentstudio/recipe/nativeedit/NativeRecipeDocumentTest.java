package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeDocumentTest {
    @Test void acceptsNativeDefaultMinecraftNamespaceWithoutRewritingTheSource() {
        var source=JsonParser.parseString("{\"type\":\"crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"id\":\"minecraft:button\"}}").getAsJsonObject();
        assertEquals(source,new NativeRecipeDocument(source).json());
        assertEquals("minecraft:crafting_shapeless",NativeRecipeDocument.serializerId(source));
    }
    private static NativeRecipeDocument sample() {
        return new NativeRecipeDocument(JsonParser.parseString("{\"type\":\"farmersdelight:cutting\","
                + "\"ingredients\":[{\"item\":\"minecraft:oak_log\"}],\"result\":[{\"item\":\"minecraft:oak_planks\",\"count\":6,\"chance\":0.125}],"
                + "\"custom/data\":{\"components\":{\"mod:state\":{\"value\":1234567890123456789}}}}").getAsJsonObject());
    }
    @Test void preservesUnknownFieldsAndExactNumbers() {
        var doc = sample();
        var before = doc.json();
        doc.setPrimitive(List.of("result", "0", "count"), "8");
        assertEquals(8, doc.at(List.of("result", "0", "count")).getAsInt());
        assertEquals(before.get("custom/data"), doc.json().get("custom/data"));
        assertEquals("0.125", doc.at(List.of("result", "0", "chance")).getAsString());
    }
    @Test void rejectsMalformedNumbersWithoutChangingDraft() {
        var doc = sample();
        var before = doc.json();
        for (String bad : List.of("NaN", "Infinity", "1e9999999999", "1,2", "", "{\"x\":1}"))
            assertThrows(IllegalArgumentException.class, () -> doc.setPrimitive(List.of("result", "0", "count"), bad));
        assertEquals(before, doc.json());
    }
    @Test void stringsRemainStringsAndKeysAreNotJsonPointers() {
        var doc = sample();
        doc.setPrimitive(List.of("ingredients", "0", "item"), "minecraft:stone");
        assertEquals("minecraft:stone", doc.at(List.of("ingredients", "0", "item")).getAsString());
        assertEquals("1234567890123456789", doc.at(List.of("custom/data", "components", "mod:state", "value")).getAsString());
    }
    @Test void duplicateIsIndependentAndRemovalPreservesSibling() {
        var doc = sample();
        doc.duplicate(List.of("result", "0"));
        doc.setPrimitive(List.of("result", "1", "count"), "2");
        assertEquals(6, doc.at(List.of("result", "0", "count")).getAsInt());
        doc.remove(List.of("result", "0"));
        assertEquals(2, doc.at(List.of("result", "0", "count")).getAsInt());
    }
    @Test void neverExposesMutableBackingJson() {
        var doc = sample();
        doc.json().remove("type");
        doc.at(List.of("result")).getAsJsonArray().remove(0);
        assertTrue(doc.json().has("type"));
        assertEquals(1, doc.at(List.of("result")).getAsJsonArray().size());
    }
    @Test void addsMissingOptionalFieldsAndEmptyArrayMembersWithoutReplacingExistingData() {
        var doc=sample();
        doc.add(List.of("result","0"),"bonus",new com.google.gson.JsonPrimitive(2));
        assertEquals(2,doc.at(List.of("result","0","bonus")).getAsInt());
        assertThrows(IllegalArgumentException.class,()->doc.add(List.of("result","0"),"count",new com.google.gson.JsonPrimitive(0)));
        doc.add(List.of(),"extras",new com.google.gson.JsonArray());
        doc.add(List.of("extras"),"",new com.google.gson.JsonObject());
        assertEquals(1,doc.children(List.of("extras")).size());
        doc.remove(List.of("result","0","bonus"));
        assertFalse(doc.at(List.of("result","0")).getAsJsonObject().has("bonus"));
        assertThrows(IllegalArgumentException.class,()->doc.remove(List.of("type")));
    }
    @Test void rejectsNonRecipeAndExcessiveNesting() {
        assertThrows(IllegalArgumentException.class, () -> new NativeRecipeDocument(JsonParser.parseString("{}").getAsJsonObject()));
        String nested = "{\"type\":\"mod:test\",\"x\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}";
        assertThrows(IllegalArgumentException.class, () -> new NativeRecipeDocument(JsonParser.parseString(nested).getAsJsonObject()));
    }
}
