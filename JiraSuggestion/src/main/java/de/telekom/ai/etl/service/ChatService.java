package de.telekom.ai.etl.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;

    public String chat(String message) {
        return chatModel.call(message);
    }

    public ChatResponse chatWithOptions(String message) {
        ChatResponse response = chatModel.call(
                new Prompt(message,
                        ChatOptions.builder()
                                .model("llama3")
                                .temperature(0.2d).build()
                )
        );
        return response;
    }
}
