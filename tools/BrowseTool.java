package tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class BrowseTool {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Extract clean text or Markdown from a URL using Jina Reader.
     */
    public static String browse(String url) {
        try {
            String jinaUrl = "https://r.jina.ai/" + url;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jinaUrl))
                    .header("Accept", "text/plain")
                    .header("User-Agent", "KnowledgeSucker/1.0 (Java)")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body().trim();
            } else {
                return "[BROWSE ERROR] HTTP " + response.statusCode();
            }
        } catch (Exception e) {
            return "[BROWSE ERROR] " + e.getMessage();
        }
    }
}
