package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RemovalSaveTransferTest {
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    @Test
    void chunksRoundTripLargeRuleWithoutOversizedPacket() {
        List<ResourceLocation> exclusions = new ArrayList<>();
        for (int i = 0; i < 12_000; i++) exclusions.add(id("example:recipe_" + i));
        var rule = new RemovalEntry(RemovalMode.MOD, "example", "", exclusions);
        byte[] bytes = RemovalStateCodec.encodeRules(List.of(rule));
        List<byte[]> chunks = RuleTransfer.split(bytes);
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length <= 32 * 1024));
        var assembled = new RuleTransfer.Assembler(bytes.length, chunks.size());
        for (int i = 0; i < chunks.size(); i++) assembled.append(i, chunks.get(i));
        assertEquals(12_000, RemovalStateCodec.decodeRules(assembled.finish()).get(0).excludedRecipeIds().size());
    }

    @Test
    void incompleteOutOfOrderAndTooLargeTransfersAreRejected() {
        byte[] bytes = new byte[RuleTransfer.CHUNK_BYTES + 1];
        var missing = new RuleTransfer.Assembler(bytes.length, 2);
        missing.append(0, new byte[RuleTransfer.CHUNK_BYTES]);
        assertThrows(IllegalStateException.class, missing::finish);
        assertThrows(IllegalArgumentException.class, () -> missing.append(0, new byte[]{'x'}));
        assertThrows(IllegalArgumentException.class, () -> new RuleTransfer.Assembler(4 * 1024 * 1024 + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RuleTransfer.Assembler(1, 1).append(0, new byte[32 * 1024 + 1]));
    }

    @Test
    void strictRulesRejectDuplicateKeysAndInvalidExclusions() {
        assertThrows(IllegalArgumentException.class, () -> RemovalStateCodec.decodeRules(
                "[{\"mode\":\"MOD\",\"value\":\"example\"},{\"mode\":\"MOD\",\"value\":\"example\"}]"
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> RemovalStateCodec.decodeRules(
                "[{\"mode\":\"MOD\",\"value\":\"example\",\"excluded_recipe_ids\":[\"bad space\"]}]"
                        .getBytes(StandardCharsets.UTF_8)));
        assertEquals(Set.of(id("example:missing")), Set.copyOf(RemovalStateCodec.decodeRules(
                "[{\"mode\":\"MOD\",\"value\":\"example\",\"excluded_recipe_ids\":[\"example:missing\"]}]"
                        .getBytes(StandardCharsets.UTF_8)).get(0).excludedRecipeIds()));
    }

    @Test
    void candidates257ProduceThreeVersionedPages() {
        var candidates = new ArrayList<RemovalCandidate>();
        for (int i = 0; i < 257; i++) candidates.add(new RemovalCandidate(id("example:r" + i), null, null, Set.of()));
        var pages = RemovalCatalogPages.of(candidates, 77L, 128);
        assertEquals(List.of(128, 128, 1), pages.stream().map(page -> page.candidates().size()).toList());
        assertEquals(257, pages.stream().flatMap(page -> page.candidates().stream()).map(RemovalCandidate::id).distinct().count());
        assertFalse(RemovalCatalogPages.matchesVersion(pages.get(0), 78L));
    }

    @Test
    void invalidDraftDoesNotWriteAndValidDraftWritesExactlyOnce() {
        AtomicInteger writes = new AtomicInteger();
        var duplicate = List.of(new RemovalEntry(RemovalMode.MOD, "example", ""),
                new RemovalEntry(RemovalMode.MOD, "example", ""));
        var rejected = RemovalDraftCommitter.commit(duplicate, rules -> writes.incrementAndGet(),
                () -> CompletableFuture.completedFuture(null)).join();
        assertEquals(RemovalDraftCommitter.Status.REJECTED, rejected.status());
        assertEquals(0, writes.get());

        var applied = RemovalDraftCommitter.commit(List.of(new RemovalEntry(RemovalMode.MOD, "example", "")),
                rules -> writes.incrementAndGet(), () -> CompletableFuture.completedFuture(null)).join();
        assertEquals(RemovalDraftCommitter.Status.APPLIED, applied.status());
        assertEquals(1, writes.get());
    }

    @Test
    void reloadFailureIsDistinctFromWriteFailure() {
        var rule = List.of(new RemovalEntry(RemovalMode.MOD, "example", ""));
        AtomicInteger writes = new AtomicInteger();
        var persisted = RemovalDraftCommitter.commit(rule, rules -> writes.incrementAndGet(),
                () -> CompletableFuture.failedFuture(new IllegalStateException("reload failed"))).join();
        assertEquals(RemovalDraftCommitter.Status.PERSISTED_RELOAD_FAILED, persisted.status());
        assertEquals(1, writes.get());
        var rejected = RemovalDraftCommitter.commit(rule, rules -> { throw new java.io.IOException("disk full"); },
                () -> CompletableFuture.completedFuture(null)).join();
        assertEquals(RemovalDraftCommitter.Status.REJECTED, rejected.status());
    }
}
