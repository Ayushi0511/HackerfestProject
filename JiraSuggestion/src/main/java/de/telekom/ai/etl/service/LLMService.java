package de.telekom.ai.etl.service;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

@Service
public class LLMService {

    private final OllamaChatModel ollamaChatModel;

    public LLMService(OllamaChatModel ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
    }

    public String analyzeComments(String comments) {
        String prompt = "You are a DevOps engineer. Analyze the following comments and provide resolution steps:\n\n" + comments;
        return ollamaChatModel.call(prompt);
    }
}