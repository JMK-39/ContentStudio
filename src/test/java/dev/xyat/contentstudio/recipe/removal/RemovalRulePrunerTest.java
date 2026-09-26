package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemovalRulePrunerTest {
    @Test
    void missingOutputItemRemovesOnlyItsOwnRule() {
        var missing = new RemovalEntry(RemovalMode.OUTPUT, "missing:item", "");
        var present = new RemovalEntry(RemovalMode.OUTPUT, "example:plate", "");
        var exact = new RemovalEntry(RemovalMode.RECIPE_ID, "missing:recipe", "");

        assertEquals(List.of(present, exact), RemovalRulePruner.removeMissingOutputItems(
                List.of(missing, present, exact), id -> id.equals(new ResourceLocation("example:plate"))));
    }
}
