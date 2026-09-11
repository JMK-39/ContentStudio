package dev.xyat.contentstudio.recipe.compat.jei;

import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.removal.RecipeSummary;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public final class RecipeRemovalJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("contentstudio", "recipe_removal");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeJeiBridge.setAccess(new Access(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeJeiBridge.setAccess(null);
    }

    private record Access(IJeiRuntime runtime) implements RecipeJeiBridge.Access {
        private List<IFocus<?>> focus(ItemStack output) {
            return List.of(runtime.getJeiHelpers().getFocusFactory()
                    .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output));
        }

        @Override
        public void show(ItemStack output) {
            runtime.getRecipesGui().show(focus(output));
        }

        @Override
        public List<RecipeJeiBridge.Entry> recipes(ItemStack output) {
            List<RecipeJeiBridge.Entry> result = new ArrayList<>();
            List<IFocus<?>> focus = focus(output);
            runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().limitFocus(focus).get()
                    .forEach(category -> collect(category, focus, output, result));
            return result;
        }

        private <T> void collect(IRecipeCategory<T> category, List<IFocus<?>> focus, ItemStack output,
                                 List<RecipeJeiBridge.Entry> result) {
            try {
                runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).includeHidden()
                        .limitFocus(focus).get().forEach(recipe -> {
                            try {
                                ResourceLocation id = category.getRegistryName(recipe);
                                if (id == null && recipe instanceof Recipe<?> vanilla) id = vanilla.getId();
                                var level = Minecraft.getInstance().level;
                                var registered = level == null || id == null ? null
                                        : level.getRecipeManager().byKey(id).orElse(null);
                                RecipeSummary summary = registered == null
                                        ? new RecipeSummary(id, category.getRecipeType().getUid(), output.copy(), List.of(), 0)
                                        : RecipeSummary.of(registered, level.registryAccess());
                                result.add(new RecipeJeiBridge.Entry(summary, category.getTitle(), registered != null,
                                        () -> runtime.getRecipesGui().showRecipes(category, List.of(recipe), focus),
                                        () -> runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                                                runtime.getJeiHelpers().getFocusFactory().createFocusGroup(focus))
                                                .map(LayoutPreview::new).orElse(null)));
                            } catch (RuntimeException ignored) {
                            }
                        });
            } catch (RuntimeException ignored) {
            }
        }
    }

    private record LayoutPreview(IRecipeLayoutDrawable<?> layout) implements RecipeJeiBridge.Preview {
        private LayoutPreview { layout.setPosition(0, 0); }
        @Override public int width() { return layout.getRect().getWidth(); }
        @Override public int height() { return layout.getRect().getHeight(); }
        @Override public void draw(GuiGraphics graphics, int mouseX, int mouseY) { layout.drawRecipe(graphics, mouseX, mouseY); }
        @Override public void drawOverlays(GuiGraphics graphics, int mouseX, int mouseY) { layout.drawOverlays(graphics, mouseX, mouseY); }
        @Override public void tick() { layout.tick(); }
    }
}
