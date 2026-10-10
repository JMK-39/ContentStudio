package dev.xyat.contentstudio.recipe.compat.jei;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphicsInterop;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
//? if >=1.21 {
/*import net.minecraft.world.item.crafting.RecipeHolder;
*///?}


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import com.google.gson.JsonObject;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import dev.xyat.kineticcore.api.registry.KineticRegistries;

@JeiPlugin
public final class RecipeRemovalJeiPlugin implements IModPlugin {
    @Override
    @Nonnull
    public ResourceLocation getPluginUid() {
        return KineticResourceIds.of("contentstudio", "recipe_removal");
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        RecipeJeiBridge.setAccess(new Access(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeJeiBridge.setAccess(null);
    }

    private static final class Access implements RecipeJeiBridge.Access {
        private final IJeiRuntime runtime;
        private final Map<ResourceLocation, List<EditorFactory<?>>> editors = new LinkedHashMap<>();
        private final Map<String, List<EditorFactory<?>>> types = new LinkedHashMap<>();
        private boolean indexed;
        private Access(IJeiRuntime runtime) { this.runtime = runtime; }

        @Override public RecipeJeiBridge.EditorPreview editor(ResourceLocation id, JsonObject json) {
            if (!indexed) {
                indexed = true;
                runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().forEach(this::index);
            }
            var factories = editors.getOrDefault(id, List.of());
            String serializer=dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument.serializerId(json);
            var ordered=new ArrayList<>(factories);
            for(var factory:types.getOrDefault(serializer,List.of()))if(!ordered.contains(factory))ordered.add(factory);
            ordered.sort(java.util.Comparator.comparingInt(factory->factory.category().getRecipeType().getUid().toString().equals(serializer)?0:1));
            for(var factory:ordered) {
                var result=factory.create(id,json,runtime);if(result!=null)return result;
            }
            return null;
        }

        private <T> void index(IRecipeCategory<T> category) {
            try {
                runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).includeHidden().get().forEach(recipe -> {
                    try {
                        var id = recipeId(category, recipe);
                        if (id != null) {
                            var factory=new EditorFactory<>(id,category);editors.computeIfAbsent(id,key->new ArrayList<>()).add(factory);
                            Object nativeRecipe=recipe;
//? if >=1.21 {
/*                            if(nativeRecipe instanceof RecipeHolder<?> holder)nativeRecipe=holder.value();
*///?}
                            if(nativeRecipe instanceof Recipe<?> value) {
                                String serializer=net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(value.getSerializer()).toString();
                                var categories=types.computeIfAbsent(serializer,key->new ArrayList<>());
                                if(categories.stream().noneMatch(existing->existing.category()==category))categories.add(factory);
                            }
                        }
                    } catch (RuntimeException ignored) { }
                });
            } catch (RuntimeException ignored) { }
        }
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
//? if >=26.1 {
/*
                                if (id == null && recipe instanceof RecipeHolder<?> vanilla) id = vanilla.id().identifier();
*///?} else if >=1.21 {
/*
                                if (id == null && recipe instanceof RecipeHolder<?> vanilla) id = vanilla.id();
*///?} else {
                                if (id == null && recipe instanceof Recipe<?> vanilla) id = vanilla.getId();
//?}
                                var level = KineticClientRuntime.currentLevel();
                                // 26.1 clients only know the recipes the server synced.
//? if >=26.1 {
/*
                                var registered = level == null || id == null ? null
                                        : dev.xyat.contentstudio.recipe.client.ClientRecipes.byId(id);
*///?} else {
                                var registered = level == null || id == null ? null
                                        : level.getRecipeManager().byKey(id).orElse(null);
//?}
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

    private static <T> ResourceLocation recipeId(IRecipeCategory<T> category, T recipe) {
        ResourceLocation id = category.getRegistryName(recipe);
//? if >=26.1 {
/*        if (id == null && recipe instanceof RecipeHolder<?> holder) id = holder.id().identifier();
*///?} else if >=1.21 {
/*        if (id == null && recipe instanceof RecipeHolder<?> holder) id = holder.id();
*///?} else {
        if (id == null && recipe instanceof Recipe<?> value) id = value.getId();
//?}
        return id;
    }

    private record EditorFactory<T>(ResourceLocation id, IRecipeCategory<T> category) {
        private RecipeJeiBridge.EditorPreview create(ResourceLocation draftId,JsonObject json, IJeiRuntime runtime) {
            Object decoded = JeiDraftRecipes.decode(draftId, json);
            Class<? extends T> recipeClass = category.getRecipeType().getRecipeClass();
//? if >=1.21 {
/*            if (!recipeClass.isInstance(decoded) && decoded instanceof RecipeHolder<?> holder) decoded = holder.value();
*///?}
            // A category backed by a synthetic display object is view-only unless its native recipe can be decoded.
            if (!recipeClass.isInstance(decoded)) return null;
            return runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipeClass.cast(decoded),
                    runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of()))
                    .map(layout -> (RecipeJeiBridge.EditorPreview) new EditorLayout(layout, runtime)).orElse(null);
        }
    }

