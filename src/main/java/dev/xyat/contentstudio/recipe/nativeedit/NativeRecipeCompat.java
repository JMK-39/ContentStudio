package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.world.item.ItemStack;

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
}
