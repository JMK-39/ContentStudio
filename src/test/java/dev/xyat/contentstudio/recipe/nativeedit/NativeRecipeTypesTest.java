package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeTypesTest {
    @Test void runtimeTypeWinsOverAThirdPartySerializerForEveryVanillaWorkstation() {
        var json=JsonParser.parseString("{\"type\":\"custom:serializer\"}").getAsJsonObject();
        for(String type:java.util.List.of("crafting","smelting","blasting","smoking","campfire_cooking","smithing","stonecutting"))
            assertEquals("minecraft",NativeRecipeTypes.owner("minecraft:"+type,json));
        assertEquals("create",NativeRecipeTypes.owner("create:assembling",json));
        assertEquals("tacz",NativeRecipeTypes.owner("tacz:gun_smith_table_crafting",json));
    }
    @Test void fallbackUsesTheActiveConditionalSerializerAndNeverTheWrapper() {
        var wrapper=JsonParser.parseString("{\"type\":\"forge:conditional\",\"recipes\":[{\"enabled\":false,\"recipe\":{\"type\":\"minecraft:crafting_shaped\"}},{\"enabled\":true,\"recipe\":{\"type\":\"create:assembling\"}}]}").getAsJsonObject();
        var active=NativeRecipeView.resolve(wrapper,branch->branch.get("enabled").getAsBoolean());
        assertEquals("create",NativeRecipeTypes.owner(null,active.recipe()));
        assertEquals("minecraft",NativeRecipeTypes.owner(null,JsonParser.parseString("{\"type\":\"stonecutting\"}").getAsJsonObject()));
    }
}
