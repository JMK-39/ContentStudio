package dev.xyat.contentstudio.villager;

import dev.xyat.contentstudio.villager.config.VillagerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerTradeSourceModeTest {
    @BeforeAll
    static void bootstrap() throws java.io.IOException {
        VillagerTradeValidationTest.bootstrap();
    }

    @Test
    void acceptsKnownModesAndRejectsUnknownValues() {
        assertEquals(VillagerConfig.TradeSourceMode.MERGE_ALL,
                VillagerConfig.TradeSourceMode.parse("  MERGE_ALL  "));
        assertEquals(VillagerConfig.TradeSourceMode.LOCAL_ONLY,
                VillagerConfig.TradeSourceMode.parse("local_only"));
        assertNull(VillagerConfig.TradeSourceMode.parse("replace_all"));
        assertNull(VillagerConfig.TradeSourceMode.parse(null));
    }

    @Test
    void skippedOfferCountCountsDistinctRowsRatherThanBadFields() {
        var result = new VillagerConfig.TradeValidationResult(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(
                        new VillagerConfig.TradeValidationIssue(VillagerConfig.TradeIssueKind.OFFER, 3, "minecraft:farmer", 1, 0, "count", "0"),
                        new VillagerConfig.TradeValidationIssue(VillagerConfig.TradeIssueKind.OFFER, 3, "minecraft:farmer", 1, 2, "count", "65"),
                        new VillagerConfig.TradeValidationIssue(VillagerConfig.TradeIssueKind.OFFER, 4, "minecraft:farmer", 1, -1, "format", "bad"),
                        new VillagerConfig.TradeValidationIssue(VillagerConfig.TradeIssueKind.GROUP, 5, "minecraft:farmer", 1, -1, "group", "bad")));
        assertEquals(2, result.skippedOfferCount());
    }
}
