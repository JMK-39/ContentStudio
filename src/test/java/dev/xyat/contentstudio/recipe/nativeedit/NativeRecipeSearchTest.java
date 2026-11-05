package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeSearchTest {
    @Test void assemblyCanBeFoundByItsIntermediateItemInsteadOfOnlyTheFinalPackage() throws Exception {
        var recipe=JsonParser.parseString("{\"type\":\"create:sequenced_assembly\",\"ingredient\":{\"tag\":\"forge:plates/iron\"},\"transitionalItem\":{\"item\":\"kubejs:incomplete_emx_b1\"},\"results\":[{\"item\":\"kubejs:package_emx_b1\"}],\"sequence\":[]}").getAsJsonObject();
        var method=Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeSearch").getMethod("matches",String.class,JsonObject.class,String.class);
        assertEquals(true,method.invoke(null,"create:kjs/37dazy8ngdran6yb6bzbwu50f",recipe,"kubejs:incomplete_emx_b1"));
        assertEquals(true,method.invoke(null,"create:kjs/37dazy8ngdran6yb6bzbwu50f",recipe,"forge:plates/iron"));
        assertEquals(true,method.invoke(null,"create:kjs/37dazy8ngdran6yb6bzbwu50f",recipe,"PACKAGE_EMX_B1"));
        assertEquals(false,method.invoke(null,"create:kjs/37dazy8ngdran6yb6bzbwu50f",recipe,"incomplete_12g"));
    }
}
