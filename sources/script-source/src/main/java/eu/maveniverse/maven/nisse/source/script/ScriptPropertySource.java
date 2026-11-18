/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.script;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * script source: compiles and runs a Java source file (a "Java script") in the current JVM, and publishes the
 * properties it prints.
 * <p>
 * The script is configured with {@code "nisse.source.script.file"} property (resolved against current working
 * directory), and is not set by default. The script follows the {@code java Script.java} contract, so it may be run
 * standalone as well:
 * <ul>
 *     <li>standard input: Nisse configuration (system and user properties) in Java Properties format</li>
 *     <li>{@code args[0]}: current working directory, {@code args[1]}: session root directory</li>
 *     <li>standard output: properties to publish, as plain {@code key=value} lines (UTF-8); unlike Java Properties
 *     format, there is no escaping, so values (like Windows paths) are taken as is; blank lines and {@code #} comments
 *     are ignored, lines without {@code =} fail the build (use {@code System.err} for diagnostics)</li>
 * </ul>
 * Main method is selected as {@code java} launcher does (static or instance, with or without {@code String[]}).
 * Script sees only JDK classes. Any failure (missing file, compilation error, exception) fails the build.
 * <p>
 * Warning: as script runs in the build JVM, {@code System.exit()} terminates the build, and there is no timeout.
 */
@Singleton
@Named(ScriptPropertySource.NAME)
public class ScriptPropertySource implements PropertySource {
    public static final String NAME = "script";

    public static final String FILE_NAME = NisseConfiguration.SOURCE_PREFIX + NAME + ".file";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        String scriptFile = configuration.getConfiguration().get(FILE_NAME);
        if (scriptFile == null) {
            return Collections.emptyMap();
        }
        Path script = configuration.getCurrentWorkingDirectory().resolve(scriptFile);
        if (!Files.isRegularFile(script)) {
            throw new IllegalArgumentException("Nisse script does not exist: " + script);
        }
        Properties input = new Properties();
        input.putAll(configuration.getConfiguration());
        return run(
                script,
                input,
                configuration.getCurrentWorkingDirectory().toString(),
                configuration.getSessionRootDirectory().toString());
    }

    static Map<String, String> run(Path script, Properties input, String... args) {
        Class<?> scriptClass = compile(script);
        Method main = findMainOrNull(scriptClass); // compile() ensures it is not null
        try {
            ByteArrayOutputStream in = new ByteArrayOutputStream();
            // ISO-8859-1 with escapes, so Properties.load(System.in) in script reads it right
            input.store(in, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            InputStream oldIn = System.in;
            PrintStream oldOut = System.out;
            Thread thread = Thread.currentThread();
            ClassLoader oldTccl = thread.getContextClassLoader();
            try {
                System.setIn(new ByteArrayInputStream(in.toByteArray()));
                System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8.name()));
                thread.setContextClassLoader(scriptClass.getClassLoader());
                Object target = Modifier.isStatic(main.getModifiers()) ? null : instantiate(scriptClass);
                if (main.getParameterCount() == 1) {
                    main.invoke(target, (Object) args);
                } else {
                    main.invoke(target);
                }
            } finally {
                System.out.flush();
                System.setIn(oldIn);
                System.setOut(oldOut);
                thread.setContextClassLoader(oldTccl);
            }

            return parse(script, new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Nisse script failed: " + script, e.getCause());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nisse script cannot be invoked: " + script, e);
        }
    }

    /**
     * Parses plain {@code key=value} lines: no escape processing (unlike Java Properties), so values like Windows paths
     * printed as is stay intact. Blank lines and lines starting with {@code #} are ignored, key and value are trimmed,
     * and (mavenized) empty value means {@code "true"}.
     */
    static Map<String, String> parse(Path script, String output) {
        HashMap<String, String> result = new HashMap<>();
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int idx = trimmed.indexOf('=');
            if (idx < 1) {
                throw new IllegalStateException("Nisse script " + script + " printed invalid line (expected key=value;"
                        + " use System.err for diagnostic output): " + line);
            }
            String value = trimmed.substring(idx + 1).trim();
            result.put(trimmed.substring(0, idx).trim(), value.isEmpty() ? Boolean.TRUE.toString() : value);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Compiles the script in memory, against JDK only, and returns its first top-level class having a main method.
     */
    private static Class<?> compile(Path script) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("Nisse script requires JDK, but running on JRE: " + script);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // insertion order is generation order: top-level classes in source order
        LinkedHashMap<String, ByteArrayOutputStream> classes = new LinkedHashMap<>();
        try (StandardJavaFileManager standard =
                javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            standard.setLocation(StandardLocation.CLASS_PATH, Collections.emptyList());
            ForwardingJavaFileManager<StandardJavaFileManager> inMemory =
                    new ForwardingJavaFileManager<StandardJavaFileManager>(standard) {
                        @Override
                        public JavaFileObject getJavaFileForOutput(
                                Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                            return new SimpleJavaFileObject(URI.create("mem:///" + className + ".class"), kind) {
                                @Override
                                public OutputStream openOutputStream() {
                                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                    classes.put(className, bytes);
                                    return bytes;
                                }
                            };
                        }
                    };
            boolean success = javac.getTask(
                            null,
                            inMemory,
                            diagnostics,
                            Collections.singletonList("-proc:none"),
                            null,
                            Collections.singletonList(
                                    new ForwardingJavaFileObject<JavaFileObject>(
                                            standard.getJavaFileObjects(script.toFile())
                                                    .iterator()
                                                    .next()) {
                                        // like java launcher: public class name need not match file name
                                        @Override
                                        public boolean isNameCompatible(String simpleName, Kind kind) {
                                            return true;
                                        }
                                    }))
                    .call();
            if (!success) {
                throw new IllegalStateException("Nisse script compilation failed: " + script + "\n"
                        + diagnostics.getDiagnostics().stream()
                                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                                .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT))
                                .collect(Collectors.joining("\n")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // parent is platform (or bootstrap on Java 8) loader: script sees JDK only, not Maven nor Nisse
        ClassLoader loader = new ClassLoader(platformClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes.toByteArray(), 0, bytes.size());
            }
        };
        for (String name : classes.keySet()) {
            // shortcut: nested classes are recognized by '$', top-level classes with '$' in name are not supported
            if (!name.contains("$")) {
                try {
                    Class<?> candidate = loader.loadClass(name);
                    if (findMainOrNull(candidate) != null) {
                        return candidate;
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("Nisse script has no main method: " + script);
    }

    private static ClassLoader platformClassLoader() {
        try {
            return (ClassLoader)
                    ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (ReflectiveOperationException e) {
            return null; // Java 8: bootstrap
        }
    }

    /**
     * Launcher order: {@code main(String[])} before {@code main()}, own or inherited, static or instance; not private.
     */
    private static Method findMainOrNull(Class<?> scriptClass) {
        for (Class<?>[] params : new Class<?>[][] {{String[].class}, {}}) {
            for (Class<?> c = scriptClass; c != null; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod("main", params);
                    if (!Modifier.isPrivate(m.getModifiers()) && m.getReturnType() == void.class) {
                        m.setAccessible(true);
                        return m;
                    }
                } catch (NoSuchMethodException e) {
                    // next
                }
            }
        }
        return null;
    }

    private static Object instantiate(Class<?> scriptClass) throws ReflectiveOperationException {
        Constructor<?> constructor = scriptClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
