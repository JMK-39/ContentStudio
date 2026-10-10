package dev.xyat.contentstudio.recipe.nativeedit;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiRecipeSlotBindingsTest {
    private int[] bind(List<Boolean> outputs, List<Set<String>> shown, List<Boolean> candidates,
                       List<Set<String>> values) throws Exception {
        return (int[]) Class.forName("dev.xyat.contentstudio.recipe.nativeedit.JeiRecipeSlotBindings")
                .getMethod("match", List.class, List.class, List.class, List.class)
                .invoke(null, outputs, shown, candidates, values);
    }
    @Test void matchesByIngredientAndRoleRatherThanJeiDisplayOrder() throws Exception {
        assertArrayEquals(new int[]{2, 1, 0}, bind(List.of(true, false, false),
                List.of(Set.of("item:diamond"), Set.of("item:iron"), Set.of("item:gold")),
                List.of(false, false, true),
                List.of(Set.of("item:gold"), Set.of("item:iron"), Set.of("item:diamond"))));
    }
    @Test void rejectsAmbiguousDuplicateInputsAndPrefersAnExactAlternativeSet() throws Exception {
        assertArrayEquals(new int[]{-1, 0, -1}, bind(List.of(false, false, false),
                List.of(Set.of("item:oak"), Set.of("item:oak", "item:birch"), Set.of("item:oak")),
                List.of(false, false, false),
                List.of(Set.of("item:oak", "item:birch"), Set.of("item:oak"), Set.of("item:oak"))));
    }
    @Test void reorderedSameItemOutputsCannotSilentlyChooseTheFirstJsonPath() throws Exception {
        assertArrayEquals(new int[]{-1,-1},bind(List.of(true,true),List.of(Set.of("item:iron"),Set.of("item:iron")),
                List.of(true,true),List.of(Set.of("item:iron"),Set.of("item:iron"))));
    }
    @Test void renderOnlyAndCatalystRolesAreNotEditable() throws Exception {
        var role=Class.forName("dev.xyat.contentstudio.recipe.nativeedit.JeiRecipeSlotBindings")
                .getMethod("isEditableRole",String.class);
        assertEquals(false,role.invoke(null,"RENDER_ONLY"));
        assertEquals(false,role.invoke(null,"CATALYST"));
        assertEquals(true,role.invoke(null,"INPUT"));
        assertEquals(true,role.invoke(null,"OUTPUT"));
    }
    @Test void decorativeSlotsAreNeverAssignedToUnrelatedJsonFields() throws Exception {
        assertArrayEquals(new int[]{-1, -1, 0}, bind(List.of(false, true, false),
                List.of(Set.of("item:workstation"), Set.of("item:iron"), Set.of("item:iron")),
                List.of(false), List.of(Set.of("item:iron"))));
    }
    @Test void itemAndFluidWithTheSameIdentifierRemainDistinct() throws Exception {
        assertArrayEquals(new int[]{1, 0}, bind(List.of(false, false),
                List.of(Set.of("resource:water"), Set.of("item:water")),
                List.of(false, false), List.of(Set.of("item:water"), Set.of("resource:water"))));
    }
}
