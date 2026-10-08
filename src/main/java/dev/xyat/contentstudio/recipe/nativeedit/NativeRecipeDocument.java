package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Lossless structural draft. Paths contain literal object keys, not escaped JSON pointers. */
public final class NativeRecipeDocument {
    public static final int MAX_CHARS = 12_000;
    private final JsonObject root;

    public NativeRecipeDocument(JsonObject source) {
        if (!source.has("type") || !source.get("type").isJsonPrimitive()
                || !source.getAsJsonPrimitive("type").isString()
                || !source.get("type").getAsString().matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Missing recipe serializer ID");
        validate(source, 0, new int[]{0});
        if (source.toString().length() > MAX_CHARS) throw new IllegalArgumentException("Recipe exceeds editor size limit");
        root = source.deepCopy();
    }

    private static void validate(JsonElement value, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 2048) throw new IllegalArgumentException("Recipe structure exceeds editor limits");
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> validate(e.getValue(), depth + 1, count));
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> validate(e, depth + 1, count));
    }

    public JsonObject json() { return root.deepCopy(); }
    /** Native resource IDs may omit minecraft:. Keep the author's JSON unchanged. */
    public static String serializerId(JsonObject source) {
        String type=source.get("type").getAsString();
        return type.contains(":")?type:"minecraft:"+type;
    }
    public JsonElement at(List<String> path) { return resolve(path).deepCopy(); }
    public List<String> children(List<String> path) {
        JsonElement value = resolve(path);
        if (value.isJsonObject()) return List.copyOf(value.getAsJsonObject().keySet());
        if (value.isJsonArray()) {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < value.getAsJsonArray().size(); i++) keys.add(Integer.toString(i));
            return List.copyOf(keys);
        }
        return List.of();
    }

    public void setPrimitive(List<String> path, String text) {
        JsonElement old = resolve(path);
        if (!old.isJsonPrimitive()) throw new IllegalArgumentException("Select a primitive field");
        JsonPrimitive value = old.getAsJsonPrimitive();
        JsonElement replacement;
        if (value.isBoolean()) {
            if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException("Expected true or false");
            replacement = new JsonPrimitive(Boolean.parseBoolean(text));
        } else if (value.isNumber()) {
            try {
                if (!text.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
                    throw new NumberFormatException();
                BigDecimal number = new BigDecimal(text);
                if (Math.abs((long) number.scale()) > 4096 || number.precision() > 4096) throw new NumberFormatException();
                replacement = JsonParser.parseString(text);
            } catch (RuntimeException e) { throw new IllegalArgumentException("Expected a finite JSON number", e); }
        } else replacement = new JsonPrimitive(text);
        JsonObject candidate = root.deepCopy();
        replace(candidate, path, replacement);
        new NativeRecipeDocument(candidate); // Validate before changing the draft.
        replace(root, path, replacement);
    }

    public void duplicate(List<String> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("Select an array member");
        JsonElement parent = resolve(path.subList(0, path.size() - 1));
        if (!parent.isJsonArray()) throw new IllegalArgumentException("Only array members can be duplicated");
        JsonObject candidate = root.deepCopy();
        resolve(candidate, path.subList(0, path.size() - 1)).getAsJsonArray().add(resolve(path).deepCopy());
        new NativeRecipeDocument(candidate);
        parent.getAsJsonArray().add(resolve(path).deepCopy());
    }

    public void remove(List<String> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot remove the recipe root");
        if (path.equals(List.of("type"))) throw new IllegalArgumentException("Cannot remove the recipe serializer");
        JsonElement parent = resolve(path.subList(0, path.size() - 1));
        String key=path.get(path.size()-1);
        if (parent.isJsonArray()) parent.getAsJsonArray().remove(Integer.parseInt(key));
        else parent.getAsJsonObject().remove(key);
    }

    public void add(List<String> path, String key, JsonElement value) {
        JsonObject candidate=root.deepCopy();
        JsonElement parent=resolve(candidate,path);
        if(parent.isJsonObject()) {
            if(key.isBlank() || key.length()>256 || parent.getAsJsonObject().has(key))
                throw new IllegalArgumentException("Choose a new field name");
            parent.getAsJsonObject().add(key,value.deepCopy());
        } else if(parent.isJsonArray()) parent.getAsJsonArray().add(value.deepCopy());
        else throw new IllegalArgumentException("Select an object or array");
        new NativeRecipeDocument(candidate);
        if(resolve(path).isJsonObject()) resolve(path).getAsJsonObject().add(key,value.deepCopy());
        else resolve(path).getAsJsonArray().add(value.deepCopy());
    }

    private JsonElement resolve(List<String> path) { return resolve(root, path); }
    private static JsonElement resolve(JsonElement value, List<String> path) {
        for (String key : path) {
            if (value.isJsonObject()) value = value.getAsJsonObject().get(key);
            else if (value.isJsonArray()) value = value.getAsJsonArray().get(Integer.parseInt(key));
            else throw new IllegalArgumentException("Invalid field path");
            if (value == null) throw new IllegalArgumentException("Field no longer exists");
        }
        return value;
    }
    private static void replace(JsonObject root, List<String> path, JsonElement value) {
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot replace the recipe root");
        JsonElement parent = resolve(root, path.subList(0, path.size() - 1));
        String key = path.get(path.size() - 1);
        if (parent.isJsonObject()) parent.getAsJsonObject().add(key, value);
        else parent.getAsJsonArray().set(Integer.parseInt(key), value);
    }
}