    private static final class EditorLayout implements RecipeJeiBridge.EditorPreview {
        private final IRecipeLayoutDrawable<?> layout;
        private final List<RecipeJeiBridge.EditorSlot> slots;
        private EditorLayout(IRecipeLayoutDrawable<?> layout, IJeiRuntime runtime) {
            this.layout = layout;
            layout.setPosition(0, 0);
            var result = new ArrayList<RecipeJeiBridge.EditorSlot>();
            for (var view : layout.getRecipeSlotsView().getSlotViews()) {
                if (!(view instanceof IRecipeSlotDrawable drawable)) continue;
                var rect = drawable.getAreaIncludingBackground();
                Set<String> values = new LinkedHashSet<>();
                view.getAllIngredients().forEach(ingredient -> {
                    try { values.add(token(ingredient, runtime)); } catch (RuntimeException ignored) { }
                });
                result.add(new RecipeJeiBridge.EditorSlot(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(),
                        view.getRole().name(), values, view.getSlotName().orElse("")));
            }
            slots = List.copyOf(result);
        }
        private static <T> String token(ITypedIngredient<T> ingredient, IJeiRuntime runtime) {
            var stack = ingredient.getItemStack();
            if (stack.isPresent()) return "item:" + KineticRegistries.items().id(stack.get().getItem());
            var helper = runtime.getIngredientManager().getIngredientHelper(ingredient.getType());
            return "resource:" + helper.getResourceLocation(ingredient.getIngredient());
        }
        @Override public List<RecipeJeiBridge.EditorSlot> slots() { return slots; }
        @Override public net.minecraft.network.chat.Component category() { return layout.getRecipeCategory().getTitle(); }
        @Override public int width() { return layout.getRect().getWidth(); }
        @Override public int height() { return layout.getRect().getHeight(); }
        @Override public void draw(KineticGraphics graphics, int mouseX, int mouseY) { layout.drawRecipe(KineticGraphicsInterop.unwrap(graphics), mouseX, mouseY); }
        @Override public void drawOverlays(KineticGraphics graphics, int mouseX, int mouseY) { layout.drawOverlays(KineticGraphicsInterop.unwrap(graphics), mouseX, mouseY); }
        @Override public void tick() { layout.tick(); }
    }

    private record LayoutPreview(IRecipeLayoutDrawable<?> layout) implements RecipeJeiBridge.Preview {
        private LayoutPreview { layout.setPosition(0, 0); }
        @Override public int width() { return layout.getRect().getWidth(); }
        @Override public int height() { return layout.getRect().getHeight(); }
        @Override public void draw(KineticGraphics graphics, int mouseX, int mouseY) { layout.drawRecipe(KineticGraphicsInterop.unwrap(graphics), mouseX, mouseY); }
        @Override public void drawOverlays(KineticGraphics graphics, int mouseX, int mouseY) { layout.drawOverlays(KineticGraphicsInterop.unwrap(graphics), mouseX, mouseY); }
        @Override public void tick() { layout.tick(); }
    }
}
