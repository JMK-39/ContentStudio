package dev.xyat.contentstudio.villager.util;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

public final class VillagerTradeRuntimeUtil {
    private VillagerTradeRuntimeUtil() {
    }

    public static int getDefaultOfferCount(String owner, int level) {
        if (VillagerConfig.isWanderingTrader(owner)) {
            return VillagerConfig.clampTradeLevel(owner, level) == 1 ? 5 : 1;
        }
        return 2;
    }

    public static VillagerTrades.ItemListing[] getVanillaListings(String owner, int level) {
        String cleanOwner = VillagerConfig.clean(owner);
        int safeLevel = VillagerConfig.clampTradeLevel(cleanOwner, level);

        VillagerTrades.ItemListing[] baseline = VillagerTradeRegistry.getBaselineListings(cleanOwner, safeLevel);
        if (baseline != null) {
            return baseline;
        }

        if (VillagerConfig.isWanderingTrader(cleanOwner)) {
            return VillagerTrades.WANDERING_TRADER_TRADES.get(safeLevel);
        }

        ResourceLocation id = KineticResourceIds.tryParse(cleanOwner);
        if (id == null) {
            return null;
        }

        VillagerProfession profession = KineticRegistries.villagerProfessions().get(id);

        var tradesByLevel = VillagerTrades.TRADES.get(profession);
        if (tradesByLevel == null) {
            return null;
        }
        return tradesByLevel.get(safeLevel);
    }

    public static MerchantOffer createVanillaOffer(String owner, int level, int vanillaIndex, Entity entity, RandomSource random) {
        VillagerTrades.ItemListing[] listings = getVanillaListings(owner, level);
        if (listings == null || vanillaIndex < 0 || vanillaIndex >= listings.length) {
            return null;
        }
        try {
            return listings[vanillaIndex].getOffer(entity, random);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static MerchantOffer createPreviewOffer(String owner, int level, int vanillaIndex) {
        return createVanillaOffer(owner, level, vanillaIndex, null, RandomSource.create(1234567L + (long) level * 131L + vanillaIndex * 17L));
    }

    public static void addConfiguredOffers(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, int fallbackCount) {
        String cleanOwner = VillagerConfig.clean(owner);
        int safeLevel = VillagerConfig.clampTradeLevel(cleanOwner, level);
        VillagerConfig.TradeGroup group = VillagerTradeRegistry.getActiveTradeGroup(cleanOwner, safeLevel);
        boolean hasOverrides = VillagerTradeRegistry.hasActiveVanillaOverrides(cleanOwner, safeLevel);

        boolean hasCustomOffers = VillagerTradeRegistry.hasActiveCustomOffers(cleanOwner, safeLevel);

        if (group == null && !hasOverrides && !hasCustomOffers) {
            addVanillaOffers(target, entity, random, cleanOwner, safeLevel, fallbackCount, false);
            return;
        }

        if (group != null && group.disableLevel()) {
            return;
        }

        if (group != null && "add".equals(group.mode)) {
            addVanillaOffers(target, entity, random, cleanOwner, safeLevel, fallbackCount, true);
            addCustomOffers(target, random, cleanOwner, safeLevel, group.offerCount);
            return;
        }

        int count = group == null ? fallbackCount : group.offerCount;
        addCombinedOffers(target, entity, random, cleanOwner, safeLevel, count);
    }

    private static void addVanillaOffers(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, int count, boolean respectOverrides) {
        if (count <= 0) {
            return;
        }

        VillagerTrades.ItemListing[] listings = getVanillaListings(owner, level);
        if (listings == null || listings.length == 0) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < listings.length; i++) {
            int weight = 1;
            if (respectOverrides || VillagerTradeRegistry.hasActiveVanillaOverrides(owner, level)) {
                if (VillagerTradeRegistry.isActiveVanillaTradeDisabled(owner, level, i)) {
                    continue;
                }
                weight = VillagerTradeRegistry.getActiveVanillaTradeWeight(owner, level, i);
            }
            if (weight > 0) {
                candidates.add(Candidate.vanilla(weight, i));
            }
        }

        addCandidates(target, entity, random, owner, level, candidates, count);
    }

    private static void addCustomOffers(MerchantOffers target, RandomSource random, String owner, int level, int count) {
        if (count <= 0) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (VillagerConfig.TradeOfferData data : VillagerTradeRegistry.getActiveTradeOffers(owner, level)) {
            if (data.weight() > 0) {
                candidates.add(Candidate.custom(data.weight(), data));
            }
        }

        addCandidates(target, null, random, owner, level, candidates, count);
    }

    private static void addCombinedOffers(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, int count) {
        if (count <= 0) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        VillagerTrades.ItemListing[] listings = getVanillaListings(owner, level);
        if (listings != null) {
            for (int i = 0; i < listings.length; i++) {
                if (VillagerTradeRegistry.isActiveVanillaTradeDisabled(owner, level, i)) {
                    continue;
                }
                int weight = VillagerTradeRegistry.getActiveVanillaTradeWeight(owner, level, i);
                if (weight > 0) {
                    candidates.add(Candidate.vanilla(weight, i));
                }
            }
        }

        for (VillagerConfig.TradeOfferData data : VillagerTradeRegistry.getActiveTradeOffers(owner, level)) {
            if (data.weight() > 0) {
                candidates.add(Candidate.custom(data.weight(), data));
            }
        }

        addCandidates(target, entity, random, owner, level, candidates, count);
    }

    private static void addCandidates(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, List<Candidate> candidates, int count) {
        int added = 0;
        while (!candidates.isEmpty() && added < count) {
            int selected = pickWeightedIndex(candidates, random);
            if (selected < 0 || selected >= candidates.size()) {
                break;
            }

            Candidate candidate = candidates.remove(selected);
            MerchantOffer offer;
            if (candidate.customOffer != null) {
                if (hasCustomOffer(target, candidate.customOffer.uniqueId())) {
                    continue;
                }
                offer = candidate.customOffer.createOffer();
            } else {
                offer = createVanillaOffer(owner, level, candidate.vanillaIndex, entity, random);
            }

            if (offer == null) {
                continue;
            }
            target.add(offer);
            added++;
        }
    }

    private static int pickWeightedIndex(List<Candidate> candidates, RandomSource random) {
        long total = 0L;
        for (Candidate candidate : candidates) {
            if (candidate.weight > 0) {
                total += candidate.weight;
            }
        }
        if (total <= 0L) {
            return -1;
        }

        long target = Math.floorMod(random.nextLong(), total);
        long current = 0L;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.weight <= 0) {
                continue;
            }
            current += candidate.weight;
            if (target < current) {
                return i;
            }
        }
        return candidates.size() - 1;
    }

    private static boolean hasCustomOffer(MerchantOffers offers, String id) {
        for (MerchantOffer offer : offers) {
            if (VillagerConfig.hasCustomTradeId(offer, id)) {
                return true;
            }
        }
        return false;
    }

    private record Candidate(int weight, int vanillaIndex, VillagerConfig.TradeOfferData customOffer) {
        private static Candidate vanilla(int weight, int vanillaIndex) {
            return new Candidate(weight, vanillaIndex, null);
        }

        private static Candidate custom(int weight, VillagerConfig.TradeOfferData customOffer) {
            return new Candidate(weight, -1, customOffer);
        }
    }
}
