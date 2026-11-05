package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Native item identities include intermediate stacks, tag inputs and NBT variants. */
public final class NativeRecipeSearch {
    private NativeRecipeSearch() { }
    public static boolean matches(String id,JsonObject recipe,String query) {
        return (id+" "+recipe).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
}
