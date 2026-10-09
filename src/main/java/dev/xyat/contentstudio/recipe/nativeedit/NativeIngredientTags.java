package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Extracts ingredient tag candidates without loading game classes; the caller validates resource IDs. */
public final class NativeIngredientTags {
    private NativeIngredientTags() { }
    public static List<String> candidates(JsonElement json) {
        var ids=new LinkedHashSet<String>();collect(json,ids);return List.copyOf(ids);
    }
    private static void collect(JsonElement value,Set<String> ids) {
        if(value.isJsonArray()){value.getAsJsonArray().forEach(v->collect(v,ids));return;}
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()) {
            String text=value.getAsString();if(text.startsWith("#"))ids.add(text.substring(1));return;
        }
        if(!value.isJsonObject())return;
        var object=value.getAsJsonObject();
        if(object.has("fluid")||object.has("FluidName")||object.has("chemical")||object.has("gas"))return;
        if(object.has("item")&&object.get("item").isJsonObject()){collect(object.get("item"),ids);return;}
        for(String wrapper:List.of("ingredient","base_ingredient","baseIngredient","children","alternatives","items","values","block"))
            if(object.has(wrapper)){collect(object.get(wrapper),ids);return;}
        if(!object.has("id")&&!object.has("item")&&object.has("tag")&&object.get("tag").isJsonPrimitive()&&object.getAsJsonPrimitive("tag").isString())
            ids.add(object.get("tag").getAsString());
    }
}
