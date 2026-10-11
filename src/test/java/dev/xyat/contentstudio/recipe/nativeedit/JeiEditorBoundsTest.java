package dev.xyat.contentstudio.recipe.nativeedit;

import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class JeiEditorBoundsTest {
    @Test void sharedCookingBackgroundDoesNotEnlargeTheEditableIngredientArea() throws Exception {
        // JEI permits a slot background to span the entire cooking ingredient grid.
        var slot=(IRecipeSlotDrawable)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IRecipeSlotDrawable.class},(p,m,a)->switch(m.getName()) {
            case "getRect" -> new Rect2i(8,12,16,16);
            case "getAreaIncludingBackground" -> new Rect2i(0,0,62,42);
            case "getAllIngredients" -> Stream.empty();
            case "getRole" -> RecipeIngredientRole.INPUT;
            case "getSlotName" -> Optional.empty();
            default -> null;
        });
        IRecipeSlotsView views=()->List.of(slot);
        var layout=(IRecipeLayoutDrawable<?>)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IRecipeLayoutDrawable.class},(p,m,a)->m.getName().equals("getRecipeSlotsView")?views:null);
        var constructor=Class.forName("dev.xyat.contentstudio.recipe.compat.jei.RecipeRemovalJeiPlugin$EditorLayout").getDeclaredConstructor(IRecipeLayoutDrawable.class,IJeiRuntime.class);
        constructor.setAccessible(true);
        var preview=(RecipeJeiBridge.EditorPreview)constructor.newInstance(layout,null);
        var bounds=preview.slots().get(0);
        assertEquals(List.of(8,12,16,16),List.of(bounds.x(),bounds.y(),bounds.width(),bounds.height()));
    }
}
