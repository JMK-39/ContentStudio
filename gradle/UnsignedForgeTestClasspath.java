import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/** Test runtime copies only: Forge remapping invalidates the signatures retained in some dependencies. */
public final class UnsignedForgeTestClasspath {
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]);List<Path> input=Files.readAllLines(Path.of(args[1])).stream().map(Path::of).toList();
        List<Path> prepared=prepare(input,output);var mapping=new Properties();
        for(int i=0;i<input.size();i++)mapping.setProperty(input.get(i).toString(),prepared.get(i).toString());
        try(var writer=Files.newOutputStream(Path.of(args[2]))){mapping.store(writer,"Forge verification runtime only");}
    }
    public static List<Path> prepare(List<Path> input,Path output) throws Exception {
        Files.createDirectories(output);var result=new ArrayList<Path>();
        for(Path source:input) {
            if(!Files.isRegularFile(source)||!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")||!signed(source)) {result.add(source);continue;}
            byte[] key=MessageDigest.getInstance("SHA-256").digest(source.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            Path target=output.resolve(HexFormat.of().formatHex(key,0,12)+"-"+source.getFileName());
            if(target.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize()))throw new IOException("Refusing to replace source dependency");
            copy(source,target);result.add(target);System.out.println("Unsigned Forge test dependency copy: "+source.getFileName());
        }
        return List.copyOf(result);
    }
    private static boolean signature(String name) {
        return name.toUpperCase(Locale.ROOT).matches("META-INF/(?:[^/]+\\.(?:SF|RSA|DSA|EC)|SIG-[^/]+)");
    }
    private static boolean signed(Path file) throws IOException {
        try(var zip=new ZipFile(file.toFile())) {return zip.stream().anyMatch(entry->signature(entry.getName()));}
    }
    private static void copy(Path source,Path target) throws IOException {
        Path staged=Files.createTempFile(target.getParent(),"unsigned-test-",".tmp");
        try {
            try(var zip=new ZipFile(source.toFile());var output=new ZipOutputStream(Files.newOutputStream(staged))) {
                for(var entry:Collections.list(zip.entries())) {
                    if(signature(entry.getName()))continue;
                    var copied=new ZipEntry(entry.getName());if(entry.getTime()>=0)copied.setTime(entry.getTime());output.putNextEntry(copied);
                    try(var input=zip.getInputStream(entry)) {
                        if(entry.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                            var manifest=new Manifest(input);clean(manifest.getMainAttributes());
                            manifest.getEntries().values().forEach(UnsignedForgeTestClasspath::clean);
                            manifest.getEntries().entrySet().removeIf(section->section.getValue().isEmpty());manifest.write(output);
                        } else input.transferTo(output);
                    }
                    output.closeEntry();
                }
            }
            try {Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(staged);}
    }
    private static void clean(Attributes attributes) {
        attributes.keySet().removeIf(key->key.toString().toLowerCase(Locale.ROOT).matches("signature-version|magic|digest-algorithms|.*-digest(?:-.*)?"));
    }
}
