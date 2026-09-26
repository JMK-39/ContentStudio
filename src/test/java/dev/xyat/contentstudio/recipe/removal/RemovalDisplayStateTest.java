package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RemovalDisplayStateTest {
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    private static RemovalCandidate original() {
        return new RemovalCandidate(id("example:original"), id("minecraft:crafting"),
                id("example:output"), Set.of());
    }

    @Test
    void showsEveryCauseAndExcludedStatusOnlyWhenNoRuleStillBlocks() {
        var output = new RemovalEntry(RemovalMode.OUTPUT, "example:output", "");
        var type = new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", "",
                List.of(id("example:original")));
        var state = RemovalDisplayState.of(original(), List.of(output, type));
        assertEquals(RemovalDisplayState.Status.REMOVED, state.status());
        assertEquals(List.of(output, type), state.matchingRules());
        assertEquals(List.of(output), state.blockingRules());

        var excluded = RemovalDisplayState.of(original(), List.of(type));
        assertEquals(RemovalDisplayState.Status.EXCLUDED, excluded.status());
        assertTrue(excluded.blockingRules().isEmpty());
        assertEquals(RemovalDisplayState.Status.ACTIVE,
                RemovalDisplayState.of(original(), List.of()).status());
    }

    @Test
    void restoringOneOriginalRemovesExactRuleAndExcludesAllBroadCauses() {
        var exact = new RemovalEntry(RemovalMode.RECIPE_ID, "example:original", "");
        var output = new RemovalEntry(RemovalMode.OUTPUT, "example:output", "");
        var type = new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", "",
                List.of(id("example:else")));
        var other = new RemovalEntry(RemovalMode.MOD, "other", "");

        var restored = RemovalDisplayState.restore(original(), List.of(exact, output, type, other));
        assertEquals(3, restored.size());
        assertEquals(List.of(id("example:original")), restored.get(0).excludedRecipeIds());
        assertEquals(List.of(id("example:else"), id("example:original")), restored.get(1).excludedRecipeIds());
        assertEquals(other, restored.get(2));
        assertEquals(RemovalDisplayState.Status.EXCLUDED,
                RemovalDisplayState.of(original(), restored).status());
    }

    @Test
    void removingAgainClearsMatchingExclusionsWithoutChangingOtherRecipes() {
        var candidate = original();
        var output = new RemovalEntry(RemovalMode.OUTPUT, "example:output", "",
                List.of(id("example:original"), id("example:another")));
        var changed = RemovalDisplayState.remove(candidate, List.of(output));
        assertEquals(List.of(id("example:another")), changed.get(0).excludedRecipeIds());
        assertEquals(RemovalDisplayState.Status.REMOVED,
                RemovalDisplayState.of(candidate, changed).status());

        var exact = RemovalDisplayState.remove(candidate, List.of());
        assertEquals(List.of(new RemovalEntry(RemovalMode.RECIPE_ID, "example:original", "")), exact);
    }

    @Test
    void actionTargetUsesOriginalRowProvenanceEvenWhenFinalRecipeHasSameId() {
        record Row(ResourceLocation id) { }
        var originalRow = new Row(id("example:original"));
        var laterScriptRow = new Row(id("example:original"));
        assertEquals(originalRow, laterScriptRow);
        var rows = new OriginalRecipeRows<Row>();
        rows.register(originalRow, original());
        assertEquals(original(), rows.candidateFor(originalRow));
        assertNull(rows.candidateFor(laterScriptRow));
        rows.clear();
        assertNull(rows.candidateFor(originalRow));
    }

    @Test
    void exactConfirmationReplacesOnlyTheEditedRangeRule() {
        var range = new RemovalEntry(RemovalMode.OUTPUT, "example:output", "");
        var unrelated = new RemovalEntry(RemovalMode.MOD, "other", "");
        var exact = new RemovalEntry(RemovalMode.RECIPE_ID, "example:original", "");
        var result = RemovalDraftEdits.replaceRangeWithExact(List.of(range, unrelated), range,
                List.of(exact));

        assertEquals(List.of(exact, unrelated), result);
        assertEquals(RemovalDisplayState.Status.REMOVED,
                RemovalDisplayState.of(original(), result).status());
        var unchecked = new RemovalCandidate(id("example:unchecked"), id("minecraft:crafting"),
                id("example:output"), Set.of());
        assertEquals(RemovalDisplayState.Status.ACTIVE,
                RemovalDisplayState.of(unchecked, result).status());

        assertEquals(List.of(unrelated),
                RemovalDraftEdits.replaceRangeWithExact(List.of(range, unrelated), range, List.of()));
    }
}
