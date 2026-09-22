package dev.xyat.contentstudio.loot.server;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.contentstudio.loot.mixin.LootDataManagerAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class LootTableOverrideStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[LootModule]";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path ROOT = KineticPaths.configDirectory().resolve("kineticcore");
    private static final Path OVERRIDES_FILE = ROOT.resolve("loot_overrides.json");
    private static final ResourceLocation GLOBAL_CHEST_APPEND = KineticResourceIds.parse(LootEntryInfo.GLOBAL_CHEST_APPEND_ID);
    private static final Object STORAGE_LOCK = new Object();
    private static final Object LOOT_REFERENCE_LOCK = new Object();
    private static final String GLOBAL_POOL_NAME_PREFIX = "contentstudio_global_append#";
    private static volatile JsonObject storageCache;
    private static volatile boolean storageLoadFailed;
    private static volatile Set<ResourceLocation> automaticNonContainerLootTableCache;
    private static volatile List<GlobalRemoveRule> removedRuleCache = List.of();
    private static volatile Set<ResourceLocation> excludedLootTableCache = Set.of();
    private static volatile Set<LootTable> globalRemovalTargetTables = Set.of();
    private static volatile Set<ResourceLocation> globalRemovalTargetTableIds = Set.of();
    private static volatile boolean excludedLootTableCacheReady;
    private static volatile boolean removedRuleCacheReady;
    private static final ThreadLocal<Boolean> LOAD_EVENT_BYPASS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> LOOT_TABLE_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static List<LootEntryInfo> buildEntries(MinecraftServer server, int mode) {
        List<LootEntryInfo> result = new ArrayList<>();

        if (mode == LootEntryInfo.MODE_ENTITY) {
            KineticRegistries.entityTypes().entries().entrySet().forEach(entry -> {
                ResourceLocation targetId = entry.getKey();
                EntityType<?> type = entry.getValue();
                ResourceLocation lootTable = type.getDefaultLootTable();
                if (isValidLootTable(lootTable)) {
                    result.add(new LootEntryInfo(mode, targetId.toString(), lootTable.toString(), hasOverride(lootTable)));
                }
            });
        } else if (mode == LootEntryInfo.MODE_BLOCK) {
            KineticRegistries.blocks().entries().entrySet().forEach(entry -> {
                ResourceLocation targetId = entry.getKey();
                Block block = entry.getValue();
                ResourceLocation lootTable = block.getLootTable();
                if (isValidLootTable(lootTable)) {
                    result.add(new LootEntryInfo(mode, targetId.toString(), lootTable.toString(), hasOverride(lootTable)));
                }
            });
        } else if (mode == LootEntryInfo.MODE_CHEST) {
            result.add(new LootEntryInfo(
                    mode,
                    LootEntryInfo.GLOBAL_CHEST_APPEND_ID,
                    LootEntryInfo.GLOBAL_CHEST_APPEND_ID,
                    hasGlobalChestAppend()
            ));
            result.add(new LootEntryInfo(
                    mode,
                    LootEntryInfo.GLOBAL_CHEST_REMOVE_ID,
                    LootEntryInfo.GLOBAL_CHEST_REMOVE_ID,
                    hasGlobalRemovedItems()
            ));
            result.add(new LootEntryInfo(
                    mode,
                    LootEntryInfo.GLOBAL_CHEST_EXCLUDE_ID,
                    LootEntryInfo.GLOBAL_CHEST_EXCLUDE_ID,
                    hasGlobalExcludedLootTables()
            ));
            buildChestEntries(server, result);
        }

        result.sort(Comparator
                .comparingInt(LootTableOverrideStore::entryRank)
                .thenComparing(LootEntryInfo::overridden, Comparator.reverseOrder())
                .thenComparing(LootEntryInfo::targetId));
        return result;
    }

    private static int entryRank(LootEntryInfo entry) {
        if (entry.isGlobalChestAppend()) {
            return 0;
        }
        if (entry.isGlobalChestRemove()) {
            return 1;
        }
        if (entry.isGlobalChestExclude()) {
            return 2;
        }
        return 3;
    }

    private static void buildChestEntries(MinecraftServer server, List<LootEntryInfo> result) {
        if (server == null) {
            return;
        }

        server.getResourceManager().listResources(
                "loot_tables",
                id -> id.getPath().endsWith(".json")
        ).forEach((resourceId, resource) -> {
            ResourceLocation lootTableId = lootTableIdFromResource(resourceId);
            if (lootTableId == null) {
                return;
            }

            try (Reader reader = resource.openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (isContainerLootTable(lootTableId, element) && isNotAutomaticallyNonContainerLootTable(lootTableId)) {
                    result.add(new LootEntryInfo(
                            LootEntryInfo.MODE_CHEST,
                            lootTableId.toString(),
                            lootTableId.toString(),
                            hasOverride(lootTableId)
                    ));
                }
            } catch (Exception ignored) {
            }
        });
    }

    public static String readJson(MinecraftServer server, ResourceLocation lootTableId) {
        if (GLOBAL_CHEST_APPEND.equals(lootTableId)) {
            return GSON.toJson(readGlobalChestAppendElement());
        }

        JsonElement override = readOverrideElement(lootTableId);
        if (override != null) {
            return GSON.toJson(override);
        }

        return readResourceJson(server, lootTableId);
    }

    public static String readResourceJson(MinecraftServer server, ResourceLocation lootTableId) {
        if (server == null) {
            return createEmptyTableJson("minecraft:generic");
        }

        ResourceLocation resourceId = lootTableResourceId(lootTableId);
        Optional<Resource> resource = server.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            LOGGER.warn("{} 原始战利品资源不存在 lootTable={} resource={}", LOG_PREFIX, lootTableId, resourceId);
            return createEmptyTableJson("minecraft:generic");
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonElement element = JsonParser.parseReader(reader);
            return GSON.toJson(element);
        } catch (Exception e) {
            LOGGER.error("{} 读取原始战利品失败 lootTable={} reason={}", LOG_PREFIX, lootTableId, exceptionMessage(e), e);
            return createEmptyTableJson("minecraft:generic");
        }
    }

    public static SaveResult save(MinecraftServer server, ResourceLocation lootTableId, String json) {
        if (GLOBAL_CHEST_APPEND.equals(lootTableId)) {
            return saveGlobalChestAppend(server, json);
        }

        try {
            JsonElement element = parseAndValidate(server, lootTableId, json);
            JsonObject previousRoot = editableStorageRoot().deepCopy();
            JsonObject root = previousRoot.deepCopy();
            getTables(root).add(lootTableId.toString(), element);
            commitStorageChange(previousRoot, root, () -> applyLootTableToMemory(server, lootTableId));
            return new SaveResult(true, "msg.contentstudio.loot.loots.save_success", GSON.toJson(element), true);
        } catch (Exception e) {
            LOGGER.error("{} 保存失败 lootTable={} reason={} json={}", LOG_PREFIX, lootTableId, exceptionMessage(e), json, e);
            return new SaveResult(false, "msg.contentstudio.loot.loots.save_failed", json == null ? "" : json, hasOverride(lootTableId));
        }
    }

    private static SaveResult saveGlobalChestAppend(MinecraftServer server, String json) {
        try {
            JsonElement element = parseAndValidate(server, GLOBAL_CHEST_APPEND, json);
            JsonObject object = element.getAsJsonObject();
            object.addProperty("type", "minecraft:chest");
            if (!object.has("pools") || !object.get("pools").isJsonArray()) {
                object.add("pools", new JsonArray());
            }

            JsonObject previousRoot = editableStorageRoot().deepCopy();
            JsonObject root = previousRoot.deepCopy();
            root.add("global_chest_append", object);
            commitStorageChange(previousRoot, root, () -> applyAllChestLootTablesToMemory(server));
            return new SaveResult(true, "msg.contentstudio.loot.loots.global_append.save_success", GSON.toJson(object), hasGlobalChestAppend());
        } catch (Exception e) {
            LOGGER.error("{} 保存全局箱子追加失败 reason={} json={}", LOG_PREFIX, exceptionMessage(e), json, e);
            return new SaveResult(false, "msg.contentstudio.loot.loots.global_append.save_failed", json == null ? "" : json, hasGlobalChestAppend());
        }
    }

    public static String previewResetJson(MinecraftServer server, ResourceLocation lootTableId) {
        if (GLOBAL_CHEST_APPEND.equals(lootTableId)) {
            return GSON.toJson(createEmptyChestTable());
        }
        return readResourceJson(server, lootTableId);
    }

    public static SaveResult reset(MinecraftServer server, ResourceLocation lootTableId) {
        if (GLOBAL_CHEST_APPEND.equals(lootTableId)) {
            try {
                JsonObject previousRoot = editableStorageRoot().deepCopy();
                JsonObject root = previousRoot.deepCopy();
                root.add("global_chest_append", createEmptyChestTable());
                commitStorageChange(previousRoot, root, () -> applyAllChestLootTablesToMemory(server));
                return new SaveResult(true, "msg.contentstudio.loot.loots.global_append.reset_success", GSON.toJson(createEmptyChestTable()), false);
            } catch (Exception e) {
                LOGGER.error("{} 恢复全局箱子追加失败 reason={}", LOG_PREFIX, exceptionMessage(e), e);
                return new SaveResult(false, "msg.contentstudio.loot.loots.global_append.reset_failed", GSON.toJson(readGlobalChestAppendElement()), hasGlobalChestAppend());
            }
        }

        try {
            JsonObject previousRoot = editableStorageRoot().deepCopy();
            JsonObject root = previousRoot.deepCopy();
            getTables(root).remove(lootTableId.toString());
            commitStorageChange(previousRoot, root, () -> applyLootTableToMemory(server, lootTableId));
            String json = readResourceJson(server, lootTableId);
            return new SaveResult(true, "msg.contentstudio.loot.loots.reset_success", json, false);
        } catch (Exception e) {
            LOGGER.error("{} 恢复失败 lootTable={} reason={}", LOG_PREFIX, lootTableId, exceptionMessage(e), e);
            return new SaveResult(false, "msg.contentstudio.loot.loots.reset_failed", readJson(server, lootTableId), hasOverride(lootTableId));
        }
    }

    public static void applyAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        applyAll(server, server.getResourceManager());
    }

    public static void applyAll(MinecraftServer server, ResourceManager resourceManager) {
        if (server == null || resourceManager == null || !hasAnyOverrides()) {
            return;
        }

        Map<ResourceLocation, LootTable> replacements = new HashMap<>();
        boolean applyGlobalChest = hasGlobalChestAppend() || hasGlobalRemovedItems();
        for (ResourceLocation tableId : server.getLootData().getKeys(LootDataType.TABLE)) {
            boolean directOverride = hasOverride(tableId);
            if (!directOverride && !applyGlobalChest) {
                continue;
            }

            JsonElement raw = readResourceElement(resourceManager, tableId);
            JsonElement transformed = transformLootTableJson(resourceManager, tableId, raw);
            if (directOverride || isContainerLootTable(tableId, transformed)) {
                replacements.put(tableId, deserializeLootTable(resourceManager, tableId, transformed));
            }
        }
        replaceLootTables(server.getLootData(), replacements);
        refreshGlobalRemovalTargetTables(server);
    }

    public static boolean hasOverride(ResourceLocation lootTableId) {
        if (GLOBAL_CHEST_APPEND.equals(lootTableId)) {
            return hasGlobalChestAppend();
        }
        if (LootEntryInfo.GLOBAL_CHEST_EXCLUDE_ID.equals(lootTableId.toString())) {
            return hasGlobalExcludedLootTables();
        }
        return readOverrideElement(lootTableId) != null;
    }

    public static boolean hasAnyOverrides() {
        return !getTables(storageRoot()).entrySet().isEmpty() || hasGlobalChestAppend() || hasGlobalRemovedItems();
    }

    public static JsonElement parseAndValidate(MinecraftServer server, ResourceLocation lootTableId, String json) {
        JsonElement element = JsonParser.parseString(Objects.requireNonNullElse(json, "").trim());
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Not a JSON object");
        }

        deserializeLootTable(server, lootTableId, element);
        return element;
    }

    public static JsonElement transformLootTableJson(ResourceManager resourceManager, ResourceLocation lootTableId, JsonElement original) {
        if (lootTableId == null || original == null) {
            return original;
        }

        JsonElement override = readOverrideElement(lootTableId);
        boolean changed = override != null;
        JsonElement working = override == null ? original : override.deepCopy();
        if (!working.isJsonObject()) {
            return working;
        }

        JsonObject object = working.getAsJsonObject();
        if (isContainerLootTable(lootTableId, object) && shouldApplyGlobalChestRules(lootTableId)) {
            JsonArray globalPools = globalAppendPools();
            if (globalPools.size() > 0) {
                if (!changed) {
                    object = object.deepCopy();
                    working = object;
                }
                JsonArray originalPools = object.has("pools") && object.get("pools").isJsonArray()
                        ? object.getAsJsonArray("pools")
                        : new JsonArray();
                JsonArray mergedPools = new JsonArray();
                for (int i = 0; i < globalPools.size(); i++) {
                    JsonElement pool = globalPools.get(i).deepCopy();
                    if (pool.isJsonObject()) {
                        pool.getAsJsonObject().addProperty("name", GLOBAL_POOL_NAME_PREFIX + i);
                    }
                    mergedPools.add(pool);
                }
                for (JsonElement pool : originalPools) {
                    mergedPools.add(pool.deepCopy());
                }
                object.add("pools", mergedPools);
                changed = true;
            }

            Set<ResourceLocation> removedItems = globalDirectRemovedItemSet();
            if (!removedItems.isEmpty()) {
                JsonObject candidate = changed ? object : object.deepCopy();
                if (removeDirectItemsFromTable(candidate, removedItems)) {
                    object = candidate;
                    working = object;
                    changed = true;
                }
            }
        }

        return changed ? working : original;
    }

    public static void invalidateLootReferenceCache() {
        synchronized (LOOT_REFERENCE_LOCK) {
            automaticNonContainerLootTableCache = null;
            globalRemovalTargetTables = Set.of();
            globalRemovalTargetTableIds = Set.of();
        }
    }

    public static boolean isLoadEventBypass() {
        return LOAD_EVENT_BYPASS.get();
    }

    public static void enterLootTableGeneration() {
        LOOT_TABLE_DEPTH.set(LOOT_TABLE_DEPTH.get() + 1);
    }

    public static void exitLootTableGeneration() {
        int depth = LOOT_TABLE_DEPTH.get() - 1;
        if (depth <= 0) {
            LOOT_TABLE_DEPTH.remove();
        } else {
            LOOT_TABLE_DEPTH.set(depth);
        }
    }

    public static boolean shouldSkipGlobalAppendPool(LootPool pool) {
        if (pool == null || LOOT_TABLE_DEPTH.get() <= 1) {
            return false;
        }
        String name = pool.getName();
        return name != null && name.startsWith(GLOBAL_POOL_NAME_PREFIX);
    }

    public static LootTable createLoadEventReplacement(MinecraftServer server, ResourceLocation lootTableId) {
        if (server == null || lootTableId == null || LOAD_EVENT_BYPASS.get()) {
            return null;
        }
        if (GLOBAL_CHEST_APPEND.equals(lootTableId)) {
            return null;
        }

        boolean directOverride = hasOverride(lootTableId);
        boolean globalChestRules = hasGlobalChestAppend() || hasGlobalRemovedItems();
        if (!directOverride && !globalChestRules) {
            return null;
        }

        JsonElement raw = readResourceElement(server, lootTableId);
        if (!directOverride && !isContainerLootTable(lootTableId, raw)) {
            return null;
        }
        JsonElement transformed = transformLootTableJson(server.getResourceManager(), lootTableId, raw);
        if (transformed == raw || transformed.equals(raw)) {
            return null;
        }
        return deserializeLootTable(server, lootTableId, transformed);
    }

    public static boolean isGloballyRemoved(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (GlobalRemoveRule rule : globalRemovedRules()) {
            if (rule.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    public static List<GlobalRemoveRule> getGlobalRemovedRules() {
        return List.copyOf(globalRemovedRules());
    }

    public static GlobalRemoveResult saveGlobalRemovedItems(MinecraftServer server, List<GlobalRemoveRule> rules) {
        try {
            LinkedHashSet<GlobalRemoveRule> validated = new LinkedHashSet<>();
            for (GlobalRemoveRule rule : rules == null ? List.<GlobalRemoveRule>of() : rules) {
                if (rule != null && rule.isValid()) {
                    validated.add(new GlobalRemoveRule(rule.itemId(), rule.mode(), rule.nbt()));
                }
            }

            JsonArray array = new JsonArray();
            for (GlobalRemoveRule rule : validated) {
                JsonObject object = new JsonObject();
                object.addProperty("item", rule.itemId());
                object.addProperty("mode", rule.mode().id());
                if (!rule.nbt().isBlank()) {
                    object.addProperty("nbt", rule.nbt());
                }
                array.add(object);
            }
            JsonObject previousRoot = editableStorageRoot().deepCopy();
            JsonObject root = previousRoot.deepCopy();
            root.add("global_chest_remove", array);
            commitStorageChange(previousRoot, root, () -> applyAllChestLootTablesToMemory(server));
            return new GlobalRemoveResult(true, "msg.contentstudio.loot.loots.global_remove.save_success", getGlobalRemovedRules());
        } catch (Exception e) {
            LOGGER.error("{} 保存全局箱子删除失败 reason={}", LOG_PREFIX, exceptionMessage(e), e);
            return new GlobalRemoveResult(false, "msg.contentstudio.loot.loots.global_remove.save_failed", getGlobalRemovedRules());
        }
    }

    public static List<String> getGlobalExcludedLootTables() {
        List<String> result = new ArrayList<>();
        for (ResourceLocation id : globalExcludedLootTableSet()) {
            result.add(id.toString());
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static GlobalExcludeResult saveGlobalExcludedLootTables(MinecraftServer server, List<String> lootTableIds) {
        try {
            LinkedHashSet<ResourceLocation> validated = new LinkedHashSet<>();
            for (String value : lootTableIds == null ? List.<String>of() : lootTableIds) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                ResourceLocation id = KineticResourceIds.tryParse(value.trim());
                if (id != null) {
                    validated.add(id);
                }
            }

            JsonArray array = new JsonArray();
            for (ResourceLocation id : validated) {
                array.add(id.toString());
            }
            JsonObject previousRoot = editableStorageRoot().deepCopy();
            JsonObject root = previousRoot.deepCopy();
            root.add("global_chest_exclude", array);
            commitStorageChange(previousRoot, root, () -> applyAllChestLootTablesToMemory(server));
            return new GlobalExcludeResult(true, "msg.contentstudio.loot.loots.global_exclude.save_success", getGlobalExcludedLootTables());
        } catch (Exception e) {
            LOGGER.error("{} 保存全局箱子排除表失败 reason={}", LOG_PREFIX, exceptionMessage(e), e);
            return new GlobalExcludeResult(false, "msg.contentstudio.loot.loots.global_exclude.save_failed", getGlobalExcludedLootTables());
        }
    }

    public static boolean isGloballyExcludedLootTable(ResourceLocation lootTableId) {
        return lootTableId != null && globalExcludedLootTableSet().contains(lootTableId);
    }

    private static JsonElement readResourceElement(MinecraftServer server, ResourceLocation lootTableId) {
        return readResourceElement(server.getResourceManager(), lootTableId);
    }

    private static JsonElement readResourceElement(ResourceManager resourceManager, ResourceLocation lootTableId) {
        ResourceLocation resourceId = lootTableResourceId(lootTableId);
        Optional<Resource> resource = resourceManager.getResource(resourceId);
        if (resource.isEmpty()) {
            return createEmptyTable("minecraft:generic");
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            return createEmptyTable("minecraft:generic");
        }
    }

    private static void applyLootTableToMemory(MinecraftServer server, ResourceLocation lootTableId) {
        if (server == null || lootTableId == null) {
            return;
        }
        JsonElement raw = readResourceElement(server, lootTableId);
        JsonElement transformed = transformLootTableJson(server.getResourceManager(), lootTableId, raw);
        LootTable table = deserializeLootTable(server, lootTableId, transformed);
        replaceLootTable(server.getLootData(), lootTableId, table);
        refreshGlobalRemovalTargetTables(server);
    }

    private static void applyAllChestLootTablesToMemory(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Map<ResourceLocation, LootTable> replacements = new HashMap<>();
        for (ResourceLocation tableId : server.getLootData().getKeys(LootDataType.TABLE)) {
            JsonElement raw = readResourceElement(server, tableId);
            JsonElement transformed = transformLootTableJson(server.getResourceManager(), tableId, raw);
            if (isContainerLootTable(tableId, transformed)) {
                replacements.put(tableId, deserializeLootTable(server, tableId, transformed));
            }
        }
        replaceLootTables(server.getLootData(), replacements);
        refreshGlobalRemovalTargetTables(server);
    }

    private static LootTable deserializeLootTable(MinecraftServer server, ResourceLocation lootTableId, JsonElement json) {
        return deserializeLootTable(server.getResourceManager(), lootTableId, json);
    }

    private static LootTable deserializeLootTable(ResourceManager resourceManager, ResourceLocation lootTableId, JsonElement json) {
        boolean previous = LOAD_EVENT_BYPASS.get();
        LOAD_EVENT_BYPASS.set(true);
        try {
            JsonElement safeJson = sanitizeRuntimeLootTable(lootTableId, json);
            try {
                Optional<LootTable> parsed = LootDataType.TABLE.deserialize(lootTableId, safeJson, resourceManager);
                if (parsed.isPresent()) {
                    return parsed.get();
                }
                LOGGER.error("{} 战利品表运行时解析失败，已使用屏障安全表 lootTable={}", LOG_PREFIX, lootTableId);
            } catch (Exception e) {
                LOGGER.error("{} 战利品表运行时解析异常，已使用屏障安全表 lootTable={} reason={}",
                        LOG_PREFIX, lootTableId, exceptionMessage(e), e);
            }
            return LootDataType.TABLE.deserialize(lootTableId, createBarrierFallbackTable(), resourceManager)
                    .orElseThrow(() -> new IllegalStateException("Cannot create safe loot table: " + lootTableId));
        } finally {
            LOAD_EVENT_BYPASS.set(previous);
        }
    }

    private static JsonElement sanitizeRuntimeLootTable(ResourceLocation lootTableId, JsonElement json) {
        if (json == null || json.isJsonNull()) {
            LOGGER.error("{} 战利品表为空，已使用屏障安全表 lootTable={}", LOG_PREFIX, lootTableId);
            return createBarrierFallbackTable();
        }
        JsonElement safe = json.deepCopy();
        int replaced = replaceInvalidItemEntries(safe, lootTableId);
        if (replaced > 0) {
            LOGGER.error("{} 检测到无效战利品物品，运行时已替换为屏障 lootTable={} count={}",
                    LOG_PREFIX, lootTableId, replaced);
        }
        return safe;
    }

    private static int replaceInvalidItemEntries(JsonElement element, ResourceLocation lootTableId) {
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        int replaced = 0;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                replaced += replaceInvalidItemEntries(child, lootTableId);
            }
            return replaced;
        }
        if (!element.isJsonObject()) {
            return 0;
        }

        JsonObject object = element.getAsJsonObject();
        String type = object.has("type") && object.get("type").isJsonPrimitive()
                ? object.get("type").getAsString()
                : "";
        if (("minecraft:item".equals(type) || "item".equals(type)) && object.has("name")) {
            String itemId = object.get("name").isJsonPrimitive() ? object.get("name").getAsString() : "";
            if (isUnregisteredItem(itemId)) {
                LOGGER.error("{} 无效战利品物品已安全替换 lootTable={} item={}", LOG_PREFIX, lootTableId, itemId);
                object.addProperty("name", "minecraft:barrier");
                replaced++;
            }
        }

        for (Map.Entry<String, JsonElement> child : new ArrayList<>(object.entrySet())) {
            replaced += replaceInvalidItemEntries(child.getValue(), lootTableId);
        }
        return replaced;
    }

    private static boolean isUnregisteredItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return true;
        }
        try {
            ResourceLocation id = KineticResourceIds.parse(itemId);
            var item = KineticRegistries.items().get(id);
            return item == null || item == net.minecraft.world.item.Items.AIR;
        } catch (Exception ignored) {
            return true;
        }
    }

    private static JsonObject createBarrierFallbackTable() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:generic");
        JsonArray pools = new JsonArray();
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        JsonArray entries = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", "minecraft:barrier");
        entries.add(entry);
        pool.add("entries", entries);
        pools.add(pool);
        root.add("pools", pools);
        return root;
    }

    private static void replaceLootTable(LootDataManager manager, ResourceLocation lootTableId, LootTable table) {
        replaceLootTables(manager, Map.of(lootTableId, table));
    }

    private static void replaceLootTables(LootDataManager manager, Map<ResourceLocation, LootTable> replacements) {
        if (replacements.isEmpty()) {
            return;
        }
        LootDataManagerAccessor accessor = (LootDataManagerAccessor) manager;
        Map<LootDataId<?>, Object> updated = new HashMap<>();
        accessor.contentstudio_loots$getElements().forEach((key, value) -> updated.put(key, value));
        replacements.forEach((lootTableId, table) ->
                updated.put(new LootDataId<>(LootDataType.TABLE, lootTableId), table));
        accessor.contentstudio_loots$setElements(Map.copyOf(updated));
    }

    public static boolean shouldFilterGlobalRemoval(LootTable table) {
        if (table == null || globalRemovedRules().isEmpty()) {
            return false;
        }
        if (globalRemovalTargetTables.contains(table)) {
            return true;
        }
        ResourceLocation tableId = table.getLootTableId();
        return tableId != null && globalRemovalTargetTableIds.contains(tableId);
    }

    private static void refreshGlobalRemovalTargetTables(MinecraftServer server) {
        if (server == null || globalRemovedRules().isEmpty()) {
            globalRemovalTargetTables = Set.of();
            globalRemovalTargetTableIds = Set.of();
            return;
        }

        Set<LootTable> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        LinkedHashSet<ResourceLocation> targetIds = new LinkedHashSet<>();
        ResourceManager resourceManager = server.getResourceManager();
        for (ResourceLocation tableId : server.getLootData().getKeys(LootDataType.TABLE)) {
            if (!shouldApplyGlobalChestRules(tableId)) {
                continue;
            }
            JsonElement raw = readResourceElement(resourceManager, tableId);
            if (!isContainerLootTable(tableId, raw)) {
                continue;
            }
            LootTable table = server.getLootData().getLootTable(tableId);
            if (table != null) {
                targets.add(table);
                targetIds.add(tableId);
            }
        }
        globalRemovalTargetTables = Collections.unmodifiableSet(targets);
        globalRemovalTargetTableIds = targetIds.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(targetIds);
    }

    private static ResourceLocation lootTableIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix = "loot_tables/";
        String suffix = ".json";
        if (!path.startsWith(prefix) || !path.endsWith(suffix) || path.length() <= prefix.length() + suffix.length()) {
            return null;
        }
        return KineticResourceIds.of(resourceId.getNamespace(), path.substring(prefix.length(), path.length() - suffix.length()));
    }

    private static ResourceLocation lootTableResourceId(ResourceLocation lootTableId) {
        return KineticResourceIds.of(lootTableId.getNamespace(), "loot_tables/" + lootTableId.getPath() + ".json");
    }

    private static boolean shouldApplyGlobalChestRules(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return false;
        }
        return !globalExcludedLootTableSet().contains(lootTableId)
                && isNotAutomaticallyNonContainerLootTable(lootTableId);
    }

    private static boolean isNotAutomaticallyNonContainerLootTable(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return true;
        }
        String path = lootTableId.getPath();
        if (path.startsWith("entities/") || path.contains("/entities/")
                || path.startsWith("blocks/") || path.contains("/blocks/")) {
            return false;
        }

        Set<ResourceLocation> cached = automaticNonContainerLootTableCache;
        if (cached == null) {
            synchronized (LOOT_REFERENCE_LOCK) {
                cached = automaticNonContainerLootTableCache;
                if (cached == null) {
                    LinkedHashSet<ResourceLocation> values = new LinkedHashSet<>();
                    KineticRegistries.entityTypes().values().forEach(type -> {
                        ResourceLocation id = type.getDefaultLootTable();
                        if (isValidLootTable(id)) {
                            values.add(id);
                        }
                    });
                    KineticRegistries.blocks().values().forEach(block -> {
                        ResourceLocation id = block.getLootTable();
                        if (isValidLootTable(id)) {
                            values.add(id);
                        }
                    });
                    cached = Collections.unmodifiableSet(values);
                    automaticNonContainerLootTableCache = cached;
                }
            }
        }
        return !cached.contains(lootTableId);
    }

    private static boolean removeDirectItemsFromTable(JsonObject table, Set<ResourceLocation> removedItems) {
        if (!table.has("pools") || !table.get("pools").isJsonArray()) {
            return false;
        }
        boolean changed = false;
        for (JsonElement poolElement : table.getAsJsonArray("pools")) {
            if (!poolElement.isJsonObject()) {
                continue;
            }
            JsonObject pool = poolElement.getAsJsonObject();
            if (pool.has("entries") && pool.get("entries").isJsonArray()) {
                changed |= removeDirectItemsFromEntries(pool.getAsJsonArray("entries"), removedItems);
            }
        }
        return changed;
    }

    private static boolean removeDirectItemsFromEntries(JsonArray entries, Set<ResourceLocation> removedItems) {
        boolean changed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonElement element = entries.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String type = stringValue(entry.get("type"));
            String name = stringValue(entry.get("name"));
            ResourceLocation itemId = KineticResourceIds.tryParse(name);
            if (("minecraft:item".equals(type) || "item".equals(type))
                    && itemId != null
                    && removedItems.contains(itemId)) {
                entries.remove(i);
                changed = true;
                continue;
            }
            if (entry.has("children") && entry.get("children").isJsonArray()) {
                changed |= removeDirectItemsFromEntries(entry.getAsJsonArray("children"), removedItems);
            }
            if (entry.has("entries") && entry.get("entries").isJsonArray()) {
                changed |= removeDirectItemsFromEntries(entry.getAsJsonArray("entries"), removedItems);
            }
        }
        return changed;
    }

    private static boolean isContainerLootTable(ResourceLocation lootTableId, JsonElement element) {
        if (lootTableId != null) {
            String path = lootTableId.getPath();
            if (path.startsWith("chests/") || path.contains("/chests/")) {
                return true;
            }
        }
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        String type = stringValue(object.get("type"));
        return "minecraft:chest".equals(type) || "chest".equals(type);
    }

    private static JsonElement readOverrideElement(ResourceLocation lootTableId) {
        JsonElement element = getTables(storageRoot()).get(lootTableId.toString());
        return element == null || element.isJsonNull() ? null : element;
    }

    private static JsonElement readGlobalChestAppendElement() {
        JsonObject root = storageRoot();
        JsonElement element = root.get("global_chest_append");
        if (element != null && element.isJsonObject()) {
            return element;
        }
        return createEmptyChestTable();
    }

    private static JsonArray globalAppendPools() {
        JsonElement element = readGlobalChestAppendElement();
        if (!element.isJsonObject()) {
            return new JsonArray();
        }
        JsonObject object = element.getAsJsonObject();
        return object.has("pools") && object.get("pools").isJsonArray()
                ? object.getAsJsonArray("pools")
                : new JsonArray();
    }

    private static boolean hasGlobalChestAppend() {
        return globalAppendPools().size() > 0;
    }

    private static boolean hasGlobalRemovedItems() {
        return !globalRemovedRules().isEmpty();
    }

    private static boolean hasGlobalExcludedLootTables() {
        return !globalExcludedLootTableSet().isEmpty();
    }

    private static Set<ResourceLocation> globalExcludedLootTableSet() {
        if (excludedLootTableCacheReady) {
            return excludedLootTableCache;
        }
        synchronized (STORAGE_LOCK) {
            if (excludedLootTableCacheReady) {
                return excludedLootTableCache;
            }
            LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
            JsonObject root = storageRoot();
            JsonElement element = root.get("global_chest_exclude");
            if (element != null && element.isJsonArray()) {
                for (JsonElement value : element.getAsJsonArray()) {
                    if (!value.isJsonPrimitive()) {
                        continue;
                    }
                    ResourceLocation id = KineticResourceIds.tryParse(value.getAsString());
                    if (id != null) {
                        result.add(id);
                    }
                }
            }
            excludedLootTableCache = Collections.unmodifiableSet(result);
            excludedLootTableCacheReady = true;
            return excludedLootTableCache;
        }
    }

    private static List<GlobalRemoveRule> globalRemovedRules() {
        if (removedRuleCacheReady) {
            return removedRuleCache;
        }
        synchronized (STORAGE_LOCK) {
            if (removedRuleCacheReady) {
                return removedRuleCache;
            }
            List<GlobalRemoveRule> result = new ArrayList<>();
            JsonObject root = storageRoot();
            JsonElement element = root.get("global_chest_remove");
            if (element != null && element.isJsonArray()) {
                for (JsonElement value : element.getAsJsonArray()) {
                    GlobalRemoveRule rule = readGlobalRemoveRule(value);
                    if (rule != null && rule.isValid()) {
                        result.add(rule);
                    }
                }
            }
            removedRuleCache = List.copyOf(result);
            removedRuleCacheReady = true;
            return removedRuleCache;
        }
    }

    private static GlobalRemoveRule readGlobalRemoveRule(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            return GlobalRemoveRule.item(value.getAsString());
        }
        if (!value.isJsonObject()) {
            return null;
        }
        JsonObject object = value.getAsJsonObject();
        String itemId = stringValue(object.get("item"));
        GlobalRemoveRule.MatchMode mode = GlobalRemoveRule.MatchMode.fromId(stringValue(object.get("mode")));
        String nbt = stringValue(object.get("nbt"));
        return new GlobalRemoveRule(itemId, mode, nbt);
    }

    private static Set<ResourceLocation> globalDirectRemovedItemSet() {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (GlobalRemoveRule rule : globalRemovedRules()) {
            if (rule.mode() != GlobalRemoveRule.MatchMode.ITEM) {
                continue;
            }
            ResourceLocation id = rule.itemResourceLocation();
            if (id != null) {
                result.add(id);
            }
        }
        return result.isEmpty() ? Set.of() : Collections.unmodifiableSet(result);
    }

    private static JsonObject storageRoot() {
        JsonObject cached = storageCache;
        if (cached != null) {
            return cached;
        }
        synchronized (STORAGE_LOCK) {
            if (storageCache == null) {
                storageCache = loadStorageRootFromDisk();
                removedRuleCacheReady = false;
                excludedLootTableCacheReady = false;
            }
            return storageCache;
        }
    }

    private static JsonObject editableStorageRoot() {
        JsonObject root = storageRoot();
        if (!storageLoadFailed) {
            return root;
        }
        synchronized (STORAGE_LOCK) {
            if (storageLoadFailed) {
                storageCache = loadStorageRootFromDisk();
                removedRuleCacheReady = false;
                excludedLootTableCacheReady = false;
            }
            if (storageLoadFailed) {
                throw new IllegalStateException("Loot override storage could not be loaded");
            }
            return storageCache;
        }
    }

    private static JsonObject mutableStorageRoot() {
        return editableStorageRoot().deepCopy();
    }

    private static JsonObject loadStorageRootFromDisk() {
        if (!Files.isRegularFile(OVERRIDES_FILE)) {
            storageLoadFailed = false;
            return createStorageRoot();
        }
        try {
            JsonElement element = JsonParser.parseString(Files.readString(OVERRIDES_FILE, StandardCharsets.UTF_8));
            if (element.isJsonObject()) {
                storageLoadFailed = false;
                return normalizeStorageRoot(element.getAsJsonObject());
            }
            storageLoadFailed = true;
            LOGGER.error("{} 战利品配置根节点不是 JSON 对象 file={}", LOG_PREFIX, OVERRIDES_FILE);
        } catch (Exception e) {
            storageLoadFailed = true;
            LOGGER.error("{} 读取独立战利品文件失败 file={} reason={}", LOG_PREFIX, OVERRIDES_FILE, exceptionMessage(e), e);
        }
        return createStorageRoot();
    }

    private static JsonObject normalizeStorageRoot(JsonObject root) {
        if (!root.has("tables") || !root.get("tables").isJsonObject()) {
            root.add("tables", new JsonObject());
        }
        if (!root.has("global_chest_append") || !root.get("global_chest_append").isJsonObject()) {
            root.add("global_chest_append", createEmptyChestTable());
        }
        if (!root.has("global_chest_remove") || !root.get("global_chest_remove").isJsonArray()) {
            root.add("global_chest_remove", new JsonArray());
        }
        if (!root.has("global_chest_exclude") || !root.get("global_chest_exclude").isJsonArray()) {
            root.add("global_chest_exclude", new JsonArray());
        }
        root.addProperty("version", 4);
        return root;
    }

    private static JsonObject createStorageRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 4);
        root.add("tables", new JsonObject());
        root.add("global_chest_append", createEmptyChestTable());
        root.add("global_chest_remove", new JsonArray());
        root.add("global_chest_exclude", new JsonArray());
        return root;
    }

    private static JsonObject getTables(JsonObject root) {
        return root.getAsJsonObject("tables");
    }

    private static void commitStorageChange(JsonObject previousRoot, JsonObject updatedRoot, Runnable applyRuntime) throws IOException {
        writeStorageRoot(updatedRoot);
        try {
            applyRuntime.run();
        } catch (RuntimeException exception) {
            try {
                writeStorageRoot(previousRoot);
                applyRuntime.run();
            } catch (Throwable rollbackError) {
                exception.addSuppressed(rollbackError);
                LOGGER.error("{} 回滚战利品配置失败 reason={}", LOG_PREFIX, exceptionMessage(rollbackError), rollbackError);
            }
            throw exception;
        }
    }

    private static void writeStorageRoot(JsonObject root) throws IOException {
        JsonObject normalized = normalizeStorageRoot(root);
        Files.createDirectories(ROOT);
        Path tmp = ROOT.resolve("loot_overrides.json.tmp");
        Files.writeString(tmp, GSON.toJson(normalized), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, OVERRIDES_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, OVERRIDES_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
        synchronized (STORAGE_LOCK) {
            storageCache = normalized.deepCopy();
            storageLoadFailed = false;
            removedRuleCacheReady = false;
            excludedLootTableCacheReady = false;
        }
    }

    private static boolean isValidLootTable(ResourceLocation id) {
        return id != null && !BuiltInLootTables.EMPTY.equals(id);
    }

    private static JsonObject createEmptyChestTable() {
        return createEmptyTable("minecraft:chest");
    }

    private static JsonObject createEmptyTable(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.add("pools", new JsonArray());
        return object;
    }

    private static String createEmptyTableJson(String type) {
        return GSON.toJson(createEmptyTable(type));
    }

    private static String stringValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static String exceptionMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getName() : message;
    }

    public record SaveResult(boolean success, String messageKey, String json, boolean overridden) {
    }

    public record GlobalRemoveResult(boolean success, String messageKey, List<GlobalRemoveRule> rules) {
    }

    public record GlobalExcludeResult(boolean success, String messageKey, List<String> lootTableIds) {
    }
}
