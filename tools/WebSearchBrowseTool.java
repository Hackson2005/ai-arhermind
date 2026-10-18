package tools;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

public class WebSearchBrowseTool {

    private static final String TAVILY_API_KEY_ENV = "TAVILY_API_KEY";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static String webSearchAndBrowse(String query, int maxResults) {
        try {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Search query must not be empty");
            }
            if (maxResults < 1 || maxResults > 20) {
                throw new IllegalArgumentException("maxResults must be between 1 and 20");
            }

            String apiKey = System.getenv(TAVILY_API_KEY_ENV);
            List<SearchResult> results;
            if (apiKey == null || apiKey.isBlank()) {
                results = duckDuckGoSearch(query, maxResults);
            } else {
                try {
                    results = tavilySearch(query, maxResults, apiKey);
                    if (results.isEmpty()) results = duckDuckGoSearch(query, maxResults);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    System.err.println("Tavily search failed; trying DuckDuckGo: " + e.getMessage());
                    results = duckDuckGoSearch(query, maxResults);
                }
            }

            if (results.isEmpty()) {
                throw new IllegalStateException("Search providers returned no results");
            }

            StringBuilder knowledge = new StringBuilder();
            for (SearchResult item : results) {
                String content = BrowseTool.browse(item.url);
                if (content.startsWith("[BROWSE ERROR]") && !item.snippet.isBlank()) {
                    content += "\n\nSearch snippet: " + item.snippet;
                }
                knowledge.append("### ").append(item.title).append("\n")
                         .append("URL: ").append(item.url).append("\n\n")
                         .append(content.length() > 4000 ? content.substring(0, 4000) : content)
                         .append("\n\n---\n\n");
            }
            return knowledge.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[SEARCH+BROWSE ERROR] Search was interrupted";
        } catch (Exception e) {
            return "[SEARCH+BROWSE ERROR] " + e.getMessage();
        }
    }

    private static List<SearchResult> tavilySearch(String query, int maxResults, String apiKey)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", query);
        body.put("max_results", maxResults);
        body.put("include_raw_content", false);

        HttpRequest searchRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> searchResponse =
                client.send(searchRequest, HttpResponse.BodyHandlers.ofString());
        if (searchResponse.statusCode() != 200) {
            throw new IllegalStateException("Tavily HTTP " + searchResponse.statusCode()
                    + ": " + searchResponse.body());
        }

        Object parsed = JsonParser.parse(searchResponse.body());
        if (!(parsed instanceof Map)) {
            throw new IllegalStateException("Tavily response must be a JSON object");
        }
        Object resultValue = ((Map<?, ?>) parsed).get("results");
        if (!(resultValue instanceof List)) {
            throw new IllegalStateException("Tavily response is missing its results array");
        }

        List<SearchResult> results = new ArrayList<>();
        List<?> resultItems = (List<?>) resultValue;
        for (int i = 0; i < Math.min(resultItems.size(), maxResults); i++) {
            Object result = resultItems.get(i);
            if (!(result instanceof Map)) {
                throw new IllegalStateException("Tavily result " + i + " must be a JSON object");
            }
            Map<?, ?> item = (Map<?, ?>) result;
            Object urlValue = item.get("url");
            if (!(urlValue instanceof String) || ((String) urlValue).isBlank()) {
                throw new IllegalStateException("Tavily result " + i + " is missing its URL");
            }

            String url = (String) urlValue;
            Object titleValue = item.get("title");
            String title = titleValue instanceof String ? (String) titleValue : "No title";
            Object snippetValue = item.get("content");
            String snippet = snippetValue instanceof String ? (String) snippetValue : "";
            results.add(new SearchResult(title, url, snippet));
        }
        return results;
    }

    private static List<SearchResult> duckDuckGoSearch(String query, int maxResults)
            throws IOException, InterruptedException {
        String searchUrl = "https://html.duckduckgo.com/html/?q="
                + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("User-Agent", "AetherMind/1.0 (web research tool)")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("DuckDuckGo HTTP " + response.statusCode());
        }

        DuckDuckGoParser parser = new DuckDuckGoParser();
        new ParserDelegator().parse(new StringReader(response.body()), parser, true);
        List<SearchResult> results = new ArrayList<>();
        for (SearchResult result : parser.results) {
            String url = unwrapDuckDuckGoUrl(result.url);
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue;
            results.add(new SearchResult(result.title, url, result.snippet));
            if (results.size() >= maxResults) break;
        }
        return results;
    }

    private static String unwrapDuckDuckGoUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.startsWith("//") ? "https:" + rawUrl : rawUrl);
            if (uri.getHost() == null || !uri.getHost().endsWith("duckduckgo.com")) return rawUrl;
            String query = uri.getRawQuery();
            if (query == null) return rawUrl;
            for (String pair : query.split("&")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && "uddg".equals(keyValue[0])) {
                    return URLDecoder.decode(keyValue[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        return rawUrl;
    }

    private static final class SearchResult {
        final String title;
        final String url;
        final String snippet;

        SearchResult(String title, String url, String snippet) {
            this.title = title == null || title.isBlank() ? "No title" : title;
            this.url = url;
            this.snippet = snippet == null ? "" : snippet;
        }
    }

    private static final class DuckDuckGoParser extends HTMLEditorKit.ParserCallback {
        private enum Capture { NONE, TITLE, SNIPPET }

        final List<SearchResult> results = new ArrayList<>();
        private Capture capture = Capture.NONE;
        private SearchResultBuilder current;
        private int depth;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            String classes = String.valueOf(attributes.getAttribute(HTML.Attribute.CLASS));
            if (capture != Capture.NONE) {
                depth++;
                return;
            }
            if (tag == HTML.Tag.A && classes.contains("result__a")) {
                current = new SearchResultBuilder();
                current.url = String.valueOf(attributes.getAttribute(HTML.Attribute.HREF));
                capture = Capture.TITLE;
                depth = 1;
            } else if (current != null && classes.contains("result__snippet")) {
                capture = Capture.SNIPPET;
                depth = 1;
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (capture != Capture.NONE && tag == HTML.Tag.BR) {
                appendText(" ");
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            appendText(new String(data));
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (capture == Capture.NONE) return;
            if (--depth > 0) return;
            if (capture == Capture.TITLE && current != null) {
                capture = Capture.NONE;
            } else if (capture == Capture.SNIPPET && current != null) {
                results.add(new SearchResult(current.title.toString().trim(),
                        current.url, current.snippet.toString().trim()));
                current = null;
                capture = Capture.NONE;
            } else {
                capture = Capture.NONE;
            }
        }

        private void appendText(String text) {
            if (current == null) return;
            if (capture == Capture.TITLE) current.title.append(text);
            if (capture == Capture.SNIPPET) current.snippet.append(text);
        }
    }

    private static final class SearchResultBuilder {
        String url = "";
        final StringBuilder title = new StringBuilder();
        final StringBuilder snippet = new StringBuilder();
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (json.length() > 1) json.append(',');
            json.append('"').append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append('"').append(escapeJson((String) value)).append('"');
            } else {
                json.append(value);
            }
        }
        return json.append('}').toString();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    private static final class JsonParser {
        private final String json;
        private int index;

        private JsonParser(String json) {
            this.json = json;
        }

        static Object parse(String json) {
            JsonParser parser = new JsonParser(json);
            Object value = parser.parseValue();
            parser.skipWhitespace();
            if (parser.index != json.length()) {
                throw parser.error("trailing data");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= json.length()) throw error("unexpected end");
            char c = json.charAt(index);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw error("unexpected character");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) throw error("expected object key");
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < json.length()) {
                char c = json.charAt(index++);
                if (c == '"') return value.toString();
                if (c < 0x20) throw error("unescaped control character");
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (index >= json.length()) throw error("unterminated escape");
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"': case '\\': case '/': value.append(escaped); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (index + 4 > json.length()) throw error("incomplete unicode escape");
                        try {
                            value.append((char) Integer.parseInt(json.substring(index, index + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("invalid unicode escape");
                        }
                        index += 4;
                        break;
                    default: throw error("invalid escape");
                }
            }
            throw error("unterminated string");
        }

        private Object parseNumber() {
            int start = index;
            if (peek('-')) index++;
            while (index < json.length() && Character.isDigit(json.charAt(index))) index++;
            if (peek('.')) {
                index++;
                while (index < json.length() && Character.isDigit(json.charAt(index))) index++;
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) index++;
                while (index < json.length() && Character.isDigit(json.charAt(index))) index++;
            }
            try {
                return Double.valueOf(json.substring(start, index));
            } catch (NumberFormatException e) {
                throw error("invalid number");
            }
        }

        private Boolean parseBoolean() {
            if (json.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (json.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw error("invalid boolean");
        }

        private Object parseNull() {
            if (!json.startsWith("null", index)) throw error("invalid null");
            index += 4;
            return null;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) throw error("expected '" + expected + "'");
            index++;
        }

        private boolean peek(char c) {
            return index < json.length() && json.charAt(index) == c;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid Tavily JSON at " + index + ": " + message);
        }
    }
}
