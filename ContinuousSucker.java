import tools.WebSearchBrowseTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContinuousSucker {

    private static final List<String> TOPICS = Arrays.asList(
            "latest AI research 2026",
            "new LLM releases",
            "browser automation tools"
    );
    private static final Path KNOWLEDGE_DIRECTORY = Path.of("knowledge");
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));

        scheduler.scheduleAtFixedRate(() -> {
            try {
                collectKnowledge();
            } catch (RuntimeException e) {
                System.err.println("Knowledge collection run failed: " + e.getMessage());
            }
        }, 0, 6, TimeUnit.HOURS);
    }

    private static void collectKnowledge() {
        LocalDateTime runTime = LocalDateTime.now();
        System.out.println("[" + runTime + "] Starting knowledge collection...");

        for (String topic : TOPICS) {
            try {
                String knowledge = WebSearchBrowseTool.webSearchAndBrowse(topic, 5);
                if (knowledge.startsWith("[SEARCH+BROWSE ERROR]")) {
                    throw new IOException(knowledge);
                }
                if (knowledge.isBlank()) {
                    throw new IOException("Search returned no content");
                }

                Files.createDirectories(KNOWLEDGE_DIRECTORY);
                String filename = topic.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
                Path output = KNOWLEDGE_DIRECTORY.resolve(
                        filename + "_" + runTime.format(FILE_TIMESTAMP) + ".md");
                Files.writeString(output, knowledge, StandardCharsets.UTF_8);
                System.out.println("  Saved: " + topic + " -> " + output);
            } catch (Exception e) {
                System.err.println("Failed on topic \"" + topic + "\": " + e.getMessage());
            }
        }

        System.out.println("Knowledge collection run complete.");
    }
}
