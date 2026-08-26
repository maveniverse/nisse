/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
/*
 * Adapted from Apache Maven 4's org.apache.maven.cling.props.MavenProperties.
 * Original source: https://github.com/apache/maven, licensed under Apache License 2.0.
 * Modified for Java 8 compatibility and to use NisseInterpolator.
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Enhancement of the standard {@code Properties} managing the maintenance of comments, etc.
 * <p>
 * Adapted from Maven 4's {@code org.apache.maven.cling.props.MavenProperties}.
 */
class MavenProperties extends AbstractMap<String, String> {

    /** Constant for the supported comment characters. */
    private static final String COMMENT_CHARS = "#!";

    /** The list of possible key/value separators */
    private static final char[] SEPARATORS = new char[] {'=', ':'};

    /** The white space characters used as key/value separators. */
    private static final char[] WHITE_SPACE = new char[] {' ', '\t', '\f'};

    /** Unlike standard java props, use UTF-8 */
    static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;

    /** Constant for the platform specific line separator. */
    private static final String LINE_SEPARATOR = System.lineSeparator();

    /** Constant for the radix of hex numbers. */
    private static final int HEX_RADIX = 16;

    /** Constant for the length of a unicode literal. */
    private static final int UNICODE_LEN = 4;

    private final Map<String, String> storage = new LinkedHashMap<>();
    private final Map<String, Layout> layout = new LinkedHashMap<>();
    private List<String> header;
    private List<String> footer;
    private Path location;
    private UnaryOperator<String> callback;
    boolean substitute = true;
    boolean typed;

    MavenProperties() {}

    MavenProperties(Path location) throws IOException {
        this(location, null);
    }

    MavenProperties(Path location, UnaryOperator<String> callback) throws IOException {
        this.location = location;
        this.callback = callback;
        if (Files.exists(location)) {
            load(location);
        }
    }

    MavenProperties(boolean substitute) {
        this.substitute = substitute;
    }

    MavenProperties(Path location, boolean substitute) {
        this.location = location;
        this.substitute = substitute;
    }

    public void load(Path location) throws IOException {
        try (InputStream is = Files.newInputStream(location)) {
            load(is);
        }
    }

    public void load(URL location) throws IOException {
        try (InputStream is = location.openStream()) {
            load(is);
        }
    }

    public void load(InputStream is) throws IOException {
        load(new InputStreamReader(is, DEFAULT_ENCODING));
    }

    public void load(Reader reader) throws IOException {
        loadLayout(reader, false);
    }

    public void save() throws IOException {
        save(this.location);
    }

    public void save(Path location) throws IOException {
        try (OutputStream os = Files.newOutputStream(location)) {
            save(os);
        }
    }

    public void save(OutputStream os) throws IOException {
        save(new OutputStreamWriter(os, DEFAULT_ENCODING));
    }

    public void save(Writer writer) throws IOException {
        saveLayout(writer, typed);
    }

    public void store(OutputStream os, String comment) throws IOException {
        this.save(os);
    }

