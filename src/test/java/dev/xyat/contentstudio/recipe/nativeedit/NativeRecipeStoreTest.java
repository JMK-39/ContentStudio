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
    @Test void atomicUpdateRetainsOtherRecipesAndRejectsStaleDrafts() throws Exception {
        Path file=temporary.resolve("overrides.json");
        var id=KineticResourceIds.parse("example:cutting");
        var draft=JsonParser.parseString("{\"type\":\"farmersdelight:cutting\",\"custom\":{\"number\":1234567890123456789}}").getAsJsonObject();
        String first=NativeRecipeStore.revision(new JsonObject());
        NativeRecipeStore.update(file,id,draft,first);
        String written=Files.readString(file);
        assertThrows(IOException.class,()->NativeRecipeStore.update(file,id,null,first));
        assertEquals(written,Files.readString(file));
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
        Path file=temporary.resolve("overrides.json");Files.writeString(file,"{bad json");
        assertThrows(IOException.class,()->NativeRecipeStore.update(file,KineticResourceIds.parse("example:a"),null,"stale"));
        assertEquals("{bad json",Files.readString(file));
    }
}
