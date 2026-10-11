package dev.xyat.contentstudio.recipe.nativeedit;

import org.junit.jupiter.api.Test;
import java.util.function.BiPredicate;
import static org.junit.jupiter.api.Assertions.*;

class JeiRecipeSlotBoundsTest {
    @Test void extractsTheIngredientHitAreaFromASharedCategoryBackground() throws Exception {
        BiPredicate<Double,Double> hit=(x,y)->x>=8&&x<24&&y>=12&&y<28;
        int[] bounds=JeiRecipeSlotBounds.resolve(0,0,62,42,hit);
        assertArrayEquals(new int[]{8,12,16,16},bounds);
    }
}