    public String getProperty(String key) {
        return this.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        if (this.get(key) != null) {
            return this.get(key);
        }
        return defaultValue;
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return new AbstractSet<Entry<String, String>>() {
            @Override
            public Iterator<Entry<String, String>> iterator() {
                return new Iterator<Entry<String, String>>() {
                    final Iterator<Entry<String, String>> keyIterator =
                            storage.entrySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return keyIterator.hasNext();
                    }

                    @Override
                    public Entry<String, String> next() {
                        final Entry<String, String> entry = keyIterator.next();
                        return new Entry<String, String>() {
                            @Override
                            public String getKey() {
                                return entry.getKey();
                            }

                            @Override
                            public String getValue() {
                                return entry.getValue();
                            }

                            @Override
                            public String setValue(String value) {
                                String old = entry.setValue(value);
                                if (old == null || !old.equals(value)) {
                                    Layout l = layout.get(entry.getKey());
                                    if (l != null) {
                                        l.clearValue();
                                    }
                                }
                                return old;
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        keyIterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return storage.size();
            }
        };
    }

    public Enumeration<?> propertyNames() {
        return Collections.enumeration(storage.keySet());
    }

    public Object setProperty(String key, String value) {
        return this.put(key, value);
    }

    @Override
    public String put(String key, String value) {
        String old = storage.put(key, value);
        if (old == null || !old.equals(value)) {
            Layout l = layout.get(key);
            if (l != null) {
                l.clearValue();
            }
        }
        return old;
    }

    void putAllSubstituted(Map<? extends String, ? extends String> m) {
        storage.putAll(m);
    }

    public String put(String key, List<String> commentLines, List<String> valueLines) {
        commentLines = new ArrayList<>(commentLines);
        valueLines = new ArrayList<>(valueLines);
        String escapedKey = escapeKey(key);
        StringBuilder sb = new StringBuilder();
        if (valueLines.isEmpty()) {
            valueLines.add(escapedKey + "=");
            sb.append(escapedKey).append("=");
        } else {
            String val0 = valueLines.get(0);
            String rv0 = typed ? val0 : escapeJava(val0);
            if (!val0.trim().startsWith(escapedKey)) {
                valueLines.set(0, escapedKey + " = " + rv0);
                sb.append(escapedKey).append(" = ").append(rv0);
            } else {
                valueLines.set(0, rv0);
                sb.append(rv0);
            }
        }
        for (int i = 1; i < valueLines.size(); i++) {
            String val = valueLines.get(i);
            valueLines.set(i, typed ? val : escapeJava(val));
            while (!val.isEmpty() && Character.isWhitespace(val.charAt(0))) {
                val = val.substring(1);
            }
            sb.append(val);
        }
        String[] property = PropertiesReader.parseProperty(sb.toString());
        this.layout.put(key, new Layout(commentLines, valueLines));
        return storage.put(key, property[1]);
    }

    public String put(String key, List<String> commentLines, String value) {
        commentLines = new ArrayList<>(commentLines);
        this.layout.put(key, new Layout(commentLines, null));
        return storage.put(key, value);
    }

    public String put(String key, String comment, String value) {
        return put(key, Collections.singletonList(comment), value);
    }

    public boolean update(Map<String, String> props) {
        MavenProperties properties;
        if (props instanceof MavenProperties) {
            properties = (MavenProperties) props;
        } else {
            properties = new MavenProperties();
            properties.putAll(props);
        }
        return update(properties);
    }

    public boolean update(MavenProperties properties) {
        boolean modified = false;
        for (String key : new ArrayList<>(this.keySet())) {
            if (!properties.containsKey(key)) {
                this.remove(key);
                modified = true;
            }
        }
        for (String key : properties.keySet()) {
            String v = this.get(key);
            List<String> comments = properties.getComments(key);
            List<String> value = properties.getRaw(key);
            if (v == null) {
                this.put(key, comments, value);
                modified = true;
            } else if (!v.equals(properties.get(key))) {
                if (comments.isEmpty()) {
                    comments = this.getComments(key);
                }
                this.put(key, comments, value);
                modified = true;
            }
        }
        return modified;
    }

    public List<String> getRaw(String key) {
        if (layout.containsKey(key)) {
            if (layout.get(key).getValueLines() != null) {
                return new ArrayList<>(layout.get(key).getValueLines());
            }
        }
        List<String> result = new ArrayList<>();
        if (storage.containsKey(key)) {
            result.add(storage.get(key));
        }
        return result;
    }

    public List<String> getComments(String key) {
        if (layout.containsKey(key)) {
            if (layout.get(key).getCommentLines() != null) {
                return new ArrayList<>(layout.get(key).getCommentLines());
            }
        }
        return new ArrayList<>();
    }

    @Override
    public String remove(Object key) {
        Layout l = layout.get(key);
        if (l != null) {
            l.clearValue();
        }
        return storage.remove(key);
    }

    @Override
    public void clear() {
        for (Layout l : layout.values()) {
            l.clearValue();
        }
        storage.clear();
    }

    public List<String> getHeader() {
        return header;
    }

    public void setHeader(List<String> header) {
        this.header = header;
    }

    public List<String> getFooter() {
        return footer;
    }

    public void setFooter(List<String> footer) {
        this.footer = footer;
    }

    protected void loadLayout(Reader in, boolean maybeTyped) throws IOException {
        PropertiesReader reader = new PropertiesReader(in, maybeTyped);
        boolean hasProperty = false;
        while (reader.nextProperty()) {
            hasProperty = true;
            storage.put(reader.getPropertyName(), reader.getPropertyValue());
            int idx = checkHeaderComment(reader.getCommentLines());
            layout.put(
                    reader.getPropertyName(),
                    new Layout(
                            idx < reader.getCommentLines().size()
                                    ? new ArrayList<>(reader.getCommentLines()
                                            .subList(
                                                    idx,
                                                    reader.getCommentLines().size()))
                                    : null,
                            new ArrayList<>(reader.getValueLines())));
        }
        typed = maybeTyped && reader.typed != null && reader.typed;
        if (!typed) {
            for (Entry<String, String> e : storage.entrySet()) {
                e.setValue(unescapeJava(e.getValue()));
            }
        }
        if (hasProperty) {
            footer = new ArrayList<>(reader.getCommentLines());
        } else {
            header = new ArrayList<>(reader.getCommentLines());
        }
        if (substitute) {
            substitute();
        }
    }

    public void substitute() {
        substitute(callback);
    }

    public void substitute(UnaryOperator<String> callback) {
        NisseInterpolator.substituteVars(storage, callback);
    }

    protected void saveLayout(Writer out, boolean typed) throws IOException {
        PropertiesWriter writer = new PropertiesWriter(out, typed);
        if (header != null) {
            for (String s : header) {
                writer.writeln(s);
            }
        }

        for (String key : storage.keySet()) {
            Layout l = layout.get(key);
            if (l != null && l.getCommentLines() != null) {
                for (String s : l.getCommentLines()) {
                    writer.writeln(s);
                }
            }
            if (l != null && l.getValueLines() != null) {
                for (int i = 0; i < l.getValueLines().size(); i++) {
                    String s = l.getValueLines().get(i);
                    if (i < l.getValueLines().size() - 1) {
                        writer.writeln(s + "\\");
                    } else {
                        writer.writeln(s);
                    }
                }
            } else {
                writer.writeProperty(key, storage.get(key));
            }
        }
        if (footer != null) {
            for (String s : footer) {
                writer.writeln(s);
            }
        }
        writer.flush();
    }

    private int checkHeaderComment(List<String> commentLines) {
        if (getHeader() == null && layout.isEmpty()) {
            int index = commentLines.size() - 1;
            while (index >= 0 && !commentLines.get(index).isEmpty()) {
                index--;
            }
            setHeader(new ArrayList<>(commentLines.subList(0, index + 1)));
            return index + 1;
        } else {
            return 0;
        }
    }

    static boolean isCommentLine(String line) {
        String s = line.trim();
        return s.isEmpty() || COMMENT_CHARS.indexOf(s.charAt(0)) >= 0;
    }

    protected static String unescapeJava(String str) {
        if (str == null) {
            return null;
        }
        int sz = str.length();
        StringBuilder out = new StringBuilder(sz);
        StringBuilder unicode = new StringBuilder(UNICODE_LEN);
        boolean hadSlash = false;
        boolean inUnicode = false;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (inUnicode) {
                unicode.append(ch);
                if (unicode.length() == UNICODE_LEN) {
                    try {
                        int value = Integer.parseInt(unicode.toString(), HEX_RADIX);
                        out.append((char) value);
                        unicode.setLength(0);
                        inUnicode = false;
                        hadSlash = false;
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Unable to parse unicode value: " + unicode, nfe);
                    }
                }
                continue;
            }

            if (hadSlash) {
                hadSlash = false;
                switch (ch) {
                    case '\\':
                        out.append('\\');
                        break;
                    case '\'':
                        out.append('\'');
                        break;
                    case '\"':
                        out.append('"');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'u':
                        inUnicode = true;
                        break;
                    default:
                        out.append(ch);
                        break;
                }
                continue;
            } else if (ch == '\\') {
                hadSlash = true;
                continue;
            }
            out.append(ch);
        }

        if (hadSlash) {
            out.append('\\');
        }

        return out.toString();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    protected static String escapeJava(String str) {
        if (str == null) {
            return null;
        }
        int sz = str.length();
        StringBuilder out = new StringBuilder(sz * 2);
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (ch > 0xfff) {
                out.append("\\u").append(hex(ch));
            } else if (ch > 0xff) {
                out.append("\\u0").append(hex(ch));
            } else if (ch > 0x7f) {
                out.append("\\u00").append(hex(ch));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b':
                        out.append('\\');
                        out.append('b');
                        break;
                    case '\n':
                        out.append('\\');
                        out.append('n');
                        break;
                    case '\t':
                        out.append('\\');
                        out.append('t');
                        break;
                    case '\f':
                        out.append('\\');
                        out.append('f');
                        break;
                    case '\r':
                        out.append('\\');
                        out.append('r');
                        break;
                    default:
                        if (ch > 0xf) {
                            out.append("\\u00").append(hex(ch));
                        } else {
                            out.append("\\u000").append(hex(ch));
                        }
                        break;
                }
            } else {
                switch (ch) {
                    case '"':
                        out.append('\\');
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        out.append('\\');
                        break;
                    default:
                        out.append(ch);
                        break;
                }
            }
        }
        return out.toString();
    }

    protected static String hex(char ch) {
        return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
    }

    public static boolean contains(char[] array, char valueToFind) {
        if (array == null) {
            return false;
        }
        for (char c : array) {
            if (valueToFind == c) {
                return true;
            }
        }
        return false;
    }

    private static String escapeKey(String key) {
        StringBuilder newkey = new StringBuilder();

        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);

            if (contains(SEPARATORS, c) || contains(WHITE_SPACE, c)) {
                newkey.append('\\');
                newkey.append(c);
            } else {
                newkey.append(c);
            }
        }

