package dev.xyat.contentstudio.villager;

import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//? if <1.21
@org.junit.jupiter.api.Disabled("Forge registry checks require its transformed loader; run these in the runtime fixture")
class VillagerTradeValidationTest {
    private static java.nio.file.Path bootstrapDirectory;

    @BeforeAll
    static void bootstrap() throws IOException {
        if (bootstrapDirectory != null) return;
        var directory = Files.createTempDirectory("villager-validation-");
        //? if >=1.21 {
        /*if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        *///?} else {
        net.minecraftforge.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        //?}
        bootstrapDirectory = directory;
    }

    @Test
    void reportsMultipleBadFieldsButCountsEachRejectedOfferOnce() {
        List<String> bad = offerParts();
        bad.set(3, "0");
        bad.set(9, "65");
        bad.set(13, "NaN");
        bad.set(16, "perhaps");
        var result = VillagerConfig.validateTradeListsDetailed(List.of(),
                List.of(String.join("|", bad), "broken"), List.of());

        assertEquals(2, result.skippedOfferCount());
        assertTrue(result.validOffers().isEmpty());
        assertEquals(4, result.issues().stream().filter(issue -> issue.index() == 0).count());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.index() == 0 && issue.slot() == 0
                && issue.reasonKey().endsWith(".count")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.index() == 0 && issue.slot() == 2
                && issue.reasonKey().endsWith(".count")));
        assertFalse(VillagerConfig.areValidTradeLists(List.of(), List.of(String.join("|", bad)), List.of()));
    }

    @Test
    void retainsValidEntriesAndAllowsAbsentSecondPayment() {
        String valid = String.join("|", offerParts());
        var result = VillagerConfig.validateTradeListsDetailed(
                List.of("minecraft:farmer|1|add|2", "minecraft:farmer|6|add|2"),
                List.of(valid, "broken"), List.of("minecraft:farmer|1|0|true|3"));

        assertEquals(List.of("minecraft:farmer|1|add|2"), result.validGroups());
        assertEquals(List.of(valid), result.validOffers());
        assertEquals(List.of("minecraft:farmer|1|0|true|3"), result.validOverrides());
        assertEquals(1, result.skippedOfferCount());
        assertTrue(VillagerConfig.areValidTradeLists(List.of(), List.of(valid), List.of()));
    }

    @Test
    void acceptsOnlyTheItemDataSyntaxOfItsMinecraftVersion() {
        List<String> nbt = offerParts();
        nbt.set(10, "{Damage:1}");
        List<String> components = offerParts();
        components.set(10, "[damage=1]");
        //? if >=1.21 {
        /*assertFalse(VillagerConfig.areValidTradeLists(List.of(), List.of(String.join("|", nbt)), List.of()));
        assertTrue(VillagerConfig.areValidTradeLists(List.of(), List.of(String.join("|", components)), List.of()));
        *///?} else {
        assertTrue(VillagerConfig.areValidTradeLists(List.of(), List.of(String.join("|", nbt)), List.of()));
        assertFalse(VillagerConfig.areValidTradeLists(List.of(), List.of(String.join("|", components)), List.of()));
        //?}
    }

    @Test
    void missingOrOversizedListsRemainInvalid() {
        assertFalse(VillagerConfig.areValidTradeLists(null, List.of(), List.of()));
        assertFalse(VillagerConfig.areValidTradeLists(
                java.util.Collections.nCopies(8193, "minecraft:farmer|1|add|2"), List.of(), List.of()));
    }

    @Test
    void localOnlySuppressesVanillaEvenForUnconfiguredLevels() throws ReflectiveOperationException {
        withLocalTrades(List.of(), List.of(), () -> {
            var offers = new net.minecraft.world.item.trading.MerchantOffers();
            VillagerTradeRuntimeUtil.addConfiguredOffers(offers, null, net.minecraft.util.RandomSource.create(4),
                    "minecraft:farmer", 1, 2);
            assertTrue(offers.isEmpty());
            assertTrue(VillagerTradeRegistry.hasActiveLevelChanges("minecraft:farmer", 5));
            assertTrue(VillagerTradeRegistry.hasActiveLevelChanges(VillagerConfig.WANDERING_TRADER_ID, 2));
        });
    }

    @Test
    void localOnlyHonorsCountDisabledLevelsAndZeroWeight() throws ReflectiveOperationException {
        List<String> weightedOut = offerParts();
        weightedOut.set(19, "0");
        withLocalTrades(List.of("minecraft:farmer|1|add|1"),
                List.of(String.join("|", weightedOut), String.join("|", offerParts()), String.join("|", offerParts())), () -> {
                    var offers = new net.minecraft.world.item.trading.MerchantOffers();
                    VillagerTradeRuntimeUtil.addConfiguredOffers(offers, null, net.minecraft.util.RandomSource.create(4),
                            "minecraft:farmer", 1, 2);
                    assertEquals(1, offers.size());
                    assertEquals("minecraft:diamond_sword", VillagerConfig.itemId(offers.get(0).getResult()));
                });
        withLocalTrades(List.of("minecraft:farmer|1|disable_level|2"), List.of(String.join("|", offerParts())), () -> {
            var offers = new net.minecraft.world.item.trading.MerchantOffers();
            VillagerTradeRuntimeUtil.addConfiguredOffers(offers, null, net.minecraft.util.RandomSource.create(4),
                    "minecraft:farmer", 1, 2);
            assertTrue(offers.isEmpty());
        });
        withLocalTrades(List.of(), List.of(String.join("|", weightedOut)), () -> {
            var offers = new net.minecraft.world.item.trading.MerchantOffers();
            VillagerTradeRuntimeUtil.addConfiguredOffers(offers, null, net.minecraft.util.RandomSource.create(4),
                    "minecraft:farmer", 1, 2);
            assertTrue(offers.isEmpty());
        });
    }

    @Test
    void draftReplacementKeepsInvalidRowsWithoutPublishingClampedTrades() throws ReflectiveOperationException {
        List<String> bad = offerParts();
        bad.set(3, "0");
        bad.set(9, "65");
        String badOffer = String.join("|", bad);
        String validOffer = String.join("|", offerParts());
        String badGroup = "minecraft:farmer|99|add|2";
        String badOverride = "minecraft:farmer|1|0|perhaps|4";
        withLocalTrades(List.of(badGroup), List.of(badOffer, "broken", validOffer), () -> {
            VillagerConfig.replaceTradeLists(List.of(badGroup), List.of(badOffer, "broken", validOffer), List.of(badOverride));
            VillagerConfig.normalizeTradeLists();
            assertEquals(List.of(badGroup), VillagerConfig.villagerTradeGroups);
            assertEquals(List.of(badOffer, "broken", validOffer), VillagerConfig.villagerTradeOffers);
            assertEquals(List.of(badOverride), VillagerConfig.villagerDefaultTradeOverrides);
            assertNull(VillagerConfig.getTradeGroup("minecraft:farmer", 5));
            assertNull(VillagerConfig.getVanillaTradeOverride("minecraft:farmer", 1, 0));
            assertEquals(1, VillagerConfig.getTradeOffers("minecraft:farmer", 1).size());
            assertEquals(2, VillagerConfig.getTradeOffers("minecraft:farmer", 1).get(0).index());
            try {
                var rebuild = VillagerTradeRegistry.class.getDeclaredMethod("rebuildActiveState");
                rebuild.setAccessible(true);
                rebuild.invoke(null);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            assertNull(VillagerTradeRegistry.getActiveTradeGroup("minecraft:farmer", 5));
            assertNull(VillagerTradeRegistry.getActiveVanillaTradeOverride("minecraft:farmer", 1, 0));
            assertEquals(1, VillagerTradeRegistry.getActiveTradeOffers("minecraft:farmer", 1).size());
            assertEquals(2, VillagerTradeRegistry.getActiveTradeOffers("minecraft:farmer", 1).get(0).index());
        });
    }

    @Test
    void publishedSnapshotIgnoresLaterClientRulesAndLateOverrideDrafts() throws ReflectiveOperationException {
        withLocalTrades(List.of("minecraft:farmer|1|add|1"), List.of(String.join("|", offerParts())), () -> {
            List<String> savedGroups = VillagerTradeRegistry.getAuthoritativeTradeGroups();
            boolean savedLate = VillagerTradeRegistry.isActiveTradeLateOverride();
            VillagerConfig.replaceTradeLists(List.of("minecraft:farmer|2|add|3"), List.of("unrepaired draft"), List.of());
            VillagerConfig.enableVillagerTradeLateOverride = !savedLate;
            try {
                var rebuild = VillagerTradeRegistry.class.getDeclaredMethod("rebuildActiveState");
                rebuild.setAccessible(true);
                rebuild.invoke(null);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            assertEquals(List.of("minecraft:farmer|1|add|1"), savedGroups);
            assertEquals(savedGroups, VillagerTradeRegistry.getAuthoritativeTradeGroups());
            assertEquals(savedLate, VillagerTradeRegistry.isActiveTradeLateOverride());
            assertNotNull(VillagerTradeRegistry.getActiveTradeGroup("minecraft:farmer", 1));
            assertNull(VillagerTradeRegistry.getActiveTradeGroup("minecraft:farmer", 2));
            assertEquals(1, VillagerTradeRegistry.getActiveTradeOffers("minecraft:farmer", 1).size());
            assertThrows(UnsupportedOperationException.class, () -> savedGroups.add("mutate server snapshot"));
        });
    }

    @Test
    void canonicalAcceptedSnapshotMatchesServerNormalizationWithoutChangingTheRawDraft() throws ReflectiveOperationException {
        withLocalTrades(List.of(), List.of(), () -> {
            List<String> legacyParts = offerParts();
            legacyParts.remove(19);
            legacyParts.set(3, "01");
            legacyParts.set(13, "0.050");
            String legacyOffer = String.join("|", legacyParts);
            List<String> rawGroups = List.of(" MINECRAFT:FARMER |1|ADD|02");
            List<String> rawOffers = List.of(legacyOffer, "BAD");
            List<String> rawOverrides = List.of("minecraft:farmer|1|0|TRUE|03");
            VillagerConfig.villagerTradeGroups = new ArrayList<>(rawGroups);
            VillagerConfig.villagerTradeOffers = new ArrayList<>(rawOffers);
            VillagerConfig.villagerDefaultTradeOverrides = new ArrayList<>(rawOverrides);

            var canonical = VillagerConfig.canonicalizeValidTradeLists(rawGroups, rawOffers, rawOverrides);
            assertEquals(List.of("minecraft:farmer|1|add|2"), canonical.validGroups());
            assertEquals(List.of(String.join("|", offerParts())), canonical.validOffers());
            assertEquals(List.of("minecraft:farmer|1|0|true|3"), canonical.validOverrides());
            assertEquals(1, canonical.skippedOfferCount());
            assertEquals(rawGroups, VillagerConfig.villagerTradeGroups);
            assertEquals(rawOffers, VillagerConfig.villagerTradeOffers);
            assertEquals(rawOverrides, VillagerConfig.villagerDefaultTradeOverrides);

            var accepted = VillagerConfig.validateTradeListsDetailed(rawGroups, rawOffers, rawOverrides);
            VillagerConfig.replaceTradeLists(accepted.validGroups(), accepted.validOffers(), accepted.validOverrides());
            assertEquals(canonical.validGroups(), VillagerConfig.villagerTradeGroups);
            assertEquals(canonical.validOffers(), VillagerConfig.villagerTradeOffers);
            assertEquals(canonical.validOverrides(), VillagerConfig.villagerDefaultTradeOverrides);
        });
    }

    private static void withLocalTrades(List<String> groups, List<String> offers, Runnable check) throws ReflectiveOperationException {
        var oldMode = VillagerConfig.getTradeSourceMode();
        List<String> oldGroups = new ArrayList<>(VillagerConfig.villagerTradeGroups);
        List<String> oldOffers = new ArrayList<>(VillagerConfig.villagerTradeOffers);
        List<String> oldOverrides = new ArrayList<>(VillagerConfig.villagerDefaultTradeOverrides);
        boolean oldLate = VillagerConfig.enableVillagerTradeLateOverride;
        var snapshot = VillagerTradeRegistry.class.getDeclaredField("authoritativeConfig");
        snapshot.setAccessible(true);
        Object oldSnapshot = snapshot.get(null);
        var capture = VillagerTradeRegistry.class.getDeclaredMethod("captureAuthoritativeState");
        capture.setAccessible(true);
        var rebuild = VillagerTradeRegistry.class.getDeclaredMethod("rebuildActiveState");
        rebuild.setAccessible(true);
        try {
            VillagerConfig.setTradeSourceMode(VillagerConfig.TradeSourceMode.LOCAL_ONLY);
            VillagerConfig.replaceTradeLists(groups, offers, List.of());
            capture.invoke(null);
            rebuild.invoke(null);
            check.run();
        } finally {
            VillagerConfig.setTradeSourceMode(oldMode);
            VillagerConfig.replaceTradeLists(oldGroups, oldOffers, oldOverrides);
            VillagerConfig.enableVillagerTradeLateOverride = oldLate;
            snapshot.set(null, oldSnapshot);
            rebuild.invoke(null);
        }
    }

    private static List<String> offerParts() {
        //? if >=1.21 {
        /*String data = "[]";
        *///?} else {
        String data = "{}";
        //?}
        return new ArrayList<>(List.of("minecraft:farmer", "1", "minecraft:emerald", "1", data,
                "", "0", data, "minecraft:diamond_sword", "1", data,
                "16", "1", "0.05", "0", "0", "true", "0", "true", "1"));
    }
}
