package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Viewer acquisition-source tabs are not craftable recipe categories. */
public final class RecipeViewerCategoryFilter {
    private static final Set<String> SOURCE_CATEGORIES = Set.of(
            "loot", "loots", "chest_loot", "entity_loot", "mob_loot", "block_loot",
            "loot_table", "loot_tables", "drop", "drops", "mob_drops", "entity_drops",
            "block_drops", "trade", "trades", "villager_trades", "worldgen",
            "world_gen", "ore_generation", "information"
    );

    private RecipeViewerCategoryFilter() { }

    public static boolean isRecipeCategory(ResourceLocation typeId) {
        if (typeId == null) return false;
        String path = typeId.getPath();
        String category = path.substring(path.lastIndexOf('/') + 1);
        return !SOURCE_CATEGORIES.contains(category);
    }
}
