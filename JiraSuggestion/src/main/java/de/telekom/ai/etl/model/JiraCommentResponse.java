package de.telekom.ai.etl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraCommentResponse {
    private int startAt;
    private int maxResults;
    private int total;
    private List<JiraComment> comments;
}

