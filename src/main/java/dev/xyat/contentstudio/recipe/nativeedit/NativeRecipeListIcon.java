package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** Browsing must still work when an original recipe is too large for the bounded editor. */
public final class NativeRecipeListIcon {
    private NativeRecipeListIcon() { }
    public static String result(JsonObject json) {
        try {
            var document=new NativeRecipeDocument(json);
            for(var slot:NativeRecipeLayout.of(json).slots())if(slot.output()) {
                String id=stackId(document.at(slot.path()));if(!id.isEmpty())return id;
            }
        }catch(RuntimeException unsupported){ }
        return "minecraft:knowledge_book";
    }
    private static String stackId(JsonElement value) {
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())return value.getAsString();
        if(value.isJsonObject())for(String key:List.of("id","item"))if(value.getAsJsonObject().has(key))return stackId(value.getAsJsonObject().get(key));
        return "";
    }
}
