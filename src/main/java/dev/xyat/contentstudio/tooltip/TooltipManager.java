package dev.xyat.contentstudio.tooltip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TooltipManager {
    private static final int MAX_ITEMS = 8192;
    private static final int MAX_RULES_PER_ITEM = 512;
    private static final int MAX_TOTAL_RULES = 32768;
    private static final int MAX_TEXT_LENGTH = 32767;
    private static final Type RULE_MAP_TYPE = new TypeToken<Map<String, List<TooltipRule>>>() {}.getType();
    private static final Path CONFIG_FILE = KineticPaths.configFile("kineticcore/contentstudio_tooltips.json");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Gson GSON = new GsonBuilder().create();
    private static final Gson FILE_GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Map<String, List<TooltipRule>> tooltipData = new HashMap<>();

    private TooltipManager() {
    }

    public static class TooltipRule {
        public int mode = 1;
        public int line = 0;
        public int keyCond = 0;
        public String text = "";
    }

    public static void load() {
        loadInternal();
    }

    public static boolean loadForEditor() {
        return loadInternal();
    }

    private static boolean loadInternal() {
        if (!Files.exists(CONFIG_FILE)) {
            tooltipData = new HashMap<>();
            return true;
        }

        try {
            JsonElement document = JsonParser.parseString(Files.readString(CONFIG_FILE, StandardCharsets.UTF_8));
            if (!document.isJsonObject()) return false;
            JsonObject root = document.getAsJsonObject();
            if (!root.has("rules") || !root.get("rules").isJsonObject()) return false;
            Map<String, List<TooltipRule>> loaded = GSON.fromJson(root.get("rules"), RULE_MAP_TYPE);
            if (!hasValidStructure(loaded)) {
                LOGGER.error("Rejected invalid tooltip rules in {}", CONFIG_FILE);
                return false;
            }
            tooltipData = copyData(loaded);
            return true;
        } catch (Exception exception) {
            LOGGER.error("Failed to load tooltip rules from {}", CONFIG_FILE, exception);
            return false;
        }
    }

    public static boolean isValidData(Map<String, List<TooltipRule>> data) {
        if (!hasValidStructure(data)) return false;
        for (String itemId : data.keySet()) {
            if (isInvalidItemId(itemId)) return false;
        }
        return true;
    }

    public static boolean hasValidStructure(Map<String, List<TooltipRule>> data) {
        if (data == null || data.size() > MAX_ITEMS) return false;
        int totalRules = 0;
        for (Map.Entry<String, List<TooltipRule>> entry : data.entrySet()) {
            String itemId = entry.getKey();
            if (itemId == null || itemId.isBlank() || !itemId.equals(itemId.trim())
                    || KineticResourceIds.tryParse(itemId) == null) return false;
            List<TooltipRule> rules = entry.getValue();
            if (rules == null || rules.size() > MAX_RULES_PER_ITEM) return false;
            totalRules += rules.size();
            if (totalRules > MAX_TOTAL_RULES) return false;
            for (TooltipRule rule : rules) {
                if (rule == null || (rule.mode != 0 && rule.mode != 1)) return false;
                if (rule.line < 0 || rule.line > 9999) return false;
                if (rule.keyCond < 0 || rule.keyCond > 3) return false;
                if (rule.text == null || rule.text.isBlank() || rule.text.length() > MAX_TEXT_LENGTH) return false;
            }
        }
        return true;
    }

    private static boolean isInvalidItemId(String value) {
        if (value.isBlank() || !value.equals(value.trim())) return true;
        ResourceLocation id = KineticResourceIds.tryParse(value);
        if (id == null || !KineticRegistries.items().contains(id)) return true;
        Item item = KineticRegistries.items().get(id);
        return item == null || item == Items.AIR;
    }

    public static boolean save(Map<String, List<TooltipRule>> nextData) {
        if (!isValidData(nextData)) {
            LOGGER.error("Refusing to save invalid tooltip rules");
            return false;
        }

        Map<String, List<TooltipRule>> snapshot = copyData(nextData);
        JsonObject root = new JsonObject();
        root.add("rules", GSON.toJsonTree(snapshot, RULE_MAP_TYPE));
        Path temp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(temp, FILE_GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            tooltipData = snapshot;
            return true;
        } catch (Exception exception) {
            LOGGER.error("Failed to save tooltip rules to {}", CONFIG_FILE, exception);
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    public static Map<String, List<TooltipRule>> copyData(Map<String, List<TooltipRule>> source) {
        Map<String, List<TooltipRule>> copy = new HashMap<>();
        for (Map.Entry<String, List<TooltipRule>> entry : source.entrySet()) {
            List<TooltipRule> rules = new ArrayList<>();
            for (TooltipRule sourceRule : entry.getValue()) {
                TooltipRule rule = new TooltipRule();
                rule.mode = sourceRule.mode;
                rule.line = sourceRule.line;
                rule.keyCond = sourceRule.keyCond;
                rule.text = sourceRule.text;
                rules.add(rule);
            }
            copy.put(entry.getKey(), rules);
        }
        return copy;
    }
}
