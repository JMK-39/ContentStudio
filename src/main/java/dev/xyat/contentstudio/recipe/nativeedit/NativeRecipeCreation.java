package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonObject;
import java.util.Set;

/** Creation copies a template into a separate recipe; it never overwrites that template. */
public final class NativeRecipeCreation {
    private NativeRecipeCreation() { }
    public static JsonObject draft(JsonObject template,String id) {
        var body=template.deepCopy();body.addProperty("id",id);body.addProperty("creating",true);body.addProperty("edited",false);
        return body;
    }
    public static void checkTarget(String target,Set<String> occupied) {
        if(occupied.contains(target))throw new IllegalArgumentException("ID_EXISTS");
    }
    public static boolean vanilla(String type) {
        return type.startsWith("minecraft:")||type.startsWith("forge:")||type.startsWith("neoforge:");
    }
}
