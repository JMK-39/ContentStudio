package dev.xyat.contentstudio.recipe.removal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict wire representation for a complete removal-rule draft. */
public final class RemovalStateCodec {
    private RemovalStateCodec() { }

    public static byte[] encodeRules(List<RemovalEntry> rules) {
        JsonArray array = new JsonArray();
        for (RemovalEntry rule : rules) {
            JsonObject object = new JsonObject();
            object.addProperty("mode", rule.mode().name());
            object.addProperty("value", rule.value());
            object.addProperty("comment", rule.comment());
            JsonArray exclusions = new JsonArray();
            rule.excludedRecipeIds().forEach(id -> exclusions.add(id.toString()));
            object.add("excluded_recipe_ids", exclusions);
            array.add(object);
        }
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static List<RemovalEntry> decodeRules(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > RuleTransfer.MAX_BYTES) {
            throw new IllegalArgumentException("Invalid rule draft size");
        }
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) throw new IllegalArgumentException("Expected rule array");
            List<RemovalEntry> result = new ArrayList<>();
            Set<RemovalEntry.Key> keys = new HashSet<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid rule object");
                JsonObject object = element.getAsJsonObject();
                RemovalMode mode = RemovalMode.valueOf(requiredString(object, "mode"));
                String value = requiredString(object, "value");
                if (value.isBlank() || value.length() > 256 || !value.equals(value.trim())) {
                    throw new IllegalArgumentException("Invalid rule value");
                }
                if (mode == RemovalMode.MOD) {
                    ResourceLocation probe = KineticResourceIds.tryParse(value + ":entry");
                    if (probe == null || !probe.getNamespace().equals(value)) {
                        throw new IllegalArgumentException("Invalid mod namespace");
                    }
                } else if (KineticResourceIds.tryParse(value) == null) {
                    throw new IllegalArgumentException("Invalid scope resource ID");
                }
                String comment = object.has("comment") ? object.get("comment").getAsString() : "";
                if (comment.length() > 2048) throw new IllegalArgumentException("Comment too long");
                List<ResourceLocation> exclusions = new ArrayList<>();
                if (object.has("excluded_recipe_ids")) {
                    JsonArray array = object.getAsJsonArray("excluded_recipe_ids");
                    for (JsonElement excluded : array) {
                        ResourceLocation id = KineticResourceIds.tryParse(excluded.getAsString());
                        if (id == null) throw new IllegalArgumentException("Invalid excluded recipe ID");
                        exclusions.add(id);
                    }
                }
                RemovalEntry rule = new RemovalEntry(mode, value, comment, exclusions);
                if (!keys.add(rule.key())) throw new IllegalArgumentException("Duplicate removal rule");
                result.add(rule);
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException("Invalid rule draft", exception);
        }
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return object.get(name).getAsString();
    }
}
