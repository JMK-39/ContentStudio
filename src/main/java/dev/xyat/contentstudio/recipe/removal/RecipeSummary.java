package dev.xyat.contentstudio.recipe.removal;

import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

public record RecipeSummary(ResourceLocation id, ResourceLocation type, ItemStack output,
                            List<Ingredient> inputs, int craftingWidth) {
    public static RecipeSummary of(Recipe<?> recipe, RegistryAccess registries) {
        ResourceLocation type = KineticRegistries.recipeTypes().id(recipe.getType());
        return new RecipeSummary(recipe.getId(), type == null ? recipe.getId() : type,
                recipe.getResultItem(registries).copy(), List.copyOf(recipe.getIngredients()),
                recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0);
    }

    public void encode(NetworkBuffer buffer) {
        buffer.writeResourceLocation(id);
        buffer.writeResourceLocation(type);
        buffer.writeItemStack(output);
        buffer.writeVarInt(inputs.size());
        inputs.forEach(buffer::writeIngredient);
        buffer.writeVarInt(craftingWidth);
    }

    public static RecipeSummary decode(NetworkBuffer buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        ResourceLocation type = buffer.readResourceLocation();
        ItemStack output = buffer.readItemStack();
        int size = buffer.readVarInt();
        List<Ingredient> inputs = new ArrayList<>();
        for (int i = 0; i < size; i++) inputs.add(buffer.readIngredient());
        return new RecipeSummary(id, type, output, List.copyOf(inputs), buffer.readVarInt());
    }
}
