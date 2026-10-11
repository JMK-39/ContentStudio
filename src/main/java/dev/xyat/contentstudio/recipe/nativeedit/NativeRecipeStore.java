package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Lossless native overrides grouped by registered recipe type owner, with serializer fallback. */
public final class NativeRecipeStore {
    private static final class Location {
        static final Path FILE=KineticPlatform.configDirectory().resolve("kineticcore/datapack/data/contentstudio/recipe/minecraft.json");
    }
    private static final Map<String,String> OWNERS=new HashMap<>();
    public static Path file() {return Location.FILE;}
    public static final ResourceLocation RESOURCE=KineticResourceIds.of("contentstudio","recipe/minecraft.json");
    private NativeRecipeStore() { }
    public static void initialize() {
        try {local();}catch(IOException error){com.mojang.logging.LogUtils.getLogger().error("Could not read recipe files; existing files are preserved",error);}
    }
    public static synchronized void recordType(ResourceLocation id,ResourceLocation type) {
        if(type!=null)OWNERS.put(id.toString(),type.getNamespace());
    }
    public static synchronized void regroup() throws IOException {local();}
    static String owner(String id,JsonObject json) {
        String known=OWNERS.get(id);
        return known!=null?known:NativeRecipeTypes.owner(null,NativeRecipeView.resolve(json,branch->true).recipe());
    }
    public static Map<ResourceLocation,JsonObject> parse(JsonObject root) {
        var result=new LinkedHashMap<ResourceLocation,JsonObject>();
        root.entrySet().forEach(entry->result.put(KineticResourceIds.parse(entry.getKey()),new NativeRecipeDocument(entry.getValue().getAsJsonObject()).json()));
        return result;
    }
    public static Map<ResourceLocation,JsonObject> load(ResourceManager resources) throws IOException {
        var roots=new LinkedHashMap<ResourceLocation,net.minecraft.server.packs.resources.Resource>();
        for(String legacy:List.of("recipe_overrides.json","recipe/recipe_overrides.json"))resources.getResource(KineticResourceIds.of("contentstudio",legacy)).ifPresent(resource->roots.put(KineticResourceIds.of("contentstudio",legacy),resource));
        resources.listResources("recipe",id->id.getNamespace().equals("contentstudio") && id.getPath().matches("recipe/[a-z0-9_.-]+\\.json") && !id.getPath().endsWith("recipe_bundle.json") && !id.getPath().endsWith("recipe_overrides.json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->roots.put(e.getKey(),e.getValue()));
        var result=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(var entry:roots.entrySet())try(var reader=entry.getValue().openAsReader()) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            var overrides=entry.getKey().getPath().endsWith("recipe_overrides.json")?root:root.has("overrides")?root.getAsJsonObject("overrides"):new JsonObject();
            for(var override:overrides.entrySet())try {
                if(override.getValue().isJsonNull()) {result.remove(KineticResourceIds.parse(override.getKey()));continue;}
                var single=new JsonObject();single.add(override.getKey(),override.getValue());result.putAll(parse(single));}
            catch(RuntimeException invalid){com.mojang.logging.LogUtils.getLogger().warn("Skipping unusable native override {}: {}",override.getKey(),invalid.getMessage());}
        }catch(RuntimeException invalid){throw new IOException("Invalid native recipe overrides",invalid);}
        return result;
    }
    public static synchronized JsonObject local() throws IOException {return local(file());}
    static JsonObject local(Path file) throws IOException {return NativeRecipeFiles.local(file.getParent(),NativeRecipeStore::owner);}
    public static String revision(JsonObject root) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static synchronized void update(ResourceLocation id,JsonObject value,String expectedRevision) throws IOException {update(file(),id,value,expectedRevision);}
    static synchronized void update(Path file,ResourceLocation id,JsonObject value,String expectedRevision) throws IOException {
        var root=local(file);
        if(!revision(root).equals(expectedRevision))throw new IOException("STALE");
        if(value==null)root.remove(id.toString());else root.add(id.toString(),new NativeRecipeDocument(value).json());
        NativeRecipeFiles.save(file.getParent(),root,NativeRecipeStore::owner);
    }
}
