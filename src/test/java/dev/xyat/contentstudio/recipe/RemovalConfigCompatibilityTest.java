package dev.xyat.contentstudio.recipe;

import com.google.gson.JsonParser;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemovalConfigCompatibilityTest {
    @Test
    void legacyRemovalHasNoExclusions() {
        var root = JsonParser.parseString("{\"removals\":[{\"mode\":\"MOD\",\"value\":\"minecraft\"}]}").getAsJsonObject();
        assertEquals(List.of(), RemovalConfigCodec.readRemovals(root).get(0).excludedRecipeIds());
    }

    @Test
    void unknownRecipeIdSurvivesJsonRoundTrip() {
        var original = new RemovalEntry(dev.xyat.contentstudio.recipe.removal.RemovalMode.MOD,
                "minecraft", "note", List.of(new ResourceLocation("minecraft:old")));
        var root = JsonParser.parseString("{}").getAsJsonObject();
        root.add("removals", RemovalConfigCodec.writeRemovals(List.of(original)));
        assertEquals(List.of(original), RemovalConfigCodec.readRemovals(root));
        assertEquals("minecraft:old", root.getAsJsonArray("removals").get(0).getAsJsonObject()
                .getAsJsonArray("excluded_recipe_ids").get(0).getAsString());
    }
}
