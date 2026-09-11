package dev.xyat.contentstudio.tooltip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TooltipManager {
    private static final int MAX_ITEMS = 8192;
    private static final int MAX_RULES_PER_ITEM = 512;
    private static final int MAX_TOTAL_RULES = 32768;
    private static final int MAX_TEXT_LENGTH = 32767;

    public static final Gson GSON = new GsonBuilder().create();
    private static final File KJS_DIR = FMLPaths.GAMEDIR.get().resolve("kubejs/client_scripts").toFile();
    private static final File SCRIPT_FILE = new File(KJS_DIR, "tooltipadd.js");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Map<String, List<TooltipRule>> tooltipData = new HashMap<>();

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
        if (!SCRIPT_FILE.exists()) {
            tooltipData = new HashMap<>();
            return true;
        }

        try {
            String content = Files.readString(SCRIPT_FILE.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("let\\s+rules\\s*=\\s*(\\{[\\s\\S]*?});\\s*for\\s*\\(let\\s+id\\s+in\\s+rules\\)").matcher(content);
            if (!matcher.find()) {
                LOGGER.error("Could not locate tooltip rule database in {}", SCRIPT_FILE);
                return false;
            }

            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(matcher.group(1)).getAsJsonObject();
            Map<String, List<TooltipRule>> loaded = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                List<TooltipRule> rules = new ArrayList<>();
                for (com.google.gson.JsonElement ruleElement : entry.getValue().getAsJsonArray()) {
                    com.google.gson.JsonArray array = ruleElement.getAsJsonArray();
                    if (array.size() < 4) return false;
                    TooltipRule rule = new TooltipRule();
                    rule.mode = array.get(0).getAsInt();
                    rule.line = array.get(1).getAsInt();
                    rule.keyCond = array.get(2).getAsInt();
                    rule.text = array.get(3).getAsString();
                    rules.add(rule);
                }
                loaded.put(entry.getKey(), rules);
            }
            if (!isStructurallyValidData(loaded)) {
                LOGGER.error("Rejected invalid tooltip database in {}", SCRIPT_FILE);
                return false;
            }
            tooltipData = loaded;
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to parse KJS script dynamically", e);
            return false;
        }
    }

    public static boolean isValidData(Map<String, List<TooltipRule>> data) {
        if (!isStructurallyValidData(data)) return false;
        for (String itemId : data.keySet()) {
            if (!isValidItemId(itemId)) return false;
        }
        return true;
    }

    private static boolean isStructurallyValidData(Map<String, List<TooltipRule>> data) {
        if (data == null || data.size() > MAX_ITEMS) return false;
        int totalRules = 0;
        for (Map.Entry<String, List<TooltipRule>> entry : data.entrySet()) {
            if (entry.getKey() == null || ResourceLocation.tryParse(entry.getKey().trim()) == null) return false;
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

    private static boolean isValidItemId(String value) {
        if (value == null || value.isBlank()) return false;
        ResourceLocation id = ResourceLocation.tryParse(value.trim());
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return false;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && item != Items.AIR;
    }

    public static boolean saveAndGenerateJS() {
        return saveAndGenerateJS(tooltipData);
    }

    public static boolean saveAndGenerateJS(Map<String, List<TooltipRule>> nextData) {
        if (!isValidData(nextData)) {
            LOGGER.error("Refusing to save invalid tooltip data");
            return false;
        }

        Map<String, List<TooltipRule>> snapshot = copyData(nextData);
        String js = buildScript(snapshot);
        Path target = SCRIPT_FILE.toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");

        try {
            Files.createDirectories(KJS_DIR.toPath());
            Files.writeString(temp, js, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tooltipData = snapshot;
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save tooltip data", e);
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private static Map<String, List<TooltipRule>> copyData(Map<String, List<TooltipRule>> source) {
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

    private static String buildScript(Map<String, List<TooltipRule>> data) {
        StringBuilder js = new StringBuilder();
        js.append("// 注意：此脚本即为 Tooltip 数据库，由 TooltipModule 自动生成并解析，请勿随意修改其代码结构！\n");
        js.append("// WARNING: This script serves as the Tooltip database, auto-generated by TooltipModule. Do not modify its structure!\n\n");
        js.append("ItemEvents.tooltip(e => {\n");
        js.append("  // 数据格式: '物品ID': [ [模式(0覆盖/1追加), 行号, 按键(0无/1Shift/2Alt/3组合), '文本'] ]\n");
        js.append("  let rules = {\n");

        int itemIndex = 0;
        for (Map.Entry<String, List<TooltipRule>> entry : data.entrySet()) {
            js.append("    \"").append(escapeJs(entry.getKey())).append("\": [\n");
            List<TooltipRule> rules = entry.getValue();
            for (int i = 0; i < rules.size(); i++) {
                TooltipRule rule = rules.get(i);
                js.append("      [").append(rule.mode).append(", ").append(rule.line).append(", ")
                        .append(rule.keyCond).append(", \"").append(escapeJs(rule.text)).append("\"]");
                if (i < rules.size() - 1) js.append(",");
                js.append("\n");
            }
            js.append("    ]");
            if (itemIndex < data.size() - 1) js.append(",");
            js.append("\n");
            itemIndex++;
        }
        js.append("  };\n\n");
        js.append("  for (let id in rules) {\n");
        js.append("    e.addAdvanced(id, (item, advanced, textList) => {\n");
        js.append("      rules[id].forEach(r => {\n");
        js.append("        let mode = r[0], line = r[1], key = r[2], text = r[3];\n");
        js.append("        let pass = (key == 0) ||\n");
        js.append("                   (key == 1 && e.shift && !e.alt) ||\n");
        js.append("                   (key == 2 && !e.shift && e.alt) ||\n");
        js.append("                   (key == 3 && e.shift && e.alt);\n");
        js.append("        if (pass) {\n");
        js.append("          let comp = Component.translate(text);\n");
        js.append("          if (mode == 0) {\n");
        js.append("            if (textList.size() > line) textList.set(line, comp);\n");
        js.append("            else textList.add(comp);\n");
        js.append("          } else {\n");
        js.append("            textList.add(comp);\n");
        js.append("          }\n");
        js.append("        }\n");
        js.append("      });\n");
        js.append("    });\n");
        js.append("  }\n");
        js.append("});\n");
        return js.toString();
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
