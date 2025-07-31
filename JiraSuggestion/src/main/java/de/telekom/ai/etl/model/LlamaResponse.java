package de.telekom.ai.etl.model;

import lombok.Data;
import lombok.ToString;

import java.time.ZonedDateTime;

@ToString
@Data
public class LlamaResponse {
    private String model;
    private ZonedDateTime created_at;
    private Message message;
    private String done_reason;
    private boolean done;
    private long total_duration;
    private long load_duration;
    private int prompt_eval_count;
    private long prompt_eval_duration;
    private int eval_count;
    private long eval_duration;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
