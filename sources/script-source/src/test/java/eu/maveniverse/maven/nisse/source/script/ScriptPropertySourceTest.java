package eu.maveniverse.maven.nisse.source.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

public class ScriptPropertySourceTest {
    @TempDir
    Path tmp;

    @Test
    void notConfigured() {
        assertTrue(properties(null).isEmpty());
    }

    @Test
    void missingFile() {
        assertThrows(IllegalArgumentException.class, () -> properties("missing.java"));
    }

    @Test
    void classicScript() throws IOException {
        // helper class first: the first top-level class with main is used
        script(
                "Classic.java",
                "import java.util.Properties;\n"
                        + "class Helper { static String upper(String s) { return s.toUpperCase(); } }\n"
                        + "public class Classic {\n"
                        + "  public static void main(String[] args) throws Exception {\n"
                        + "    Properties cfg = new Properties();\n"
                        + "    cfg.load(System.in);\n"
                        + "    System.out.println(\"color=\" + Helper.upper(cfg.getProperty(\"color\")));\n"
                        + "    System.out.println(\"cwd=\" + args[0]);\n"
                        + "    System.out.println(\"root=\" + args[1]);\n"
                        + "    System.out.println(\"name=Ünő\");\n"
                        + "    System.out.println(\"flag=\");\n"
                        + "  }\n"
                        + "}\n");
        InputStream in = System.in;
        PrintStream out = System.out;
        Map<String, String> result = properties("Classic.java");
        assertSame(in, System.in);
        assertSame(out, System.out);
        assertEquals("RED", result.get("color"));
        assertEquals(tmp.toString(), result.get("cwd"));
        assertEquals(tmp.resolve("root").toString(), result.get("root"));
        assertEquals("Ünő", result.get("name"));
        assertEquals("true", result.get("flag"));
        assertEquals(5, result.size());
    }

    @Test
    void publicClassNameNeedNotMatchFileName() throws IOException {
        script(
                "nisse.java",
                "public class Nisse { public static void main(String[] a) { System.out.println(\"ok=yes\"); } }\n");
        assertEquals(Collections.singletonMap("ok", "yes"), properties("nisse.java"));
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_25)
    void compactScript() throws IOException {
        script(
                "nisse.java",
                "void main(String[] args) throws Exception {\n"
                        + "  var cfg = new java.util.Properties();\n"
                        + "  cfg.load(System.in);\n"
                        + "  IO.println(\"color=\" + cfg.getProperty(\"color\"));\n"
                        + "}\n");
        assertEquals(Collections.singletonMap("color", "red"), properties("nisse.java"));
    }

    @Test
    void compilationError() throws IOException {
        script("Broken.java", "public class Broken { void main() { nope(); } }\n");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> properties("Broken.java"));
        assertTrue(e.getMessage().contains("compilation failed"), e.getMessage());
        assertTrue(e.getMessage().contains("line 1"), e.getMessage());
    }

    @Test
    void cannotSeeNisseClasses() throws IOException {
        script(
                "Sneaky.java",
                "public class Sneaky { public static void main(String[] a) {"
                        + " System.out.println(eu.maveniverse.maven.nisse.core.PropertySource.class); } }\n");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> properties("Sneaky.java"));
        assertTrue(e.getMessage().contains("compilation failed"), e.getMessage());
    }

    @Test
    void scriptThrows() throws IOException {
        script(
                "Fail.java",
                "public class Fail { public static void main(String[] a) { throw new RuntimeException(\"boom\"); } }\n");
        PrintStream out = System.out;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> properties("Fail.java"));
        assertEquals("boom", e.getCause().getMessage());
        assertSame(out, System.out);
    }

    @Test
    void noMain() throws IOException {
        script("NoMain.java", "public class NoMain {}\n");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> properties("NoMain.java"));
        assertTrue(e.getMessage().contains("no main"), e.getMessage());
    }

    private void script(String name, String source) throws IOException {
        Files.write(tmp.resolve(name), source.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> properties(String scriptFile) {
        Map<String, String> userProperties = new HashMap<>();
        userProperties.put("color", "red");
        if (scriptFile != null) {
            userProperties.put(ScriptPropertySource.FILE_NAME, scriptFile);
        }
        return new ScriptPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withUserProperties(userProperties)
                        .withCurrentWorkingDirectory(tmp)
                        .withSessionRootDirectory(tmp.resolve("root"))
                        .build());
    }
}
