package dev.xyat.contentstudio.villager.config;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.util.IMerchantOfferAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class VillagerConfig {
    public static final String WANDERING_TRADER_ID = "minecraft:wandering_trader";

    private static final Path CONFIG_PATH = KineticPaths.configDirectory().resolve("kineticcore/villager.toml");
    private static CommentedFileConfig configData;

    public static int villagerTickInterval = 100;
    public static boolean enableVillagerTradeUpdateProtection = true;
    public static boolean enableVillagerTradeLateOverride = false;

    public static final Set<String> VILLAGER_FOLLOW_ITEMS_CACHE = new HashSet<>();
    public static boolean enableVillagerFollow = true;
    public static List<String> villagerFollowItems = new ArrayList<>();

    public static boolean enableCustomVillagerTrades = true;
    public static List<String> villagerTradeGroups = new ArrayList<>();
    public static List<String> villagerTradeOffers = new ArrayList<>();
    public static List<String> villagerDefaultTradeOverrides = new ArrayList<>();

    private static final Map<String, TradeGroup> TRADE_GROUP_CACHE = new HashMap<>();
    private static final Map<String, List<TradeOfferData>> TRADE_OFFER_CACHE = new HashMap<>();
    private static final Map<String, VanillaTradeOverride> VANILLA_OVERRIDE_CACHE = new HashMap<>();

    public static void load() {

        try {
            if (configData != null) {
                configData.close();
            }

            configData = CommentedFileConfig.builder(CONFIG_PATH)
                    .sync()
                    .preserveInsertionOrder()
                    .writingMode(WritingMode.REPLACE)
                    .build();

            configData.load();
            readValues();
            normalizeTradeLists();
            writeCompleteConfig();
        } catch (Exception e) {
            VillagerModule.LOGGER.error("VillagerConfig Load Failed", e);
            if (configData != null) {
                try {
                    configData.close();
                } catch (Exception closeException) {
                    VillagerModule.LOGGER.debug("Failed to close broken villager config", closeException);
                }
                configData = null;
            }
        }
    }

    private static void writeCompleteConfig() {
        configData.clear();

        configData.set("villager.tickInterval", villagerTickInterval);
        configData.setComment("villager", """
         村民优化、行为控制与自定义交易设置。交易修改通过编辑器保存后会实时应用到标准村民交易池。
         Villager optimization, behavior control, and custom trade settings. Trade changes are applied live to the standard villager trade pool when saved from the editor.""");
        configData.setComment("villager.tickInterval", """
          降低村民的 AI 检测与寻路频率（单位：Tick，20 Tick = 1 秒）。默认为 100（5秒）。
          Reduces the frequency of Villager AI checks and pathfinding. Unit: ticks. 20 ticks = 1 second. Default is 100.
          设为 1 则恢复原版频率。
          Set to 1 to restore vanilla frequency.""");

        configData.set("villager.trade_update_protection", enableVillagerTradeUpdateProtection);
        configData.setComment("villager.trade_update_protection", """
          交易/升级保护。开启后，被脑叶切除的村民在交易升级倒计时期间仍会执行最低限度更新。
          Trade/level-up protection. When enabled, lobotomized villagers still run the minimal merchant update needed for vanilla trade level-up timers.""");

        configData.set("villager.trade_late_override", enableVillagerTradeLateOverride);
        configData.setComment("villager.trade_late_override", """
          村民交易后覆盖模式。开启后不会使用 Tick 轮询，而是在村民/流浪商人的交易刷新完成后最后一次应用 ContentStudio 交易规则，用于覆盖其它模组在刷新过程中动态追加的交易。
          Villager trade late-override mode. Does not poll every tick. When enabled, ContentStudio applies its trade rules once after villager/wandering-trader trade refresh completes, so dynamically appended trades from other mods can be overridden.""");

        configData.set("villager.follow_enable", enableVillagerFollow);
        configData.setComment("villager.follow_enable", """
          是否允许村民跟随手持特定物品的玩家。
          Whether villagers can follow players holding configured items.""");

        configData.set("villager.follow_items", villagerFollowItems);
        configData.setComment("villager.follow_items", """
          村民跟随的物品 ID 列表。
          Item IDs that make villagers follow players.""");

        configData.set("villager.trade_groups", villagerTradeGroups);
        configData.setComment("villager.trade_groups", """
          职业等级交易规则列表。格式：职业ID|等级|模式|抽取数量。
          流浪商人使用 minecraft:wandering_trader，等级 1 表示普通交易，等级 2 表示稀有交易。
          Profession-level trade rules. Format: profession_id|level|mode|offer_count.
          Wandering trader uses minecraft:wandering_trader. Level 1 means generic trades, level 2 means rare trades.
          模式：add、replace_level、replace_all、disable_level。
          Modes: add, replace_level, replace_all, disable_level.""");

        configData.set("villager.trade_offers", villagerTradeOffers);
        configData.setComment("villager.trade_offers", """
          具体自定义交易列表。格式：
          职业ID|等级|买入A物品|买入A数量|买入A_NBT|买入B物品|买入B数量|买入B_NBT|卖出物品|卖出数量|卖出NBT|最大交易次数|村民经验|价格倍率|需求值|特殊价格|是否给玩家经验|当前已使用次数|是否允许补货|权重
          Custom trade offer list. Format:
          profession_id|level|buy_a_item|buy_a_count|buy_a_nbt|buy_b_item|buy_b_count|buy_b_nbt|sell_item|sell_count|sell_nbt|max_uses|villager_xp|price_multiplier|demand|special_price|reward_player_xp|uses|allow_restock|weight.""");

        configData.set("villager.default_trade_overrides", villagerDefaultTradeOverrides);
        configData.setComment("villager.default_trade_overrides", """
          原版交易覆盖规则。格式：职业ID|等级|原版交易序号|是否启用|权重。
          关闭某条原版交易后，自定义选择池不会再抽到它。权重越高越容易出现。
          Vanilla trade override rules. Format: profession_id|level|vanilla_trade_index|enabled|weight.
          Disabled vanilla trades are removed from the custom selection pool. Higher weight means more likely to appear.""");

        configData.save();
    }

    private static void readValues() {
        villagerTickInterval = configData.getOrElse("villager.tickInterval", 100);
        if (villagerTickInterval < 1) {
            villagerTickInterval = 1;
        }

        enableVillagerTradeUpdateProtection = configData.getOrElse("villager.trade_update_protection", true);
        enableVillagerTradeLateOverride = configData.getOrElse("villager.trade_late_override", false);
        enableVillagerFollow = configData.getOrElse("villager.follow_enable", true);
        villagerFollowItems = new ArrayList<>(configData.getOrElse(
                "villager.follow_items",
                Arrays.asList("minecraft:emerald", "minecraft:emerald_block")
        ));

        VILLAGER_FOLLOW_ITEMS_CACHE.clear();
        for (String itemId : villagerFollowItems) {
            String id = clean(itemId);
            if (!id.isEmpty()) {
                VILLAGER_FOLLOW_ITEMS_CACHE.add(id);
            }
        }

        enableCustomVillagerTrades = true;
        villagerTradeGroups = new ArrayList<>(configData.getOrElse("villager.trade_groups", new ArrayList<>()));
        villagerTradeOffers = new ArrayList<>(configData.getOrElse("villager.trade_offers", new ArrayList<>()));
        villagerDefaultTradeOverrides = new ArrayList<>(configData.getOrElse("villager.default_trade_overrides", new ArrayList<>()));

        rebuildTradeCache();
    }

    public static boolean isVillagerFollowItem(ItemStack stack) {
        if (!enableVillagerFollow || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        return id != null && VILLAGER_FOLLOW_ITEMS_CACHE.contains(id.toString());
    }

    public static void save() {
        if (configData == null) {
            load();
        }
        if (configData == null) {
            throw new IllegalStateException("Villager config is not loaded");
        }

        Path backupPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".save-backup");
        boolean hadOriginal = Files.exists(CONFIG_PATH);
        try {
            if (hadOriginal) {
                Files.copy(CONFIG_PATH, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            normalizeTradeLists();
            writeCompleteConfig();
            readValues();
            try {
                Files.deleteIfExists(backupPath);
            } catch (Exception cleanupException) {
                VillagerModule.LOGGER.debug("Failed to remove villager config save backup", cleanupException);
            }
        } catch (Exception exception) {
            restoreAfterFailedSave(backupPath, hadOriginal);
            throw new IllegalStateException("Failed to save villager config", exception);
        }
    }

    private static void restoreAfterFailedSave(Path backupPath, boolean hadOriginal) {
        try {
            if (configData != null) {
                configData.close();
            }
        } catch (Exception closeException) {
            VillagerModule.LOGGER.debug("Failed to close villager config before rollback", closeException);
        }
        configData = null;

        try {
            if (hadOriginal && Files.exists(backupPath)) {
                Files.move(backupPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(backupPath);
            }
        } catch (Exception restoreFileException) {
            VillagerModule.LOGGER.error("Failed to restore villager config file after save failure", restoreFileException);
        }

        try {
            configData = CommentedFileConfig.builder(CONFIG_PATH)
                    .sync()
                    .preserveInsertionOrder()
                    .writingMode(WritingMode.REPLACE)
                    .build();
            configData.load();
            readValues();
            normalizeTradeLists();
        } catch (Exception reloadException) {
            VillagerModule.LOGGER.error("Failed to reload villager config after save failure", reloadException);
            if (configData != null) {
                try {
                    configData.close();
                } catch (Exception ignored) {
                }
                configData = null;
            }
        }
    }

    public static void replaceTradeLists(
            List<String> groups,
            List<String> offers,
            List<String> overrides
    ) {
        villagerTradeGroups = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
        villagerTradeOffers = offers == null ? new ArrayList<>() : new ArrayList<>(offers);
        villagerDefaultTradeOverrides = overrides == null ? new ArrayList<>() : new ArrayList<>(overrides);
        normalizeTradeLists();
    }

    public static void normalizeTradeLists() {
        List<String> groups = new ArrayList<>();
        for (String line : villagerTradeGroups) {
            TradeGroup group = TradeGroup.parse(line);
            if (group != null) {
                groups.add(group.toConfigLine());
            }
        }

        List<String> offers = new ArrayList<>();
        for (String line : villagerTradeOffers) {
            TradeOfferData data = TradeOfferData.parse(line, offers.size());
            if (data != null) {
                offers.add(data.toConfigLine());
            }
        }

        List<String> overrides = new ArrayList<>();
        for (String line : villagerDefaultTradeOverrides) {
            VanillaTradeOverride override = VanillaTradeOverride.parse(line);
            if (override != null) {
                overrides.add(override.toConfigLine());
            }
        }

        villagerTradeGroups = groups;
        villagerTradeOffers = offers;
        villagerDefaultTradeOverrides = overrides;
        rebuildTradeCache();
    }

    public static TradeGroup getTradeGroup(String profession, int level) {
        return TRADE_GROUP_CACHE.get(buildTradeKey(profession, level));
    }


    public static void setTradeGroup(TradeGroup newGroup) {
        if (newGroup == null || newGroup.profession.isEmpty()) {
            return;
        }
        boolean replaced = false;
        for (int i = 0; i < villagerTradeGroups.size(); i++) {
            TradeGroup group = TradeGroup.parse(villagerTradeGroups.get(i));
            if (group != null && group.matches(newGroup.profession, newGroup.level)) {
                villagerTradeGroups.set(i, newGroup.toConfigLine());
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            villagerTradeGroups.add(newGroup.toConfigLine());
        }
        rebuildTradeCache();
    }


    public static List<TradeOfferData> getTradeOffers(String profession, int level) {
        List<TradeOfferData> offers = TRADE_OFFER_CACHE.get(buildTradeKey(profession, level));
        return offers == null ? new ArrayList<>() : new ArrayList<>(offers);
    }

    public static boolean hasCustomTradeId(MerchantOffer offer, String id) {
        Object offerObject = offer;
        if (offerObject instanceof IMerchantOfferAccess offerAccess) {
            return id != null && id.equals(offerAccess.contentstudio_villager$getCustomTradeId());
        }
        return false;
    }


    public static int addTradeOffer(TradeOfferData data) {
        if (data == null || data.profession().isEmpty()) {
            return -1;
        }
        villagerTradeOffers.add(data.withIndex(villagerTradeOffers.size()).toConfigLine());
        rebuildTradeCache();
        return villagerTradeOffers.size() - 1;
    }

    public static void setTradeOfferAt(int index, TradeOfferData data) {
        if (data == null || index < 0 || index >= villagerTradeOffers.size()) {
            return;
        }
        villagerTradeOffers.set(index, data.withIndex(index).toConfigLine());
        rebuildTradeCache();
    }

    public static void removeTradeOfferAt(int index) {
        if (index < 0 || index >= villagerTradeOffers.size()) {
            return;
        }
        villagerTradeOffers.remove(index);
        rebuildTradeCache();
    }


    public static VanillaTradeOverride getVanillaTradeOverride(String profession, int level, int vanillaIndex) {
        return VANILLA_OVERRIDE_CACHE.get(buildVanillaOverrideKey(profession, level, vanillaIndex));
    }

    public static boolean hasVanillaTradeOverrides(String profession, int level) {
        String prefix = buildTradeKey(profession, level) + "|";
        for (String key : VANILLA_OVERRIDE_CACHE.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVanillaTradeEnabled(String profession, int level, int vanillaIndex) {
        VanillaTradeOverride override = getVanillaTradeOverride(profession, level, vanillaIndex);
        return override == null || override.enabled();
    }

    public static int getVanillaTradeWeight(String profession, int level, int vanillaIndex) {
        VanillaTradeOverride override = getVanillaTradeOverride(profession, level, vanillaIndex);
        return override == null ? 1 : override.weight();
    }

    public static void setVanillaTradeOverride(VanillaTradeOverride override) {
        if (override == null || override.profession().isEmpty()) {
            return;
        }
        boolean replaced = false;
        for (int i = 0; i < villagerDefaultTradeOverrides.size(); i++) {
            VanillaTradeOverride old = VanillaTradeOverride.parse(villagerDefaultTradeOverrides.get(i));
            if (old != null && old.matches(override.profession(), override.level(), override.vanillaIndex())) {
                villagerDefaultTradeOverrides.set(i, override.toConfigLine());
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            villagerDefaultTradeOverrides.add(override.toConfigLine());
        }
        rebuildTradeCache();
    }

    public static void setVanillaTradeEnabled(String profession, int level, int vanillaIndex, boolean enabled) {
        int weight = getVanillaTradeWeight(profession, level, vanillaIndex);
        setVanillaTradeOverride(new VanillaTradeOverride(profession, level, vanillaIndex, enabled, weight));
    }

    private static void rebuildTradeCache() {
        TRADE_GROUP_CACHE.clear();
        TRADE_OFFER_CACHE.clear();
        VANILLA_OVERRIDE_CACHE.clear();

        for (String line : villagerTradeGroups) {
            TradeGroup group = TradeGroup.parse(line);
            if (group != null) {
                TRADE_GROUP_CACHE.put(buildTradeKey(group.profession, group.level), group);
            }
        }

        int offerIndex = 0;
        for (String line : villagerTradeOffers) {
            TradeOfferData data = TradeOfferData.parse(line, offerIndex);
            if (data != null) {
                TRADE_OFFER_CACHE.computeIfAbsent(buildTradeKey(data.profession(), data.level()), key -> new ArrayList<>()).add(data);
                offerIndex++;
            }
        }

        for (String line : villagerDefaultTradeOverrides) {
            VanillaTradeOverride override = VanillaTradeOverride.parse(line);
            if (override != null) {
                VANILLA_OVERRIDE_CACHE.put(buildVanillaOverrideKey(override.profession(), override.level(), override.vanillaIndex()), override);
            }
        }
    }

    private static String buildTradeKey(String profession, int level) {
        String owner = clean(profession);
        return owner + "|" + clampTradeLevel(owner, level);
    }

    private static String buildVanillaOverrideKey(String profession, int level, int vanillaIndex) {
        return buildTradeKey(profession, level) + "|" + Math.max(0, vanillaIndex);
    }

    public static int clampTradeLevel(String profession, int level) {
        return WANDERING_TRADER_ID.equals(clean(profession)) ? clampInt(level, 1, 2) : clampInt(level, 1, 5);
    }

    public static boolean isWanderingTrader(String profession) {
        return WANDERING_TRADER_ID.equals(clean(profession));
    }

    public static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int parseIntValue(String value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        try {
            return clampInt(Integer.parseInt(value.trim()), min, max);
        } catch (Exception e) {
            return fallback;
        }
    }


    public static float parseFloatValue(String value, float fallback, float min, float max) {
        if (value == null) {
            return fallback;
        }
        try {
            return clampFloat(Float.parseFloat(value.trim()), min, max);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean parseBooleanValue(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> fallback;
        };
    }

    public static boolean areValidFollowItems(List<String> values) {
        if (values == null || values.size() > 4096) return false;
        for (String value : values) {
            if (isInvalidItemId(value, false)) return false;
        }
        return true;
    }

    public static boolean areValidTradeLists(List<String> groups, List<String> offers, List<String> overrides) {
        if (groups == null || offers == null || overrides == null) return false;
        if (groups.size() > 8192 || offers.size() > 8192 || overrides.size() > 8192) return false;
        for (String line : groups) {
            if (!isValidTradeGroupLine(line)) return false;
        }
        for (String line : offers) {
            if (!isValidTradeOfferLine(line)) return false;
        }
        for (String line : overrides) {
            if (!isValidVanillaOverrideLine(line)) return false;
        }
        return true;
    }

    private static boolean isValidTradeGroupLine(String line) {
        List<String> parts = splitConfigLine(line);
        if (parts.size() != 4 || isInvalidProfession(parts.get(0))) return false;
        Integer level = strictInt(parts.get(1), 1, 5);
        Integer count = strictInt(parts.get(3), 0, 64);
        if (level == null || count == null || level != clampTradeLevel(parts.get(0), level)) return false;
        String mode = clean(parts.get(2));
        return mode.equals("add") || mode.equals("replace_level") || mode.equals("replace_all") || mode.equals("disable_level");
    }

    private static boolean isValidVanillaOverrideLine(String line) {
        List<String> parts = splitConfigLine(line);
        if (parts.size() != 5 || isInvalidProfession(parts.get(0))) return false;
        Integer level = strictInt(parts.get(1), 1, 5);
        if (level == null || level != clampTradeLevel(parts.get(0), level)) return false;
        if (strictInt(parts.get(2), 0, 999999) == null) return false;
        if (isNotStrictBoolean(parts.get(3))) return false;
        return strictInt(parts.get(4), 0, 999999) != null;
    }

    private static boolean isValidTradeOfferLine(String line) {
        List<String> parts = splitConfigLine(line);
        if ((parts.size() != 19 && parts.size() != 20) || isInvalidProfession(parts.get(0))) return false;
        Integer level = strictInt(parts.get(1), 1, 5);
        if (level == null || level != clampTradeLevel(parts.get(0), level)) return false;
        if (isInvalidItemId(parts.get(2), false) || strictInt(parts.get(3), 1, 64) == null || isInvalidNbt(parts.get(4))) return false;

        Integer buyBCount = strictInt(parts.get(6), 0, 64);
        if (buyBCount == null || isInvalidNbt(parts.get(7))) return false;
        if (buyBCount > 0 && isInvalidItemId(parts.get(5), false)) return false;
        if (buyBCount == 0 && isInvalidItemId(parts.get(5), true)) return false;

        if (isInvalidItemId(parts.get(8), false) || strictInt(parts.get(9), 1, 64) == null || isInvalidNbt(parts.get(10))) return false;
        if (strictInt(parts.get(11), 0, 999999) == null) return false;
        if (strictInt(parts.get(12), 0, 999999) == null) return false;
        if (strictFloat(parts.get(13), 0.0F, 1000.0F) == null) return false;
        if (strictInt(parts.get(14), -999999, 999999) == null) return false;
        if (strictInt(parts.get(15), -999999, 999999) == null) return false;
        if (isNotStrictBoolean(parts.get(16))) return false;
        if (strictInt(parts.get(17), 0, 999999) == null) return false;
        if (isNotStrictBoolean(parts.get(18))) return false;
        return parts.size() == 19 || strictInt(parts.get(19), 0, 999999) != null;
    }

    private static boolean isInvalidProfession(String value) {
        String profession = clean(value);
        if (WANDERING_TRADER_ID.equals(profession)) return false;
        ResourceLocation id = KineticResourceIds.tryParse(profession);
        return id == null || !KineticRegistries.villagerProfessions().contains(id);
    }

    private static boolean isInvalidItemId(String value, boolean allowEmpty) {
        String itemId = clean(value);
        if (allowEmpty && (itemId.isEmpty() || itemId.equals("air") || itemId.equals("minecraft:air"))) return false;
        ResourceLocation id = KineticResourceIds.tryParse(itemId);
        if (id == null || !KineticRegistries.items().contains(id)) return true;
        Item item = KineticRegistries.items().get(id);
        return item == null || item == Items.AIR;
    }

    private static boolean isInvalidNbt(String value) {
        String nbt = value == null ? "" : value.trim();
        if (nbt.length() > 32767) return true;
        if (nbt.isEmpty() || nbt.equals("{}")) return false;
        try {
            TagParser.parseTag(nbt);
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private static Integer strictInt(String value, int min, int max) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static Float strictFloat(String value, float min, float max) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value.trim());
            return Float.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean isNotStrictBoolean(String value) {
        if (value == null) return true;
        String cleanValue = value.trim().toLowerCase(Locale.ROOT);
        return !cleanValue.equals("true") && !cleanValue.equals("false");
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    public static String stackNbt(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null || stack.getTag().isEmpty()) {
            return "";
        }
        return stack.getTag().toString();
    }

    private static ItemStack createStack(String itemId, int count, String nbt) {
        String id = clean(itemId);
        if (id.isEmpty() || id.equals("minecraft:air") || id.equals("air")) {
            return ItemStack.EMPTY;
        }

        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }

        Item item = KineticRegistries.items().get(location);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, clampInt(count, 1, 64));
        String nbtText = nbt == null ? "" : nbt.trim();
        if (!nbtText.isEmpty() && !nbtText.equals("{}")) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtText);
                stack.setTag(tag);
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    public static List<String> splitConfigLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        if (line == null) {
            return parts;
        }

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '|') {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        if (escaped) {
            current.append('\\');
        }
        parts.add(current.toString());
        return parts;
    }

    public static String escapeConfigPart(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    public static class TradeGroup {
        public String profession;
        public int level;
        public String mode;
        public int offerCount;

        public TradeGroup(String profession, int level, String mode, int offerCount) {
            this.profession = clean(profession);
            this.level = clampTradeLevel(this.profession, level);
            this.mode = normalizeMode(mode);
            this.offerCount = clampInt(offerCount, 0, 64);
        }

        public static TradeGroup parse(String line) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }

            List<String> parts = splitConfigLine(line);
            if (parts.size() < 4) {
                return null;
            }

            String profession = clean(parts.get(0));
            if (profession.isEmpty()) {
                return null;
            }

            int level = clampTradeLevel(profession, parseIntValue(parts.get(1), 1, 1, 5));
            String mode = normalizeMode(parts.get(2));
            int count = parseIntValue(parts.get(3), 2, 0, 64);
            return new TradeGroup(profession, level, mode, count);
        }


        public boolean replaceAll() {
            return mode.equals("replace_all");
        }

        public boolean disableLevel() {
            return mode.equals("disable_level");
        }

        public boolean matches(String profession, int level) {
            String owner = clean(profession);
            return this.profession.equals(owner) && this.level == clampTradeLevel(owner, level);
        }

        public String toConfigLine() {
            return escapeConfigPart(profession) + "|" + level + "|" + mode + "|" + offerCount;
        }

        public static String normalizeMode(String mode) {
            return switch (clean(mode)) {
                case "replace", "replace_level" -> "replace_level";
                case "replace_all", "clear_all" -> "replace_all";
                case "disable", "disable_level" -> "disable_level";
                default -> "add";
            };
        }
    }

    public record VanillaTradeOverride(
            String profession,
            int level,
            int vanillaIndex,
            boolean enabled,
            int weight
    ) {
        public VanillaTradeOverride {
            profession = clean(profession);
            level = clampTradeLevel(profession, level);
            vanillaIndex = Math.max(0, vanillaIndex);
            weight = clampInt(weight, 0, 999999);
        }

        public static VanillaTradeOverride parse(String line) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            List<String> parts = splitConfigLine(line);
            if (parts.size() < 5) {
                return null;
            }
            String profession = clean(parts.get(0));
            if (profession.isEmpty()) {
                return null;
            }
            return new VanillaTradeOverride(
                    profession,
                    parseIntValue(parts.get(1), 1, 1, 5),
                    parseIntValue(parts.get(2), 0, 0, 999999),
                    parseBooleanValue(parts.get(3), true),
                    parseIntValue(parts.get(4), 1, 0, 999999)
            );
        }

        public boolean matches(String profession, int level, int vanillaIndex) {
            String owner = clean(profession);
            return this.profession.equals(owner)
                    && this.level == clampTradeLevel(owner, level)
                    && this.vanillaIndex == Math.max(0, vanillaIndex);
        }

        public String toConfigLine() {
            return String.join("|",
                    escapeConfigPart(profession),
                    String.valueOf(level),
                    String.valueOf(vanillaIndex),
                    String.valueOf(enabled),
                    String.valueOf(weight)
            );
        }
    }

    public record TradeOfferData(
            String profession,
            int level,
            String buyAItem,
            int buyACount,
            String buyANbt,
            String buyBItem,
            int buyBCount,
            String buyBNbt,
            String sellItem,
            int sellCount,
            String sellNbt,
            int maxUses,
            int xp,
            float priceMultiplier,
            int demand,
            int specialPrice,
            boolean rewardExp,
            int uses,
            boolean allowRestock,
            int weight,
            int index
    ) {
        public TradeOfferData {
            profession = clean(profession);
            level = clampTradeLevel(profession, level);
            buyAItem = clean(buyAItem);
            buyACount = clampInt(buyACount, 1, 64);
            buyANbt = buyANbt == null ? "" : buyANbt.trim();
            buyBItem = clean(buyBItem);
            buyBCount = clampInt(buyBCount, 0, 64);
            buyBNbt = buyBNbt == null ? "" : buyBNbt.trim();
            sellItem = clean(sellItem);
            sellCount = clampInt(sellCount, 1, 64);
            sellNbt = sellNbt == null ? "" : sellNbt.trim();
            maxUses = clampInt(maxUses, 0, 999999);
            xp = clampInt(xp, 0, 999999);
            priceMultiplier = clampFloat(priceMultiplier, 0.0F, 1000.0F);
            demand = clampInt(demand, -999999, 999999);
            specialPrice = clampInt(specialPrice, -999999, 999999);
            uses = clampInt(uses, 0, 999999);
            weight = clampInt(weight, 0, 999999);
            index = Math.max(0, index);
        }

        public static TradeOfferData parse(String line, int index) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }

            List<String> parts = splitConfigLine(line);
            if (parts.size() < 19) {
                return null;
            }

            String profession = clean(parts.get(0));
            if (profession.isEmpty()) {
                return null;
            }

            int level = parseIntValue(parts.get(1), 1, 1, 5);
            int weight = parts.size() > 19 ? parseIntValue(parts.get(19), 1, 0, 999999) : 1;
            return new TradeOfferData(
                    profession,
                    level,
                    parts.get(2),
                    parseIntValue(parts.get(3), 1, 1, 64),
                    parts.get(4),
                    parts.get(5),
                    parseIntValue(parts.get(6), 0, 0, 64),
                    parts.get(7),
                    parts.get(8),
                    parseIntValue(parts.get(9), 1, 1, 64),
                    parts.get(10),
                    parseIntValue(parts.get(11), 16, 0, 999999),
                    parseIntValue(parts.get(12), 1, 0, 999999),
                    parseFloatValue(parts.get(13), 0.05F, 0.0F, 1000.0F),
                    parseIntValue(parts.get(14), 0, -999999, 999999),
                    parseIntValue(parts.get(15), 0, -999999, 999999),
                    parseBooleanValue(parts.get(16), true),
                    parseIntValue(parts.get(17), 0, 0, 999999),
                    parseBooleanValue(parts.get(18), true),
                    weight,
                    index
            );
        }

        public MerchantOffer createOffer() {
            ItemStack buyA = createStack(buyAItem, buyACount, buyANbt);
            ItemStack buyB = createStack(buyBItem, buyBCount, buyBNbt);
            ItemStack sell = createStack(sellItem, sellCount, sellNbt);
            if (buyA.isEmpty() || sell.isEmpty()) {
                return null;
            }

            CompoundTag tag = new CompoundTag();
            tag.put("buy", buyA.save(new CompoundTag()));
            if (!buyB.isEmpty()) {
                tag.put("buyB", buyB.save(new CompoundTag()));
            }
            tag.put("sell", sell.save(new CompoundTag()));
            tag.putInt("uses", Math.min(uses, maxUses));
            tag.putInt("maxUses", maxUses);
            tag.putBoolean("rewardExp", rewardExp);
            tag.putInt("xp", xp);
            tag.putFloat("priceMultiplier", priceMultiplier);
            tag.putInt("specialPrice", specialPrice);
            tag.putInt("demand", demand);
            tag.putString("contentstudioTradeId", uniqueId());
            tag.putBoolean("contentstudioNoRestock", !allowRestock);

            MerchantOffer offer = new MerchantOffer(tag);
            applyOfferMeta(offer);
            return offer;
        }

        private void applyOfferMeta(MerchantOffer offer) {
            Object offerObject = offer;
            if (offerObject instanceof IMerchantOfferAccess offerAccess) {
                offerAccess.contentstudio_villager$setCustomTradeId(uniqueId());
                offerAccess.contentstudio_villager$setRestockDisabled(!allowRestock);
            }
        }

        public String uniqueId() {
            return profession + "|" + level + "|" + index;
        }

        public boolean matches(String profession, int level) {
            String owner = clean(profession);
            return this.profession.equals(owner) && this.level == clampTradeLevel(owner, level);
        }

        public TradeOfferData withOwner(String newProfession, int newLevel, int newIndex) {
            return new TradeOfferData(
                    newProfession,
                    newLevel,
                    buyAItem,
                    buyACount,
                    buyANbt,
                    buyBItem,
                    buyBCount,
                    buyBNbt,
                    sellItem,
                    sellCount,
                    sellNbt,
                    maxUses,
                    xp,
                    priceMultiplier,
                    demand,
                    specialPrice,
                    rewardExp,
                    uses,
                    allowRestock,
                    weight,
                    newIndex
            );
        }

        public TradeOfferData withIndex(int newIndex) {
            return withOwner(profession, level, newIndex);
        }

        public String toConfigLine() {
            return String.join("|",
                    escapeConfigPart(profession),
                    String.valueOf(level),
                    escapeConfigPart(buyAItem),
                    String.valueOf(buyACount),
                    escapeConfigPart(buyANbt),
                    escapeConfigPart(buyBItem),
                    String.valueOf(buyBCount),
                    escapeConfigPart(buyBNbt),
                    escapeConfigPart(sellItem),
                    String.valueOf(sellCount),
                    escapeConfigPart(sellNbt),
                    String.valueOf(maxUses),
                    String.valueOf(xp),
                    String.valueOf(priceMultiplier),
                    String.valueOf(demand),
                    String.valueOf(specialPrice),
                    String.valueOf(rewardExp),
                    String.valueOf(uses),
                    String.valueOf(allowRestock),
                    String.valueOf(weight)
            );
        }
    }

}
