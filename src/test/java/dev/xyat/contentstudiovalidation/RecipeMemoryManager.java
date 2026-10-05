//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
final class RecipeMemoryManager {
    private static Object invoke(String method,Class<?>[] types,Object... args){try{var m=dev.xyat.contentstudio.recipe.RecipeMemoryManager.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    static net.minecraft.world.item.crafting.Ingredient ingredient(dev.xyat.contentstudio.recipe.RecipeRecord record,int index){return (net.minecraft.world.item.crafting.Ingredient)invoke("ingredient",new Class<?>[]{dev.xyat.contentstudio.recipe.RecipeRecord.class,int.class},record,index);}
    static net.minecraft.world.item.crafting.RecipeHolder<?> buildRecipe(dev.xyat.contentstudio.recipe.RecipeRecord record){return (net.minecraft.world.item.crafting.RecipeHolder<?>)invoke("buildRecipe",new Class<?>[]{dev.xyat.contentstudio.recipe.RecipeRecord.class},record);}
    // 26.1 builds recipes and ingredients against the reload registries.
    static net.minecraft.world.item.crafting.Ingredient ingredient(dev.xyat.contentstudio.recipe.RecipeRecord record,int index,net.minecraft.core.HolderLookup.Provider registries){return (net.minecraft.world.item.crafting.Ingredient)invoke("ingredient",new Class<?>[]{dev.xyat.contentstudio.recipe.RecipeRecord.class,int.class,net.minecraft.core.HolderLookup.Provider.class},record,index,registries);}
    static net.minecraft.world.item.crafting.RecipeHolder<?> buildRecipe(dev.xyat.contentstudio.recipe.RecipeRecord record,net.minecraft.core.HolderLookup.Provider registries){return (net.minecraft.world.item.crafting.RecipeHolder<?>)invoke("buildRecipe",new Class<?>[]{dev.xyat.contentstudio.recipe.RecipeRecord.class,net.minecraft.core.HolderLookup.Provider.class},record,registries);}
    static dev.xyat.contentstudio.recipe.removal.RemovalCandidate candidateOf(net.minecraft.world.item.crafting.RecipeHolder<?> holder,net.minecraft.core.HolderLookup.Provider registries,net.neoforged.neoforge.common.conditions.ICondition.IContext context){return (dev.xyat.contentstudio.recipe.removal.RemovalCandidate)invoke("candidateOf",new Class<?>[]{net.minecraft.world.item.crafting.RecipeHolder.class,net.minecraft.core.HolderLookup.Provider.class,net.neoforged.neoforge.common.conditions.ICondition.IContext.class},holder,registries,context);}
}
*///?}
