package dev.xyat.contentstudio.loot.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loot tables kept as files in KineticCore data packs ({@code config/kineticcore/datapack/data/<pack>/data/...}),
 * such as the ones the RealmControl structure editor creates. The loot editor edits these files directly instead of
 * keeping an override, so the table travels with its data pack, and a save takes effect at once.
 */
public final class LootTableFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    //? if >=1.21 {
    /*private static final String FOLDER = "loot_table";
    *///?} else {
    private static final String FOLDER = "loot_tables";
    //?}

    private LootTableFiles() {
    }

    private static Path packsRoot() {
        return KineticPlatform.configDirectory().resolve("kineticcore").resolve("datapack").resolve("data");
    }

    /** The data pack folders, by name, as KineticCore lists them. */
    private static List<Path> packs() {
        List<Path> packs = new ArrayList<>();
        Path root = packsRoot();
        if (!Files.isDirectory(root)) return packs;
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(Files::isDirectory).sorted().forEach(packs::add);
        } catch (IOException ignored) {
            // No packs to edit.
        }
        return packs;
    }

    /** The file of a loot table in a KineticCore data pack, or {@code null} when it comes from elsewhere. */
    @Nullable
    public static Path find(ResourceLocation id) {
        if (id == null || id.getPath().contains("..")) return null;
        for (Path pack : packs()) {
            Path file = pack.resolve("data").resolve(id.getNamespace()).resolve(FOLDER).resolve(id.getPath() + ".json");
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }

    /** Every loot table file in KineticCore data packs, including ones written since the last reload. */
    public static List<ResourceLocation> all() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Path pack : packs()) {
            Path data = pack.resolve("data");
            if (!Files.isDirectory(data)) continue;
            try (Stream<Path> namespaces = Files.list(data)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    Path folder = namespace.resolve(FOLDER);
                    if (!Files.isDirectory(folder)) continue;
                    try (Stream<Path> files = Files.walk(folder)) {
                        files.filter(file -> file.getFileName().toString().endsWith(".json")).forEach(file -> {
                            String path = folder.relativize(file).toString().replace('\\', '/');
                            ResourceLocation id = KineticResourceIds.tryParse(namespace.getFileName() + ":" + path.substring(0, path.length() - 5));
                            if (id != null && !ids.contains(id)) ids.add(id);
                        });
                    }
                }
            } catch (IOException ignored) {
                // A pack that cannot be read lists no tables.
            }
        }
        return ids;
    }

    /**
     * A copy without the item entries whose item this game does not know: what KineticCore loads from the file, so a
     * table that names items of a removed mod can still be checked and saved.
     */
    public static JsonElement withoutMissingItems(JsonElement element) {
        JsonElement copy = element.deepCopy();
        removeMissing(copy);
        return copy;
    }

    private static void removeMissing(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) removeMissing(child);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("entries", "children")) {
            if (!(object.get(key) instanceof JsonArray array)) continue;
            for (Iterator<JsonElement> iterator = array.iterator(); iterator.hasNext(); ) {
                if (isMissingItem(iterator.next())) iterator.remove();
            }
        }
        for (Map.Entry<String, JsonElement> child : object.entrySet()) removeMissing(child.getValue());
    }

    private static boolean isMissingItem(JsonElement entry) {
        if (!entry.isJsonObject()) return false;
        JsonObject object = entry.getAsJsonObject();
        String type = object.get("type") != null && object.get("type").isJsonPrimitive() ? object.get("type").getAsString() : "";
        if ((!type.equals("minecraft:item") && !type.equals("item")) || object.get("name") == null || !object.get("name").isJsonPrimitive()) return false;
        ResourceLocation id = KineticResourceIds.tryParse(object.get("name").getAsString());
        return id == null || !KineticRegistries.items().contains(id);
    }

    public static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Writes a table to a temporary file beside it first, then moves it over the old one. */
    public static void write(Path file, JsonElement json) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
