//? if >=26.1 {
/*package dev.xyat.contentstudio.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.List;

/^*
 * What a recipe makes and takes, read from its recipe book displays. 26.1 recipes no longer expose a result stack or
 * an ingredient grid directly; their first display describes both the way the recipe book shows them.
 ^/
public final class RecipeView {
    private RecipeView() {
    }

    public static ItemStack output(Recipe<?> recipe, HolderLookup.Provider registries) {
        RecipeDisplay display = firstDisplay(recipe);
        return display == null ? ItemStack.EMPTY : display.result().resolveForFirstStack(context(registries));
    }

    /^*
     * The item a recipe makes, without building a stack. While datapacks reload, items have no components and tags
     * are not bound yet, so neither stacks nor tag-based displays can be resolved; null when the display shows no
     * single item or cannot be built yet.
     ^/
    public static net.minecraft.core.Holder<net.minecraft.world.item.Item> outputItem(Recipe<?> recipe) {
        try {
            RecipeDisplay display = firstDisplay(recipe);
            return display == null ? null : itemOf(display.result());
        } catch (RuntimeException unbound) {
            return null;
        }
    }

    private static net.minecraft.core.Holder<net.minecraft.world.item.Item> itemOf(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stack) return stack.stack().item();
        if (slot instanceof SlotDisplay.ItemSlotDisplay item) return item.item();
        if (slot instanceof SlotDisplay.Composite composite && !composite.contents().isEmpty()) return itemOf(composite.contents().getFirst());
        return null;
    }

    /^* The alternatives of each input slot in recipe order; an empty list is an empty grid slot. ^/
    public static List<List<ItemStack>> inputs(Recipe<?> recipe, HolderLookup.Provider registries) {
        ContextMap context = context(registries);
        return slots(recipe).stream().map(slot -> List.copyOf(slot.resolveForStacks(context))).toList();
    }

    /^* The width of a shaped crafting grid, or 0 when the inputs are not laid out on a grid. ^/
    public static int craftingWidth(Recipe<?> recipe) {
        return firstDisplay(recipe) instanceof ShapedCraftingRecipeDisplay shaped ? shaped.width() : 0;
    }

    private static List<SlotDisplay> slots(Recipe<?> recipe) {
        RecipeDisplay display = firstDisplay(recipe);
        if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
        if (display instanceof FurnaceRecipeDisplay furnace) return List.of(furnace.ingredient());
        if (display instanceof StonecutterRecipeDisplay stonecutter) return List.of(stonecutter.input());
        if (display instanceof SmithingRecipeDisplay smithing) return List.of(smithing.template(), smithing.base(), smithing.addition());
        return recipe.placementInfo().ingredients().stream().map(Ingredient::display).toList();
    }

    private static RecipeDisplay firstDisplay(Recipe<?> recipe) {
        List<RecipeDisplay> displays = recipe.display();
        return displays.isEmpty() ? null : displays.getFirst();
    }

    private static ContextMap context(HolderLookup.Provider registries) {
        return new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, registries).create(SlotDisplayContext.CONTEXT);
    }
}
*///?}
