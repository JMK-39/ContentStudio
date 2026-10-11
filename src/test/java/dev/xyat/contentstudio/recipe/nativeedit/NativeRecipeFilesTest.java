package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeFilesTest {
    @TempDir Path temporary;
    private static final BiFunction<String,JsonObject,String> OWNER=(id,json)->json.get("type").getAsString().split(":")[0];
    private JsonObject json(String text) {return JsonParser.parseString(text).getAsJsonObject();}
    private JsonObject read(Path path) throws Exception {return json(Files.readString(path));}
    @Test void repeatedReadsNeverConvertRewriteOrDeleteLegacyFiles() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        String bundle="{\"version\":1,\"recipes\":[{\"id\":\"mymod:old\"}],\"removals\":[],\"custom_setting\":true}";
        String overrides="{\"pack:gun\":{\"type\":\"tacz:gun_smith_table_crafting\",\"result\":{\"type\":\"gun\",\"id\":\"tacz:ak47\"}}}";
        Path oldBundle=directory.resolve("recipe_bundle.json"),oldOverrides=directory.resolve("recipe_overrides.json");
        Files.writeString(oldBundle,bundle);Files.writeString(oldOverrides,overrides);
        var modified=Files.getLastModifiedTime(oldOverrides);
        for(int i=0;i<2;i++) {
            assertEquals(json(overrides),NativeRecipeFiles.local(directory,OWNER));
            assertEquals(json(bundle),NativeRecipeFiles.workstation(directory.resolve("minecraft.json")));
        }
        assertEquals(bundle,Files.readString(oldBundle));assertEquals(overrides,Files.readString(oldOverrides));
        assertEquals(modified,Files.getLastModifiedTime(oldOverrides));
        try(var paths=Files.list(directory)){assertEquals(2,paths.count());}
    }
    @Test void explicitSaveUsesTypeOwnerAndKeepsOriginalIdsAndUnrelatedSettings() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        Files.writeString(directory.resolve("minecraft.json"),"""
                {"recipes":[{"uuid":"kept"}],"removals":[],"custom_setting":true}
                """);
        var edits=json("""
                {"create:vanilla":{"type":"custom:shaped"},"minecraft:machine":{"type":"create:mixing"},
                 "pack:gun":{"type":"tacz:gun_smith_table_crafting","result":{"id":"tacz:ak47"}}}
                """);
        NativeRecipeFiles.save(directory,edits,(id,data)->id.equals("create:vanilla")?"minecraft":OWNER.apply(id,data));
        var minecraft=read(directory.resolve("minecraft.json"));
        assertTrue(minecraft.get("custom_setting").getAsBoolean());assertEquals(1,minecraft.getAsJsonArray("recipes").size());
        assertTrue(minecraft.getAsJsonObject("overrides").has("create:vanilla"));
        assertTrue(read(directory.resolve("create.json")).getAsJsonObject("overrides").has("minecraft:machine"));
        assertEquals("tacz:ak47",read(directory.resolve("tacz.json")).getAsJsonObject("overrides").getAsJsonObject("pack:gun").getAsJsonObject("result").get("id").getAsString());
    }
    @Test void restoringLegacyOverrideDoesNotResurrectItOrRewriteItsSource() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        Path oldFile=directory.resolve("recipe_overrides.json");
        String legacy="{\"example:one\":{\"type\":\"minecraft:stonecutting\"}}";
        Files.writeString(oldFile,legacy);
        NativeRecipeFiles.save(directory,new JsonObject(),OWNER);
        assertEquals(0,NativeRecipeFiles.local(directory,OWNER).size());
        assertTrue(read(directory.resolve("minecraft.json")).getAsJsonObject("overrides").get("example:one").isJsonNull());
        NativeRecipeFiles.save(directory,NativeRecipeFiles.local(directory,OWNER),OWNER);
        assertEquals(0,NativeRecipeFiles.local(directory,OWNER).size());assertEquals(legacy,Files.readString(oldFile));
    }
    @Test void currentGroupedEditWinsWithoutChangingEitherSource() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        String legacy="""
                {"example:same":{"type":"create:mixing","time":1},"example:other":{"type":"minecraft:stonecutting"}}
                """;
        String current="{\"overrides\":{\"example:same\":{\"type\":\"create:mixing\",\"time\":2}}}";
        Files.writeString(temporary.resolve("recipe_overrides.json"),legacy);Files.writeString(directory.resolve("create.json"),current);
        var merged=NativeRecipeFiles.local(directory,OWNER);
        assertEquals(2,merged.getAsJsonObject("example:same").get("time").getAsInt());assertTrue(merged.has("example:other"));
        assertEquals(legacy,Files.readString(temporary.resolve("recipe_overrides.json")));assertEquals(current,Files.readString(directory.resolve("create.json")));
    }
    @Test void workstationSaveRetainsTheLatestNativeOverrides() throws Exception {
        Path file=temporary.resolve("minecraft.json");
        Files.writeString(file,"""
                {"recipes":[],"overrides":{"mod:stone":{"type":"minecraft:stonecutting","count":4}}}
                """);
        NativeRecipeFiles.writeMinecraft(file,json("{\"recipes\":[{\"uuid\":\"kept\"}],\"overrides\":{}}"));
        assertEquals(4,read(file).getAsJsonObject("overrides").getAsJsonObject("mod:stone").get("count").getAsInt());
        assertEquals(1,read(file).getAsJsonArray("recipes").size());
    }
    @Test void invalidDestinationPreservesBothFiles() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        String legacy="{\"example:one\":{\"type\":\"minecraft:stonecutting\"}}";
        Files.writeString(directory.resolve("recipe_overrides.json"),legacy);Files.writeString(directory.resolve("minecraft.json"),"{broken");
        assertThrows(Exception.class,()->NativeRecipeFiles.local(directory,OWNER));
        assertEquals(legacy,Files.readString(directory.resolve("recipe_overrides.json")));assertEquals("{broken",Files.readString(directory.resolve("minecraft.json")));
    }
    @Test void workstationReadsPreferInnerBundleEvenWhenNativeVanillaFileExists() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        Files.writeString(temporary.resolve("recipe_bundle.json"),"{\"recipes\":[{\"id\":\"old:record\"}],\"removals\":[]}");
        Files.writeString(directory.resolve("recipe_bundle.json"),"{\"recipes\":[{\"id\":\"current:record\"}],\"removals\":[]}");
        Files.writeString(directory.resolve("minecraft.json"),"{\"overrides\":{}}");
        assertEquals("current:record",NativeRecipeFiles.workstation(directory.resolve("minecraft.json")).getAsJsonArray("recipes").get(0).getAsJsonObject().get("id").getAsString());
        assertTrue(Files.exists(temporary.resolve("recipe_bundle.json")));assertTrue(Files.exists(directory.resolve("recipe_bundle.json")));
    }
    @Test void invalidLegacyEntriesDoNotBlockSavingAndRestoringAValidEdit() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        Path source=directory.resolve("recipe_overrides.json");
        String legacy="""
                {"example:good":{"type":"minecraft:stonecutting","count":1},
                 "example:bad":17,"example:missing_type":{"count":9}}
                """;
        Files.writeString(source,legacy);
        var edits=NativeRecipeFiles.local(directory,OWNER);
        edits.getAsJsonObject("example:good").addProperty("count",2);
        NativeRecipeFiles.save(directory,edits,OWNER);
        assertEquals(2,NativeRecipeFiles.local(directory,OWNER).getAsJsonObject("example:good").get("count").getAsInt());
        assertEquals(legacy,Files.readString(source));
        edits=NativeRecipeFiles.local(directory,OWNER);edits.remove("example:good");
        NativeRecipeFiles.save(directory,edits,OWNER);
        assertFalse(NativeRecipeFiles.local(directory,OWNER).has("example:good"));
        assertEquals(legacy,Files.readString(source));
    }
    @Test void ordinaryRecipeCollisionRejectsWholeSaveWithoutChangingAnyFiles() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        String ordinary="""
                {"type":"minecraft:crafting_shaped","pattern":["X"],"key":{"X":{"item":"minecraft:stone"}},"result":{"item":"minecraft:diamond"}}
                """;
        Files.writeString(directory.resolve("minecraft.json"),ordinary);
        var edits=json("""
                {"example:machine":{"type":"create:mixing"},"example:cut":{"type":"minecraft:stonecutting"}}
                """);
        assertThrows(Exception.class,()->NativeRecipeFiles.save(directory,edits,OWNER));
        assertEquals(ordinary,Files.readString(directory.resolve("minecraft.json")));
        assertFalse(Files.exists(directory.resolve("create.json")));
        try(var paths=Files.list(directory)){assertEquals(1,paths.count());}
    }
    @Test void workstationSaveDoesNotReplaceAnOrdinaryRecipe() throws Exception {
        Path file=temporary.resolve("minecraft.json");
        String ordinary="""
                {"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},"result":"minecraft:stone_bricks","count":1}
                """;
        Files.writeString(file,ordinary);
        assertThrows(Exception.class,()->NativeRecipeFiles.writeMinecraft(file,json("{\"recipes\":[],\"removals\":[]}")));
        assertEquals(ordinary,Files.readString(file));
    }
    @Test void invalidExistingGroupedEntryIsPreservedButInvalidNewEntryIsRejected() throws Exception {
        Path directory=temporary.resolve("recipe");Files.createDirectories(directory);
        Files.writeString(directory.resolve("minecraft.json"),"{\"overrides\":{\"example:bad\":17},\"custom_setting\":true}");
        var edits=NativeRecipeFiles.local(directory,OWNER);
        edits.add("example:good",json("{\"type\":\"minecraft:stonecutting\"}"));
        NativeRecipeFiles.save(directory,edits,OWNER);
        assertEquals(17,read(directory.resolve("minecraft.json")).getAsJsonObject("overrides").get("example:bad").getAsInt());
        assertTrue(read(directory.resolve("minecraft.json")).get("custom_setting").getAsBoolean());
        String written=Files.readString(directory.resolve("minecraft.json"));
        edits.addProperty("example:new_bad",19);
        assertThrows(Exception.class,()->NativeRecipeFiles.save(directory,edits,OWNER));
        assertEquals(written,Files.readString(directory.resolve("minecraft.json")));
    }
}
