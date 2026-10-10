package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeCreationTest {
    @Test void newRecipeDraftHasItsOwnIdAndKeepsTheFullTemplate() throws Exception {
        var original=JsonParser.parseString("{\"id\":\"goety:source\",\"recipe\":{\"type\":\"goety:ritual\",\"soulCost\":10,\"custom\":{\"keep\":true}},\"catalog\":8,\"revision\":\"hash\",\"edited\":true,\"view_path\":[]}").getAsJsonObject();
        var created=(JsonObject)Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeCreation")
                .getMethod("draft",JsonObject.class,String.class).invoke(null,original,"contentstudio:custom/ritual");
        assertEquals("contentstudio:custom/ritual",created.get("id").getAsString());
        assertTrue(created.get("creating").getAsBoolean());assertFalse(created.get("edited").getAsBoolean());
        created.getAsJsonObject("recipe").addProperty("soulCost",20);
        assertEquals(10,original.getAsJsonObject("recipe").get("soulCost").getAsInt());
        assertTrue(created.getAsJsonObject("recipe").getAsJsonObject("custom").get("keep").getAsBoolean());
    }
    @Test void creationCannotOverwriteAnExistingOrRuntimeRecipe() throws Exception {
        var method=Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeCreation")
                .getMethod("checkTarget",String.class,Set.class);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,"goety:existing",Set.of("goety:existing")));
        assertEquals("ID_EXISTS",failure.getCause().getMessage());
        assertDoesNotThrow(()->method.invoke(null,"contentstudio:new_recipe",Set.of("goety:existing")));
    }
}
