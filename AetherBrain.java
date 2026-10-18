import tools.BrowseTool;
import tools.PlaywrightBrowseTool;
import tools.WebSearchBrowseTool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Shared desktop brain: loads {@code shared/knowledge.json} (or the copy
 * packaged in the JAR) and replies with whole-word / whole-phrase matches,
 * longest key first.
 */
public final class AetherBrain {

    public static final int MEMORY_SIZE = 8;
    public static final String GOODBYE = "Neural activity ceasing... Goodbye, creator.";

    private static final Pattern RECALL =
        Pattern.compile("\\b(remember|earlier|before)\\b");

    private final List<Topic> topics;
    private final List<String> fallbacks;
    private final ArrayDeque<String> memory = new ArrayDeque<>();
    private final Random rand = new Random();

    public static boolean isExit(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9\\s]", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s.equals("exit") || s.equals("quit") || s.equals("bye") || s.equals("goodbye");
    }

    public AetherBrain() {
        String json = loadJsonText();
        Parsed parsed = parseKnowledge(json);
        parsed.topics.sort((a, b) -> Integer.compare(b.key.length(), a.key.length()));
        this.topics = parsed.topics;
        this.fallbacks = parsed.fallbacks;
        if (topics.isEmpty() || fallbacks.isEmpty()) {
            throw new IllegalStateException("knowledge.json is missing topics or fallbacks");
        }
    }

    public String respond(String raw) {
        String response = respondLocally(raw);
        if (response != null) return response;
        return fallbacks.get(rand.nextInt(fallbacks.size()));
    }

    public String respondLocally(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if (memory.size() >= MEMORY_SIZE) memory.removeFirst();
        memory.addLast(lower);

        for (Topic t : topics) {
            if (t.pattern.matcher(lower).find()) {
                return t.replies.get(rand.nextInt(t.replies.size()));
            }
        }
        if (RECALL.matcher(lower).find()) {
            if (memory.size() > 1) {
                String[] arr = memory.toArray(new String[0]);
                return "I remember you said: \"" + arr[arr.length - 2] + "\"";
            }
            return "My short-term memory is still empty.";
        }
        return null;
    }

    public String browse(String url) {
        return BrowseTool.browse(url);
    }

    public String browseWithPlaywright(String url) {
        return PlaywrightBrowseTool.browse(url);
    }

    public String webSearchAndBrowse(String query, int maxResults) {
        return WebSearchBrowseTool.webSearchAndBrowse(query, maxResults);
    }

    public List<String> memorySnapshot() {
        return new ArrayList<>(memory);
    }

    public void restoreMemory(List<String> items) {
        memory.clear();
        if (items == null) return;
        int start = Math.max(0, items.size() - MEMORY_SIZE);
        for (int i = start; i < items.size(); i++) {
            memory.addLast(items.get(i));
        }
    }

    private static String loadJsonText() {
        InputStream in = AetherBrain.class.getResourceAsStream("/knowledge.json");
        if (in != null) {
            try { return readUtf8(in); }
            catch (IOException e) { throw new IllegalStateException("Failed to read packaged knowledge.json", e); }
        }
        Path[] candidates = new Path[] {
            Path.of("shared", "knowledge.json"),
            Path.of("knowledge.json"),
            Path.of("web", "knowledge.json")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try { return Files.readString(p, StandardCharsets.UTF_8); }
                catch (IOException e) { throw new IllegalStateException("Failed to read " + p, e); }
            }
        }
        throw new IllegalStateException(
            "knowledge.json not found. Run from the repo root or build with ./build.sh"
        );
    }

    private static String readUtf8(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = stream.read(chunk)) >= 0) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class Topic {
        final String key;
        final Pattern pattern;
        final List<String> replies;

        Topic(String key, List<String> replies) {
            this.key = key;
            this.pattern = Pattern.compile("\\b" + Pattern.quote(key) + "\\b");
            this.replies = replies;
        }
    }

    private static final class Parsed {
        final List<Topic> topics = new ArrayList<>();
        final List<String> fallbacks = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Parsed parseKnowledge(String json) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalStateException("knowledge.json root must be an object");
        }
        Map<String, Object> map = (Map<String, Object>) root;
        Parsed out = new Parsed();
        Object fb = map.get("fallbacks");
        if (fb instanceof List) {
            for (Object item : (List<Object>) fb) out.fallbacks.add(String.valueOf(item));
        }
        Object topics = map.get("topics");
        if (topics instanceof List) {
            for (Object item : (List<Object>) topics) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> t = (Map<String, Object>) item;
                String key = String.valueOf(t.get("key")).toLowerCase(Locale.ROOT);
                List<String> replies = new ArrayList<>();
                Object rs = t.get("replies");
                if (rs instanceof List) {
                    for (Object r : (List<Object>) rs) replies.add(String.valueOf(r));
                }
                if (!key.isEmpty() && !replies.isEmpty()) {
                    out.topics.add(new Topic(key, replies));
                }
            }
        }
        return out;
    }

    /** Minimal JSON parser for objects, arrays, strings, numbers, booleans, null. */
    static final class MiniJson {
        private final String s;
        private int i;

        MiniJson(String s) { this.s = s; }

        static Object parse(String s) {
            MiniJson p = new MiniJson(s);
            Object v = p.parseValue();
            p.skipWs();
            if (p.i != p.s.length()) throw p.err("trailing data");
            return v;
        }

        private Object parseValue() {
            skipWs();
            if (i >= s.length()) throw err("unexpected end");
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw err("unexpected '" + c + "'");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                skipWs();
                if (peek('}')) { i++; return map; }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) { i++; return list; }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw err("unterminated escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': case '\\': case '/': sb.append(e); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("bad unicode escape");
                            int cp = Integer.parseInt(s.substring(i, i + 4), 16);
                            sb.append((char) cp);
                            i += 4;
                            break;
                        default: throw err("bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw err("unterminated string");
        }

        private Object parseNumber() {
            int start = i;
            if (peek('-')) i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (peek('.')) {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String n = s.substring(start, i);
            if (n.contains(".")) return Double.parseDouble(n);
            try { return Long.parseLong(n); }
            catch (NumberFormatException e) { return Double.parseDouble(n); }
        }

        private Object parseBool() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw err("bad boolean");
        }

        private Object parseNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw err("bad null");
        }

        private void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            skipWs();
            if (!peek(c)) throw err("expected '" + c + "'");
            i++;
        }

        private IllegalStateException err(String msg) {
            return new IllegalStateException("JSON parse error at " + i + ": " + msg);
        }
    }
}
