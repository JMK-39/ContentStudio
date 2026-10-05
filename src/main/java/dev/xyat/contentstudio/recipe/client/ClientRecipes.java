//? if >=26.1 {
/*package dev.xyat.contentstudio.recipe.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collection;

/^*
 * The recipes the server sent to this client. 26.1 clients have no recipe manager; the server asks NeoForge to sync
 * the recipe types (see RecipeMemoryManager) and the received recipes are kept here until the player logs out.
 ^/
public final class ClientRecipes {
    private static volatile RecipeMap recipes = RecipeMap.EMPTY;
    private static boolean registered;

    private ClientRecipes() {
    }

    public static synchronized void register() {
        if (registered) return;
        NeoForge.EVENT_BUS.addListener((RecipesReceivedEvent event) -> recipes = event.getRecipeMap());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> recipes = RecipeMap.EMPTY);
        registered = true;
    }

    public static Collection<RecipeHolder<?>> all() {
        return recipes.values();
    }

    public static RecipeHolder<?> byId(ResourceLocation id) {
        return id == null ? null : recipes.byKey(ResourceKey.create(Registries.RECIPE, id));
    }
}
*///?}
