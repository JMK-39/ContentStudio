package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerCategoryFilterTest {
    @Test
    void excludesAcquisitionSourcesButKeepsSpecialProcessingCategories() {
        assertFalse(RecipeViewerCategoryFilter.isRecipeCategory(new ResourceLocation("val:chest_loot")));
        assertFalse(RecipeViewerCategoryFilter.isRecipeCategory(new ResourceLocation("example:mob_drops")));
        assertFalse(RecipeViewerCategoryFilter.isRecipeCategory(new ResourceLocation("example:villager_trades")));
        assertTrue(RecipeViewerCategoryFilter.isRecipeCategory(new ResourceLocation("example:arcane_anvil")));
        assertTrue(RecipeViewerCategoryFilter.isRecipeCategory(new ResourceLocation("example:loot_fabricator")));
    }
}
