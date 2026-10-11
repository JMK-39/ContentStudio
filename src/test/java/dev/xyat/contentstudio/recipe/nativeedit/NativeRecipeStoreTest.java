package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeStoreTest {
    @TempDir Path temporary;
    @Test void groupingUsesRegisteredTypeRatherThanRawRecipeIdOrCustomSerializer() throws Exception {
        Path file=temporary.resolve("recipe/minecraft.json");
        var vanillaId=KineticResourceIds.parse("thirdparty:keep_original_id");
        NativeRecipeStore.recordType(vanillaId,KineticResourceIds.parse("minecraft:crafting"));
        var vanilla=JsonParser.parseString("{\"type\":\"thirdparty:special_shaped\"}").getAsJsonObject();
        NativeRecipeStore.update(file,vanillaId,vanilla,NativeRecipeStore.revision(new JsonObject()));
        assertEquals(vanilla,NativeRecipeFiles.read(file).getAsJsonObject("overrides").get(vanillaId.toString()));
        var customId=KineticResourceIds.parse("minecraft:keep_custom_id");
        NativeRecipeStore.recordType(customId,KineticResourceIds.parse("create:assembling"));
        var custom=JsonParser.parseString("{\"type\":\"other:machine_serializer\"}").getAsJsonObject();
        NativeRecipeStore.update(file,customId,custom,NativeRecipeStore.revision(NativeRecipeStore.local(file)));
        assertEquals(custom,NativeRecipeFiles.read(file.resolveSibling("create.json")).getAsJsonObject("overrides").get(customId.toString()));
        assertFalse(NativeRecipeFiles.read(file).getAsJsonObject("overrides").has(customId.toString()));
    }
    @Test void readingMovedLocationPreservesExistingOverridesWithoutConversion() throws Exception {
        Path oldFile=temporary.resolve("recipe_overrides.json");
        Path file=temporary.resolve("recipe/recipe_overrides.json");
        String saved="{\"example:cutting\":{\"type\":\"farmersdelight:cutting\"}}";
        Files.writeString(oldFile,saved);
        assertEquals(JsonParser.parseString(saved),NativeRecipeStore.local(file));
        assertFalse(Files.exists(file.resolveSibling("farmersdelight.json")));
        assertFalse(Files.exists(file));
        assertEquals(saved,Files.readString(oldFile));
        try(var paths=Files.walk(temporary)) { assertFalse(paths.anyMatch(path->path.toString().endsWith(".bak"))); }
    }
    @Test void readingKeepsNewerEditsAndRetainsBothLegacyFiles() throws Exception {
        Path oldFile=temporary.resolve("recipe_overrides.json");
        Path file=temporary.resolve("recipe/recipe_overrides.json");
        Files.createDirectories(file.getParent());
        Files.writeString(oldFile,"{\"example:same\":{\"type\":\"test:old\"},\"example:other\":{\"type\":\"test:other\"}}");
        Files.writeString(file,"{\"example:same\":{\"type\":\"test:new\"}}");
        var merged=NativeRecipeStore.local(file);
        assertEquals("test:new",merged.getAsJsonObject("example:same").get("type").getAsString());
        assertTrue(merged.has("example:other"));
        assertTrue(Files.exists(oldFile));
        assertEquals(merged,NativeRecipeStore.local(file));
    }
    @Test void failedRelocationLeavesBothExistingFilesIntact() throws Exception {
        Path oldFile=temporary.resolve("recipe_overrides.json");
        Path file=temporary.resolve("recipe/recipe_overrides.json");
        Files.createDirectories(file.getParent());
        String saved="{\"example:cutting\":{\"type\":\"farmersdelight:cutting\"}}";
        Files.writeString(oldFile,saved);
        Files.writeString(file,"{broken");
        assertThrows(IOException.class,()->NativeRecipeStore.local(file));
        assertEquals(saved,Files.readString(oldFile));
        assertEquals("{broken",Files.readString(file));
    }
    @Test void atomicUpdateRetainsOtherRecipesAndRejectsStaleDrafts() throws Exception {
        Path file=temporary.resolve("overrides.json");
        var id=KineticResourceIds.parse("example:cutting");
        var draft=JsonParser.parseString("{\"type\":\"farmersdelight:cutting\",\"custom\":{\"number\":1234567890123456789}}").getAsJsonObject();
        String first=NativeRecipeStore.revision(new JsonObject());
        NativeRecipeStore.update(file,id,draft,first);
        Path grouped=file.resolveSibling("farmersdelight.json");String written=Files.readString(grouped);
        assertThrows(IOException.class,()->NativeRecipeStore.update(file,id,null,first));
        assertEquals(written,Files.readString(grouped));
        var root=NativeRecipeStore.local(file);
        NativeRecipeStore.update(file,KineticResourceIds.parse("example:second"),draft,NativeRecipeStore.revision(root));
        root=NativeRecipeStore.local(file);
        assertEquals(draft,root.get(id.toString()));
        NativeRecipeStore.update(file,id,null,NativeRecipeStore.revision(root));
        assertFalse(NativeRecipeStore.local(file).has(id.toString()));
        assertTrue(NativeRecipeStore.local(file).has("example:second"));
        try(var paths=Files.list(temporary)) {assertEquals(1,paths.count());}
    }
    @Test void malformedSavedFileIsNeverOverwritten() throws Exception {
        Path file=temporary.resolve("minecraft.json");Files.writeString(file,"{bad json");
        assertThrows(IOException.class,()->NativeRecipeStore.update(file,KineticResourceIds.parse("example:a"),null,"stale"));
        assertEquals("{bad json",Files.readString(file));
    }
}
