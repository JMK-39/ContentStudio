package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemovalRuleValidatorTest {
    @Test
    void duplicateKeyIsRejectedEvenWhenContentDiffers() {
        var existing = new RemovalEntry(RemovalMode.MOD, "minecraft", "old",
                List.of(new ResourceLocation("minecraft:old")));
        var submitted = new RemovalEntry(RemovalMode.MOD, "minecraft", "new");

        assertFalse(RemovalRuleValidator.canAdd(submitted, List.of(existing)));
        assertTrue(RemovalRuleValidator.canAdd(
                new RemovalEntry(RemovalMode.MOD, "forge", ""), List.of(existing)));
        assertTrue(RemovalRuleValidator.canAdd(
                new RemovalEntry(RemovalMode.TYPE, "minecraft", ""), List.of(existing)));
    }
}
