package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Optional native serializers with a documented second initialization step. No viewer dependency. */
public final class NativeRecipeCompat {
    private NativeRecipeCompat() { }
    public static void initialize(Object recipe) {
        if(recipe.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe")) {
            try {recipe.getClass().getMethod("init").invoke(recipe);}
            catch(ReflectiveOperationException error) {
                throw new IllegalArgumentException("TACZ recipe output could not be initialized",error);
            }
        }
    }
    /** TACZ's raw gun/ammo/attachment IDs are not registry item IDs. Use its own optional native result factory. */
    public static ItemStack taczPreview(JsonObject json) {
        if(!KineticPlatform.isModLoaded("tacz"))return ItemStack.EMPTY;
        try {
            Class<?> assets=Class.forName("com.tacz.guns.resource.CommonAssetsManager");
            Class<?> raw=Class.forName("com.tacz.guns.crafting.result.RawGunTableResult");
            Gson gson=(Gson)assets.getField("GSON").get(null);
            Object result=raw.getMethod("init",raw).invoke(null,gson.fromJson(json,raw));
            return ((ItemStack)result.getClass().getMethod("getResult").invoke(result)).copy();
        }catch(ReflectiveOperationException | RuntimeException unavailable){return ItemStack.EMPTY;}
    }
    /** Cuisine base IDs refer to table definitions. Ask the mod's native ingredient collector, never its JEI classes. */
    public static List<ItemStack> cuisineBasePreview(String id) {
        if(!KineticPlatform.isModLoaded("youkaisfeasts"))return List.of();
        //? if <26.1 {
        try {
            var key=dev.xyat.kineticcore.api.resource.KineticResourceIds.tryParse(id);if(key==null)return List.of();
            String prefix="dev.xkmc.youkaishomecoming.content.pot.table.item.";
            Class<?> variant=Class.forName(prefix+"VariantTableItemBase");
            Object base=((Map<?,?>)variant.getField("MAP").get(null)).get(key);
            var ingredients=new ArrayList<net.minecraft.world.item.crafting.Ingredient>();
            if(base!=null)variant.getMethod("collectIngredients",List.class,List.class).invoke(base,ingredients,new ArrayList<>());
            else {
                Class<?> fixed=Class.forName(prefix+"IngredientTableItem");base=((Map<?,?>)fixed.getField("FIXED").get(null)).get(key);
                if(base!=null)fixed.getMethod("collectIngredients",List.class).invoke(base,ingredients);
            }
            return ingredients.stream().flatMap(i->Arrays.stream(i.getItems())).limit(128).map(ItemStack::copy).toList();
        }catch(ReflectiveOperationException | RuntimeException unavailable){return List.of();}
        //?} else {
        /*return List.of();
        *///?}
    }
}
