package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;

/** Separate native JSON overrides; the original workstation bundle remains untouched. */
public final class NativeRecipeStore {
    private static final class Location {
        static final Path FILE = KineticPlatform.configDirectory().resolve("kineticcore/datapack/data/contentstudio/recipe_overrides.json");
    }
    public static Path file() { return Location.FILE; }
    public static final ResourceLocation RESOURCE = KineticResourceIds.of("contentstudio", "recipe_overrides.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private NativeRecipeStore() { }

    public static Map<ResourceLocation, JsonObject> parse(JsonObject root) {
        Map<ResourceLocation, JsonObject> recipes = new LinkedHashMap<>();
        for (var entry : root.entrySet()) {
            ResourceLocation id = KineticResourceIds.parse(entry.getKey());
            recipes.put(id, new NativeRecipeDocument(entry.getValue().getAsJsonObject()).json());
        }
        return recipes;
    }
    public static Map<ResourceLocation, JsonObject> load(ResourceManager resources) throws IOException {
        var resource = resources.getResource(RESOURCE);
        if (resource.isEmpty()) return Map.of();
        try (var reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<ResourceLocation, JsonObject> recipes = new LinkedHashMap<>();
            for (var entry : root.entrySet()) {
                try {
                    JsonObject single = new JsonObject(); single.add(entry.getKey(), entry.getValue());
                    recipes.putAll(parse(single));
                } catch (RuntimeException error) {
                    com.mojang.logging.LogUtils.getLogger().warn("Skipping unusable native recipe override {}: {}", entry.getKey(), error.getMessage());
                }
            }
            return recipes;
        } catch (RuntimeException e) { throw new IOException("Invalid native recipe overrides", e); }
    }
    public static synchronized JsonObject local() throws IOException {
        return local(file());
    }
    static JsonObject local(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        if (Files.size(file) > 8_000_000) throw new IOException("Native recipe file exceeds editor limit");
        try { return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid native recipe file; preserving it", e); }
    }
    public static String revision(JsonObject root) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static synchronized void update(ResourceLocation id, JsonObject value, String expectedRevision) throws IOException {
        update(file(), id, value, expectedRevision);
    }
    static synchronized void update(Path file, ResourceLocation id, JsonObject value, String expectedRevision) throws IOException {
        JsonObject root = local(file);
        if (!revision(root).equals(expectedRevision)) throw new IOException("STALE");
        if (value == null) root.remove(id.toString());
        else root.add(id.toString(), new NativeRecipeDocument(value).json());
        String encoded=GSON.toJson(root);
        if(encoded.getBytes(StandardCharsets.UTF_8).length>8_000_000) throw new IOException("Native recipe file exceeds editor limit");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "recipe-overrides-", ".tmp");
        try {
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