        return newkey.toString();
    }

    /**
     * Reader for properties lines that handles backslash line continuation.
     */
    public static class PropertiesReader extends LineNumberReader {
        private final List<String> commentLines;
        private final List<String> valueLines;
        private String propertyName;
        private String propertyValue;
        private boolean maybeTyped;
        Boolean typed;

        public PropertiesReader(Reader reader, boolean maybeTyped) {
            super(reader);
            commentLines = new ArrayList<>();
            valueLines = new ArrayList<>();
            this.maybeTyped = maybeTyped;
        }

        public String readProperty() throws IOException {
            commentLines.clear();
            valueLines.clear();
            StringBuilder buffer = new StringBuilder();

            while (true) {
                String line = readLine();
                if (line == null) {
                    return null;
                }

                if (isCommentLine(line)) {
                    commentLines.add(line);
                    continue;
                }

                boolean combine = checkCombineLines(line);
                if (combine) {
                    line = line.substring(0, line.length() - 1);
                }
                valueLines.add(line);
                while (!line.isEmpty() && contains(WHITE_SPACE, line.charAt(0))) {
                    line = line.substring(1, line.length());
                }
                buffer.append(line);
                if (!combine) {
                    break;
                }
            }
            return buffer.toString();
        }

        public boolean nextProperty() throws IOException {
            String line = readProperty();

            if (line == null) {
                return false;
            }

            String[] property = parseProperty(line);
            boolean typed = false;
            if (maybeTyped && property[1].length() >= 2) {
                typed = property[1].matches(
                        "\\s*[TILFDXSCBilfdxscb]?(\\[[\\S\\s]*\\]|\\([\\S\\s]*\\)|\\{[\\S\\s]*\\}|\"[\\S\\s]*\")\\s*");
            }
            if (this.typed == null) {
                this.typed = typed;
            } else {
                this.typed = this.typed & typed;
            }
            propertyName = unescapeJava(property[0]);
            propertyValue = property[1];
            return true;
        }

        public List<String> getCommentLines() {
            return commentLines;
        }

        public List<String> getValueLines() {
            return valueLines;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getPropertyValue() {
            return propertyValue;
        }

        private static boolean checkCombineLines(String line) {
            int bsCount = 0;
            for (int idx = line.length() - 1; idx >= 0 && line.charAt(idx) == '\\'; idx--) {
                bsCount++;
            }
            return bsCount % 2 != 0;
        }

        static String[] parseProperty(String line) {
            String[] result = new String[2];
            StringBuilder key = new StringBuilder();
            StringBuilder value = new StringBuilder();

            int state = 0;

            for (int pos = 0; pos < line.length(); pos++) {
                char c = line.charAt(pos);

                switch (state) {
                    case 0:
                        if (c == '\\') {
                            state = 1;
                        } else if (contains(WHITE_SPACE, c)) {
                            state = 2;
                        } else if (contains(SEPARATORS, c)) {
                            state = 3;
                        } else {
                            key.append(c);
                        }
                        break;

                    case 1:
                        if (contains(SEPARATORS, c) || contains(WHITE_SPACE, c)) {
                            key.append(c);
                        } else {
                            key.append('\\');
                            key.append(c);
                        }
                        state = 0;
                        break;

                    case 2:
                        if (contains(WHITE_SPACE, c)) {
                            state = 2;
                        } else if (contains(SEPARATORS, c)) {
                            state = 3;
                        } else {
                            value.append(c);
                            state = 4;
                        }
                        break;

                    case 3:
                        if (contains(WHITE_SPACE, c)) {
                            state = 3;
                        } else {
                            value.append(c);
                            state = 4;
                        }
                        break;

                    case 4:
                        value.append(c);
                        break;

                    default:
                        throw new IllegalStateException();
                }
            }

            result[0] = key.toString();
            result[1] = value.toString();

            return result;
        }
    }

    /**
     * Writer for properties lines.
     */
    public static class PropertiesWriter extends FilterWriter {
        private boolean typed;

        public PropertiesWriter(Writer writer, boolean typed) {
            super(writer);
            this.typed = typed;
        }

        public void writeProperty(String key, String value) throws IOException {
            write(escapeKey(key));
            write(" = ");
            write(typed ? value : escapeJava(value));
            writeln(null);
        }

        public void writeln(String s) throws IOException {
            if (s != null) {
                write(s);
            }
            write(LINE_SEPARATOR);
        }
    }

    /**
     * Layout data holder for comment and value lines.
     */
    protected static class Layout {
        private List<String> commentLines;
        private List<String> valueLines;

        public Layout() {}

        public Layout(List<String> commentLines, List<String> valueLines) {
            this.commentLines = commentLines;
            this.valueLines = valueLines;
        }

        public List<String> getCommentLines() {
            return commentLines;
        }

        public void setCommentLines(List<String> commentLines) {
            this.commentLines = commentLines;
        }

        public List<String> getValueLines() {
            return valueLines;
        }

        public void setValueLines(List<String> valueLines) {
            this.valueLines = valueLines;
        }

        public void clearValue() {
            this.valueLines = null;
        }
    }
}
