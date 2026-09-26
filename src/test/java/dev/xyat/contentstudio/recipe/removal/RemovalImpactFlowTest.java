package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RemovalImpactFlowTest {
    private static final ResourceLocation IRON = id("minecraft:iron_ingot");
    private static final ResourceLocation GOLD = id("minecraft:gold_ingot");

    @Test
    void pagesMustCompleteBeforeEitherCommitModeCanBeBuilt() {
        RemovalImpactFlow flow = new RemovalImpactFlow(new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", ""), List.of());
        assertFalse(flow.isComplete());
        assertThrows(IllegalStateException.class, flow::buildExactRules);
        assertEquals(RemovalImpactFlow.PageStatus.PROGRESS,
                flow.acceptPage(0, 2, 19L, false, List.of(candidate("example:first", IRON))));
        assertEquals(RemovalImpactFlow.PageStatus.COMPLETE,
                flow.acceptPage(1, 2, 19L, false, List.of(candidate("example:second", GOLD))));
        assertEquals(List.of(id("example:first"), id("example:second")), flow.draft().selectedRecipeIds());
        assertEquals(2, flow.buildExactRules().size());
    }

    @Test
    void togglingVisibleOutputKeepsRecipesUnderOtherOutputsSelected() {
        RemovalImpactFlow flow = new RemovalImpactFlow(new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", ""), List.of());
        flow.acceptPage(0, 1, 21L, false, List.of(candidate("example:iron", IRON), candidate("example:gold", GOLD)));
        assertEquals(2, flow.candidates().size());
        flow.draft().toggleOutput(IRON);
        assertEquals(List.of(id("example:gold")), flow.draft().selectedRecipeIds());
        assertEquals(List.of(id("example:gold")), flow.buildExactRules().stream()
                .map(rule -> id(rule.value())).toList());
        assertEquals(List.of(id("example:iron")), flow.buildRangeRule(false).excludedRecipeIds());
    }

    @Test
    void uncheckedRecipeStillReportsOtherBlockingRule() {
        RemovalEntry selectedRule = new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", "");
        RemovalEntry otherRule = new RemovalEntry(RemovalMode.OUTPUT, IRON.toString(), "");
        RemovalImpactFlow flow = new RemovalImpactFlow(selectedRule, List.of(selectedRule, otherRule));
        flow.acceptPage(0, 1, 21L, false, List.of(candidate("example:iron", IRON), candidate("example:gold", GOLD)));
        flow.draft().toggleRecipe(id("example:iron"));
        assertEquals(List.of(otherRule), flow.otherBlockingRules(id("example:iron")));
        assertTrue(flow.otherBlockingRules(id("example:gold")).isEmpty());
    }

    @Test
    void versionChangeDiscardsPartialCatalogAndRequiresFreshFirstPage() {
        RemovalImpactFlow flow = new RemovalImpactFlow(new RemovalEntry(RemovalMode.MOD, "example", ""), List.of());
        flow.acceptPage(0, 2, 1L, false, List.of(candidate("example:old", IRON)));
        assertEquals(RemovalImpactFlow.PageStatus.RESTART,
                flow.acceptPage(1, 2, 2L, true, List.of()));
        assertEquals(0, flow.loadedCount());
        assertFalse(flow.isComplete());
        flow.acceptPage(0, 1, 2L, false, List.of(candidate("example:new", GOLD)));
        assertEquals(List.of(id("example:new")), flow.draft().selectedRecipeIds());
    }

    @Test
    void staleExclusionsAreRetainedUntilExplicitlyCleared() {
        RemovalEntry rule = new RemovalEntry(RemovalMode.MOD, "example", "",
                List.of(id("example:missing"), id("example:iron")));
        RemovalImpactFlow flow = new RemovalImpactFlow(rule, List.of(rule));
        flow.acceptPage(0, 1, 2L, false, List.of(candidate("example:iron", IRON)));
        assertEquals(List.of(id("example:missing"), id("example:iron")), flow.buildRangeRule(false).excludedRecipeIds());
        assertEquals(List.of(id("example:iron")), flow.buildRangeRule(true).excludedRecipeIds());
        assertEquals(List.of(id("example:missing")), flow.staleExcludedRecipeIds());
    }

    private static RemovalCandidate candidate(String value, ResourceLocation output) {
        return new RemovalCandidate(id(value), id("minecraft:crafting"), output, Set.of());
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
}
