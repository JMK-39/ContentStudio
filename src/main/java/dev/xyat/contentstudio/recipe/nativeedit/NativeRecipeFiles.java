package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiFunction;

/** Module-owned recipe envelopes. Reading never converts, rewrites or removes existing files. */
public final class NativeRecipeFiles {
    private static final Gson GSON=new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create();
    private NativeRecipeFiles() { }
    public static JsonObject read(Path file) throws IOException {
        if(!Files.exists(file))return new JsonObject();
        if(Files.size(file)>8_000_000)throw new IOException("Recipe file exceeds editor limit");
        try {return JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(RuntimeException invalid){throw new IOException("Invalid recipe file; preserving "+file,invalid);}
    }
    public static synchronized JsonObject local(Path directory,BiFunction<String,JsonObject,String> owner) throws IOException {
        var envelopes=envelopes(directory);var merged=legacyOverrides(directory);
        envelopes.values().forEach(root->{if(root.has("overrides"))root.getAsJsonObject("overrides").entrySet().forEach(e->{
            if(e.getValue().isJsonNull())merged.remove(e.getKey());else merged.add(e.getKey(),e.getValue().deepCopy());
        });});
        return sorted(merged);
    }
    public static synchronized void save(Path directory,JsonObject recipes,BiFunction<String,JsonObject,String> owner) throws IOException {
        persist(directory,envelopes(directory),recipes,owner);
    }
    private static JsonObject legacyOverrides(Path directory) throws IOException {
        var result=new JsonObject();
        for(Path base:List.of(directory.getParent(),directory)) {
            Path file=base.resolve("recipe_overrides.json");
            if(Files.isRegularFile(file))read(file).entrySet().forEach(e->result.add(e.getKey(),e.getValue().deepCopy()));
        }
        return result;
    }
    /** Old workstation settings remain readable without creating a replacement file. */
    public static synchronized JsonObject workstation(Path file) throws IOException {
        var result=new JsonObject();
        for(Path base:List.of(file.getParent().getParent(),file.getParent())) {
            Path legacy=base.resolve("recipe_bundle.json");
            if(Files.isRegularFile(legacy))read(legacy).entrySet().forEach(e->result.add(e.getKey(),e.getValue().deepCopy()));
        }
        read(file).entrySet().forEach(e->result.add(e.getKey(),e.getValue().deepCopy()));
        return result;
    }
    /** Workstation saves preserve the most recent native overrides while holding the same filesystem lock. */
    public static synchronized void writeMinecraft(Path file,JsonObject root) throws IOException {
        var latest=read(file);
        protectOrdinaryRecipe(file,latest);
        if(latest.has("overrides"))root.add("overrides",latest.get("overrides").deepCopy());
        write(file,root);
    }
    private static Map<String,JsonObject> envelopes(Path directory) throws IOException {
        var result=new TreeMap<String,JsonObject>();
        if(!Files.isDirectory(directory))return result;
        try(var paths=Files.list(directory)) {
            for(Path path:paths.filter(Files::isRegularFile).sorted().toList()) {
                String name=path.getFileName().toString();
                if(!name.matches("[a-z0-9_.-]+\\.json")||Set.of("recipe_bundle.json","recipe_overrides.json").contains(name))continue;
                var root=read(path);
                if(root.has("type"))continue; // An ordinary datapack recipe is not a private settings envelope.
                result.put(name.substring(0,name.length()-5),root);
            }
        }
        return result;
    }
    private static void persist(Path directory,Map<String,JsonObject> roots,JsonObject recipes,BiFunction<String,JsonObject,String> owner) throws IOException {
        var groups=new TreeMap<String,JsonObject>();
        var legacy=legacyOverrides(directory);
        for(var entry:recipes.entrySet()) {
            try {
                String mod=typeOwner(owner,entry.getKey(),entry.getValue());
                groups.computeIfAbsent(mod,key->new JsonObject()).add(entry.getKey(),entry.getValue().deepCopy());
            }catch(RuntimeException invalid) {
                // Unusable existing data must not block a valid edit, or be moved or dropped.
                // It stays in its original legacy file, or in the same existing envelope.
                boolean preserved=false;
                for(var previous:roots.entrySet()) {
                    var values=previous.getValue().getAsJsonObject("overrides");
                    if(values!=null && entry.getValue().equals(values.get(entry.getKey()))) {
                        groups.computeIfAbsent(previous.getKey(),key->new JsonObject()).add(entry.getKey(),entry.getValue().deepCopy());
                        preserved=true;break;
                    }
                }
                if(!preserved && !entry.getValue().equals(legacy.get(entry.getKey())))
                    throw new IOException("Invalid new recipe override: "+entry.getKey(),invalid);
            }
        }
        // A null override explicitly restores the underlying recipe, preventing a retained
        // legacy file from resurrecting an edit the player has removed in the editor.
        for(var entry:legacy.entrySet())if(!recipes.has(entry.getKey())) {
            String mod;
            try {mod=typeOwner(owner,entry.getKey(),entry.getValue());}
            catch(RuntimeException invalid){mod="contentstudio";}
            groups.computeIfAbsent(mod,key->new JsonObject()).add(entry.getKey(),JsonNull.INSTANCE);
        }
        roots.keySet().forEach(mod->groups.computeIfAbsent(mod,key->new JsonObject()));
        var staged=new LinkedHashMap<Path,Path>();var empty=new ArrayList<Path>();
        try {
            for(var group:groups.entrySet()) {
                var root=roots.getOrDefault(group.getKey(),new JsonObject()).deepCopy();
                root.add("overrides",sorted(group.getValue()));
                Path target=directory.resolve(group.getKey()+".json");
                var latest=read(target);
                protectOrdinaryRecipe(target,latest);
                if(group.getValue().size()==0 && root.keySet().stream().allMatch(Set.of("version","overrides")::contains)) {empty.add(target);continue;}
                if(root.equals(latest))continue;
                staged.put(target,stage(target,root));
            }
            for(var entry:staged.entrySet())move(entry.getValue(),entry.getKey());
            for(Path path:empty)Files.deleteIfExists(path);
        } finally {for(Path temporary:staged.values())Files.deleteIfExists(temporary);}
    }
    private static String typeOwner(BiFunction<String,JsonObject,String> owner,String id,JsonElement data) {
        String mod=owner.apply(id,data.getAsJsonObject());
        if(mod==null||!mod.matches("[a-z0-9_.-]+"))throw new IllegalArgumentException("Invalid recipe type owner: "+mod);
        return mod;
    }
    private static void protectOrdinaryRecipe(Path file,JsonObject existing) throws IOException {
        if(existing.has("type"))throw new IOException("Recipe settings cannot replace ordinary datapack recipe: "+file);
    }
    private static JsonObject sorted(JsonObject root) {var result=new JsonObject();root.keySet().stream().sorted().forEach(id->result.add(id,root.get(id).deepCopy()));return result;}
    private static Path stage(Path file,JsonObject root) throws IOException {
        String json=GSON.toJson(root);
        if(json.getBytes(StandardCharsets.UTF_8).length>8_000_000)throw new IOException("Recipe file exceeds editor limit");
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"recipe-",".tmp");
        try {Files.writeString(temporary,json,StandardCharsets.UTF_8);return temporary;}
        catch(IOException error){Files.deleteIfExists(temporary);throw error;}
    }
    private static void write(Path file,JsonObject root) throws IOException {Path temporary=stage(file,root);try{move(temporary,file);}finally{Files.deleteIfExists(temporary);}}
    private static void move(Path from,Path to) throws IOException {
        try {Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException unavailable){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}
    }
}
