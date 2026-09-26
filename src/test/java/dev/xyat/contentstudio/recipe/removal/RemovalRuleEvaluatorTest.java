package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RemovalRuleEvaluatorTest {
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    @Test
    void eachModeMatchesKnownCandidateFields() {
        var candidate = new RemovalCandidate(id("minecraft:one"), id("minecraft:crafting"),
                id("minecraft:iron_ingot"), Set.of(id("forge:ingots/iron")));
        assertEquals(5, RemovalRuleEvaluator.matchingRules(candidate, List.of(
                new RemovalEntry(RemovalMode.RECIPE_ID, "minecraft:one", ""),
                new RemovalEntry(RemovalMode.MOD, "minecraft", ""),
                new RemovalEntry(RemovalMode.OUTPUT, "minecraft:iron_ingot", ""),
                new RemovalEntry(RemovalMode.TAG, "forge:ingots/iron", ""),
                new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", "")
        )).size());
    }

    @Test
    void missingFieldsDoNotMatchAndMalformedValuesDoNotMatch() {
        var candidate = new RemovalCandidate(id("minecraft:one"), null, null, Set.of());
        assertTrue(RemovalRuleEvaluator.matchingRules(candidate, List.of(
                new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", ""),
                new RemovalEntry(RemovalMode.OUTPUT, "minecraft:iron_ingot", ""),
                new RemovalEntry(RemovalMode.TAG, "forge:ingots/iron", ""),
                new RemovalEntry(RemovalMode.RECIPE_ID, "bad id", "")
        )).isEmpty());
    }

    @Test
    void exclusionRemovesOnlyItsOwnBroadRule() {
        var candidate = new RemovalCandidate(id("minecraft:one"), id("minecraft:crafting"),
                id("minecraft:iron_ingot"), Set.of());
        var excluded = new RemovalEntry(RemovalMode.MOD, "minecraft", "", List.of(id("minecraft:one")));
        var other = new RemovalEntry(RemovalMode.OUTPUT, "minecraft:iron_ingot", "");
        assertEquals(List.of(excluded, other), RemovalRuleEvaluator.matchingRules(candidate, List.of(excluded, other)));
        assertEquals(List.of(other), RemovalRuleEvaluator.blockingRules(candidate, List.of(excluded, other)));
    }

    @Test
    void exclusionsAreDeduplicatedImmutableAndPartOfContentEquality() {
        var excluded = new RemovalEntry(RemovalMode.MOD, "minecraft", "comment",
                List.of(id("minecraft:one"), id("minecraft:one")));
        assertEquals(List.of(id("minecraft:one")), excluded.excludedRecipeIds());
        assertThrows(UnsupportedOperationException.class, () -> excluded.excludedRecipeIds().add(id("minecraft:two")));
        assertEquals(new RemovalEntry(RemovalMode.MOD, "minecraft", "other").key(), excluded.key());
        assertNotEquals(new RemovalEntry(RemovalMode.MOD, "minecraft", "comment"), excluded);
        assertNotEquals(new RemovalEntry(RemovalMode.MOD, "minecraft", "other", List.of(id("minecraft:one"))), excluded);
    }
}
