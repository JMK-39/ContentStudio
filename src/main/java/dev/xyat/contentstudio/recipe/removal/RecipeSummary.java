package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** A recipe's identity and ingredients, including recipes removed from the active manager. */
public record RecipeSummary(ResourceLocation id, ResourceLocation type, ItemStack output,
                            List<Ingredient> inputs, int craftingWidth) {
    public static RecipeSummary of(Recipe<?> recipe, RegistryAccess registries) {
        ResourceLocation type = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
        return new RecipeSummary(recipe.getId(), type == null ? recipe.getId() : type,
                recipe.getResultItem(registries).copy(), List.copyOf(recipe.getIngredients()),
                recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeResourceLocation(type);
        buf.writeItem(output);
        buf.writeVarInt(inputs.size());
        inputs.forEach(input -> input.toNetwork(buf));
        buf.writeVarInt(craftingWidth);
    }

    public static RecipeSummary decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        ResourceLocation type = buf.readResourceLocation();
        ItemStack output = buf.readItem();
        int size = buf.readVarInt();
        List<Ingredient> inputs = new ArrayList<>();
        for (int i = 0; i < size; i++) inputs.add(Ingredient.fromNetwork(buf));
        return new RecipeSummary(id, type, output, List.copyOf(inputs), buf.readVarInt());
    }
}
