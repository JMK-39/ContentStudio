package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeSessionTest {
    @Test void successfulCreateKeepsTheDraftReadyForAnotherSaveUnderItsExistingId() throws Exception {
        var body=JsonParser.parseString("{\"id\":\"pack:existing_id\",\"recipe\":{\"type\":\"minecraft:stonecutting\",\"result\":\"minecraft:stone_slab\",\"count\":2},\"catalog\":3,\"revision\":\"old\",\"edited\":false,\"creating\":true}").getAsJsonObject();
        var session=new NativeRecipeFieldsPage.Session(body,()->{});session.target="pack:kept_id";
        var saved=body.deepCopy();saved.addProperty("id","pack:kept_id");saved.addProperty("catalog",4);saved.addProperty("revision","new");saved.addProperty("edited",true);saved.addProperty("added",true);
        session.saved(saved);
        assertEquals("pack:kept_id",session.id);assertEquals("pack:kept_id",session.target);
        assertEquals(4,session.catalog);assertEquals("new",session.revision);
        assertFalse(session.creating);assertTrue(session.edited);assertTrue(session.added);
        assertEquals(session.document.json().toString(),session.original);
    }
}
