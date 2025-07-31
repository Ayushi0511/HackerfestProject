package de.telekom.ai.etl.controller;

import de.telekom.ai.etl.model.CommentEntry;
import de.telekom.ai.etl.service.JiraVectorService;
import de.telekom.ai.etl.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ClientQueryController {

    private final OllamaService ollamaService;
    private final JiraVectorService jiraVectorService;
    private final VectorStore vectorStore;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientQueryController.class);

    public ClientQueryController(OllamaService ollamaService, JiraVectorService jiraVectorService, VectorStore vectorStore) {
        this.ollamaService = ollamaService;
        this.jiraVectorService = jiraVectorService;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/history-suggestions")
    public Mono<String> getSummary(@RequestParam String query, @RequestParam(defaultValue = "1") int topk) {
        LOGGER.info("Search query: {}", query);
        return Mono.fromCallable(() ->
                        vectorStore.similaritySearch(SearchRequest.builder()
                                .query(query)
                                .topK(topk)
                                .build())
                ).subscribeOn(Schedulers.boundedElastic())
                .flatMap(results -> {
                    if (results.isEmpty()) {
                        LOGGER.warn("No results found for query: {}", query);
                        return Mono.just("No results found");
                    }
                    List<String> combinedContents = getCombinedContents(results);
                    String inputForModel = String.join("\n\n---\n\n", combinedContents);
                    LOGGER.info("Input for Llama3 model: {}", inputForModel);
                    if( inputForModel.isEmpty()) {
                        LOGGER.warn("Input for model is empty, returning default message");
                        return Mono.just("No relevant history found for the query.");
                    }
                    return ollamaService.getSummary(inputForModel).flatMap(
                            response -> {
                                return Mono.just(response.getMessage().getContent());
                            }
                    );
                }).doOnError(error -> {
                    LOGGER.error("Error during query processing: {}", error.getMessage(), error);
                });
        }


        @GetMapping(value="/chat-with-agent")
        public Mono<String> chatWithAgent(@RequestParam String query) {
                LOGGER.info("Chat query: {}", query);
            return ollamaService.getSummary(query)
                    .flatMap(llamaResponse -> Mono.just(llamaResponse.getMessage().getContent()))
                         .doOnError(error -> {
                         LOGGER.error("Error during chat processing: {}", error.getMessage(), error);
                    });
        }

    private List<String> getCombinedContents(List<Document> results) {
        List<String> combinedContents = results.stream()
                .filter(this::filterOutLowScoreDocuments)
                .map(doc -> {
                    String issueText = doc.getText();
                    Map<String, Object> metadata = doc.getMetadata();

                    String assigneeCommentsRaw = "";
                    String reporterCommentsRaw = "";

                    if (metadata != null) {
                        if (metadata.containsKey("assignee.comments")) {
                            Object obj = metadata.get("assignee.comments");
                            assigneeCommentsRaw = obj != null ? obj.toString() : "";
                        }

                        if (metadata.containsKey("reporter.comments")) {
                            Object obj = metadata.get("reporter.comments");
                            reporterCommentsRaw = obj != null ? obj.toString() : "";
                        }
                    }

                    List<CommentEntry> assigneeEntries = parseComments(assigneeCommentsRaw,metadata,"assignee");
                    List<CommentEntry> reporterEntries = parseComments(reporterCommentsRaw,metadata,"reporter");

                    List<CommentEntry> allComments = new ArrayList<>();
                    allComments.addAll(assigneeEntries);
                    allComments.addAll(reporterEntries);
                    allComments.sort(Comparator.comparing(CommentEntry::getCreated));
                    StringBuilder commentsBuilder = new StringBuilder();
                    for (CommentEntry entry : allComments) {
                        commentsBuilder.append("Date: ").append(entry.getCreated()).append("\n");
                        commentsBuilder.append("Comment: ").append(entry.getComment()).append("\n\n");
                        if (!entry.getAuthorName().isEmpty()) {
                            commentsBuilder.append("Author: ").append(entry.getAuthorName()).append("\n\n");
                        }
                    }
                    return "Issue Description:\n" + extractSummaryAndDescriptionSimple(issueText) +
                            "\n\nComments (sorted by date):\n" + commentsBuilder.toString()+"Key: " + metadata.getOrDefault("key", "");
        }).collect(Collectors.toList());
        return combinedContents;
    }

    public  boolean filterOutLowScoreDocuments(Document doc) {
        if (doc != null && doc.getScore() != null && doc.getScore() <= 0.6) {
            LOGGER.info("Skipping document with low score: {},key:{}", doc.getScore(),doc.getMetadata()!=null?doc.getMetadata().get("key"):"N/A");
            return false;
        }
        return true;
    }

    private List<CommentEntry> parseComments(String rawComments, Map<String, Object> metadata, String role) {
            List<CommentEntry> entries = new ArrayList<>();
            if (rawComments == null || rawComments.isEmpty()) {
                return entries;
            }
            // Split rawComments by comment separator "---"
            String[] parts = rawComments.split("---");

            for (String part : parts) {
                if (part.trim().isEmpty()) continue;
                String createdStr = "";
                String bodyStr = "";
                // Extract created date string
                int createdIdx = part.indexOf("created:{{");
                if (createdIdx >= 0) {
                    int start = createdIdx + "created:{{".length();
                    int end = part.indexOf("}}", start);
                    if (end > start) {
                        createdStr = part.substring(start, end).trim();
                    }
                }
                // Extract body text
                int bodyIdx = part.indexOf("body:{{");
                if (bodyIdx >= 0) {
                    int start = bodyIdx + "body:{{".length();
                    int end = part.indexOf("}}", start);
                    if (end > start) {
                        bodyStr = part.substring(start, end).trim();
                    } else {
                        // If no closing bracket, take till end of part
                        bodyStr = part.substring(start).trim();
                    }
                }
                OffsetDateTime createdDateTime;
                if (createdStr.length() > 5) {
                    createdStr = createdStr.replaceFirst("(\\+\\d{2})(\\d{2})$", "$1:$2");
                }
                try {
                    createdDateTime = OffsetDateTime.parse(createdStr);
                } catch (Exception e) {
                    // fallback in case of parsing error, use minimal time
                    createdDateTime = OffsetDateTime.MIN;
                }
                if (!createdStr.isEmpty() || !bodyStr.isEmpty() || metadata != null) {
                    String authorName = "";
                    if (metadata != null && !metadata.isEmpty()) {
                        if (role.equalsIgnoreCase("assignee")) {
                            authorName = (String) metadata.get("assignee.name");
                        } else if (role.equalsIgnoreCase("reporter")) {
                            authorName = (String) metadata.get("reporter.name");
                        }
                    }
                    entries.add(new CommentEntry(createdDateTime, bodyStr, authorName));
                }
            }
            return entries;
    }

    public static String extractSummaryAndDescriptionSimple(String text) {
        StringBuilder summary = new StringBuilder();
        StringBuilder description = new StringBuilder();
        boolean inSummary = false;
        boolean inDescription = false;

        List<String> headers = Arrays.asList("Status:", "Priority:", "Issue Type:", "Project:", "Assignee:", "Reporter:");
        for (String line : text.split("\\r?\\n")) {
            if (line.startsWith("Summary:")) {
                inSummary = true;
                inDescription = false;
                summary.append(line.substring("Summary:".length()).trim()).append("\n");
            } else if (line.startsWith("Description:")) {
                inDescription = true;
                inSummary = false;
                description.append(line.substring("Description:".length()).trim()).append("\n");
            } else if (headers.stream().anyMatch(line::startsWith)) {
                inSummary = false;
                inDescription = false;
            } else {
                if (inSummary) {
                    summary.append(line).append("\n");
                } else if (inDescription) {
                    description.append(line).append("\n");
                }
            }
        }
        return "Summary: " + summary.toString().trim() + "\n Description: " + description.toString().trim();
    }

}
