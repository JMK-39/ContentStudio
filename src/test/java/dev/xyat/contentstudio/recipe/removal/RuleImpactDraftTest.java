package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleImpactDraftTest {
    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static RemovalCandidate recipe(String recipeId, String outputId) {
        return new RemovalCandidate(id(recipeId), id("minecraft:crafting"),
                outputId == null ? null : id(outputId), Set.of());
    }

    private static RemovalEntry rangeRule(ResourceLocation... exclusions) {
        return new RemovalEntry(RemovalMode.MOD, "example", "", List.of(exclusions));
    }

    @Test
    void newRangeStartsWithEveryMatchingOriginalSelected() {
        var draft = new RuleImpactDraft(rangeRule(), List.of(
                recipe("example:one", "example:gear"),
                recipe("example:two", "example:gear"),
                recipe("other:unrelated", "example:gear")));

        assertEquals(List.of(id("example:one"), id("example:two")), draft.selectedRecipeIds());
        assertEquals(RuleImpactDraft.Selection.ALL, draft.selectionForOutput(id("example:gear")));
        assertEquals(List.of(), draft.buildRangeRule().excludedRecipeIds());
    }

    @Test
    void existingExclusionsStartUncheckedAndAnOutputGroupMovesThroughAllPartialNone() {
        var draft = new RuleImpactDraft(rangeRule(id("example:one")), List.of(
                recipe("example:one", "example:gear"),
                recipe("example:two", "example:gear"),
                recipe("example:three", "example:plate")));

        assertEquals(RuleImpactDraft.Selection.PARTIAL, draft.selectionForOutput(id("example:gear")));
        draft.toggleOutput(id("example:gear"));
        assertEquals(RuleImpactDraft.Selection.ALL, draft.selectionForOutput(id("example:gear")));
        assertEquals(List.of(id("example:one"), id("example:two"), id("example:three")), draft.selectedRecipeIds());
        draft.toggleOutput(id("example:gear"));
        assertEquals(RuleImpactDraft.Selection.NONE, draft.selectionForOutput(id("example:gear")));
        assertEquals(List.of(id("example:three")), draft.selectedRecipeIds());
        assertEquals(List.of(id("example:one"), id("example:two")), draft.buildRangeRule().excludedRecipeIds());
    }

    @Test
    void singleRecipeToggleDoesNotTouchOtherRecipesOrOutputGroups() {
        var draft = new RuleImpactDraft(rangeRule(), List.of(
                recipe("example:one", "example:gear"),
                recipe("example:two", "example:gear"),
                recipe("example:three", null)));

        draft.toggleRecipe(id("example:one"));
        assertEquals(RuleImpactDraft.Selection.PARTIAL, draft.selectionForOutput(id("example:gear")));
        assertEquals(RuleImpactDraft.Selection.ALL, draft.selectionForOutput(null));
        assertEquals(List.of(id("example:two"), id("example:three")), draft.selectedRecipeIds());
        draft.toggleRecipe(id("example:one"));
        assertEquals(RuleImpactDraft.Selection.ALL, draft.selectionForOutput(id("example:gear")));
    }

    @Test
    void selectAllActsOnEveryCandidateRegardlessOfPreviousItemSelection() {
        var draft = new RuleImpactDraft(rangeRule(), List.of(
                recipe("example:shown", "example:gear"),
                recipe("example:hidden", "example:plate")));
        draft.toggleRecipe(id("example:shown"));

        draft.selectAll(false);
        assertTrue(draft.selectedRecipeIds().isEmpty());
        assertEquals(List.of(id("example:shown"), id("example:hidden")),
                draft.buildRangeRule().excludedRecipeIds());
        draft.selectAll(true);
        assertEquals(List.of(id("example:shown"), id("example:hidden")), draft.selectedRecipeIds());
    }

    @Test
    void unavailableExclusionSurvivesSelectionChangesButFutureDifferentIdIsStillBlocked() {
        var draft = new RuleImpactDraft(rangeRule(id("example:temporarily_absent"), id("example:one")),
                List.of(recipe("example:one", "example:gear")));

        draft.selectAll(true);
        assertEquals(List.of(id("example:temporarily_absent")), draft.buildRangeRule().excludedRecipeIds());
        draft.selectAll(false);
        var updated = draft.buildRangeRule();
        assertEquals(List.of(id("example:temporarily_absent"), id("example:one")), updated.excludedRecipeIds());
        assertTrue(RemovalRuleEvaluator.blockingRules(recipe("example:future", "example:gear"),
                List.of(updated)).contains(updated));
    }

    @Test
    void exactActionCreatesOnlySelectedIdRulesAndDoesNotChangeTheRangeRule() {
        var initial = rangeRule(id("example:missing"));
        var draft = new RuleImpactDraft(initial, List.of(
                recipe("example:one", "example:gear"), recipe("example:two", "example:gear")));
        draft.toggleRecipe(id("example:two"));

        assertEquals(List.of(new RemovalEntry(RemovalMode.RECIPE_ID, "example:one", "")),
                draft.buildExactRules());
        assertEquals(List.of(id("example:missing")), initial.excludedRecipeIds());
        assertThrows(UnsupportedOperationException.class,
                () -> draft.selectedRecipeIds().add(id("example:three")));
        assertThrows(UnsupportedOperationException.class,
                () -> draft.buildExactRules().add(new RemovalEntry(RemovalMode.RECIPE_ID, "example:three", "")));
    }
}
