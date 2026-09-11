package dev.xyat.contentstudio.loot.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class LootJsonEditUtil {
    private static final Set<String> EDITED_FUNCTIONS = Set.of(
            "set_count",
            "looting_enchant",
            "set_damage",
            "furnace_smelt",
            "enchant_randomly",
            "enchant_with_levels",
            "explosion_decay",
            "set_nbt"
    );

    record Range(double min, double max) {
    }

    private LootJsonEditUtil() {
    }

    static String string(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    static int integer(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    static double decimal(JsonElement element, double fallback) {
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static Range range(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull()) {
            return new Range(fallback, fallback);
        }
        if (element.isJsonPrimitive()) {
            double value = decimal(element, fallback);
            return new Range(value, value);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            double min = decimal(object.get("min"), fallback);
            double max = decimal(object.get("max"), min);
            return new Range(min, max);
        }
        return new Range(fallback, fallback);
    }

    static JsonElement rangeValue(double min, double max) {
        if (Math.abs(min - max) < 0.000001D) {
            return AbstractLootEditorScreen.GSON.toJsonTree(min);
        }
        JsonObject range = new JsonObject();
        range.addProperty("type", "minecraft:uniform");
        range.addProperty("min", min);
        range.addProperty("max", max);
        return range;
    }

    static double chance(JsonObject entry) {
        JsonObject condition = findObject(entry, "conditions", "condition", "random_chance");
        return condition == null ? 1.0D : decimal(condition.get("chance"), 1.0D);
    }

    static void setChance(JsonObject entry, double value) {
        JsonArray conditions = array(entry, "conditions");
        removeMatching(conditions, "condition", "random_chance");
        if (value < 0.999999D) {
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:random_chance");
            condition.addProperty("chance", value);
            conditions.add(condition);
        }
        putOrRemove(entry, "conditions", conditions);
    }

    static Range count(JsonObject entry) {
        JsonObject function = findFunction(entry, "set_count");
        return function == null ? new Range(1, 1) : range(function.get("count"), 1);
    }

    static void setCount(JsonObject entry, int min, int max) {
        JsonArray current = array(entry, "functions");
        JsonArray updated = new JsonArray();
        for (JsonElement element : current) {
            if (element.isJsonObject()
                    && string(element.getAsJsonObject().get("function")).endsWith("set_count")) {
                continue;
            }
            updated.add(element);
        }
        JsonObject function = new JsonObject();
        function.addProperty("function", "minecraft:set_count");
        function.add("count", rangeValue(min, max));
        updated.add(function);
        putOrRemove(entry, "functions", updated);
    }

    static Range damage(JsonObject entry) {
        JsonObject function = findFunction(entry, "set_damage");
        return function == null ? new Range(1, 1) : range(function.get("damage"), 1);
    }

    static void setDamage(JsonObject entry, boolean enabled, double min, double max) {
        setFunctionEnabled(entry, "set_damage", enabled);
        JsonObject function = findFunction(entry, "set_damage");
        if (function != null) {
            function.add("damage", rangeValue(min, max));
        }
    }

    static Range looting(JsonObject entry) {
        JsonObject function = findFunction(entry, "looting_enchant");
        return function == null ? new Range(0, 0) : range(function.get("count"), 0);
    }

    static void setLooting(JsonObject entry, boolean enabled, int min, int max) {
        setFunctionEnabled(entry, "looting_enchant", enabled);
        JsonObject function = findFunction(entry, "looting_enchant");
        if (function != null) {
            function.add("count", rangeValue(min, max));
        }
    }

    static Range enchantmentLevels(JsonObject entry) {
        JsonObject function = findFunction(entry, "enchant_with_levels");
        return function == null ? new Range(1, 30) : range(function.get("levels"), 1);
    }

    static void setEnchantmentLevels(JsonObject entry, boolean enabled, int min, int max) {
        setFunctionEnabled(entry, "enchant_with_levels", enabled);
        JsonObject function = findFunction(entry, "enchant_with_levels");
        if (function != null) {
            function.add("levels", rangeValue(min, max));
        }
    }

    static boolean hasFunction(JsonObject entry, String suffix) {
        return findFunction(entry, suffix) != null;
    }

    static void setItemNbt(JsonObject entry, ItemStack stack) {
        JsonArray current = array(entry, "functions");
        JsonArray updated = new JsonArray();
        CompoundTag tag = stack == null || stack.isEmpty() ? null : stack.getTag();
        if (tag != null && !tag.isEmpty()) {
            JsonObject function = new JsonObject();
            function.addProperty("function", "minecraft:set_nbt");
            function.addProperty("tag", tag.toString());
            updated.add(function);
        }
        for (JsonElement element : current) {
            if (element.isJsonObject()
                    && string(element.getAsJsonObject().get("function")).endsWith("set_nbt")) {
                continue;
            }
            updated.add(element);
        }
        putOrRemove(entry, "functions", updated);
    }

    static String itemNbt(JsonObject entry) {
        JsonObject function = findFunction(entry, "set_nbt");
        return function == null ? "" : string(function.get("tag"));
    }

    static void setItemNbt(JsonObject entry, String nbt) {
        JsonArray current = array(entry, "functions");
        JsonArray updated = new JsonArray();
        String tagText = nbt == null ? "" : nbt.trim();
        if (!tagText.isEmpty() && !tagText.equals("{}")) {
            try {
                tagText = TagParser.parseTag(tagText).toString();
            } catch (Exception ignored) {
                return;
            }
            JsonObject function = new JsonObject();
            function.addProperty("function", "minecraft:set_nbt");
            function.addProperty("tag", tagText);
            updated.add(function);
        }
        for (JsonElement element : current) {
            if (element.isJsonObject()
                    && string(element.getAsJsonObject().get("function")).endsWith("set_nbt")) {
                continue;
            }
            updated.add(element);
        }
        putOrRemove(entry, "functions", updated);
    }

    static void applyItemNbt(JsonObject entry, ItemStack stack) {
        if (entry == null || stack == null || stack.isEmpty()) {
            return;
        }
        JsonObject function = findFunction(entry, "set_nbt");
        if (function == null) {
            return;
        }
        String tagText = string(function.get("tag"));
        if (tagText.isBlank()) {
            return;
        }
        try {
            stack.setTag(TagParser.parseTag(tagText));
        } catch (Exception ignored) {
        }
    }

    static void setFunctionEnabled(JsonObject entry, String suffix, boolean enabled) {
        JsonArray functions = array(entry, "functions");
        if (!enabled) {
            removeMatching(functions, "function", suffix);
            putOrRemove(entry, "functions", functions);
            return;
        }
        if (findFunction(entry, suffix) == null) {
            JsonObject function = new JsonObject();
            function.addProperty("function", "minecraft:" + suffix);
            functions.add(function);
            entry.add("functions", functions);
        }
    }

    static boolean killedByPlayer(JsonObject entry) {
        return findObject(entry, "conditions", "condition", "killed_by_player") != null;
    }

    static void setKilledByPlayer(JsonObject entry, boolean enabled) {
        JsonArray conditions = array(entry, "conditions");
        removeMatching(conditions, "condition", "killed_by_player");
        if (enabled) {
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:killed_by_player");
            conditions.add(condition);
        }
        putOrRemove(entry, "conditions", conditions);
    }

    static boolean requiresFire(JsonObject entry) {
        JsonArray conditions = array(entry, "conditions");
        for (JsonElement element : conditions) {
            if (element.isJsonObject() && isFireCondition(element.getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    static void setRequiresFire(JsonObject entry, boolean enabled) {
        JsonArray conditions = array(entry, "conditions");
        for (int i = conditions.size() - 1; i >= 0; i--) {
            JsonElement element = conditions.get(i);
            if (element.isJsonObject() && isFireCondition(element.getAsJsonObject())) {
                conditions.remove(i);
            }
        }
        if (enabled) {
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:entity_properties");
            condition.addProperty("entity", "this");
            JsonObject flags = new JsonObject();
            flags.addProperty("is_on_fire", true);
            JsonObject predicate = new JsonObject();
            predicate.add("flags", flags);
            condition.add("predicate", predicate);
            conditions.add(condition);
        }
        putOrRemove(entry, "conditions", conditions);
    }

    static int unknownFunctionCount(JsonObject entry) {
        int count = 0;
        for (JsonElement element : array(entry, "functions")) {
            if (!element.isJsonObject()) {
                count++;
                continue;
            }
            String id = string(element.getAsJsonObject().get("function"));
            String suffix = suffix(id);
            if (!EDITED_FUNCTIONS.contains(suffix)) {
                count++;
            }
        }
        return count;
    }

    static List<String> unknownFunctions(JsonObject entry) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array(entry, "functions")) {
            String id = element.isJsonObject() ? string(element.getAsJsonObject().get("function")) : "-";
            if (!EDITED_FUNCTIONS.contains(suffix(id))) {
                result.add(id.isBlank() ? AbstractLootEditorScreen.GSON.toJson(element) : id);
            }
        }
        return result;
    }

    private static JsonObject findFunction(JsonObject entry, String suffix) {
        return findObject(entry, "functions", "function", suffix);
    }

    private static JsonObject findObject(JsonObject owner, String arrayName, String idName, String suffix) {
        for (JsonElement element : array(owner, arrayName)) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (string(object.get(idName)).endsWith(suffix)) {
                    return object;
                }
            }
        }
        return null;
    }

    private static JsonArray array(JsonObject owner, String name) {
        if (owner.has(name) && owner.get(name).isJsonArray()) {
            return owner.getAsJsonArray(name);
        }
        JsonArray array = new JsonArray();
        owner.add(name, array);
        return array;
    }

    private static void removeMatching(JsonArray array, String idName, String suffix) {
        for (int i = array.size() - 1; i >= 0; i--) {
            JsonElement element = array.get(i);
            if (element.isJsonObject() && string(element.getAsJsonObject().get(idName)).endsWith(suffix)) {
                array.remove(i);
            }
        }
    }

    private static void putOrRemove(JsonObject owner, String name, JsonArray array) {
        if (array.isEmpty()) owner.remove(name);
        else owner.add(name, array);
    }

    private static boolean isFireCondition(JsonObject condition) {
        if (!string(condition.get("condition")).endsWith("entity_properties")
                || !"this".equals(string(condition.get("entity")))) {
            return false;
        }
        return AbstractLootEditorScreen.GSON.toJson(condition).contains("is_on_fire")
                && AbstractLootEditorScreen.GSON.toJson(condition).contains("true");
    }

    private static String suffix(String id) {
        int separator = id == null ? -1 : id.indexOf(':');
        return separator >= 0 ? id.substring(separator + 1) : id == null ? "" : id;
    }
}
