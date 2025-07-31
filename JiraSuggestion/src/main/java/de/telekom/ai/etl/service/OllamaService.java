package de.telekom.ai.etl.service;


import de.telekom.ai.etl.model.LlamaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OllamaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaService.class);

    private final WebClient webClient;
    private String OLLAMA_URL="http://localhost:11434";
    private String OLLAMA_MODEL= "llama3";

    public OllamaService(WebClient.Builder webClientBuilder) {
        LOGGER.info("Calling Ollama URL at: %s".formatted(OLLAMA_URL));
        LOGGER.info("Calling Ollama MODEL at: %s".formatted(OLLAMA_MODEL));
        this.webClient = webClientBuilder.baseUrl(OLLAMA_URL).build();
    }

    public Mono<LlamaResponse> getSummary(String query) {
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(buildRequestForHistorySuggesstions(query))
                .retrieve()
                .bodyToMono(LlamaResponse.class)
                .doOnSuccess(response -> {
                    LOGGER.info("Response from Ollama: " + response);
                })
                .doOnError(error -> {
                    LOGGER.error("Error calling Ollama: " + error.getMessage(), error);
                });
    }
    private Map<String, Object> buildRequestForHistorySuggesstions(String content) {
       String prompt = getPromptV1();
        Map<String, String> userMessage = Map.of(
                "role", "user",
                "content", prompt+"\n\n" + content
        );
        List<Map<String,String>> modelInput = new ArrayList<>();
        modelInput.add(userMessage);
        return Map.of(
                "model", OLLAMA_MODEL,
                "messages", modelInput,
                "stream", false,
                "temperature", 0.9
        );
    }

    private String getPromptV1() {
        return "You are an devops AI agent that analyzes Jira tickets and their discussions. Based on the provided data, please respond with the following structure:\n" +
                "\n" +
                "Title:  Provide a concise summary or the main theme of the Jira ticket(s).\n" +
                "\n" +
                "Problem:  \n" +
                "    Clearly state the actual problem or issue described in the ticket and comments.\n" +
                "\n" +
                "Solution:  \n" +
                "    Summarize the solution or resolution proposed in the comments and discussion.\n" +
                "\n"+
                "\n" +
                "Author: means assignee Name if not empty and also add this line in response to contact the author for more details. \n"+
                "\n" +
                "Issue No: Add the Issue Key If key is not empty in the input data and also add this line in response that take the reference of this issue"+
                "\n" +
                "Please only include the four sections above. Do not mention any metadata, Git URLs, or who made the comments. Exclude any URLs silently without mentioning their removal.\n" +
                "---\n" +
                "\n" +
                "Input Data: ";
    }

   /* private Map<String, Object> buildRequestWithDocument(List<Document> contents) {
        List<Map<String, Object>> modelInput = new ArrayList<>();
        contents.forEach(content -> {
            Map<String,Object> userInput = new HashMap<>();
            userInput.put("role", "user");
            userInput.put("content", content);
            modelInput.add(userInput);
        });
        return Map.of(
                "model", OLLAMA_MODEL,
                "messages", modelInput,
                "stream", false,
                "temperature", 0.9
        );
    }*/

}
