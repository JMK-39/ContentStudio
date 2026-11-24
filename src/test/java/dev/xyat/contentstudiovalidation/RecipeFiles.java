package dev.xyat.contentstudiovalidation;

import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The saved recipe files of the installation a fixture runs in, kept so they can be put back byte for byte: fixtures
 * that save recipes run in real packs too.
 */
final class RecipeFiles {
    private static final Map<Path, byte[]> FILES = new LinkedHashMap<>();
    private static boolean read;

    private RecipeFiles() {
    }

    /** Remembers every file of the native recipe directory; call before the first save. */
    static void snapshot() throws IOException {
        if (read) return;
        Path directory = NativeRecipeStore.file().getParent();
        if (Files.isDirectory(directory)) {
            try (var files = Files.list(directory)) {
                for (Path file : (Iterable<Path>) files::iterator) if (Files.isRegularFile(file)) FILES.put(file, Files.readAllBytes(file));
            }
        }
        read = true;
    }

    /** Puts the directory back as it was: the old bytes, and no file that was not there. Does nothing before a snapshot. */
    static void restore() throws IOException {
        if (!read) return;
        Path directory = NativeRecipeStore.file().getParent();
        if (Files.isDirectory(directory)) {
            try (var files = Files.list(directory)) {
                for (Path file : (Iterable<Path>) files::iterator) if (Files.isRegularFile(file) && !FILES.containsKey(file)) Files.delete(file);
            }
        }
        for (var entry : FILES.entrySet()) {
            if (!Files.exists(entry.getKey()) || !java.util.Arrays.equals(Files.readAllBytes(entry.getKey()), entry.getValue())) {
                Files.write(entry.getKey(), entry.getValue());
            }
        }
    }
}
