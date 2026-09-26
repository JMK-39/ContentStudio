package dev.xyat.contentstudio.recipe.removal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OriginalRecipeFilterTest {
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static JsonObject json() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "example:serializer");
        return json;
    }
    private static OriginalRecipeCatalog.InspectedRecipe inspected(ResourceLocation id, String type) {
        return new OriginalRecipeCatalog.InspectedRecipe(new RemovalCandidate(id,
                type == null ? null : id(type), null, Set.of()), Optional.empty());
    }

    @Test
    void removedOriginalRemainsInImmutableCatalogAndLaterSameIdSurvives() {
        var originalId = id("example:original");
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(originalId, json()));
        var catalog = OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.RECIPE_ID, "example:original", "")),
                (key, value) -> Optional.of(inspected(key, null)));
        assertTrue(source.isEmpty());
        assertTrue(catalog.entries().get(originalId).removed());
        assertThrows(UnsupportedOperationException.class, () -> catalog.entries().clear());
        JsonObject later = json();
        later.addProperty("script", true);
        source.put(originalId, later);
        assertSame(later, source.get(originalId));
        assertEquals(1, catalog.entries().size());
    }

    @Test
    void idAndModWorkWithoutResolvableOutputButOutputDoesNot() {
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(
                id("one:original"), json(), id("two:original"), json(), id("three:original"), json()));
        var catalog = OriginalRecipeCatalog.filter(source, List.of(
                new RemovalEntry(RemovalMode.RECIPE_ID, "one:original", ""),
                new RemovalEntry(RemovalMode.MOD, "two", ""),
                new RemovalEntry(RemovalMode.OUTPUT, "minecraft:stone", "")),
                (key, value) -> Optional.of(inspected(key, null)));
        assertEquals(Set.of(id("three:original")), source.keySet());
        assertEquals(3, catalog.entries().size());
    }

    @Test
    void skippedConditionAndMetadataNeverBecomeCandidates() {
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(
                id("example:_metadata"), json(), id("example:condition_false"), json(), id("example:valid"), json()));
        var catalog = OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.MOD, "example", "")),
                (key, value) -> {
                    assertFalse(key.getPath().startsWith("_"));
                    return key.getPath().equals("condition_false") ? Optional.empty() : Optional.of(inspected(key, null));
                });
        assertEquals(Set.of(id("example:valid")), catalog.entries().keySet());
        assertEquals(Set.of(id("example:_metadata"), id("example:condition_false")), source.keySet());
    }

    @Test
    void serializerJsonTypeDoesNotDriveTypeMatching() {
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(id("example:original"), json()));
        var catalog = OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.TYPE, "example:serializer", "")),
                (key, value) -> Optional.of(inspected(key, "minecraft:crafting")));
        assertFalse(catalog.entries().get(id("example:original")).removed());
        assertEquals(1, source.size());
        OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.TYPE, "minecraft:crafting", "")),
                (key, value) -> Optional.of(inspected(key, "minecraft:crafting")));
        assertTrue(source.isEmpty());
    }

    @Test
    void exclusionsProtectOnlyTheirOwnRuleAndCatalogKeepsOriginalDecision() {
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(id("example:original"), json()));
        var excluded = new RemovalEntry(RemovalMode.MOD, "example", "", List.of(id("example:original")));
        var blocking = new RemovalEntry(RemovalMode.RECIPE_ID, "example:original", "");
        var catalog = OriginalRecipeCatalog.filter(source, List.of(excluded, blocking),
                (key, value) -> Optional.of(inspected(key, null)));
        assertEquals(List.of(blocking), catalog.entries().get(id("example:original")).blockingRules());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.entries().get(id("example:original")).blockingRules().clear());
    }

    @Test
    void inspectionFailureDoesNotPartiallyMutateSource() {
        Map<ResourceLocation, JsonElement> source = new HashMap<>(Map.of(
                id("example:first"), json(), id("example:second"), json()));
        var before = new HashMap<>(source);
        assertThrows(IllegalStateException.class, () -> OriginalRecipeCatalog.filter(source,
                List.of(new RemovalEntry(RemovalMode.MOD, "example", "")),
                (key, value) -> { throw new IllegalStateException("inspection failed"); }));
        assertEquals(before, source);
    }
}
