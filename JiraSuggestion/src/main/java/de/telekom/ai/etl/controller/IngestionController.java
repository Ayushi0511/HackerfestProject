package de.telekom.ai.etl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.ai.etl.model.*;
import de.telekom.ai.etl.service.EtlPipeline;
import de.telekom.ai.etl.service.JiraVectorService;

import de.telekom.ai.etl.service.LLMService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@CrossOrigin(origins = "http://localhost:8083")
@RestController
@RequiredArgsConstructor
public class IngestionController {

    private final EtlPipeline etlPipeline;

    private final VectorStore vectorStore;

    private final ChatModel chatModel;

    @Qualifier("jiraWebClient")
    private final WebClient webClient;

    private final ObjectMapper objectMapper;

    private final JiraVectorService jiraVectorService;

    private final LLMService llmService;

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionController.class);

    @GetMapping("run-ingestion")
    public Flux<String> run(@RequestParam String query) {
//        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("programming language").build());
//
//        results.stream()
//                .map(Document::getFormattedContent)
//                .forEach(System.out::println);
        LOGGER.info("called run-ingestion with query: {}", query);
        return Mono.fromCallable(() ->
                        vectorStore.similaritySearch(SearchRequest.builder()
                                .query(query)
                                .topK(1)
                                .build())
                ).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(documents -> {
                    if (!documents.isEmpty()) {
                        return chatModel.stream("Refine message::" + documents.get(0).getFormattedContent());
                    } else {
                        return Flux.empty();
                    }
                });
//        return chatModel.stream("What is the best programming language?");
    }

    @GetMapping("/search")
    public Mono<List<Document>> searchDocuments(@RequestParam String query) {
        LOGGER.info("Search query: {}", query);
        return Mono.fromCallable(() ->
                vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(1)
                        .build())
                ).subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(results -> {
                            LOGGER.info("results: {}", results);
                        });
    }

    @GetMapping("/search2")
    public Mono<List<Map<String, Object>>> searchDocuments2(@RequestParam String query) {
        LOGGER.info("Search query: {}", query);

        return Mono.fromCallable(() ->
                        vectorStore.similaritySearch(SearchRequest.builder()
                                .query(query)
                                .topK(3) // get top 3 instead of just 1
                                .build())
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .flatMap(document -> {
                    // Extract assignee.comments and pass to LLM
                    String comments = Optional.ofNullable(document.getText())
                            .map(Object::toString)
                            .orElse("No comments available");

                    // Call LLM with comments
                    return Mono.fromCallable(() -> llmService.analyzeComments(comments))
                            .map(solution -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("document", document);
                                result.put("suggestedSolution", solution);
                                return result;
                            });
                })
                .collectList()
                .doOnSuccess(results -> LOGGER.info("Enhanced results with LLM: {}", results));
    }


    @GetMapping("/dump-jira-tickets")
    public Mono<JiraApiResponse> searchJira(@RequestParam(name = "jql", required = false, defaultValue = "") String jql,
                                   @RequestParam(name = "startAt", defaultValue = "0") int startAt,
                                   @RequestParam(name = "maxResults", defaultValue = "50") int maxResults) {
        LOGGER.info("JIRA API Path: {}", jql);
         String endpoint = "https://hackfestjira.atlassian.net/rest/api/2/search?jql="+jql+"&startAt="+startAt+"&maxResults="+maxResults;
        LOGGER.info("JIRA API endpoint: {}", endpoint);
        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(JiraApiResponse.class)
                .flatMap(apiResponse ->
                        Mono.fromRunnable(() -> {
                                    try {
                                        LOGGER.info("✅ Parsed JIRA issues: {}", apiResponse.getIssues());
                                        jiraVectorService.saveIssues(apiResponse.getIssues());
                                    } catch (Exception e) {
                                        LOGGER.error("❌ Error saving to vector store: {}", e.getMessage(), e);
                                        throw new RuntimeException("Failed to save issues", e);
                                    }
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .thenReturn(apiResponse)
                )
                .doOnError(error -> {
                    LOGGER.error("❌ Error during JIRA search: {}", error.getMessage(), error);
                });
    }

    @GetMapping("/comments/{key}")
    public Mono<JiraCommentResponse> searchJiraComments(@PathVariable String key) {
        String endpoint = "https://hackfestjira.atlassian.net/rest/api/2/issue/" + key + "/comment";
        LOGGER.info("JIRA API endpoint: {}", endpoint);

        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(JiraCommentResponse.class)
                .doOnSuccess(response -> {
                    LOGGER.info("✅JIRA comments response: {}", response);
                }).doOnError(error -> {
                    LOGGER.error("❌Error during JIRA comments search: {}", error.getMessage(), error);
                });
    }


    @GetMapping("/dump-jira-tickets2")
    public Mono<JiraApiResponse> searchJiraWithComments(
            @RequestParam(name = "jql", required = false, defaultValue = "") String jql,
            @RequestParam(name = "startAt", defaultValue = "0") int startAt,
            @RequestParam(name = "maxResults", defaultValue = "50") int maxResults) {

        LOGGER.info("JIRA API Path: {}", jql);
        String endpoint = "https://hackfestjira.atlassian.net/rest/api/2/search?jql=" + jql + "&startAt=" + startAt + "&maxResults=" + maxResults;
        LOGGER.info("JIRA API endpoint: {}", endpoint);

        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(JiraApiResponse.class)
                .flatMap(apiResponse -> {
                    JiraApiResponse issues = apiResponse;

                    return Flux.fromIterable(apiResponse.getIssues())
                            .flatMap(issue ->
                                    webClient.get()
                                            .uri("https://hackfestjira.atlassian.net/rest/api/2/issue/{key}/comment", issue.getKey())
                                            .retrieve()
                                            .bodyToMono(JiraCommentResponse.class)
                                            .map(commentsResponse -> {
                                                if (commentsResponse != null && commentsResponse.getComments() != null) {
                                                    setCommentsInJiraTickets(issue, commentsResponse.getComments());
                                                } else {
                                                      setCommentsInJiraTickets(issue,null);// Set empty list if null
                                                }
                                                return issue;
                                            })
                                            .onErrorResume(e -> {
                                                LOGGER.error("Failed to get comments for issue {}: {}", issue.getKey(), e.getMessage());
                                                return Mono.just(issue);
                                            })
                            )
                            .collectList()
                            .flatMap(issuesWithComments -> {
                                return Mono.fromRunnable(() ->
                                                jiraVectorService.saveIssues(issuesWithComments))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .thenReturn(apiResponse);
                            });
                })
                .doOnError(error -> LOGGER.error("❌ Error during JIRA search: {}", error.getMessage(), error));
    }

    private void setCommentsInJiraTickets(JiraRawIssue issue, List<JiraComment> comments) {

        JiraUser assignee  = issue.getFields().getAssignee();
        JiraUser reporter  = issue.getFields().getReporter();
        // Ensure comment lists are initialized
        if (assignee!=null && assignee.getComments() == null) {
            assignee.setComments(new ArrayList<>());
        }
        if (reporter!=null && reporter.getComments() == null) {
            reporter.setComments(new ArrayList<>());
        }
        if(comments == null){
            return;
        }
        comments.forEach(comment -> {
            if(comment.getAuthor().getAccountId().equalsIgnoreCase(assignee.getAccountId())){
                assignee.getComments().add(comment);
            } else if(comment.getAuthor().getAccountId().equalsIgnoreCase(reporter.getAccountId())){
                reporter.getComments().add(comment);
            }
        });
        LOGGER.info("JIRA ISSUE WITH COMMENTS: {}", issue);
    }
}