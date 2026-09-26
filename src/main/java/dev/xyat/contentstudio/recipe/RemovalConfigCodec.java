package dev.xyat.contentstudio.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalMode;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

final class RemovalConfigCodec {
    private RemovalConfigCodec() { }

    static JsonArray writeRemovals(List<RemovalEntry> removals) {
        JsonArray result = new JsonArray();
        for (RemovalEntry entry : removals) {
            JsonObject object = new JsonObject();
            object.addProperty("mode", entry.mode().name());
            object.addProperty("value", entry.value());
            if (!entry.comment().isEmpty()) object.addProperty("comment", entry.comment());
            if (!entry.excludedRecipeIds().isEmpty()) {
                JsonArray excluded = new JsonArray();
                entry.excludedRecipeIds().forEach(id -> excluded.add(id.toString()));
                object.add("excluded_recipe_ids", excluded);
            }
            result.add(object);
        }
        return result;
    }

    static List<RemovalEntry> readRemovals(JsonObject root) {
        List<RemovalEntry> removals = new ArrayList<>();
        JsonArray array = root.has("removals") && root.get("removals").isJsonArray()
                ? root.getAsJsonArray("removals") : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            try {
                JsonObject object = element.getAsJsonObject();
                RemovalMode mode = RemovalMode.valueOf(object.has("mode") ? object.get("mode").getAsString() : "OUTPUT");
                String value = object.has("value") ? object.get("value").getAsString() : "";
                String comment = object.has("comment") ? object.get("comment").getAsString() : "";
                if (value.isBlank()) continue;
                List<ResourceLocation> excluded = new ArrayList<>();
                if (object.has("excluded_recipe_ids") && object.get("excluded_recipe_ids").isJsonArray()) {
                    for (JsonElement excludedElement : object.getAsJsonArray("excluded_recipe_ids")) {
                        try {
                            ResourceLocation id = KineticResourceIds.tryParse(excludedElement.getAsString());
                            if (id != null) excluded.add(id);
                        } catch (RuntimeException ignored) {
                            // An invalid exclusion cannot identify a recipe.
                        }
                    }
                }
                removals.add(new RemovalEntry(mode, value, comment, excluded));
            } catch (RuntimeException ignored) {
                // Preserve the legacy behavior of skipping malformed removal entries.
            }
        }
        return removals;
    }
}
