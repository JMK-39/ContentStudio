package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonObject;

/** Recipe IDs are identity only; grouping comes from the registered runtime type or active serializer. */
public final class NativeRecipeTypes {
    private NativeRecipeTypes() { }
    public static String owner(String registeredType,JsonObject activeRecipe) {
        String type=registeredType==null||registeredType.isBlank()?NativeRecipeDocument.serializerId(activeRecipe):registeredType;
        return type.split(":",2)[0];
    }
}
