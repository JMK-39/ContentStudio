import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.tools.ToolProvider;

/** Build-tool regression: normal JVM verification stays enabled throughout. Never packaged with the mod. */
public final class UnsignedForgeTestClasspathCheck {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();Files.createDirectories(root);
        Path source=root.resolve("SignedDependency.java"),classes=root.resolve("classes");Files.createDirectories(classes);
        compile(source,classes,"original");
        Path signed=root.resolve("signed.jar");
        var manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name","fixture.signed");
        try(var output=new JarOutputStream(Files.newOutputStream(signed),manifest)) {
            output.putNextEntry(new JarEntry("fixture/SignedDependency.class"));Files.copy(classes.resolve("fixture/SignedDependency.class"),output);output.closeEntry();
        }
        Path key=root.resolve("fixture.p12");Files.deleteIfExists(key);
        run("keytool","-genkeypair","-alias","fixture","-keystore",key.toString(),"-storepass","fixturepass","-keypass","fixturepass","-dname","CN=Test Fixture","-validity","1","-keyalg","RSA");
        run("jarsigner","-keystore",key.toString(),"-storepass","fixturepass","-digestalg","SHA-384",signed.toString(),"fixture");
        compile(source,classes,"tampered");
        Path tampered=root.resolve("remapped-signed.jar");
        try(var original=new ZipFile(signed.toFile());var output=new ZipOutputStream(Files.newOutputStream(tampered))) {
            for(var entry:Collections.list(original.entries())) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                if(entry.getName().equals("fixture/SignedDependency.class"))Files.copy(classes.resolve(entry.getName()),output);
                else try(var input=original.getInputStream(entry)){input.transferTo(output);}
                output.closeEntry();
            }
        }
        byte[] originalDigest=hash(tampered);
        try {load(tampered);throw new AssertionError("Tampered signed dependency was accepted");}
        catch(SecurityException expected) {if(!expected.getMessage().contains("digest error"))throw expected;System.out.println("REPRODUCED_SHA_DIGEST_FAILURE "+expected.getMessage());}
        Path unsigned=root.resolve("unsigned.jar");
        try(var output=new ZipOutputStream(Files.newOutputStream(unsigned))) {output.putNextEntry(new ZipEntry("metadata.txt"));output.write(new byte[]{1,2,3});output.closeEntry();}
        Path blocks=root.resolve("all-signature-types.jar");
        var securityManifest=new Manifest();securityManifest.getMainAttributes().putValue("Manifest-Version","1.0");
        securityManifest.getMainAttributes().putValue("Signature-Version","1.0");securityManifest.getMainAttributes().putValue("SHA-384-Digest-Manifest","stale");
        var section=new Attributes();section.putValue("SHA-384-Digest","stale");section.putValue("Magic","signature marker");section.putValue("X-Preserved","yes");
        securityManifest.getEntries().put("metadata.txt",section);
        try(var output=new JarOutputStream(Files.newOutputStream(blocks),securityManifest)) {
            for(String name:List.of("META-INF/A.SF","meta-inf/B.rsa","META-INF/C.DSA","META-INF/D.EC","META-INF/SIG-CUSTOM","META-INF/services/fixture.Service")) {
                output.putNextEntry(new JarEntry(name));output.write(new byte[]{1,2,3});output.closeEntry();
            }
        }
        List<Path> input=List.of(tampered,unsigned,classes,blocks);
        List<Path> result=UnsignedForgeTestClasspath.prepare(input,root.resolve("copies"));
        if(!load(result.get(0)).equals("tampered"))throw new AssertionError("Transformed dependency class bytes changed");
        if(!result.get(1).equals(unsigned)||!result.get(2).equals(classes))throw new AssertionError("Unsigned files or directories were replaced");
        if(!Arrays.equals(originalDigest,hash(tampered)))throw new AssertionError("Original dependency was modified");
        try(var copy=new JarFile(result.get(3).toFile())) {
            if(Collections.list(copy.entries()).stream().anyMatch(entry->entry.getName().matches("(?i)META-INF/(?:[^/]+\\.(SF|RSA|DSA|EC)|SIG-[^/]+)")))throw new AssertionError("Signature file remains");
            if(copy.getManifest().getMainAttributes().containsKey(new Attributes.Name("Signature-Version"))||copy.getManifest().getMainAttributes().containsKey(new Attributes.Name("SHA-384-Digest-Manifest")))throw new AssertionError("Main signature attributes remain");
            var kept=copy.getManifest().getAttributes("metadata.txt");
            if(!"yes".equals(kept.getValue("X-Preserved"))||kept.size()!=1)throw new AssertionError("Section metadata was lost or signature attributes remain");
            if(copy.getEntry("META-INF/services/fixture.Service")==null)throw new AssertionError("Service descriptor was lost");
        }
        try(var copy=new JarFile(result.get(0).toFile())) {
            if(!copy.getManifest().getMainAttributes().getValue("Automatic-Module-Name").equals("fixture.signed"))throw new AssertionError("Unrelated manifest metadata was lost");
            for(var entry:Collections.list(copy.entries()))if(entry.getName().matches("(?i)META-INF/[^/]+\\.(SF|RSA|DSA|EC)"))throw new AssertionError("Signature block remains");
            for(var attrs:copy.getManifest().getEntries().values())for(var name:attrs.keySet())if(name.toString().contains("Digest"))throw new AssertionError("Manifest digest remains");
        }
        System.out.println("UNSIGNED_FORGE_TEST_CLASSPATH_PASS: digest failure reproduced; copied class loads; original and unsigned paths preserved");
    }
    private static void compile(Path source,Path classes,String value) throws Exception {
        Files.writeString(source,"package fixture; public class SignedDependency { public static String value() { return \""+value+"\"; } }");
        if(ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",classes.toString(),source.toString())!=0)throw new AssertionError("Fixture compilation failed");
    }
    private static String load(Path jar) throws Exception {
        try(var loader=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            return (String)Class.forName("fixture.SignedDependency",true,loader).getMethod("value").invoke(null);
        }
    }
    private static byte[] hash(Path file) throws Exception {return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));}
    private static void run(String tool,String...args) throws Exception {
        String executable=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")?tool+".exe":tool;
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin",executable).toString());command.addAll(List.of(args));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.waitFor()!=0)throw new AssertionError(tool+" failed: "+output);
    }
}
